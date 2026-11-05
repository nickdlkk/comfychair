package sh.hnet.comfychair.connection

import java.io.IOException
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import sh.hnet.comfychair.model.ServerImage
import sh.hnet.comfychair.model.ServerImageDiscovery

/**
 * Content-addressed upload reuse for one authenticated ComfyUIClient epoch. Memory only: never
 * stores credentials or image bytes beyond the caller's own request.
 *
 * Uploads are named by the SHA-256 of the exact encoded bytes and posted with overwrite=false, so
 * identical content re-uploads are answered from cache and the server's files stay immutable.
 * The server's returned filename/subfolder/type is kept as-is — a renamed collision never overwrites
 * the input a queued job still references. A cached entry is used only after an authenticated HEAD
 * confirms it still exists: 200 reuses it, 404 re-uploads, and any other status (401/403/5xx, no
 * HEAD support) stops rather than silently transferring the original again.
 *
 * Same-content requests share one flight: a waiting cancellation never cancels the leader, a leader
 * cancellation cancels its HTTP call and leaves retry unpoisoned. The cache is LRU-bounded.
 */
class ImageUploadReuse(
    private val http: OkHttpClient,
    private val base: HttpUrl,
    private val capacity: Int = 128
) {
    class HttpFailure(val status: Int) : IOException("Image transfer HTTP $status")

    @Volatile private var active = true

    /** The client epoch (protocol, credentials, server) changed; refuse and cancel further work. */
    fun invalidate() {
        active = false
        http.dispatcher.cancelAll()
    }

    private fun requireActive() {
        if (!active) throw kotlinx.coroutines.CancellationException("Image upload owner changed")
    }

    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, ServerImage>(16, .75f, true)
    private val flights = mutableMapOf<String, CompletableDeferred<ServerImage>>()

    /** Upload [bytes] (already encoded, [extension] in png/jpg) and reuse a prior identical upload. */
    suspend fun upload(
        bytes: ByteArray,
        extension: String,
        progress: ((Long, Long) -> Unit)? = null
    ): ServerImage {
        requireActive()
        require(bytes.isNotEmpty() && extension in setOf("png", "jpg"))
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val key = "$hash.$extension"
        var leader = false
        val flight = mutex.withLock {
            flights[key] ?: run {
                check(flights.size < capacity) { "Too many pending image uploads" }
                leader = true
                CompletableDeferred<ServerImage>().also { flights[key] = it }
            }
        }
        if (!leader) return flight.await()
        try {
            val cached = mutex.withLock { cache[key] }
            val image = if (cached != null && exists(cached)) cached else {
                // Never overwrite queued-job inputs, including when the server renames a collision.
                val payload = object : RequestBody() {
                    override fun contentType() =
                        (if (extension == "jpg") "image/jpeg" else "image/png").toMediaType()

                    override fun contentLength() = bytes.size.toLong()

                    override fun writeTo(sink: okio.BufferedSink) {
                        var offset = 0
                        while (offset < bytes.size) {
                            val count = minOf(8192, bytes.size - offset)
                            sink.write(bytes, offset, count)
                            offset += count
                            progress?.invoke(offset.toLong(), bytes.size.toLong())
                        }
                    }
                }
                val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("image", "comfychair_$key", payload)
                    .addFormDataPart("type", "input")
                    .addFormDataPart("subfolder", "")
                    .addFormDataPart("overwrite", "false")
                    .build()
                request(
                    Request.Builder()
                        .url(base.newBuilder().addPathSegments("upload/image").build())
                        .post(body).build()
                ) { response ->
                    if (!response.isSuccessful) throw HttpFailure(response.code)
                    val raw = response.body?.byteStream()?.readNBytes(8193)
                        ?: throw IOException("Empty upload response")
                    if (raw.size > 8192) throw IOException("Upload response too large")
                    val json = try {
                        JSONObject(raw.toString(Charsets.UTF_8))
                    } catch (e: org.json.JSONException) {
                        throw IOException("Invalid upload response", e)
                    }
                    val name = json.optString("name", "")
                    val folder = json.optString("subfolder", "")
                    val type = json.optString("type", "input")
                    ServerImageDiscovery.descriptor(
                        (if (folder.isEmpty()) "" else "$folder/") + name, type
                    )?.takeIf { it.type == "input" } ?: throw IOException("Invalid upload descriptor")
                }
            }
            currentCoroutineContext().ensureActive()
            requireActive()
            mutex.withLock {
                cache[key] = image
                while (cache.size > capacity) cache.remove(cache.keys.first())
            }
            flight.complete(image)
            return image
        } catch (e: Throwable) {
            flight.completeExceptionally(e)
            throw e
        } finally {
            // Cleanup must run even when the leader is cancelled.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                mutex.withLock { if (flights[key] === flight) flights.remove(key) }
            }
        }
    }

    /** HEAD the cached descriptor: 200 reuse, 404 re-upload, anything else stops (no fallback). */
    suspend fun exists(image: ServerImage): Boolean = request(
        Request.Builder().url(image.viewUrl(base)).head().build()
    ) { response ->
        when (response.code) {
            200 -> true
            404 -> false
            // Unsupported HEAD is not evidence of absence. Stop rather than transfer originals.
            else -> throw HttpFailure(response.code)
        }
    }

    private suspend fun <T> request(request: Request, read: (Response) -> T): T =
        suspendCancellableCoroutine { continuation ->
            requireActive()
            val call = http.newCall(request)
            call.timeout().timeout(60, TimeUnit.SECONDS)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(runCatching { response.use(read) })
                }
            })
        }
}

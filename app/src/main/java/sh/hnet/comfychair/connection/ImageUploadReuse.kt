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

/** One instance per authenticated ComfyUIClient epoch. Memory only: never stores credentials/bytes.
 * Files are immutable, SHA-256 addressed and overwrite=false. HEAD never transfers the original.
 */
class ImageUploadReuse(private val http: OkHttpClient, private val base: HttpUrl,
    private val capacity: Int = 128,
    private val disk: sh.hnet.comfychair.cache.PrivateImageDiskCache? = null,
    private val namespace: String = "") {
    class HttpFailure(val status: Int) : IOException("Image transfer HTTP $status")
    @Volatile private var active = true
    fun invalidate() { active = false; http.dispatcher.cancelAll() }
    private fun requireActive() {
        if (!active) throw kotlinx.coroutines.CancellationException("Image upload owner changed")
    }
    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, ServerImage>(16, .75f, true)
    suspend fun clearMemoryCache() = mutex.withLock { cache.clear(); cacheGeneration = disk?.generation() }
    private var cacheGeneration = disk?.generation()
    private val flights = mutableMapOf<String, CompletableDeferred<ServerImage>>()

    suspend fun upload(bytes: ByteArray, extension: String,
        progress: ((Long, Long) -> Unit)? = null): ServerImage {
        requireActive()
        require(bytes.isNotEmpty() && extension in setOf("png", "jpg"))
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val key = "$hash.$extension"
        val generation = disk?.generation()
        var leader = false
        val flight = mutex.withLock {
            if (cacheGeneration != generation) { cache.clear(); cacheGeneration = generation }
            flights[key] ?: run {
                check(flights.size < capacity) { "Too many pending image uploads" }
                leader = true
                CompletableDeferred<ServerImage>().also { flights[key] = it }
            }
        }
        if (!leader) return flight.await()
        try {
            val cached = mutex.withLock { cache[key] } ?: disk?.get(namespace, "upload:$key", 7 * 24 * 60 * 60 * 1000L)?.let {
                try {
                    val json = JSONObject(it.toString(Charsets.UTF_8))
                    ServerImageDiscovery.descriptor(json.getString("path"), "input")
                } catch (_: Exception) { disk.remove(namespace, "upload:$key"); null }
            }
            val image = if (cached != null && exists(cached)) cached else {
                // Never overwrite queued-job inputs, including when the server renames a collision.
                val payload = object : RequestBody() {
                    override fun contentType() = (if (extension == "jpg") "image/jpeg" else "image/png").toMediaType()
                    override fun contentLength() = bytes.size.toLong()
                    override fun writeTo(sink: okio.BufferedSink) {
                        var offset = 0
                        while (offset < bytes.size) {
                            val count = minOf(8192, bytes.size - offset)
                            sink.write(bytes, offset, count); offset += count
                            progress?.invoke(offset.toLong(), bytes.size.toLong())
                        }
                    }
                }
                val tracked = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("image", "comfychair_$key", payload)
                    .addFormDataPart("type", "input").addFormDataPart("subfolder", "")
                    .addFormDataPart("overwrite", "false").build()
                request(Request.Builder().url(base.newBuilder().addPathSegments("upload/image").build()).post(tracked).build()) { response ->
                    if (!response.isSuccessful) throw HttpFailure(response.code)
                    val raw = response.body?.byteStream()?.readNBytes(8193) ?: throw IOException("Empty upload response")
                    if (raw.size > 8192) throw IOException("Upload response too large")
                    val json = try { JSONObject(raw.toString(Charsets.UTF_8)) }
                        catch (e: org.json.JSONException) { throw IOException("Invalid upload response", e) }
                    val name = json.optString("name", "")
                    val folder = json.optString("subfolder", "")
                    val type = json.optString("type", "input")
                    ServerImageDiscovery.descriptor((if (folder.isEmpty()) "" else "$folder/") + name, type)
                        ?.takeIf { it.type == "input" } ?: throw IOException("Invalid upload descriptor")
                }
            }
            currentCoroutineContext().ensureActive()
            requireActive()
            mutex.withLock {
                if (disk?.generation() == generation) cache[key] = image
                while (cache.size > capacity) cache.remove(cache.keys.first())
            }
            disk?.put(namespace, "upload:$key", JSONObject().put("path", image.path).toString().toByteArray(Charsets.UTF_8), generation!!)
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

    suspend fun exists(image: ServerImage): Boolean = request(
        Request.Builder().url(image.viewUrl(base)).head().build()) { response ->
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
                override fun onFailure(call: Call, e: IOException) { continuation.resumeWith(Result.failure(e)) }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(runCatching { response.use(read) })
                }
            })
        }
}

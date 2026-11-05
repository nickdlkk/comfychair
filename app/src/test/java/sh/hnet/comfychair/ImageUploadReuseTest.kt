package sh.hnet.comfychair

import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import sh.hnet.comfychair.connection.ImageUploadReuse
import sh.hnet.comfychair.model.AuthCredentials
import java.util.concurrent.TimeUnit

/** Behavioural tests for content-addressed upload reuse over MockWebServer. */
class ImageUploadReuseTest {
    private val bytes = "exact encoded PNG including alpha mask".toByteArray()

    private fun response(name: String = "saved.png", folder: String = "") = MockResponse().setBody(
        """{"name":"$name","subfolder":"$folder","type":"input"}"""
    )

    private fun client(server: MockWebServer) = ComfyUIClient(hostname = server.hostName, port = server.port)
        .apply { setWorkingProtocol("http") }

    @Test
    fun repeatedExactBytesOnlyPostOnceAndHeadDoesNotDownload() = runBlocking {
        MockWebServer().use { s ->
            val c = client(s)
            s.enqueue(response("renamed.png", "nested")); s.enqueue(MockResponse())
            val first = c.uploadImageReusing(bytes, "png")
            assertEquals(first, c.uploadImageReusing(bytes, "png"))
            assertEquals("nested/renamed.png", first.path)
            val post = s.takeRequest(); val head = s.takeRequest()
            assertEquals("POST", post.method); assertEquals("HEAD", head.method)
            assertEquals("input", head.requestUrl!!.queryParameter("type"))
            assertEquals("nested", head.requestUrl!!.queryParameter("subfolder"))
            val body = post.body.readUtf8()
            assertTrue(body.contains("name=\"overwrite\"\r\nContent-Length: 5\r\n\r\nfalse"))
            assertTrue(body.contains("comfychair_")); assertEquals(2, s.requestCount)
        }
    }

    @Test
    fun changedMaskOrEncodedBytesAndFormatCreateDistinctImmutableNames() = runBlocking {
        MockWebServer().use { s ->
            val c = client(s)
            repeat(3) { s.enqueue(response()) }
            c.uploadImageReusing(bytes, "png")
            c.uploadImageReusing(bytes + 1.toByte(), "png")
            c.uploadImageReusing(bytes, "jpg")
            val bodies = (1..3).map { s.takeRequest().body.readUtf8() }
            val names = bodies.map { Regex("filename=\"([^\"]+)\"").find(it)!!.groupValues[1] }
            assertEquals(3, names.toSet().size)
            assertTrue(names.last().endsWith(".jpg")); assertTrue(bodies.last().contains("image/jpeg"))
        }
    }

    @Test
    fun missingReuploadsButAuthenticationAndServerErrorsStop() = runBlocking {
        MockWebServer().use { s ->
            val c = client(s)
            s.enqueue(response()); c.uploadImageReusing(bytes, "png")
            s.enqueue(MockResponse().setResponseCode(404)); s.enqueue(response("new.png"))
            assertEquals("new.png", c.uploadImageReusing(bytes, "png").filename)
            for (status in listOf(401, 403, 500, 405)) {
                s.enqueue(MockResponse().setResponseCode(status))
                val failure = runCatching { c.uploadImageReusing(bytes, "png") }.exceptionOrNull()
                assertEquals(status, (failure as ImageUploadReuse.HttpFailure).status)
            }
            val methods = (1..7).map { s.takeRequest().method }
            assertEquals(listOf("POST", "HEAD", "POST", "HEAD", "HEAD", "HEAD", "HEAD"), methods)
        }
    }

    @Test
    fun failedUploadIsNotCached() = runBlocking {
        MockWebServer().use { s ->
            val c = client(s)
            s.enqueue(MockResponse().setResponseCode(500)); s.enqueue(response())
            assertTrue(runCatching { c.uploadImageReusing(bytes, "png") }.isFailure)
            c.uploadImageReusing(bytes, "png")
            assertEquals(listOf("POST", "POST"), (1..2).map { s.takeRequest().method })
        }
    }

    @Test
    fun concurrentSameBytesSingleFlightAndWaiterCancellationDoesNotCancelLeader() = runBlocking {
        MockWebServer().use { s ->
            val c = client(s)
            s.enqueue(response().setBodyDelay(300, TimeUnit.MILLISECONDS))
            val first = async { c.uploadImageReusing(bytes, "png") }
            withContext(Dispatchers.IO) { assertNotNull(s.takeRequest(5, TimeUnit.SECONDS)) }
            val waiter = async { c.uploadImageReusing(bytes, "png") }
            yield(); waiter.cancelAndJoin()
            val others = (1..8).map { async { c.uploadImageReusing(bytes, "png") } }
            val result = first.await()
            others.forEach { assertEquals(result, it.await()) }
            assertEquals(1, s.requestCount)
        }
    }

    @Test
    fun leaderCancellationCancelsHttpAndRetryCanUpload() = runBlocking {
        MockWebServer().use { s ->
            val c = client(s)
            s.enqueue(response().setBodyDelay(1, TimeUnit.SECONDS))
            val task = launch { c.uploadImageReusing(bytes, "png") }
            withContext(Dispatchers.IO) { assertNotNull(s.takeRequest(5, TimeUnit.SECONDS)) }
            task.cancelAndJoin(); assertTrue(task.isCancelled)
            s.enqueue(response()); c.uploadImageReusing(bytes, "png")
            assertEquals("POST", s.takeRequest().method)
        }
    }

    @Test
    fun concurrentFailureIsSharedAndRetryNotPoisoned() = runBlocking {
        MockWebServer().use { s ->
            val c = client(s)
            s.enqueue(MockResponse().setResponseCode(500).setHeadersDelay(200, TimeUnit.MILLISECONDS))
            val tasks = (1..8).map { async { runCatching { c.uploadImageReusing(bytes, "png") } } }
            tasks.forEach { assertTrue(it.await().isFailure) }
            assertEquals(1, s.requestCount)
            s.enqueue(response()); c.uploadImageReusing(bytes, "png")
            assertEquals(2, s.requestCount)
        }
    }

    @Test
    fun clientServerAndAuthenticationEpochAreIsolated() = runBlocking {
        MockWebServer().use { a -> MockWebServer().use { b ->
            val c = client(a)
            a.enqueue(response()); c.uploadImageReusing(bytes, "png")
            a.enqueue(response()); client(a).uploadImageReusing(bytes, "png")
            c.setCredentials(AuthCredentials.Bearer("fixture"))
            a.enqueue(response()); c.uploadImageReusing(bytes, "png")
            b.enqueue(response()); client(b).uploadImageReusing(bytes, "png")
            assertEquals(3, a.requestCount); assertEquals(1, b.requestCount)
            repeat(2) { assertEquals("POST", a.takeRequest().method) }
            assertEquals("Bearer fixture", a.takeRequest().getHeader("Authorization"))
        } }
    }

    @Test
    fun protocolChangeInvalidatesReuseEpoch() {
        val c = client(MockWebServer())  // unused server; identity only
        val before = c.imageUploadIdentity()
        c.setWorkingProtocol("https")
        assertNotEquals(before, c.imageUploadIdentity())
        c.setWorkingProtocol("https")
        assertEquals(c.imageUploadIdentity(), c.imageUploadIdentity())
    }

    @Test
    fun boundedLruEvictionReuploadsWithoutDeletingServerFiles() = runBlocking {
        MockWebServer().use { s ->
            val reuse = ImageUploadReuse(okhttp3.OkHttpClient(), s.url("/"), 1)
            repeat(3) { s.enqueue(response()) }
            reuse.upload(bytes, "png"); reuse.upload(byteArrayOf(1), "png"); reuse.upload(bytes, "png")
            assertEquals(listOf("POST", "POST", "POST"), (1..3).map { s.takeRequest().method })
        }
    }

    @Test
    fun networkErrorOnHeadStopsWithoutReupload() = runBlocking {
        MockWebServer().use { s ->
            val c = client(s)
            s.enqueue(response()); c.uploadImageReusing(bytes, "png"); s.takeRequest()
            s.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            // OkHttp may retry a safe HEAD, but must never retry it as POST.
            s.enqueue(MockResponse().setResponseCode(503))
            assertTrue(runCatching { c.uploadImageReusing(bytes, "png") }.isFailure)
            assertEquals("HEAD", s.takeRequest().method)
            while (true) {
                val request = s.takeRequest(100, TimeUnit.MILLISECONDS) ?: break
                assertEquals("HEAD", request.method)
            }
        }
    }

    @Test
    fun authenticationChangeDuringUploadRejectsOldCompletion() = runBlocking {
        MockWebServer().use { s ->
            val c = client(s)
            s.enqueue(response().setBodyDelay(200, TimeUnit.MILLISECONDS))
            val task = async { runCatching { c.uploadImageReusing(bytes, "png") } }
            withContext(Dispatchers.IO) { assertNotNull(s.takeRequest(5, TimeUnit.SECONDS)) }
            c.setCredentials(AuthCredentials.Bearer("new-fixture"))
            assertTrue(task.await().exceptionOrNull() is CancellationException)
            s.enqueue(response()); c.uploadImageReusing(bytes, "png")
            assertEquals("Bearer new-fixture", s.takeRequest().getHeader("Authorization"))
        }
    }

    @Test
    fun uploadProgressCountsPayloadNotMultipartEnvelope() = runBlocking {
        MockWebServer().use { s ->
            val c = client(s); val progress = mutableListOf<Pair<Long, Long>>()
            s.enqueue(response())
            c.uploadImageReusing(ByteArray(20000), "png") { written, total -> progress.add(written to total) }
            assertEquals(20000L to 20000L, progress.last())
            assertTrue(progress.all { it.first <= it.second && it.second == 20000L })
        }
    }

    @Test
    fun malformedDescriptorNeverCachedAndOutputUploadResponseRejected() = runBlocking {
        MockWebServer().use { s ->
            val c = client(s)
            s.enqueue(MockResponse().setBody("""{"name":"x.png","type":"output"}"""))
            assertTrue(runCatching { c.uploadImageReusing(bytes, "png") }.isFailure)
            s.enqueue(response()); c.uploadImageReusing(bytes, "png")
            assertEquals(2, s.requestCount)
        }
    }
}

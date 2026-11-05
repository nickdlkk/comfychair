package sh.hnet.comfychair

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import sh.hnet.comfychair.cache.*
import sh.hnet.comfychair.connection.*
import sh.hnet.comfychair.model.*
import sh.hnet.comfychair.viewmodel.*

class PersistentServerImageTest {
    private fun directory() = Files.createTempDirectory(File(System.getProperty("java.io.tmpdir") ?: error("No scratch directory")).toPath(), "picker-test-").toFile()
    @Test fun recreationHitMissAndNoPlaintextIdentity() {
        val dir = directory()
        try {
            val first = PrivateImageDiskCache(dir)
            val owner = first.namespace(listOf("https://host", "secret-password"))
            first.put(owner, "thumb:private-name", byteArrayOf(1, 2, 3))
            val second = PrivateImageDiskCache(dir)
            assertEquals(owner, second.namespace(listOf("https://host", "secret-password")))
            assertArrayEquals(byteArrayOf(1, 2, 3), second.get(owner, "thumb:private-name", 1000))
            assertNull(second.get(second.namespace(listOf("https://other", "secret-password")), "thumb:private-name", 1000))
            assertNull(second.get(second.namespace(listOf("https://host", "other-account")), "thumb:private-name", 1000))
            assertTrue(dir.listFiles()!!.none { it.name.contains("private-name") || it.readBytes().toString(Charsets.UTF_8).contains("secret-password") })
        } finally { dir.deleteRecursively() }
    }
    @Test fun ttlEvictionAndCorruptionRecovery() {
        val dir = directory(); var now = 1000L
        try {
            val disk = PrivateImageDiskCache(dir, maxBytes = 100, maxEntries = 1, clock = { now })
            disk.put("owner", "one", byteArrayOf(1)); now++
            disk.put("owner", "two", byteArrayOf(2))
            assertNull(disk.get("owner", "one", 100))
            now += 101
            assertNull(disk.get("owner", "two", 100))
            disk.put("owner", "three", byteArrayOf(3))
            dir.listFiles()!!.single { it.extension == "entry" }.writeBytes(byteArrayOf(0))
            assertNull(disk.get("owner", "three", 100))
            disk.put("owner", "three", byteArrayOf(4))
            assertArrayEquals(byteArrayOf(4), disk.get("owner", "three", 100))
            disk.put("owner", "oversized", ByteArray(101))
            assertNull(disk.get("owner", "oversized", 100))
        } finally { dir.deleteRecursively() }
    }
    @Test fun listSourceQuerySortOwnerPartitionAndRecreation() {
        val dir = directory()
        try {
            val page = ServerImagePage(listOf(ServerImage("a.png", "dir", "input", "id")), nextCursor = "next", assets = true, totalAssets = 40)
            val query = ServerImageQuery("中文 +", ServerImageSort.SIZE)
            ServerImageListDiskCache(PrivateImageDiskCache(dir), "one").put(ServerImageSource.INPUT, query, page)
            val cache = ServerImageListDiskCache(PrivateImageDiskCache(dir), "one")
            assertEquals(page, cache.get(ServerImageSource.INPUT, query))
            assertNull(cache.get(ServerImageSource.OUTPUT, query))
            assertNull(cache.get(ServerImageSource.INPUT, query.copy(name = "other")))
            assertNull(cache.get(ServerImageSource.INPUT, query.copy(sort = ServerImageSort.NAME)))
            assertNull(ServerImageListDiskCache(PrivateImageDiskCache(dir), "two").get(ServerImageSource.INPUT, query))
        } finally { dir.deleteRecursively() }
    }
    @Test fun cachedGridSurvivesBackgroundRefreshAndFailure() = runBlocking {
        val owner = ServerImageIdentity("one", Any())
        val old = ServerImage("old.png", "", "input")
        val fresh = ServerImage("new.png", "", "input")
        var gate = CompletableDeferred<ServerImagePage>()
        val picker = ServerImagePicker(this, { owner }, { true }, { _, _, _ -> gate.await() },
            { _, image -> image }, { _, _, _ -> }, cached = { _, _, _ -> ServerImagePage(listOf(old)) })
        picker.open(1, ServerImageSource.INPUT); yield()
        assertEquals(listOf(old), picker.state.value.images)
        assertTrue(picker.state.value.loading)
        gate.complete(ServerImagePage(listOf(fresh))); yield()
        assertEquals(listOf(fresh), picker.state.value.images)
        gate = CompletableDeferred()
        picker.refresh(); yield()
        assertEquals(listOf(fresh), picker.state.value.images)
        gate.completeExceptionally(java.io.IOException("fixture")); yield()
        assertEquals(listOf(fresh), picker.state.value.images)
        assertTrue(picker.state.value.error)
        picker.close()
    }
    private fun template(type: String = "LoadImage", image: String = "{{image_filename}}") =
        """{"prompt":{"1":{"class_type":"$type","inputs":{"image":"$image"}}}}"""
    @Test fun directProvenanceRejectsMutationCompressionMaskOwnerAndCustomNodes() {
        val epoch = Any(); val bitmap = Any()
        val source = DirectInputSource(ServerImage("a.png", "dir", "input"), epoch, bitmap, 3)
        assertTrue(source.eligible(epoch, bitmap, 3, false, false, DirectInputSource.supports(template(), 1)))
        assertFalse(source.eligible(Any(), bitmap, 3, false, false, true))
        assertFalse(source.eligible(epoch, Any(), 3, false, false, true))
        assertFalse(source.eligible(epoch, bitmap, 4, false, false, true))
        assertFalse(source.eligible(epoch, bitmap, 3, true, false, true))
        assertFalse(source.eligible(epoch, bitmap, 3, false, true, true))
        assertFalse(DirectInputSource.supports(template("CustomLoadImage"), 1))
        assertFalse(DirectInputSource.supports(template(image = "prefix {{image_filename}}"), 1))
        assertTrue(DirectInputSource.supports(template(image = "{{image_filename}} [input]"), 1))
        assertFalse(DirectInputSource.supports(template(image = "{{image_filename}} [input] [input]"), 1))
        assertFalse(DirectInputSource(ServerImage("a.png", "", "output"), epoch, bitmap, 3).eligible(epoch, bitmap, 3, false, false, true))
    }
    @Test fun directInputUsesHeadNoPostAnd404OnlyFallback() = runBlocking {
        MockWebServer().use { server ->
            val client = ComfyUIClient(hostname = server.hostName, port = server.port, credentials = AuthCredentials.Bearer("fixture"))
            client.setWorkingProtocol("http")
            val image = ServerImage("中文 +.png", "nested", "input")
            val epoch = client.imageUploadIdentity()
            server.enqueue(MockResponse().setResponseCode(200))
            assertTrue(client.reuseInputImage(image, epoch))
            assertEquals("HEAD", server.takeRequest().method)
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(MockResponse().setBody("""{"name":"fallback.png","type":"input"}"""))
            if (!client.reuseInputImage(image, epoch)) client.uploadImageReusing(byteArrayOf(1), "png")
            assertEquals("HEAD", server.takeRequest().method)
            assertEquals("POST", server.takeRequest().method)
            for (status in listOf(401, 403, 405, 500)) {
                server.enqueue(MockResponse().setResponseCode(status))
                assertTrue(runCatching { client.reuseInputImage(image, epoch) }.exceptionOrNull() is ImageUploadReuse.HttpFailure)
                assertEquals("HEAD", server.takeRequest().method)
                assertNull(server.takeRequest(20, TimeUnit.MILLISECONDS))
            }
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(MockResponse().setResponseCode(503)) // safe HEAD may retry, never POST
            assertTrue(runCatching { client.reuseInputImage(image, epoch) }.isFailure)
            assertEquals("HEAD", server.takeRequest().method)
            while (true) {
                val retry = server.takeRequest(50, TimeUnit.MILLISECONDS) ?: break
                assertEquals("HEAD", retry.method)
            }
            client.setCredentials(AuthCredentials.Bearer("other"))
            assertTrue(runCatching { client.reuseInputImage(image, epoch) }.exceptionOrNull() is CancellationException)
            assertNull(server.takeRequest(20, TimeUnit.MILLISECONDS))
        }
    }
    @Test fun changedCompressedMaskedAndOwnerMismatchUploadInsteadOfDirect() = runBlocking {
        MockWebServer().use { server ->
            val client = ComfyUIClient(hostname = server.hostName, port = server.port).apply { setWorkingProtocol("http") }
            val epoch = client.imageUploadIdentity(); val bitmap = Any()
            val source = DirectInputSource(ServerImage("original.png", "", "input"), epoch, bitmap, 1)
            for (case in 0..4) {
                val eligible = source.eligible(if (case == 0) Any() else epoch, if (case == 1) Any() else bitmap,
                    if (case == 2) 2 else 1, case == 3, case == 4, true)
                assertFalse(eligible)
                server.enqueue(MockResponse().setBody("""{"name":"saved$case.png","type":"input"}"""))
                if (eligible) client.reuseInputImage(source.image, epoch)
                else client.uploadImageReusing(byteArrayOf(case.toByte()), "png")
                assertEquals("POST", server.takeRequest().method)
            }
            assertEquals(5, server.requestCount)
        }
    }
    @Test fun directHeadCancellationStopsWithoutUpload() = runBlocking {
        MockWebServer().use { server ->
            val client = ComfyUIClient(hostname = server.hostName, port = server.port).apply { setWorkingProtocol("http") }
            val epoch = client.imageUploadIdentity()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val pending = async(Dispatchers.IO) { client.reuseInputImage(ServerImage("a.png", "", "input"), epoch) }
            assertEquals("HEAD", server.takeRequest(5, TimeUnit.SECONDS)!!.method)
            pending.cancelAndJoin()
            assertEquals(1, server.requestCount)
            assertNull(server.takeRequest(20, TimeUnit.MILLISECONDS))
            client.shutdown()
        }
    }
    @Test fun persistentUploadRecreationHeadsInsteadOfPost() = runBlocking {
        val dir = directory()
        try { MockWebServer().use { server ->
            val disk = PrivateImageDiskCache(dir)
            val http = OkHttpClient()
            val bytes = byteArrayOf(1, 2)
            server.enqueue(MockResponse().setBody("""{"name":"saved.png","subfolder":"actual","type":"input"}"""))
            ImageUploadReuse(http, server.url("/"), disk = disk, namespace = "owner").upload(bytes, "png")
            assertEquals("POST", server.takeRequest().method)
            server.enqueue(MockResponse().setResponseCode(200))
            val result = ImageUploadReuse(http, server.url("/"), disk = PrivateImageDiskCache(dir), namespace = "owner").upload(bytes, "png")
            assertEquals("actual/saved.png", result.path)
            assertEquals("HEAD", server.takeRequest().method)
            assertEquals(2, server.requestCount)
        } } finally { dir.deleteRecursively() }
    }
}

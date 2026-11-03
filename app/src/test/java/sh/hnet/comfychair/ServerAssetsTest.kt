package sh.hnet.comfychair

import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import sh.hnet.comfychair.model.*
import sh.hnet.comfychair.viewmodel.*
import java.io.File

class ServerAssetsTest {
    private val base = "http://localhost:8188/".toHttpUrl()
    private fun asset(preview: String = "/api/view?type=input&filename=a.png") = JSONObject()
        .put("id", "id-1").put("name", "a.png").put("mime_type", "image/png")
        .put("tags", org.json.JSONArray(listOf("input", "models"))).put("preview_url", preview)
    private fun page(cursor: String? = null) = JSONObject().put("assets", org.json.JSONArray().put(asset()))
        .put("total", 123).put("has_more", cursor != null).put("next_cursor", cursor).toString()
    private fun client(s: MockWebServer) = ComfyUIClient(hostname = s.hostName, port = s.port,
        credentials = AuthCredentials.Bearer("fixture")).apply { setWorkingProtocol("http") }

    @Test fun nativeNamespaceMimeAndRelativePreviewAreRequired() {
        assertNotNull(ServerAssetDiscovery.image(asset(), ServerImageSource.INPUT, base))
        for (url in listOf("http://evil.test/view?type=input&filename=a.png", "//evil.test/view", "/api/view?type=output&filename=a.png",
            "/api/view?type=input&filename=../a.png", "/api/view?type=input&filename=a.png&type=output", "/api/assets/x/content"))
            assertNull(ServerAssetDiscovery.image(asset(url), ServerImageSource.INPUT, base))
        assertNull(ServerAssetDiscovery.image(asset().put("mime_type", "video/mp4"), ServerImageSource.INPUT, base))
        assertNull(ServerAssetDiscovery.image(asset().put("loader_path", "a.png").put("preview_url", JSONObject.NULL), ServerImageSource.INPUT, base))
    }

    @Test fun cursorAndAssetTotalAreNotImageCount() {
        val result = ServerAssetDiscovery.page(JSONObject(page("next")), ServerImageSource.INPUT, base, null)
        assertEquals(123L, result.totalAssets)
        assertEquals(1, result.images.size)
        assertEquals("next", result.nextCursor)
        assertTrue(runCatching { ServerAssetDiscovery.page(JSONObject(page("next")), ServerImageSource.INPUT, base, "next") }.isFailure)
    }

    @Test fun authenticatedSearchSortCursorAndIdOriginal() = runBlocking {
        MockWebServer().use { s ->
            val c = client(s)
            s.enqueue(MockResponse().setBody("{\"assets\":true}"))
            s.enqueue(MockResponse().setBody(page("cursor+")))
            val first = c.browseSourceImages(ServerImageSource.INPUT, ServerImageQuery("中文 +", ServerImageSort.NAME))
            assertEquals("/api/features", s.takeRequest().path)
            val r = s.takeRequest()
            assertEquals("Bearer fixture", r.getHeader("Authorization"))
            assertEquals("中文 +", r.requestUrl!!.queryParameter("name_contains"))
            assertEquals("name", r.requestUrl!!.queryParameter("sort"))
            assertEquals("50", r.requestUrl!!.queryParameter("limit"))
            assertEquals("missing", r.requestUrl!!.queryParameter("tags_none"))
            s.enqueue(MockResponse().setBody(page()))
            c.browseSourceImages(ServerImageSource.INPUT, ServerImageQuery(cursor = first.nextCursor))
            assertEquals("cursor+", s.takeRequest().requestUrl!!.queryParameter("after"))
            s.enqueue(MockResponse().setBody("original"))
            assertEquals("original", c.downloadSourceImage(first.images.single()).toString(Charsets.UTF_8))
            assertEquals("/api/assets/id-1/content", s.takeRequest().path)
        }
    }

    @Test fun onlyUnsupportedAndDisabledFallback() = runBlocking {
        for (status in listOf(401, 403, 500, 503)) MockWebServer().use { s ->
            val c = client(s)
            s.enqueue(MockResponse().setResponseCode(status).setBody("{}"))
            assertTrue(runCatching { c.browseSourceImages(ServerImageSource.INPUT, ServerImageQuery()) }.isFailure)
            assertEquals(1, s.requestCount)
        }
        for (mode in listOf("false", "404", "disabled")) MockWebServer().use { s ->
            val c = client(s)
            s.enqueue(MockResponse().setBody("{\"assets\":${if (mode == "false") "false" else "true"}}"))
            if (mode != "false") s.enqueue(MockResponse().setResponseCode(if (mode == "404") 404 else 503)
                .setBody("{\"error\":{\"code\":\"SERVICE_DISABLED\"}}"))
            s.enqueue(MockResponse().setBody("{\"LoadImage\":{\"input\":{\"required\":{\"image\":[[\"a.png\"]]}}}}"))
            val result = c.browseSourceImages(ServerImageSource.INPUT, ServerImageQuery())
            assertFalse(result.assets); assertEquals(1, result.images.size)
        }
    }

    @Test fun redirectsNeverLeakOrFetchArbitraryDestinations() = runBlocking {
        MockWebServer().use { s -> MockWebServer().use { other ->
            val c = client(s)
            s.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/steal")))
            assertTrue(runCatching { c.downloadSourceImage(ServerImage("a.png", "", "input")) }.isFailure)
            assertEquals(0, other.requestCount)
        } }
    }

    @Test fun inputAssetsUseCursorAndCancelDownloadKeepsListSession() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val owner = ServerImageIdentity("a", Any())
        val img = ServerImage("a.png", "", "input", "id-1")
        val calls = mutableListOf<ServerImageQuery>()
        val pending = CompletableDeferred<String>()
        var accepted = false
        val picker = ServerImagePicker(scope, { owner }, { true }, { _, _, _ -> error("legacy") },
            { _, _ -> pending.await() }, { _, _, _ -> accepted = true }, browse = { _, _, q ->
                calls += q; ServerImagePage(listOf(img), nextCursor = if (q.cursor == null) "next" else null, assets = true, totalAssets = 9)
            })
        picker.open(2, ServerImageSource.INPUT)
        picker.loadMore()
        assertEquals("next", calls.last().cursor)
        assertEquals(1, picker.state.value.images.size)
        val session = picker.state.value.session
        picker.select(img)
        assertEquals(img, picker.state.value.selected)
        assertEquals(session, picker.state.value.session)
        picker.cancelDownload(); pending.complete("stale")
        assertFalse(accepted); assertEquals(listOf(img), picker.state.value.images)
        assertEquals(session, picker.state.value.session)
        picker.close(); scope.cancel()
    }

    @Test fun debounceClearsCursorAndCancelsOldQuery() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val owner = ServerImageIdentity("a", Any())
        val calls = mutableListOf<ServerImageQuery>()
        val picker = ServerImagePicker(scope, { owner }, { true }, { _, _, _ -> error("legacy") },
            { _, _ -> "x" }, { _, _, _ -> }, browse = { _, _, q ->
                calls += q; ServerImagePage(emptyList(), nextCursor = "next", assets = true)
            })
        picker.open(1, ServerImageSource.OUTPUT)
        picker.changeQuery("old", ServerImageSort.OLDEST)
        picker.changeQuery("new", ServerImageSort.SIZE)
        assertNull(picker.state.value.nextCursor)
        delay(400)
        assertEquals(listOf("", "new"), calls.map { it.name })
        assertNull(calls.last().cursor); assertEquals(ServerImageSort.SIZE, calls.last().sort)
        picker.close(); scope.cancel()
    }

    @Test fun assetCacheIdentitySeparatesSameNameAndByteEviction() {
        val a = ServerImage("a.png", "", "input", "id-1")
        val b = a.copy(assetId = "id-2")
        assertNotEquals(a.stableKey, b.stableKey)
        val cache = ServerImagePreviewCache<Pair<ServerImageIdentity, ServerImage>, String>(4, 2)
        val owner = ServerImageIdentity("s", Any())
        cache.put(owner to a, "one", 3)
        assertNull(cache.get(ServerImageIdentity("s", Any()) to a))
        cache.put(owner to b, "two", 3)
        assertNull(cache.get(owner to a)); assertEquals("two", cache.get(owner to b))
    }

    @Test fun sparseCursorCycleIsRetryableAndDownloadFailureKeepsSession() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val owner = ServerImageIdentity("a", Any())
        val image = ServerImage("a.png", "", "output", "id-1")
        var cursor = "A"
        var fail = true
        val picker = ServerImagePicker(scope, { owner }, { true }, { _, _, _ -> error("legacy") },
            { _, _ -> if (fail) error("download") else "ok" }, { _, _, _ -> }, browse = { _, _, q ->
                ServerImagePage(if (q.cursor == null) listOf(image) else emptyList(), nextCursor = cursor, assets = true)
            })
        picker.open(1, ServerImageSource.OUTPUT)
        cursor = "B"; picker.loadMore()
        assertEquals("B", picker.state.value.nextCursor)
        assertEquals(1, picker.state.value.images.size)
        cursor = "A"; picker.loadMore()
        assertTrue(picker.state.value.moreError)
        assertEquals("B", picker.state.value.nextCursor)
        cursor = "C"; picker.loadMore(retry = true)
        assertFalse(picker.state.value.moreError)
        val session = picker.state.value.session
        picker.select(image)
        assertTrue(picker.state.value.downloadError)
        assertEquals(session, picker.state.value.session)
        assertEquals(listOf(image), picker.state.value.images)
        fail = false; picker.retryDownload()
        assertNull(picker.state.value.slot)
        scope.cancel()
    }

    @Test fun liveReadOnlyAssetsWhenExplicitlyEnabled() = runBlocking {
        val host = System.getenv("COMFYCHAIR_LIVE_READONLY_HOST") ?: return@runBlocking
        require(host == "192.168.4.36")
        val c = ComfyUIClient(hostname = host, port = 8189).apply { setWorkingProtocol("http") }
        for (source in ServerImageSource.entries) {
            val first = c.browseSourceImages(source, ServerImageQuery())
            assertTrue(first.assets); assertTrue(first.images.isNotEmpty()); assertNotNull(first.nextCursor)
            val second = c.browseSourceImages(source, ServerImageQuery(cursor = first.nextCursor))
            assertTrue(second.assets); assertTrue(second.nextCursor != first.nextCursor)
            val filtered = c.browseSourceImages(source, ServerImageQuery("edit", ServerImageSort.NAME))
            assertTrue(filtered.assets)
            for (sort in ServerImageSort.entries) assertTrue(c.browseSourceImages(source, ServerImageQuery(sort = sort)).assets)
            val image = first.images.first()
            assertTrue(c.downloadSourcePreview(image).isNotEmpty())
            assertTrue(c.downloadSourceImage(image).isNotEmpty())
            println("LIVE_READONLY ${source.name}: firstImages=${first.images.size} totalAssets=${first.totalAssets} nextImages=${second.images.size} searchAssets=${filtered.totalAssets}; preview/content GET passed")
        }
    }

    @Test fun uiSeparatesVisibilityFromRequestPause() {
        val p = listOf(File("src/main/java/sh/hnet/comfychair/ui/components/ServerImagePickerPanel.kt"),
            File("app/src/main/java/sh/hnet/comfychair/ui/components/ServerImagePickerPanel.kt")).first { it.isFile }.readText()
        assertTrue(p.contains("image.stableKey in visible, !state.downloading"))
        assertTrue(p.contains("if (!requestAllowed || bitmap != null) return@LaunchedEffect"))
        assertTrue(p.contains("onCancelDownload")); assertTrue(p.contains("autoPages < 5"))
        assertFalse(p.contains("visible && !state.downloading"))
    }
}

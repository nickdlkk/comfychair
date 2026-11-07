package sh.hnet.comfychair

import sh.hnet.comfychair.util.RealtimeDiagnostics
import org.junit.Assert.*
import org.junit.Test
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class RealtimeDiagnosticsTest {
    private fun journal(): File = File(Files.createTempDirectory(
        File(System.getenv("TMPDIR") ?: System.getProperty("java.io.tmpdir")).toPath(), "diagnostics-test-").toFile(), "recent.log")

    @Test fun optInForegroundBatchingAndNoCredentials() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"ok\":true}"))
            val file = journal()
            val r = RealtimeDiagnostics(file, false)
            try {
                val url = server.url("/events").toString()
                r.configure(false, true, url); r.record("disabled"); r.tick()
                assertEquals(0, r.pendingCount())
                r.configure(true, false, url); r.record("local_only"); r.tick()
                assertTrue(file.readText().contains("local_only")); assertEquals(0, server.requestCount)
                r.configure(true, true, url); r.record("picker_open"); r.tick()
                assertEquals(0, server.requestCount)
                r.foreground(true); r.tick()
                val req = server.takeRequest(1, TimeUnit.SECONDS)!!
                assertEquals("/events", req.path); assertEquals("POST", req.method)
                assertNull(req.getHeader("Authorization")); assertNull(req.getHeader("Cookie"))
                assertTrue(req.body.readUtf8().contains("picker_open")); assertEquals(0, r.pendingCount())
            } finally { r.close() }
        }
    }

    @Test fun boundedQueueBackoffAndTargetChange() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            val r = RealtimeDiagnostics(journal(), false)
            try {
                r.configure(true, true, server.url("/events").toString()); r.foreground(true)
                repeat(1000) { r.record("event$it") }
                assertEquals(256, r.pendingCount()); r.tick(); r.tick()
                assertEquals(1, server.requestCount)
                val body = server.takeRequest().body.readUtf8()
                assertTrue(body.contains("\"dropped\":744"))
                assertTrue(body.contains("event744")); assertFalse(body.contains("event999"))
                r.configure(true, true, "http://127.0.0.1:9011/events")
                assertEquals(0, r.pendingCount())
            } finally { r.close() }
        }
    }

    @Test fun privateJournalRotatesAndFatalNeverIncludesMessage() {
        val file = journal(); val r = RealtimeDiagnostics(file, false)
        try {
            r.configure(true, false, "")
            repeat(12) { repeat(256) { r.record("x".repeat(1024)) }; r.tick() }
            r.fatal(IllegalStateException("SECRET token=123"))
            assertTrue(file.length() <= 128 * 1024)
            assertTrue(File(file.parentFile, file.name + ".previous").length() <= 128 * 1024)
            assertTrue(file.readText().contains("IllegalStateException")); assertFalse(file.readText().contains("SECRET"))
        } finally { r.close() }
    }

    @Test fun targetRejectsSecretsAndWrongProtocol() {
        assertTrue(RealtimeDiagnostics.validTarget("http://192.168.4.168:9011/events"))
        for (url in listOf("http://user:pass@localhost/events", "http://localhost/events?token=abc", "http://localhost/upload", "file:///events", "http://localhost/events#secret"))
            assertFalse(url, RealtimeDiagnostics.validTarget(url))
    }
}

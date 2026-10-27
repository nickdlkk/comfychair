package sh.hnet.comfychair.viewmodel

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class EditingViewerComparisonTest {
    private fun testCache() = Files.createTempDirectory(
        java.nio.file.Paths.get(System.getenv("TMPDIR") ?: System.getProperty("java.io.tmpdir")),
        "editing-viewer-test").toFile()
    @Test fun holdReleaseCancelAndNewGestureDoNotLatch() {
        val gesture = HoldComparisonGesture(true)
        assertFalse(gesture.showingOriginal)
        gesture.longPress(); assertTrue(gesture.showingOriginal)
        gesture.release(); assertFalse(gesture.showingOriginal)
        gesture.longPress(); assertFalse(gesture.showingOriginal)
        val cancelled = HoldComparisonGesture(true)
        cancelled.cancel(); cancelled.longPress(); assertFalse(cancelled.showingOriginal)
        val next = HoldComparisonGesture(true)
        assertFalse(next.showingOriginal)
        next.longPress(); next.cancel(); assertFalse(next.showingOriginal)
    }
    @Test fun ineligibleNeverShowsOriginal() {
        val gesture = HoldComparisonGesture(false)
        gesture.longPress(); assertFalse(gesture.showingOriginal)
    }
    @Test fun ticketRequiresExactServerResultAndPrivateSnapshot() {
        val cache = testCache()
        try {
            val file = java.io.File(cache, "editing-original-test.png").apply { writeText("snapshot") }
            val ticket = EditingViewerSnapshots.issue(EditingViewerSnapshots.Source(file, "serverA", "promptA", "out.png", "folder", "output"))
            fun resolve(server: String = "serverA", prompt: String = "promptA", filename: String = "out.png", folder: String = "folder", type: String = "output") =
                EditingViewerSnapshots.resolve(ticket, cache, server, prompt, filename, folder, type)
            assertEquals(file.canonicalFile, resolve())
            assertNull(resolve(server = "serverB")); assertNull(resolve(prompt = "promptB"))
            assertNull(resolve(filename = "other.png")); assertNull(resolve(folder = "other")); assertNull(resolve(type = "input"))
            assertNull(EditingViewerSnapshots.resolve("unknown", cache, "serverA", "promptA", "out.png", "folder", "output"))
            assertNull(EditingViewerSnapshots.resolve(ticket, java.io.File(cache, "other-cache"), "serverA", "promptA", "out.png", "folder", "output"))
            file.delete(); assertNull(resolve())
        } finally { cache.deleteRecursively() }
    }
    @Test fun arbitraryFileInsideCacheIsRejected() {
        val cache = testCache()
        try {
            val file = java.io.File(cache, "unrelated.png").apply { writeText("not a snapshot") }
            val ticket = EditingViewerSnapshots.issue(EditingViewerSnapshots.Source(file, "s", "p", "f", "", "output"))
            assertNull(EditingViewerSnapshots.resolve(ticket, cache, "s", "p", "f", "", "output"))
        } finally { cache.deleteRecursively() }
    }
}

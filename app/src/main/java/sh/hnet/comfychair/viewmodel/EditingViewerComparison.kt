package sh.hnet.comfychair.viewmodel

import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Process-only capability: no arbitrary paths or bitmaps accepted from an Intent. */
object EditingViewerSnapshots {
    data class Source(val file: File, val server: String, val prompt: String,
        val filename: String, val subfolder: String, val type: String)
    private val tickets = ConcurrentHashMap<String, Source>()
    @Synchronized fun issue(source: Source): String = UUID.randomUUID().toString().also {
        // Cap capabilities independently of cache files; eviction safely disables old launches.
        if (tickets.size >= 64) tickets.clear()
        tickets[it] = source
    }
    fun resolve(ticket: String?, cacheDir: File, server: String, prompt: String,
        filename: String, subfolder: String, type: String): File? {
        val source = tickets[ticket ?: return null] ?: return null
        if (server != source.server || prompt.isBlank() || prompt != source.prompt ||
            filename != source.filename || subfolder != source.subfolder || type != source.type) return null
        return try {
            source.file.canonicalFile.takeIf { it.parentFile == cacheDir.canonicalFile &&
                it.name.startsWith("editing-original-") && it.extension == "png" && it.isFile && it.length() > 0 }
        } catch (_: Exception) { null }
    }
}

/** One gesture only; movement, multitouch, cancellation and identity reset disarm it. */
internal class HoldComparisonGesture(private val eligible: Boolean) {
    var showingOriginal = false; private set
    private var armed = eligible
    fun longPress() { showingOriginal = armed }
    fun cancel() { armed = false; showingOriginal = false }
    fun release() = cancel()
}

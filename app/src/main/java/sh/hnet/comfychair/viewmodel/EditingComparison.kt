package sh.hnet.comfychair.viewmodel

import java.io.File

/** Snapshot is cache-owned, never a bitmap extra or an alias of a recyclable media cache. */
data class PreparedImageWorkflow(val json: String, val sourceSnapshot: File?, val unresolved: List<String> = emptyList())

/** Exact prompt association; unknown/restored/nonediting results intentionally have no original. */
internal class ResultSourceAssociation<T> {
    private val sources = java.util.concurrent.ConcurrentHashMap<String, T>()
    fun bind(promptId: String, source: T?) {
        if (source == null) sources.remove(promptId) else sources[promptId] = source
    }
    fun sourceFor(promptId: String): T? = sources[promptId]
}

internal fun dividerFraction(position: Float, width: Float): Float =
    if (!position.isFinite() || !width.isFinite() || width <= 0f) 0.5f
    else (position / width).coerceIn(0f, 1f)

/** Centered ContentScale.Fit geometry, shared by both sides (never fit the cropped half). */
internal data class FitBounds(val left: Float, val top: Float, val width: Float, val height: Float)
internal fun fitBounds(imageWidth: Int, imageHeight: Int, width: Float, height: Float): FitBounds {
    if (imageWidth <= 0 || imageHeight <= 0 || width <= 0 || height <= 0) return FitBounds(0f, 0f, 0f, 0f)
    val scale = minOf(width / imageWidth, height / imageHeight)
    val w = imageWidth * scale; val h = imageHeight * scale
    return FitBounds((width - w) / 2, (height - h) / 2, w, h)
}

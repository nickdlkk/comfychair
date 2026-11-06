package sh.hnet.comfychair.model

import okhttp3.HttpUrl

/**
 * One image on the ComfyUI server, as the upload API and /view address it: a filename, an optional
 * subfolder and a type ("input" or "output"). Only what upload reuse needs; the source picker's
 * richer discovery model is separate.
 */
data class ServerImage(val filename: String, val subfolder: String, val type: String) {
    /** The path form the API accepts and the /view endpoint addresses: "sub/filename" or "filename". */
    val path: String get() = if (subfolder.isEmpty()) filename else "$subfolder/$filename"

    fun viewUrl(base: HttpUrl): HttpUrl = base.newBuilder().addPathSegment("view")
        .addQueryParameter("filename", filename)
        .addQueryParameter("subfolder", subfolder)
        .addQueryParameter("type", type).build()
}

/** Validation for descriptor paths coming off the wire (upload responses, listings). */
object ServerImageDiscovery {
    private val extensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif", "tif", "tiff", "avif")

    /**
     * Parse a server-reported image path into a descriptor, or null when the path is not a plain
     * relative image path (absolute, traversal, control characters, unknown extension).
     */
    fun descriptor(path: String, type: String): ServerImage? {
        if (type !in setOf("input", "output") || path.isBlank() || path.startsWith('/') ||
            '\\' in path || ':' in path || path.any { it.code < 32 } ||
            path.split('/').any { it.isEmpty() || it == "." || it == ".." } ||
            path.substringAfterLast('.', "").lowercase() !in extensions) return null
        return ServerImage(path.substringAfterLast('/'), path.substringBeforeLast('/', ""), type)
    }
}

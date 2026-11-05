package sh.hnet.comfychair.connection

import org.json.JSONObject
import sh.hnet.comfychair.model.ServerImage

/** Provenance is deliberately process-only: restored/reencoded bitmaps are not originals.
 * Retains object identity AND mutation revision; never compares decoded pixels for equivalence.
 */
class DirectInputSource(val image: ServerImage, private val owner: Any,
    private val bitmap: Any, private val revision: Int) {
    fun eligible(currentOwner: Any, currentBitmap: Any, currentRevision: Int,
        compressed: Boolean, masked: Boolean, nativeMapping: Boolean): Boolean =
        image.type == "input" && owner === currentOwner && bitmap === currentBitmap && revision == currentRevision &&
            !compressed && !masked && nativeMapping

    companion object {
        /** Every occurrence must be the entire native LoadImage.image value (optional one suffix).
         * Unknown/custom consumers and magic filenames are not trusted implicitly.
         */
        fun supports(json: String, slot: Int): Boolean = runCatching {
            val token = "{{" + (if (slot == 1) "image_filename" else "image_filename_$slot") + "}}"
            val root = JSONObject(json)
            val nodes = root.optJSONObject("prompt") ?: root.optJSONObject("nodes") ?: root
            var seen = 0
            for (id in nodes.keys()) {
                val node = nodes.optJSONObject(id) ?: continue
                if (!node.toString().contains(token)) continue
                val inputs = node.optJSONObject("inputs") ?: return false
                val value = inputs.opt("image")
                if (node.optString("class_type") != "LoadImage" || value !in listOf(token, "$token [input]")) return false
                // Reject token also present in another input, metadata, or nested value.
                val copy = JSONObject(node.toString())
                copy.getJSONObject("inputs").remove("image")
                if (copy.toString().contains(token)) return false
                seen++
            }
            seen > 0 && Regex(Regex.escape(token)).findAll(json).count() == seen
        }.getOrDefault(false)
    }
}

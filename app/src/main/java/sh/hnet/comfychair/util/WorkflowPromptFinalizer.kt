package sh.hnet.comfychair.util

import org.json.JSONArray
import org.json.JSONObject

/** Pure prompt transformations shared by direct and persisted node edits. */
object WorkflowPromptFinalizer {
    private fun nodes(root: JSONObject): JSONObject =
        root.optJSONObject("prompt") ?: root.optJSONObject("nodes") ?: root

    fun applyNodeAttributeEdits(
        workflowJson: String,
        edits: Map<String, Map<String, Any>>
    ): String {
        if (edits.isEmpty()) return workflowJson
        return try {
            val root = JSONObject(workflowJson)
            val nodes = nodes(root)
            for ((nodeId, nodeEdits) in edits) {
                val inputs = nodes.optJSONObject(nodeId)?.optJSONObject("inputs") ?: continue
                for ((inputName, value) in nodeEdits) {
                    // Edits are widget values, never graph rewiring or new inputs.
                    if (!inputs.has(inputName) || inputs.opt(inputName) is JSONArray) continue
                    when (value) {
                        is String, is Int, is Long, is Double, is Boolean -> inputs.put(inputName, value)
                        is Float -> inputs.put(inputName, value.toDouble())
                        else -> inputs.put(inputName, value.toString())
                    }
                }
            }
            root.toString()
        } catch (_: Exception) {
            workflowJson
        }
    }

    /** Repair only known PNG defaults; do not infer format or other widget values. */
    fun fillMissingPngSaveInputs(workflowJson: String): String = try {
        val root = JSONObject(workflowJson)
        val nodes = nodes(root)
        val ids = nodes.keys()
        while (ids.hasNext()) {
            val node = nodes.optJSONObject(ids.next()) ?: continue
            if (node.optString("class_type") != "SaveImageAdvanced") continue
            val inputs = node.optJSONObject("inputs") ?: continue
            if (inputs.opt("format") != "png") continue
            for ((key, fallback) in mapOf(
                "format.bit_depth" to "8-bit",
                "format.input_color_space" to "sRGB"
            )) {
                val value = inputs.opt(key)
                if (value == null || value == JSONObject.NULL || (value is String && value.isBlank())) {
                    inputs.put(key, fallback)
                }
            }
        }
        root.toString()
    } catch (_: Exception) {
        workflowJson
    }
}

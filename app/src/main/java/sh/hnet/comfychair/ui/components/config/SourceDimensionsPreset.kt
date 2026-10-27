package sh.hnet.comfychair.ui.components.config

/** No rounding: presets use the primary decoded source, independently gated by mappings. */
data class SourceDimensionsPreset(val width: Int?, val height: Int?, val onApply: () -> Unit) {
    val enabled: Boolean get() = width != null && height != null && width > 0 && height > 0
}

internal fun sourceDimensionsPreset(
    editing: Boolean, mappedWidth: Boolean, mappedHeight: Boolean,
    width: Int?, height: Int?, onWidth: (String) -> Unit, onHeight: (String) -> Unit
): SourceDimensionsPreset? {
    if (!editing || (!mappedWidth && !mappedHeight)) return null
    return SourceDimensionsPreset(width, height) {
        if (width != null && height != null && width > 0 && height > 0) {
            if (mappedWidth) onWidth(width.toString())
            if (mappedHeight) onHeight(height.toString())
        }
    }
}

package sh.hnet.comfychair.viewmodel

/** Source slot identity follows the mapped placeholder, not the number of mappings. */
internal fun activeSourceSlots(mode: ImageToImageMode, placeholders: Set<String>): List<Int> =
    if (mode == ImageToImageMode.INPAINTING) listOf(1)
    else listOf(1) + (2..4).filter { "image_filename_$it" in placeholders }

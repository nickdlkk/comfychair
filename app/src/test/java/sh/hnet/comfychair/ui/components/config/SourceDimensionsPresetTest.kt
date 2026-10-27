package sh.hnet.comfychair.ui.components.config
import org.junit.Assert.*
import org.junit.Test
import sh.hnet.comfychair.model.WorkflowCapabilities
import sh.hnet.comfychair.viewmodel.*
class SourceDimensionsPresetTest {
    @Test fun exactAsymmetricDimensionsUseOnlyMappedCallbacks() {
        for (w in listOf(false, true)) for (h in listOf(false, true)) {
            var width = "unchanged"; var height = "unchanged"
            val preset = sourceDimensionsPreset(true, w, h, 1237, 853, { width = it }, { height = it })
            assertEquals(w || h, preset != null)
            preset?.onApply?.invoke()
            assertEquals(if (w) "1237" else "unchanged", width)
            assertEquals(if (h) "853" else "unchanged", height)
        }
    }
    @Test fun missingOrInvalidSourceCannotApply() {
        for (size in listOf(null to null, 0 to 853, 1237 to null)) {
            val preset = sourceDimensionsPreset(true, true, true, size.first, size.second,
                { fail("width changed") }, { fail("height changed") })!!
            assertFalse(preset.enabled); preset.onApply()
        }
    }
    @Test fun otherModesNeverExposePreset() {
        assertNull(sourceDimensionsPreset(false, true, true, 1237, 853, {}, {}))
        val callbacks = UnifiedCallbacks(onWorkflowChange = {}, onViewWorkflow = {}, onNegativePromptChange = {})
        assertNull(ImageToImageUiState(mode = ImageToImageMode.INPAINTING,
            capabilities = WorkflowCapabilities(hasWidth = true)).toBottomSheetConfig(callbacks).parameters.sourceDimensionsPreset)
        val editing = ImageToImageUiState(capabilities = WorkflowCapabilities(hasWidth = true))
            .toBottomSheetConfig(callbacks).parameters.sourceDimensionsPreset!!
        assertFalse(editing.enabled)
    }
}

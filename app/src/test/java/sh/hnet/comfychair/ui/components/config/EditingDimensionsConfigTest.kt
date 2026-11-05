package sh.hnet.comfychair.ui.components.config

import org.junit.Assert.*
import org.junit.Test
import sh.hnet.comfychair.model.WorkflowCapabilities
import sh.hnet.comfychair.model.WorkflowDefaults
import sh.hnet.comfychair.model.WorkflowValues
import sh.hnet.comfychair.viewmodel.*

class EditingDimensionsConfigTest {
    // The upstream options sheet routes width/height edits through onWidthChange/onHeightChange;
    // the ViewModel applies them to the current mode (editing in editing mode).
    private fun callbacks(width: (String) -> Unit = {}, height: (String) -> Unit = {}) =
        UnifiedCallbacks(onWorkflowChange = {}, onViewWorkflow = {},
            onWidthChange = width, onHeightChange = height)

    private fun editingState(capabilities: WorkflowCapabilities) =
        ImageToImageUiState(editingParams = sh.hnet.comfychair.viewmodel.base.GenerationParams(capabilities = capabilities))

    @Test fun dimensionsAreIndependentlyGatedByMappedCapabilities() {
        for (hasWidth in listOf(false, true)) for (hasHeight in listOf(false, true)) {
            val state = editingState(WorkflowCapabilities.fromPlaceholders(
                buildSet { if (hasWidth) add("width"); if (hasHeight) add("height") }))
            val parameters = state.toBottomSheetConfig(callbacks()).parameters
            assertEquals(hasWidth, parameters.width?.isVisible == true)
            assertEquals(hasHeight, parameters.height?.isVisible == true)
        }
    }

    @Test fun fieldsExposeEditingValuesErrorsAndDedicatedCallbacks() {
        var width = ""; var height = ""
        val params = sh.hnet.comfychair.viewmodel.base.GenerationParams(
            capabilities = WorkflowCapabilities(hasWidth = true, hasHeight = true),
            width = "1536", height = "768", widthError = "width error", heightError = "height error")
        val state = ImageToImageUiState(editingParams = params)
        val parameters = state.toBottomSheetConfig(callbacks({ width = it }, { height = it })).parameters
        assertEquals("1536", parameters.width!!.value)
        assertEquals("768", parameters.height!!.value)
        assertEquals("width error", parameters.width!!.error)
        assertEquals("height error", parameters.height!!.error)
        parameters.width!!.onValueChange("896"); parameters.height!!.onValueChange("640")
        assertEquals("896", width); assertEquals("640", height)
    }

    @Test fun loadUsesSavedThenImportedThenUiDefaultsAndResetsOldErrors() {
        val base = editingState(WorkflowCapabilities(hasWidth = true, hasHeight = true))
        val state = base.copy(
            editingParams = base.editingParams.copy(width = "2048", height = "2048", widthError = "old"))
            .loadEditingDimensions(WorkflowValues(width = 1280), WorkflowDefaults(width = 512, height = 720))
        assertEquals("1280", state.editingParams.width); assertEquals("720", state.editingParams.height)
        assertNull(state.editingParams.widthError)
        val fresh = state.loadEditingDimensions(null, null)
        assertEquals("1024", fresh.editingParams.width); assertEquals("1024", fresh.editingParams.height)
    }
}

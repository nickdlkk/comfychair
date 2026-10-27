package sh.hnet.comfychair.ui.components.config

import org.junit.Assert.*
import org.junit.Test
import sh.hnet.comfychair.model.WorkflowCapabilities
import sh.hnet.comfychair.model.WorkflowDefaults
import sh.hnet.comfychair.model.WorkflowValues
import sh.hnet.comfychair.viewmodel.*

class EditingDimensionsConfigTest {
    private fun callbacks(width: (String) -> Unit = {}, height: (String) -> Unit = {}) =
        UnifiedCallbacks(onWorkflowChange = {}, onViewWorkflow = {}, onNegativePromptChange = {},
            onEditingWidthChange = width, onEditingHeightChange = height)

    @Test fun dimensionsAreIndependentlyGatedByMappedCapabilities() {
        for (hasWidth in listOf(false, true)) for (hasHeight in listOf(false, true)) {
            val state = ImageToImageUiState(capabilities = WorkflowCapabilities.fromPlaceholders(
                buildSet { if (hasWidth) add("width"); if (hasHeight) add("height") }))
            val parameters = state.toBottomSheetConfig(callbacks()).parameters
            assertEquals(hasWidth, parameters.width?.isVisible == true)
            assertEquals(hasHeight, parameters.height?.isVisible == true)
        }
    }

    @Test fun fieldsExposeEditingValuesErrorsAndDedicatedCallbacks() {
        var width = ""; var height = ""
        val state = ImageToImageUiState(editingWidth = "1536", editingHeight = "768",
            editingWidthError = "width error", editingHeightError = "height error",
            capabilities = WorkflowCapabilities(hasWidth = true, hasHeight = true))
        val parameters = state.toBottomSheetConfig(callbacks({ width = it }, { height = it })).parameters
        assertEquals("1536", parameters.width!!.value)
        assertEquals("768", parameters.height!!.value)
        assertEquals("width error", parameters.width!!.error)
        assertEquals("height error", parameters.height!!.error)
        parameters.width!!.onValueChange("896"); parameters.height!!.onValueChange("640")
        assertEquals("896", width); assertEquals("640", height)
    }

    @Test fun loadUsesSavedThenImportedThenUiDefaultsAndResetsOldErrors() {
        val state = ImageToImageUiState(editingWidth = "2048", editingHeight = "2048", editingWidthError = "old")
            .loadEditingDimensions(WorkflowValues(width = 1280), WorkflowDefaults(width = 512, height = 720))
        assertEquals("1280", state.editingWidth); assertEquals("720", state.editingHeight)
        assertNull(state.editingWidthError)
        val fresh = state.loadEditingDimensions(null, null)
        assertEquals("1024", fresh.editingWidth); assertEquals("1024", fresh.editingHeight)
    }

    @Test fun currentDimensionsPersistRoundTripAndDriveExecution() {
        val state = ImageToImageUiState(editingWidth = "1344", editingHeight = "896",
            capabilities = WorkflowCapabilities(hasWidth = true, hasHeight = true))
        val saved = state.saveEditingDimensions(WorkflowValues(width = 512, height = 512, nodeAttributeEdits = "kept"))
        val restored = state.loadEditingDimensions(WorkflowValues.fromJson(WorkflowValues.toJson(saved)), null)
        assertEquals(1344, restored.editingWidthForExecution())
        assertEquals(896, restored.editingHeightForExecution())
        assertEquals("kept", saved.nodeAttributeEdits)
        assertTrue(restored.hasValidEditingDimensions())
    }

    @Test fun unmappedDimensionsAreNotSubmittedOrOverwrittenAndDoNotBlock() {
        val state = ImageToImageUiState(editingWidth = "invalid", editingHeight = "")
        assertNull(state.editingWidthForExecution()); assertNull(state.editingHeightForExecution())
        assertTrue(state.hasValidEditingDimensions())
        val saved = state.saveEditingDimensions(WorkflowValues(width = 640, height = 480))
        assertEquals(640, saved.width); assertEquals(480, saved.height)
    }

    @Test fun mappedInvalidOrEmptyDimensionsBlockRatherThanFallbackAtSubmit() {
        val base = ImageToImageUiState(capabilities = WorkflowCapabilities(hasWidth = true, hasHeight = true))
        for (invalid in listOf("", "x", "0", "-1", "4097", "1.5")) {
            assertFalse(base.copy(editingWidth = invalid).hasValidEditingDimensions())
            assertFalse(base.copy(editingHeight = invalid).hasValidEditingDimensions())
        }
        assertTrue(base.copy(editingWidth = "1", editingHeight = "4096").hasValidEditingDimensions())
    }

    @Test fun oneMappedDimensionDoesNotSubmitTheOther() {
        val widthOnly = ImageToImageUiState(editingWidth = "768", editingHeight = "bad",
            capabilities = WorkflowCapabilities(hasWidth = true))
        assertEquals(768, widthOnly.editingWidthForExecution())
        assertNull(widthOnly.editingHeightForExecution())
        assertTrue(widthOnly.hasValidEditingDimensions())
        val heightOnly = widthOnly.copy(editingWidth = "bad", editingHeight = "512",
            capabilities = WorkflowCapabilities(hasHeight = true))
        assertNull(heightOnly.editingWidthForExecution())
        assertEquals(512, heightOnly.editingHeightForExecution())
        assertTrue(heightOnly.hasValidEditingDimensions())
    }

    @Test fun transientInvalidEditsPreserveLastValidSavedDimensions() {
        val state = ImageToImageUiState(editingWidth = "", editingHeight = "bad",
            capabilities = WorkflowCapabilities(hasWidth = true, hasHeight = true))
        val values = state.saveEditingDimensions(WorkflowValues(width = 1280, height = 720))
        assertEquals(1280, values.width); assertEquals(720, values.height)
    }

    @Test fun inpaintingDoesNotExposeEditingDimensionControls() {
        val state = ImageToImageUiState(mode = ImageToImageMode.INPAINTING,
            capabilities = WorkflowCapabilities(hasWidth = true, hasHeight = true))
        assertNull(state.toBottomSheetConfig(callbacks()).parameters.width)
        assertNull(state.toBottomSheetConfig(callbacks()).parameters.height)
    }
}

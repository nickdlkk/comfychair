package sh.hnet.comfychair.viewmodel

import sh.hnet.comfychair.model.WorkflowDefaults
import sh.hnet.comfychair.model.WorkflowValues
import sh.hnet.comfychair.util.ValidationUtils

/** Resolve each editable dimension independently when a workflow is loaded, not at submit. */
internal fun ImageToImageUiState.loadEditingDimensions(
    saved: WorkflowValues?, defaults: WorkflowDefaults?
): ImageToImageUiState {
    // Match the established image-generation UI defaults. These are visible and editable;
    // they are not an unconditional resolution imposed on workflows without mappings.
    val uiDefaults = ImageToImageUiState()
    val width = saved?.width?.toString() ?: defaults?.width?.toString() ?: uiDefaults.editingWidth
    val height = saved?.height?.toString() ?: defaults?.height?.toString() ?: uiDefaults.editingHeight
    return copy(
        editingWidth = width, editingHeight = height,
        editingWidthError = ValidationUtils.validateDimension(width),
        editingHeightError = ValidationUtils.validateDimension(height)
    )
}

internal fun ImageToImageUiState.editingWidthForExecution(): Int? =
    editingWidth.toIntOrNull()?.takeIf { capabilities.hasWidth && it in 1..4096 }

internal fun ImageToImageUiState.editingHeightForExecution(): Int? =
    editingHeight.toIntOrNull()?.takeIf { capabilities.hasHeight && it in 1..4096 }

internal fun ImageToImageUiState.hasValidEditingDimensions(): Boolean =
    (!capabilities.hasWidth || (editingWidthError == null && editingWidthForExecution() != null)) &&
    (!capabilities.hasHeight || (editingHeightError == null && editingHeightForExecution() != null))

/** Hidden controls and transient invalid edits must not destroy previously saved dimensions. */
internal fun ImageToImageUiState.saveEditingDimensions(existing: WorkflowValues): WorkflowValues =
    existing.copy(
        width = editingWidthForExecution() ?: existing.width,
        height = editingHeightForExecution() ?: existing.height
    )

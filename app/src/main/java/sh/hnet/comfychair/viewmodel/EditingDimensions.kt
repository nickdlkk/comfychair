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
    val width = saved?.width?.toString() ?: defaults?.width?.toString() ?: uiDefaults.editingParams.width
    val height = saved?.height?.toString() ?: defaults?.height?.toString() ?: uiDefaults.editingParams.height
    return copy(editingParams = editingParams.copy(
        width = width, height = height,
        widthError = ValidationUtils.validateDimension(width),
        heightError = ValidationUtils.validateDimension(height)
    ))
}

internal fun ImageToImageUiState.editingWidthForExecution(): Int? =
    editingParams.width.toIntOrNull()?.takeIf { editingParams.capabilities.hasWidth && it in 1..4096 }

internal fun ImageToImageUiState.editingHeightForExecution(): Int? =
    editingParams.height.toIntOrNull()?.takeIf { editingParams.capabilities.hasHeight && it in 1..4096 }

internal fun ImageToImageUiState.hasValidEditingDimensions(): Boolean =
    (!editingParams.capabilities.hasWidth || (editingParams.widthError == null && editingWidthForExecution() != null)) &&
    (!editingParams.capabilities.hasHeight || (editingParams.heightError == null && editingHeightForExecution() != null))

/** Hidden controls and transient invalid edits must not destroy previously saved dimensions. */
internal fun ImageToImageUiState.saveEditingDimensions(existing: WorkflowValues): WorkflowValues =
    existing.copy(
        width = editingWidthForExecution() ?: existing.width,
        height = editingHeightForExecution() ?: existing.height
    )

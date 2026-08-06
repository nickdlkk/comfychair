package sh.hnet.comfychair.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sh.hnet.comfychair.R
import sh.hnet.comfychair.materials.MaterialItem
import sh.hnet.comfychair.materials.MaterialLibrary

data class MaterialLibraryUiState(
    val items: List<MaterialItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isImporting: Boolean = false
)

sealed class MaterialLibraryEvent {
    data class ShowToast(val messageResId: Int) : MaterialLibraryEvent()
}

class MaterialLibraryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MaterialLibraryUiState())
    val uiState: StateFlow<MaterialLibraryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MaterialLibraryEvent>()
    val events: SharedFlow<MaterialLibraryEvent> = _events.asSharedFlow()

    // Serialise all import/delete ops to prevent concurrent writes racing on index.json
    private val writeMutex = Mutex()

    fun load(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val items = MaterialLibrary.listMaterials(context)
            _uiState.value = _uiState.value.copy(items = items, isLoading = false)
        }
    }

    fun import(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            writeMutex.withLock {
                _uiState.value = _uiState.value.copy(isImporting = true)
                val imported = MaterialLibrary.importImages(context, uris)
                val items = MaterialLibrary.listMaterials(context)
                _uiState.value = _uiState.value.copy(items = items, isImporting = false)
                _events.emit(
                    MaterialLibraryEvent.ShowToast(
                        if (imported > 0) R.string.msg_materials_imported_success else R.string.error_materials_import
                    )
                )
            }
        }
    }

    fun toggleSelection(item: MaterialItem) {
        val next = _uiState.value.selectedIds.toMutableSet()
        if (!next.add(item.id)) next.remove(item.id)
        _uiState.value = _uiState.value.copy(selectedIds = next, isSelectionMode = next.isNotEmpty())
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedIds = emptySet(), isSelectionMode = false)
    }

    fun selectAll() {
        val ids = _uiState.value.items.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(selectedIds = ids, isSelectionMode = ids.isNotEmpty())
    }

    fun deleteSelected(context: Context) {
        val ids = _uiState.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            writeMutex.withLock {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val deleted = MaterialLibrary.deleteMaterials(context, ids)
                val items = MaterialLibrary.listMaterials(context)
                _uiState.value = _uiState.value.copy(
                    items = items,
                    isLoading = false,
                    selectedIds = emptySet(),
                    isSelectionMode = false
                )
                _events.emit(
                    MaterialLibraryEvent.ShowToast(
                        if (deleted > 0) R.string.msg_materials_deleted_success else R.string.error_materials_delete
                    )
                )
            }
        }
    }

    fun loadBitmap(context: Context, item: MaterialItem, onResult: (Bitmap?) -> Unit) {
        viewModelScope.launch {
            onResult(MaterialLibrary.loadBitmap(context, item))
        }
    }
}

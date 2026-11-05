package sh.hnet.comfychair.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import sh.hnet.comfychair.ComfyUIClient
import sh.hnet.comfychair.R
import sh.hnet.comfychair.WorkflowManager
import sh.hnet.comfychair.WorkflowType
import sh.hnet.comfychair.cache.MediaStateHolder
import sh.hnet.comfychair.connection.ConnectionFailure
import sh.hnet.comfychair.connection.ConnectionManager
import sh.hnet.comfychair.ui.components.shared.WorkflowItemBase
import sh.hnet.comfychair.util.DebugLogger
import sh.hnet.comfychair.util.GenerationOutput
import sh.hnet.comfychair.util.GenerationOutputs
import sh.hnet.comfychair.util.Obfuscator
import sh.hnet.comfychair.util.SeasonalPrompts
import sh.hnet.comfychair.util.ValidationUtils
import sh.hnet.comfychair.viewmodel.base.GenerationParams
import sh.hnet.comfychair.ui.components.config.CommonGenerationState
import sh.hnet.comfychair.viewmodel.base.BaseGenerationViewModel

/**
 * Data class representing a path drawn on the mask canvas
 */
data class MaskPathData(
    val path: Path,
    val isEraser: Boolean,
    val brushSize: Float
)

/**
 * UI state for the view mode toggle
 */
enum class ImageToImageViewMode {
    SOURCE,
    PREVIEW
}

/**
 * UI state for the Image-to-Image mode (Inpainting vs Editing)
 */
enum class ImageToImageMode {
    INPAINTING,  // Existing: requires mask
    EDITING      // New: no mask, optional reference images
}

/**
 * Fallback values of the Editing mode; a workflow's saved values and template defaults come first.
 */
private val editingDefaults = GenerationParams(megapixels = "2.0", steps = "4", cfg = "1.0", scheduler = "simple")

/**
 * UI state for the Image-to-image screen.
 *
 * Each mode has its own workflow and generation parameters ([inpaintingParams], [editingParams]); the
 * positive prompt is shared. The state exposes the current mode's params directly through delegation, so
 * `state.steps` etc. always read the mode on screen. Field visibility is controlled by the workflow's
 * placeholders (`capabilities`).
 */
data class ImageToImageUiState(
    val mode: ImageToImageMode = ImageToImageMode.EDITING,
    val inpaintingParams: GenerationParams = GenerationParams(),
    val editingParams: GenerationParams = editingDefaults,

    // View state
    val viewMode: ImageToImageViewMode = ImageToImageViewMode.SOURCE,
    val sourceImage: Bitmap? = null,
    val sourceImage2: Bitmap? = null,
    val sourceImage3: Bitmap? = null,
    val sourceImage4: Bitmap? = null,
    // Original file sizes for source images (bytes, captured at load time for display)
    val sourceImageSize: Long? = null,
    val sourceImage2Size: Long? = null,
    val sourceImage3Size: Long? = null,
    val sourceImage4Size: Long? = null,
    // Bypassed source image slots (for multi-image workflows). Slot 1 is never bypassed — enforced in UI.
    val bypassedSourceSlots: Set<Int> = emptySet(),
    val previewImage: Bitmap? = null,
    val comparisonOriginal: Bitmap? = null,
    internal val comparisonSource: EditingViewerSnapshots.Source? = null,
    val maskPaths: List<MaskPathData> = emptyList(),
    val brushSize: Float = 50f,
    val isEraserMode: Boolean = false,

    // All result files of the last job, in server order (the first image is the one shown)
    val generationOutputs: List<GenerationOutput> = emptyList(),
    // Result shown in the preview (index into generationOutputs)
    val currentResultIndex: Int = 0,

    // Preview image file info (for metadata extraction)
    val previewImageFilename: String? = null,
    val previewImageSubfolder: String? = null,
    val previewImageType: String? = null,

    // Workflows offered in each mode
    val availableWorkflows: List<WorkflowItem> = emptyList(),
    val editingWorkflows: List<WorkflowItem> = emptyList(),

    // Reference images (Editing mode)
    val referenceImage1: Bitmap? = null,
    val referenceImage2: Bitmap? = null,

    val isUploading: Boolean = false,
    val isFetching: Boolean = false,
    override val isRefreshingModels: Boolean = false,
    val uploadTotalBytes: Long? = null,
    val uploadProgressBytes: Long? = null,
    val uploadLabel: String? = null,
    val compressedSlots: Set<Int> = emptySet(),
    val additionalImageSlotCount: Int = 0
) : CommonGenerationState by (if (mode == ImageToImageMode.EDITING) editingParams else inpaintingParams) {
    val currentModeWorkflows: List<WorkflowItem>
        get() = if (mode == ImageToImageMode.EDITING) editingWorkflows else availableWorkflows
}

/**
 * ViewModel for the Image-to-image screen
 */
class ImageToImageViewModel : BaseGenerationViewModel<ImageToImageUiState>() {

    override val initialState = ImageToImageUiState()

    override fun ImageToImageUiState.params() =
        if (mode == ImageToImageMode.EDITING) editingParams else inpaintingParams

    // The positive prompt is shared by both modes
    override fun ImageToImageUiState.withParams(params: GenerationParams) =
        if (mode == ImageToImageMode.EDITING) {
            copy(editingParams = params, inpaintingParams = inpaintingParams.copy(positivePrompt = params.positivePrompt))
        } else {
            copy(inpaintingParams = params, editingParams = editingParams.copy(positivePrompt = params.positivePrompt))
        }

    override fun ImageToImageUiState.mapAllParams(transform: (GenerationParams) -> GenerationParams) =
        copy(inpaintingParams = transform(inpaintingParams), editingParams = transform(editingParams))

    override val fallbackParams: GenerationParams
        get() = if (_uiState.value.mode == ImageToImageMode.EDITING) editingDefaults else GenerationParams()

    // Constants
    companion object {
        private const val TAG = "ImageToImage"
        const val OWNER_ID = "IMAGE_TO_IMAGE"
        private const val PREFS_NAME = "ImageToImageFragmentPrefs"

        // Global preferences (camelCase keys for BackupManager compatibility)
        private const val PREF_POSITIVE_PROMPT = "positivePrompt"
        private const val PREF_SELECTED_WORKFLOW_ID = "selectedWorkflowId"
        private const val PREF_MODE = "mode"
        private const val PREF_SELECTED_EDITING_WORKFLOW_ID = "selectedEditingWorkflowId"

        private const val FEATHER_RADIUS = 8
    }

    private data class DownloadedSource(val bitmap: Bitmap, val size: Long, val prepared: java.io.File?,
        val descriptor: sh.hnet.comfychair.model.ServerImage)
    private val directSources = mutableMapOf<Int, sh.hnet.comfychair.connection.DirectInputSource>()

    private fun serverImageIdentity(): ServerImageIdentity? {
        if (ConnectionManager.connectionState.value !is sh.hnet.comfychair.connection.ConnectionState.Connected) return null
        val serverId = ConnectionManager.currentServerId ?: return null
        val client = ConnectionManager.clientOrNull ?: return null
        if (MediaStateHolder.getCurrentServerId() != serverId) return null
        return ServerImageIdentity(serverId, client, client.imageUploadIdentity())
    }

    private val sourcePicker = ServerImagePicker(
        scope = viewModelScope,
        identity = ::serverImageIdentity,
        validSlot = { slot -> slot in activeSourceSlots(_uiState.value.mode, _uiState.value.editingParams.workflowPlaceholders) },
        list = { owner, source, offset -> withContext(Dispatchers.IO) {
            (owner.client as ComfyUIClient).listSourceImagePage(source, offset)
        } },
        download = { owner, image ->
            var prepared: java.io.File? = null
            try { withContext(Dispatchers.IO) {
            val bytes = (owner.client as ComfyUIClient).downloadSourceImage(image)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            // Preserve exact image dimensions; reject oversized sources instead of silently resizing.
            require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
                bounds.outWidth.toLong() * bounds.outHeight <= 16_777_216L)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw java.io.IOException("Invalid image")
            try {
                if (!MediaStateHolder.isMemoryFirstMode()) {
                    val context = applicationContext ?: throw java.io.IOException("No context")
                    prepared = java.io.File.createTempFile("server-source-", ".png", context.cacheDir)
                    prepared!!.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                DownloadedSource(bitmap, bytes.size.toLong(), prepared, image)
            } catch (e: Throwable) { prepared?.delete(); throw e }
            } } catch (e: Throwable) { prepared?.delete(); throw e }
        },
        browse = { owner, source, query -> withContext(Dispatchers.IO) {
            (owner.client as ComfyUIClient).browseSourceImages(source, query)
        } },
        cacheGeneration = { sh.hnet.comfychair.cache.PersistentImageCache.generation() },
        cached = { owner, source, query -> withContext(Dispatchers.IO) {
            (owner.client as ComfyUIClient).cachedSourceImages(source, query, owner.epoch)
        } },
        store = { owner, source, query, page, generation -> withContext(Dispatchers.IO) {
            if (serverImageIdentity() == owner) (owner.client as ComfyUIClient).cacheSourceImages(source, query, page, owner.epoch, generation)
        } },
        dispose = { value: DownloadedSource -> value.prepared?.delete(); Unit },
        accept = { owner, slot, value: DownloadedSource ->
            if (serverImageIdentity() == owner) {
                val key = when (slot) {
                    1 -> MediaStateHolder.MediaKey.ItiSource
                    2 -> MediaStateHolder.MediaKey.ItiSource2
                    3 -> MediaStateHolder.MediaKey.ItiSource3
                    else -> MediaStateHolder.MediaKey.ItiSource4
                }
                // Synchronous cache publication avoids a second unguarded coroutine after selection.
                MediaStateHolder.putPreparedBitmap(key, value.bitmap, applicationContext, owner.serverId, value.prepared)
                directSources[slot] = sh.hnet.comfychair.connection.DirectInputSource(
                    value.descriptor, owner.epoch, value.bitmap, value.bitmap.generationId)
                _uiState.update { state ->
                    val reset = state.copy(compressedSlots = state.compressedSlots - slot,
                        viewMode = ImageToImageViewMode.SOURCE)
                    when (slot) {
                        1 -> reset.copy(sourceImage = value.bitmap, sourceImageSize = value.size, maskPaths = emptyList())
                        2 -> reset.copy(sourceImage2 = value.bitmap, sourceImage2Size = value.size)
                        3 -> reset.copy(sourceImage3 = value.bitmap, sourceImage3Size = value.size)
                        else -> reset.copy(sourceImage4 = value.bitmap, sourceImage4Size = value.size)
                    }
                }
            }
        }
    )
    // Memory fronts a bounded app-private HMAC-scoped sampled-thumbnail disk cache.
    private data class PreviewKey(val owner: ServerImageIdentity, val image: sh.hnet.comfychair.model.ServerImage, val day: Long = System.currentTimeMillis() / 86_400_000L, val cacheGeneration: Long = sh.hnet.comfychair.cache.PersistentImageCache.generation())
    private val sourcePreviewCache = ServerImagePreviewCache<PreviewKey, Bitmap>(12L * 1024 * 1024, 64)
    suspend fun loadServerImagePreview(image: sh.hnet.comfychair.model.ServerImage): Bitmap =
        sourcePicker.preview(image) { owner -> withContext(Dispatchers.IO) {
            val key = PreviewKey(owner, image)
            sourcePreviewCache.get(key) ?: run {
                val client = owner.client as ComfyUIClient
                val storage = client.imageCacheStorage(owner.epoch)
                val generation = storage?.first?.generation()
                val diskKey = "thumb:" + image.stableKey
                val cachedBytes = storage?.let { (disk, namespace) -> disk.get(namespace, diskKey, 24 * 60 * 60 * 1000L) }
                if (cachedBytes != null) {
                    val saved = BitmapFactory.decodeByteArray(cachedBytes, 0, cachedBytes.size)
                    if (saved != null && saved.width <= 256 && saved.height <= 256) {
                        sourcePreviewCache.put(key, saved, saved.allocationByteCount.toLong())
                        return@withContext saved
                    }
                    storage.let { (disk, namespace) -> disk.remove(namespace, diskKey) }
                }
                val bytes = client.downloadSourcePreview(image)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                require(options.outWidth > 0 && options.outHeight > 0 &&
                    options.outWidth.toLong() * options.outHeight <= 268_435_456L)
                options.inJustDecodeBounds = false
                options.inSampleSize = 1
                while (options.outWidth / options.inSampleSize > 256 || options.outHeight / options.inSampleSize > 256)
                    options.inSampleSize *= 2
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: throw java.io.IOException("Invalid preview")
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (serverImageIdentity() != owner) throw kotlinx.coroutines.CancellationException("Preview owner changed")
                storage?.let { (disk, namespace) ->
                    val encoded = java.io.ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }.toByteArray()
                    disk.put(namespace, diskKey, encoded, generation!!)
                }
                sourcePreviewCache.put(key, bitmap, bitmap.allocationByteCount.toLong())
                bitmap
            }
        } }
    val serverImagePickerState = sourcePicker.state
    fun openServerImagePicker(slot: Int, source: sh.hnet.comfychair.model.ServerImageSource) {
        sh.hnet.comfychair.DiagnosticsApplication.event("picker_open slot=$slot source=${source.name} connected=${ConnectionManager.isConnected}")
        sourcePicker.open(slot, source)
    }
    fun loadMoreServerImages(retry: Boolean = false) = sourcePicker.loadMore(retry)
    fun changeServerImageQuery(name: String, sort: sh.hnet.comfychair.model.ServerImageSort) = sourcePicker.changeQuery(name, sort)
    fun refreshServerImages() {
        val owner = serverImageIdentity() ?: return
        val images = sourcePicker.state.value.images.toList()
        sourcePreviewCache.clear()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    (owner.client as ComfyUIClient).imageCacheStorage(owner.epoch)?.let { (disk, namespace) ->
                        images.forEach { disk.remove(namespace, "thumb:" + it.stableKey) }
                    }
                }
                if (serverImageIdentity() == owner) sourcePicker.refresh()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        }
    }
    fun removeDeletedServerImages(images: List<sh.hnet.comfychair.model.ServerImage>, session: Long) {
        sourcePicker.removeDeleted(images, session)
        sourcePreviewCache.clear()
    }
    fun cancelServerImageDownload() = sourcePicker.cancelDownload()
    fun retryServerImageDownload() = sourcePicker.retryDownload()
    fun closeServerImagePicker() = sourcePicker.close()
    fun validateServerImagePicker() = sourcePicker.connectionChanged()
    fun selectServerImage(image: sh.hnet.comfychair.model.ServerImage) = sourcePicker.select(image)

    init {
        viewModelScope.launch {
            sh.hnet.comfychair.cache.PersistentImageCache.changes.collect {
                sourcePreviewCache.clear()
                sourcePicker.close() // cancels in-flight list/preview publication, not selected sources
            }
        }
        viewModelScope.launch {
            ConnectionManager.connectionState.collect { sourcePicker.connectionChanged() }
        }
        // Keep the result list for process death (disk-first mode only, see MediaStateHolder)
        viewModelScope.launch {
            _uiState
                .map { it.generationOutputs to it.currentResultIndex }
                .distinctUntilChanged()
                .drop(1)
                .collect { (outputs, index) ->
                    MediaStateHolder.saveGenerationOutputs(applicationContext, OWNER_ID, outputs, index)
                }
        }
        // Observe workflow changes to refresh list when workflows are added/updated/deleted
        viewModelScope.launch {
            WorkflowManager.workflowsVersion.collect {
                loadWorkflows()
            }
        }
    }

    override fun onInitialize() {
        DebugLogger.i(TAG, "Initializing")

        loadWorkflows()
        restorePreferences()
        loadSavedImages()
    }

    private fun loadWorkflows() {
        // Load inpainting workflows (ITI_INPAINTING)
        val inpaintingWorkflows = WorkflowManager.getEnabledWorkflowsByType(WorkflowType.ITI_INPAINTING)
            .map { WorkflowItem(id = it.id, name = it.name) }
            .sortedBy { it.displayName }
        // Load editing workflows (ITI_EDITING)
        val editingWorkflows = WorkflowManager.getEnabledWorkflowsByType(WorkflowType.ITI_EDITING)
            .map { WorkflowItem(id = it.id, name = it.name) }
            .sortedBy { it.displayName }

        val state = _uiState.value
        val inpaintingItem = inpaintingWorkflows.find { it.name == state.inpaintingParams.selectedWorkflow }
            ?: inpaintingWorkflows.firstOrNull()
        val editingItem = editingWorkflows.find { it.name == state.editingParams.selectedWorkflow }
            ?: editingWorkflows.firstOrNull()

        _uiState.update { it.copy(availableWorkflows = inpaintingWorkflows, editingWorkflows = editingWorkflows) }

        // Reload workflow values to refresh capability flags from WorkflowDefaults
        // This is important after backup restore when workflowsVersion triggers this function
        loadModeWorkflows(inpaintingItem, editingItem)
    }

    /** Load each mode's workflow values (or clear the selection when the mode has no workflow). */
    private fun loadModeWorkflows(inpaintingItem: WorkflowItem?, editingItem: WorkflowItem?) {
        _uiState.update { state ->
            state.copy(
                inpaintingParams = inpaintingItem
                    ?.let { paramsForWorkflow(state.inpaintingParams, it.id, it.name, GenerationParams()) }
                    ?: state.inpaintingParams.copy(selectedWorkflow = "", selectedWorkflowId = ""),
                editingParams = editingItem
                    ?.let { paramsForWorkflow(state.editingParams, it.id, it.name, editingDefaults) }
                    ?: state.editingParams.copy(selectedWorkflow = "", selectedWorkflowId = "")
            )
        }
        loadEditingSlotSettings(editingItem?.id)
    }

    private fun loadEditingSlotSettings(workflowId: String?) {
        val placeholders = workflowId?.let { WorkflowManager.getWorkflowPlaceholders(it) }.orEmpty()
        val serverId = ConnectionManager.currentServerId
        val bypassed = if (workflowId != null && serverId != null) {
            workflowValuesStorage?.loadValues(serverId, workflowId)?.bypassedSourceSlots.orEmpty()
        } else emptySet()
        _uiState.update { it.copy(
            additionalImageSlotCount = listOf(2, 3, 4).count { slot -> "image_filename_$slot" in placeholders },
            bypassedSourceSlots = bypassed.filter { slot -> slot in 2..4 }.toSet()
        ) }
    }

    private fun restorePreferences() {
        val context = applicationContext ?: return
        val serverId = ConnectionManager.currentServerId ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val defaultPositivePrompt = SeasonalPrompts.getImageToImagePrompt()

        // Load global preferences with serverId prefix
        val positivePrompt = prefs.getString("${serverId}_$PREF_POSITIVE_PROMPT", null) ?: defaultPositivePrompt
        val savedWorkflowId = prefs.getString("${serverId}_$PREF_SELECTED_WORKFLOW_ID", null)
        val savedMode = prefs.getString("${serverId}_$PREF_MODE", ImageToImageMode.EDITING.name)
        val savedEditingWorkflowId = prefs.getString("${serverId}_$PREF_SELECTED_EDITING_WORKFLOW_ID", null)

        // Restore mode
        val mode = try {
            ImageToImageMode.valueOf(savedMode ?: ImageToImageMode.EDITING.name)
        } catch (e: Exception) {
            ImageToImageMode.EDITING
        }

        // Mode and the shared positive prompt first
        _uiState.update {
            it.copy(
                mode = mode,
                inpaintingParams = it.inpaintingParams.copy(positivePrompt = positivePrompt),
                editingParams = it.editingParams.copy(positivePrompt = positivePrompt)
            )
        }

        // Saved workflow of each mode, else the current selection (set by loadWorkflows), else the first one
        val state = _uiState.value
        val inpaintingItem = state.availableWorkflows.find { it.id == savedWorkflowId }
            ?: state.availableWorkflows.find { it.name == state.inpaintingParams.selectedWorkflow }
            ?: state.availableWorkflows.firstOrNull()
        val editingItem = state.editingWorkflows.find { it.id == savedEditingWorkflowId }
            ?: state.editingWorkflows.find { it.name == state.editingParams.selectedWorkflow }
            ?: state.editingWorkflows.firstOrNull()

        loadModeWorkflows(inpaintingItem, editingItem)
    }

    override fun saveConfiguration() {
        val context = applicationContext ?: return
        val serverId = ConnectionManager.currentServerId ?: return
        val state = _uiState.value
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Save global preferences with serverId prefix
        prefs.edit()
            .putString("${serverId}_$PREF_POSITIVE_PROMPT", state.positivePrompt)
            .putString("${serverId}_$PREF_MODE", state.mode.name)
            .putString("${serverId}_$PREF_SELECTED_WORKFLOW_ID", state.inpaintingParams.selectedWorkflowId)
            .putString("${serverId}_$PREF_SELECTED_EDITING_WORKFLOW_ID", state.editingParams.selectedWorkflowId)
            .apply()

        // Per-workflow values of both modes' workflows (the positive prompt change touches both)
        saveWorkflowValues(state.inpaintingParams)
        saveWorkflowValues(state.editingParams)
    }

    private fun loadSavedImages() {
        // Results strip of the last job (disk-first mode only)
        if (_uiState.value.generationOutputs.isEmpty()) {
            MediaStateHolder.loadGenerationOutputs(applicationContext, OWNER_ID)?.let { (outputs, index) ->
                _uiState.update { it.copy(generationOutputs = outputs, currentResultIndex = index) }
            }
        }
        // Restore from cache (memory in memory-first mode, disk in disk-first mode)
        val sourceImage = MediaStateHolder.getBitmap(MediaStateHolder.MediaKey.ItiSource, applicationContext)
        val sourceImage2 = MediaStateHolder.getBitmap(MediaStateHolder.MediaKey.ItiSource2, applicationContext)
        val sourceImage3 = MediaStateHolder.getBitmap(MediaStateHolder.MediaKey.ItiSource3, applicationContext)
        val sourceImage4 = MediaStateHolder.getBitmap(MediaStateHolder.MediaKey.ItiSource4, applicationContext)
        val previewImage = MediaStateHolder.getBitmap(MediaStateHolder.MediaKey.ItiPreview, applicationContext)
        val referenceImage1 = MediaStateHolder.getBitmap(MediaStateHolder.MediaKey.IteReferenceImage1, applicationContext)
        val referenceImage2 = MediaStateHolder.getBitmap(MediaStateHolder.MediaKey.IteReferenceImage2, applicationContext)

        // Fill in only what isn't shown yet: an image chosen or generated meanwhile wins
        _uiState.update {
            it.copy(
                sourceImage = it.sourceImage ?: sourceImage,
                sourceImage2 = it.sourceImage2 ?: sourceImage2,
                sourceImage3 = it.sourceImage3 ?: sourceImage3,
                sourceImage4 = it.sourceImage4 ?: sourceImage4,
                previewImage = it.previewImage ?: previewImage,
                referenceImage1 = it.referenceImage1 ?: referenceImage1,
                referenceImage2 = it.referenceImage2 ?: referenceImage2
            )
        }
    }

    override fun onStoredMediaAvailable() = loadSavedImages()

    fun fetchModels() {
        if (_uiState.value.isRefreshingModels) return
        _uiState.update { it.copy(isRefreshingModels = true) }
        viewModelScope.launch {
            try {
                ConnectionManager.refreshServerData()
                ConnectionManager.modelCache.filter { !it.isLoading }.first()
            } finally {
                _uiState.update { it.copy(isRefreshingModels = false) }
            }
        }
    }

    // View mode
    fun onViewModeChange(mode: ImageToImageViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
    }

    // Image-to-Image mode (Inpainting vs Editing)
    fun onModeChange(mode: ImageToImageMode) {
        comparisonFetchToken++
        // Each mode keeps its own workflow and values, so switching only changes which ones are shown
        _uiState.update { it.copy(mode = mode, comparisonOriginal = null, comparisonSource = null) }
        saveConfiguration()
    }

    // Reference image handlers (for Editing mode)
    fun onReferenceImage1Change(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    // Store in cache (memory or disk based on mode)
                    MediaStateHolder.putBitmap(MediaStateHolder.MediaKey.IteReferenceImage1, bitmap, context)
                    _uiState.value = _uiState.value.copy(referenceImage1 = bitmap)
                }
            } catch (e: Exception) {
                _events.emit(GenerationScreenEvent.ShowToast(R.string.error_save_image))
            }
        }
    }

    fun onReferenceImage2Change(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    // Store in cache (memory or disk based on mode)
                    MediaStateHolder.putBitmap(MediaStateHolder.MediaKey.IteReferenceImage2, bitmap, context)
                    _uiState.value = _uiState.value.copy(referenceImage2 = bitmap)
                }
            } catch (e: Exception) {
                _events.emit(GenerationScreenEvent.ShowToast(R.string.error_save_image))
            }
        }
    }

    fun onClearReferenceImage1() {
        val context = applicationContext ?: return
        viewModelScope.launch {
            MediaStateHolder.evictAndDeleteFromDisk(context, MediaStateHolder.MediaKey.IteReferenceImage1)
            _uiState.value = _uiState.value.copy(referenceImage1 = null)
        }
    }

    fun onClearReferenceImage2() {
        val context = applicationContext ?: return
        viewModelScope.launch {
            MediaStateHolder.evictAndDeleteFromDisk(context, MediaStateHolder.MediaKey.IteReferenceImage2)
            _uiState.value = _uiState.value.copy(referenceImage2 = null)
        }
    }

    /**
     * The reference slot a result could fill: in Editing mode, the first slot the workflow uses that is still empty.
     * Null when there is none, which hides "Use as reference".
     */
    fun freeReferenceSlot(state: ImageToImageUiState): Int? = when {
        state.mode != ImageToImageMode.EDITING -> null
        state.capabilities.hasReferenceImage1 && state.referenceImage1 == null -> 1
        state.capabilities.hasReferenceImage2 && state.referenceImage2 == null -> 2
        else -> null
    }

    /** Put a generated image into the first free reference slot (see [freeReferenceSlot]). */
    fun useAsReference(bitmap: Bitmap) {
        val context = applicationContext ?: return
        when (freeReferenceSlot(_uiState.value)) {
            1 -> {
                MediaStateHolder.putBitmap(MediaStateHolder.MediaKey.IteReferenceImage1, bitmap, context)
                _uiState.update { it.copy(referenceImage1 = bitmap) }
            }
            2 -> {
                MediaStateHolder.putBitmap(MediaStateHolder.MediaKey.IteReferenceImage2, bitmap, context)
                _uiState.update { it.copy(referenceImage2 = bitmap) }
            }
        }
    }

    /**
     * Use a generated image as the source image (clearing the mask) and switch to the Source tab.
     *
     * May run before the screen was first opened, i.e. before [initialize]: the context is then
     * passed in so the image is stored where [loadSavedImages] will read it, otherwise the
     * initialization would bring back the previous source.
     */
    fun useAsSource(context: Context, bitmap: Bitmap) {
        val appContext = applicationContext ?: context.applicationContext
        MediaStateHolder.putBitmap(MediaStateHolder.MediaKey.ItiSource, bitmap, appContext)
        _uiState.update {
            it.copy(sourceImage = bitmap, sourceImageSize = null, compressedSlots = it.compressedSlots - 1,
                maskPaths = emptyList(), viewMode = ImageToImageViewMode.SOURCE)
        }
    }

    /** Remove the source image and its mask (also from the device in device storage mode). */
    fun clearSourceImage() {
        val context = applicationContext ?: return
        _uiState.update { it.copy(sourceImage = null, sourceImageSize = null,
            compressedSlots = it.compressedSlots - 1, maskPaths = emptyList()) }
        viewModelScope.launch { MediaStateHolder.evictAndDeleteFromDisk(context, MediaStateHolder.MediaKey.ItiSource) }
    }

    // Source image
    fun onSourceImageChange(context: Context, uri: Uri) {
        closeServerImagePicker()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Read bytes first to capture file size, then decode bitmap
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    _events.emit(GenerationScreenEvent.ShowToast(R.string.error_save_image))
                    return@launch
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val fileSize = bytes.size.toLong()

                if (bitmap != null) {
                    // Store in cache (memory or disk based on mode)
                    MediaStateHolder.putBitmap(MediaStateHolder.MediaKey.ItiSource, bitmap, context)

                    _uiState.update { it.copy(
                        sourceImage = bitmap,
                        sourceImageSize = fileSize,
                        compressedSlots = it.compressedSlots - 1,
                        maskPaths = emptyList()
                    ) }
                }
            } catch (e: Exception) {
                _events.emit(GenerationScreenEvent.ShowToast(R.string.error_save_image))
            }
        }
    }

    /**
     * Handle additional source image (image 2/3/4) selection.
     * @param index 2, 3, or 4
     */
    fun onAdditionalSourceImageChange(context: Context, index: Int, uri: Uri) {
        closeServerImagePicker()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Read bytes first to capture file size, then decode bitmap
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    _events.emit(GenerationScreenEvent.ShowToast(R.string.error_save_image))
                    return@launch
                }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val fileSize = bytes.size.toLong()

                if (bitmap != null) {
                    val key = when (index) {
                        2 -> MediaStateHolder.MediaKey.ItiSource2
                        3 -> MediaStateHolder.MediaKey.ItiSource3
                        4 -> MediaStateHolder.MediaKey.ItiSource4
                        else -> return@launch
                    }
                    MediaStateHolder.putBitmap(key, bitmap, context)

                    _uiState.update { state ->
                        val reset = state.copy(compressedSlots = state.compressedSlots - index)
                        when (index) {
                            2 -> reset.copy(sourceImage2 = bitmap, sourceImage2Size = fileSize)
                            3 -> reset.copy(sourceImage3 = bitmap, sourceImage3Size = fileSize)
                            4 -> reset.copy(sourceImage4 = bitmap, sourceImage4Size = fileSize)
                            else -> reset
                        }
                    }
                }
            } catch (e: Exception) {
                _events.emit(GenerationScreenEvent.ShowToast(R.string.error_save_image))
            }
        }
    }

    /**
     * Handle source image selection from gallery history (already a Bitmap from MediaCache).
     * Unlike onSourceImageChange which takes a Uri, this receives a pre-loaded Bitmap directly.
     * @param slot 1-4
     */
    fun acceptStandaloneGallerySource(context: Context, value: ServerGalleryViewModel.FullImage) {
        if (serverImageIdentity() != value.owner) return
        closeServerImagePicker()
        viewModelScope.launch {
            var prepared: java.io.File? = null
            try {
                withContext(Dispatchers.IO) {
                    prepared = java.io.File.createTempFile("server-source-", ".png", context.cacheDir)
                    prepared!!.outputStream().use { check(value.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                }
                if (serverImageIdentity() != value.owner) return@launch
                MediaStateHolder.putPreparedBitmap(MediaStateHolder.MediaKey.ItiSource, value.bitmap, context, value.owner.serverId, prepared)
                directSources.remove(1)
                _uiState.update { it.copy(sourceImage = value.bitmap, sourceImageSize = value.encodedSize.toLong(),
                    maskPaths = emptyList(), compressedSlots = it.compressedSlots - 1, viewMode = ImageToImageViewMode.SOURCE,
                    comparisonOriginal = null, comparisonSource = null) }
            } finally { prepared?.delete() }
        }
    }

    fun onSourceImageFromGallery(context: Context, slot: Int, bitmap: Bitmap) {
        closeServerImagePicker()
        viewModelScope.launch(Dispatchers.IO) {
            val key = when (slot) {
                1 -> MediaStateHolder.MediaKey.ItiSource
                2 -> MediaStateHolder.MediaKey.ItiSource2
                3 -> MediaStateHolder.MediaKey.ItiSource3
                4 -> MediaStateHolder.MediaKey.ItiSource4
                else -> return@launch
            }
            MediaStateHolder.putBitmap(key, bitmap, context)
            _uiState.update { state ->
                val reset = state.copy(compressedSlots = state.compressedSlots - slot)
                when (slot) {
                    1 -> reset.copy(sourceImage = bitmap, sourceImageSize = null, maskPaths = emptyList())
                    2 -> reset.copy(sourceImage2 = bitmap, sourceImage2Size = null)
                    3 -> reset.copy(sourceImage3 = bitmap, sourceImage3Size = null)
                    4 -> reset.copy(sourceImage4 = bitmap, sourceImage4Size = null)
                    else -> reset
                }
            }
        }
    }

    /**
     * Clear an additional source image by index.
     */
    fun clearAdditionalSourceImage(index: Int) {
        val key = when (index) {
            2 -> MediaStateHolder.MediaKey.ItiSource2
            3 -> MediaStateHolder.MediaKey.ItiSource3
            4 -> MediaStateHolder.MediaKey.ItiSource4
            else -> return
        }
        val ctx = applicationContext ?: return
        viewModelScope.launch {
            MediaStateHolder.evictAndDeleteFromDisk(ctx, key)
        }
        _uiState.update { state ->
            val reset = state.copy(compressedSlots = state.compressedSlots - index)
            when (index) {
                2 -> reset.copy(sourceImage2 = null, sourceImage2Size = null)
                3 -> reset.copy(sourceImage3 = null, sourceImage3Size = null)
                4 -> reset.copy(sourceImage4 = null, sourceImage4Size = null)
                else -> reset
            }
        }
    }

    /**
     * Compress a source image slot in-place (JPEG 80%) to reduce size.
     * Updates the stored bitmap and file size. Can only be applied once per slot.
     * @param slot 1-4
     */
    fun compressSourceImage(slot: Int) {
        val state = _uiState.value
        // Guard against double compression
        if (slot in state.compressedSlots) return

        val bitmap = when (slot) {
            1 -> state.sourceImage
            2 -> state.sourceImage2
            3 -> state.sourceImage3
            4 -> state.sourceImage4
            else -> return
        } ?: return

        viewModelScope.launch(Dispatchers.IO) {
            // Compress bitmap to JPEG 80%
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val compressedBytes = outputStream.toByteArray()

            // Decode JPEG bytes back to bitmap
            val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
            if (compressedBitmap == null) return@launch

            val compressedSize = compressedBytes.size.toLong()

            // Update state — replace bitmap, update file size, mark as compressed
            _uiState.update { current ->
                val slotUpdate = when (slot) {
                    1 -> current.copy(
                        sourceImage = compressedBitmap,
                        sourceImageSize = compressedSize,
                        compressedSlots = current.compressedSlots + slot
                    )
                    2 -> current.copy(
                        sourceImage2 = compressedBitmap,
                        sourceImage2Size = compressedSize,
                        compressedSlots = current.compressedSlots + slot
                    )
                    3 -> current.copy(
                        sourceImage3 = compressedBitmap,
                        sourceImage3Size = compressedSize,
                        compressedSlots = current.compressedSlots + slot
                    )
                    4 -> current.copy(
                        sourceImage4 = compressedBitmap,
                        sourceImage4Size = compressedSize,
                        compressedSlots = current.compressedSlots + slot
                    )
                    else -> return@launch
                }
                // Also persist to MediaCache if possible
                slotUpdate
            }

            // Save to disk cache so the compressed image persists
            val ctx = applicationContext ?: return@launch
            val key = when (slot) {
                1 -> MediaStateHolder.MediaKey.ItiSource
                2 -> MediaStateHolder.MediaKey.ItiSource2
                3 -> MediaStateHolder.MediaKey.ItiSource3
                4 -> MediaStateHolder.MediaKey.ItiSource4
                else -> return@launch
            }
            MediaStateHolder.putBitmap(key, compressedBitmap, ctx)
        }
    }

    private fun encodeSourceImageForUpload(bitmap: Bitmap, compressed: Boolean): Pair<ByteArray, String> {
        val outputStream = java.io.ByteArrayOutputStream()
        return if (compressed) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.toByteArray() to "jpg"
        } else {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.toByteArray() to "png"
        }
    }

    private fun setUploadProgress(label: String, totalBytes: Long, writtenBytes: Long = 0L) {
        _uiState.update {
            it.copy(
                uploadLabel = label,
                uploadTotalBytes = totalBytes,
                uploadProgressBytes = writtenBytes
            )
        }
    }

    /**
     * Toggle bypass state for a source image slot.
     * Slot 1 (primary image) cannot be bypassed — this is enforced at the call site.
     * @param slot 2, 3, or 4
     */
    fun toggleBypassSourceImage(slot: Int) {
        if (slot !in 2..4) return  // Only additional image slots can be bypassed
        val current = _uiState.value.bypassedSourceSlots
        val updated = if (slot in current) current - slot else current + slot
        _uiState.update { it.copy(bypassedSourceSlots = updated) }
        // Persist the change
        val state = _uiState.value
        val workflowId = state.editingParams.selectedWorkflowId
        val serverId = ConnectionManager.currentServerId
        val storage = workflowValuesStorage
        if (workflowId.isNotEmpty() && serverId != null && storage != null) {
            val existing = storage.loadValues(serverId, workflowId)
            storage.saveValues(serverId, workflowId,
                state.editingParams.toWorkflowValues(existing).copy(bypassedSourceSlots = updated))
        }
    }

    // Mask operations
    fun addMaskPath(path: Path, isEraser: Boolean, brushSize: Float) {
        val pathData = MaskPathData(path, isEraser, brushSize)
        _uiState.value = _uiState.value.copy(
            maskPaths = _uiState.value.maskPaths + pathData
        )
    }

    fun onBrushSizeChange(size: Float) {
        _uiState.value = _uiState.value.copy(brushSize = size)
    }

    fun onEraserModeChange(isEraser: Boolean) {
        _uiState.value = _uiState.value.copy(isEraserMode = isEraser)
    }

    fun clearMask() {
        _uiState.value = _uiState.value.copy(maskPaths = emptyList())
    }

    fun invertMask() {
        // Mark that mask is inverted - actual inversion happens when generating mask bitmap
        // For visual feedback, we'll use a special flag
        val sourceImage = _uiState.value.sourceImage ?: return

        viewModelScope.launch {
            // Create a full-coverage path and toggle all existing paths
            val fullPath = Path().apply {
                addRect(RectF(0f, 0f, sourceImage.width.toFloat(), sourceImage.height.toFloat()), Path.Direction.CW)
            }

            // Add inverted background
            val invertedPaths = mutableListOf<MaskPathData>()
            invertedPaths.add(MaskPathData(fullPath, false, 1f))

            // Add existing paths as erasers (to remove painted areas)
            _uiState.value.maskPaths.forEach { pathData ->
                invertedPaths.add(pathData.copy(isEraser = !pathData.isEraser))
            }

            _uiState.value = _uiState.value.copy(maskPaths = invertedPaths)
        }
    }

    fun hasMask(): Boolean {
        return _uiState.value.maskPaths.isNotEmpty()
    }

    /**
     * Generate the mask bitmap from current paths
     * Returns black/white bitmap where white = inpaint area
     */
    fun generateMaskBitmap(): Bitmap? = generateMaskBitmap(_uiState.value)

    private fun generateMaskBitmap(state: ImageToImageUiState): Bitmap? {
        val sourceImage = state.sourceImage ?: return null
        if (state.maskPaths.isEmpty()) return null

        // Create mask at source image size
        val maskBitmap = Bitmap.createBitmap(sourceImage.width, sourceImage.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskBitmap)

        // Start with black background
        canvas.drawColor(Color.BLACK)

        // Draw white for painted areas
        val paintPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        val erasePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        state.maskPaths.forEach { pathData ->
            val paint = if (pathData.isEraser) erasePaint else paintPaint
            paint.strokeWidth = pathData.brushSize
            canvas.drawPath(pathData.path, paint)
        }

        // Apply feathering
        return applyFeathering(maskBitmap, FEATHER_RADIUS)
    }

    private fun applyFeathering(mask: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return mask

        val width = mask.width
        val height = mask.height
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)

        // Convert to grayscale values (0-255)
        val values = IntArray(width * height) { i ->
            Color.red(pixels[i]) // Since it's black/white, R=G=B
        }

        // Horizontal pass
        val tempValues = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                var count = 0
                for (dx in -radius..radius) {
                    val nx = x + dx
                    if (nx in 0 until width) {
                        sum += values[y * width + nx]
                        count++
                    }
                }
                tempValues[y * width + x] = sum / count
            }
        }

        // Vertical pass
        val blurredValues = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                var count = 0
                for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny in 0 until height) {
                        sum += tempValues[ny * width + x]
                        count++
                    }
                }
                blurredValues[y * width + x] = sum / count
            }
        }

        // Convert back to pixels
        for (i in pixels.indices) {
            val v = blurredValues[i]
            pixels[i] = Color.rgb(v, v, v)
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)

        return result
    }

    /**
     * Workflow selection in the current mode.
     */
    override fun onWorkflowChange(workflowName: String) {
        val workflowItem = _uiState.value.currentModeWorkflows.find { it.name == workflowName } ?: return
        DebugLogger.d(TAG, "onWorkflowChange: ${Obfuscator.workflowName(workflowName)}")
        switchWorkflow(workflowItem.id, workflowItem.name)
        if (_uiState.value.mode == ImageToImageMode.EDITING) loadEditingSlotSettings(workflowItem.id)
    }

    /**
     * Reset prompt to seasonal default.
     * Clears saved positive prompt and reloads with seasonal default.
     */
    override fun resetPromptToDefault() {
        val ctx = applicationContext ?: return
        val serverId = ConnectionManager.currentServerId ?: return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Clear saved positive prompt
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().remove("${serverId}_$PREF_POSITIVE_PROMPT").apply()
            }

            // Set prompt to seasonal default
            val defaultPrompt = SeasonalPrompts.getImageToImagePrompt()
            updateParams(save = false) { it.copy(positivePrompt = defaultPrompt) }

            // Emit toast event
            _events.emit(GenerationScreenEvent.ShowToast(R.string.msg_prompt_preset_reset_success))
        }
    }

    // Validation

    override fun hasValidConfiguration(): Boolean {
        val state = _uiState.value
        // Both modes need a source image (Inpainting also a mask, checked when the workflow is prepared)
        return state.sourceImage != null && isReadyToGenerate(state.params()) &&
            (state.mode != ImageToImageMode.EDITING || state.hasValidEditingDimensions())
    }

    /**
     * Upload source image (with mask for inpainting, without for editing) to ComfyUI and prepare workflow
     */
    private val comparisonSources = ResultSourceAssociation<java.io.File>()

    fun submitPreparedWorkflow(
        generation: GenerationViewModel, prepared: PreparedImageWorkflow, front: Boolean = false,
        onResult: (Boolean, String?, String?) -> Unit
    ) {
        val serverId = MediaStateHolder.getCurrentServerId()
        generation.startGeneration(prepared.json, OWNER_ID, front = front,
            onSubmitted = { promptId -> comparisonSources.bind("$serverId:$promptId", prepared.sourceSnapshot) },
            onResult = onResult)

    }

    suspend fun prepareWorkflow(): PreparedImageWorkflow? {
        val client = comfyUIClient ?: return null
        val preparationServer = MediaStateHolder.getCurrentServerId()
        val context = applicationContext ?: return null

        // Check connection before uploading
        val connected = suspendCoroutine { cont ->
            ConnectionManager.ensureConnection(context) { success ->
                cont.resumeWith(Result.success(success))
            }
        }
        if (!connected || comfyUIClient !== client || preparationServer != MediaStateHolder.getCurrentServerId()) return null

        val uploadOwner = client.imageUploadIdentity()
        val state = _uiState.value
        val sourceImage = state.sourceImage ?: return null

        DebugLogger.i(TAG, "Preparing workflow (mode: ${state.mode})")

        _uiState.update { it.copy(isUploading = true) }
        val workflowJson: String? = try {
            when (state.mode) {
                ImageToImageMode.EDITING -> prepareEditingWorkflow(
                client,
                sourceImage,
                state.sourceImage2,
                state.sourceImage3,
                state.sourceImage4,
                state
            )
                ImageToImageMode.INPAINTING -> prepareInpaintingWorkflow(client, sourceImage, state)
            }
        } catch (e: java.io.IOException) {
            null // A required auxiliary image failed; never silently submit without it.
        } finally {
            _uiState.update { it.copy(isUploading = false, uploadTotalBytes = null, uploadProgressBytes = null, uploadLabel = null) }
        }

        if (workflowJson == null || comfyUIClient !== client || preparationServer != MediaStateHolder.getCurrentServerId() ||
            client.imageUploadIdentity() !== uploadOwner) return null
        // Stream the exact primary source used by this preparation to private cache. No bitmap
        // copy / ByteArray / Intent payload; failure disables comparison, never generation.
        val snapshot = if (state.mode == ImageToImageMode.EDITING) withContext(Dispatchers.IO) {
            try {
                java.io.File.createTempFile("editing-original-", ".png", context.cacheDir).also { file ->
                    file.outputStream().use { check(sourceImage.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                }
            } catch (e: Exception) { null }
        } else null
        if (comfyUIClient !== client || preparationServer != MediaStateHolder.getCurrentServerId() ||
            client.imageUploadIdentity() !== uploadOwner) { snapshot?.delete(); return null }
        val unresolved = findUnresolvedPlaceholders(workflowJson)
        return PreparedImageWorkflow(workflowJson, snapshot, unresolved)
    }

    /**
     * Scan workflow JSON for any unresolved {{placeholder}} tokens.
     * Returns the list of placeholder names found in the JSON.
     */
    private fun findUnresolvedPlaceholders(workflowJson: String): List<String> {
        val placeholderRegex = Regex("""\{\{(\w+)\}\}""")
        return placeholderRegex.findAll(workflowJson)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }

    /**
     * Prepare workflow for Editing mode (no mask, optional reference images)
     */
    private suspend fun uploadPreparedImage(
        client: ComfyUIClient, bytes: ByteArray, extension: String, label: String
    ): String? {
        setUploadProgress(label, bytes.size.toLong(), 0L)
        return try {
            client.uploadImageReusing(bytes, extension) { written, _ ->
                _uiState.update { it.copy(uploadProgressBytes = written) }
            }.path
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            if (e is sh.hnet.comfychair.connection.ImageUploadReuse.HttpFailure && e.status in setOf(401, 403)) {
                applicationContext?.let { ConnectionManager.showConnectionAlert(it, ConnectionFailure.AUTHENTICATION) }
            } else {
                _events.emit(GenerationScreenEvent.ShowToast(R.string.error_save_image))
            }
            null
        }
    }

    private suspend fun prepareEditingWorkflow(
        client: ComfyUIClient,
        sourceImage: Bitmap,
        sourceImage2: Bitmap?,
        sourceImage3: Bitmap?,
        sourceImage4: Bitmap?,
        state: ImageToImageUiState
    ): String? {
        if (!state.hasValidEditingDimensions()) return null
        // Capture all encodings from this preparation's state, never current UI during uploads.
        val epoch = client.imageUploadIdentity()
        val provenance = directSources.toMap()
        val workflowTemplate = WorkflowManager.getWorkflowById(state.editingParams.selectedWorkflowId)?.jsonContent.orEmpty()
        suspend fun source(bitmap: Bitmap, slot: Int): String? {
            val original = provenance[slot]
            if (original?.eligible(epoch, bitmap, bitmap.generationId, slot in state.compressedSlots,
                    state.maskPaths.isNotEmpty(), sh.hnet.comfychair.connection.DirectInputSource.supports(workflowTemplate, slot)) == true) {
                // Explicit original server semantics: native LoadImage decodes the original file,
                // not our Android-reencoded representation. Only 404 falls back to encoded upload.
                if (client.reuseInputImage(original.image, epoch)) return original.image.path
            }
            val (bytes, ext) = withContext(Dispatchers.IO) {
                encodeSourceImageForUpload(bitmap, slot in state.compressedSlots)
            }
            return uploadPreparedImage(client, bytes, ext, "source $slot")
        }
        suspend fun reference(bitmap: Bitmap, label: String): String? {
            val bytes = withContext(Dispatchers.IO) {
                java.io.ByteArrayOutputStream().also {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }.toByteArray()
            }
            return uploadPreparedImage(client, bytes, "png", label)
        }
        val uploadedSource = source(sourceImage, 1) ?: return null
        val uploadedRef1 = state.referenceImage1?.let { reference(it, "reference 1") ?: return null }
        val uploadedRef2 = state.referenceImage2?.let { reference(it, "reference 2") ?: return null }
        suspend fun additional(bitmap: Bitmap?, slot: Int): String? =
            if (bitmap != null && "image_filename_$slot" in state.workflowPlaceholders &&
                slot !in state.bypassedSourceSlots) source(bitmap, slot)
                    ?: throw java.io.IOException("Required source upload failed") else null
        val uploadedSource2 = additional(sourceImage2, 2)
        val uploadedSource3 = additional(sourceImage3, 3)
        val uploadedSource4 = additional(sourceImage4, 4)

        // Mapped dimensions come from the current editing controls, never stale storage
        // or source-image dimensions. Unmapped inputs remain owned by the workflow.

        val p = state.editingParams
        return WorkflowManager.prepareWorkflow(
            p.selectedWorkflowId, withSubmittedSeed(p),
            WorkflowManager.WorkflowImages(
                source = uploadedSource, source2 = uploadedSource2,
                source3 = uploadedSource3, source4 = uploadedSource4,
                reference1 = uploadedRef1, reference2 = uploadedRef2,
                bypassedSourceSlots = state.bypassedSourceSlots
            )
        )
    }

    /**
     * Prepare workflow for Inpainting mode (requires mask)
     */
    private suspend fun prepareInpaintingWorkflow(
        client: ComfyUIClient,
        sourceImage: Bitmap,
        state: ImageToImageUiState
    ): String? {
        // Generate mask
        val maskBitmap = generateMaskBitmap(state)
        if (maskBitmap == null) {
            _events.emit(GenerationScreenEvent.ShowToast(R.string.hint_paint_mask))
            return null
        }

        // Combine source image with mask in alpha channel
        val imageWithMask = combineImageWithMask(sourceImage, maskBitmap)
        maskBitmap.recycle()

        // Convert bitmap to PNG byte array
        val imageBytes = withContext(Dispatchers.IO) {
            val outputStream = java.io.ByteArrayOutputStream()
            imageWithMask.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.toByteArray()
        }

        imageWithMask.recycle()

        // Hash/upload the final alpha-masked bytes, not the unmasked source.
        val uploadedFilename = uploadPreparedImage(client, imageBytes, "png", "source") ?: return null

        val p = state.inpaintingParams
        return WorkflowManager.prepareWorkflow(
            p.selectedWorkflowId, withSubmittedSeed(p), WorkflowManager.WorkflowImages(source = uploadedFilename)
        )
    }

    /**
     * Combine source image with mask in alpha channel (mask white = transparent)
     */
    private fun combineImageWithMask(source: Bitmap, mask: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)

        val sourcePixels = IntArray(source.width * source.height)
        val maskPixels = IntArray(mask.width * mask.height)

        source.getPixels(sourcePixels, 0, source.width, 0, 0, source.width, source.height)
        mask.getPixels(maskPixels, 0, mask.width, 0, 0, mask.width, mask.height)

        for (i in sourcePixels.indices) {
            val r = Color.red(sourcePixels[i])
            val g = Color.green(sourcePixels[i])
            val b = Color.blue(sourcePixels[i])

            // Mask brightness - white = inpaint area = transparent alpha
            val maskBrightness = Color.red(maskPixels[i])
            val alpha = 255 - maskBrightness // Invert: white mask -> 0 alpha (transparent)

            sourcePixels[i] = Color.argb(alpha, r, g, b)
        }

        result.setPixels(sourcePixels, 0, source.width, 0, 0, source.width, source.height)
        return result
    }

    /**
     * Fetch and save generated image from history
     * @param promptId The prompt ID to fetch
     * @param onComplete Callback with success boolean (true if image was fetched and set)
     */
    @Volatile private var comparisonFetchToken = 0L

    fun fetchGeneratedImage(promptId: String, onComplete: (success: Boolean) -> Unit) {
        val token = ++comparisonFetchToken
        val serverId = MediaStateHolder.getCurrentServerId()
        _uiState.update { it.copy(comparisonOriginal = null, comparisonSource = null) }
        val client = comfyUIClient ?: run {
            onComplete(false)
            return
        }
        val context = applicationContext ?: run {
            onComplete(false)
            return
        }

        _uiState.update { it.copy(isFetching = true) }

        client.fetchHistory(promptId) { historyJson ->
            if (historyJson == null) {
                _uiState.update { it.copy(isFetching = false) }
                onComplete(false)
                return@fetchHistory
            }

            val outputs = GenerationOutputs.fromHistory(historyJson, promptId)
            val image = outputs.firstOrNull { !it.isVideo }
            if (image != null) {
                val filename = image.filename
                val subfolder = image.subfolder
                val type = image.type

                client.fetchImage(filename, subfolder, type) { bitmap, failureType ->
                    _uiState.update { it.copy(isFetching = false) }
                    if (bitmap != null) {
                        viewModelScope.launch {
                            val source = comparisonSources.sourceFor("$serverId:$promptId")
                            val original = withContext(Dispatchers.IO) {
                                source?.let { file ->
                                    try {
                                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                        BitmapFactory.decodeFile(file.absolutePath, bounds)
                                        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                                        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 2048) {
                                            options.inSampleSize *= 2
                                        }
                                        BitmapFactory.decodeFile(file.absolutePath, options)
                                    } catch (_: Exception) { null }
                                    catch (_: OutOfMemoryError) { null }
                                }
                            }
                            if (token != comparisonFetchToken || serverId != MediaStateHolder.getCurrentServerId()) {
                                onComplete(false)
                                return@launch
                            }
                            MediaStateHolder.putBitmap(MediaStateHolder.MediaKey.ItiPreview, bitmap, context)
                            _uiState.update { it.copy(
                                comparisonOriginal = original,
                                comparisonSource = if (original != null && source != null && serverId != null)
                                    EditingViewerSnapshots.Source(source, serverId, promptId, filename, subfolder, type) else null,
                                previewImage = bitmap,
                                generationOutputs = outputs,
                                currentResultIndex = 0,
                                previewImageFilename = filename,
                                previewImageSubfolder = subfolder,
                                previewImageType = type,
                                viewMode = ImageToImageViewMode.PREVIEW
                            ) }
                            onComplete(true)
                        }
                    } else if (failureType == ConnectionFailure.STALLED) {
                        ConnectionManager.showConnectionAlert(context, failureType)
                        onComplete(false)
                    } else {
                        onComplete(false)
                    }
                }
                return@fetchHistory
            }

            _uiState.update { it.copy(isFetching = false) }
            onComplete(false)
        }
    }

    fun editingViewerTicket(): String? {
        val state = _uiState.value
        val source = state.comparisonSource ?: return null
        if (state.mode != ImageToImageMode.EDITING || state.comparisonOriginal == null ||
            source.server != MediaStateHolder.getCurrentServerId() || source.filename != state.previewImageFilename ||
            source.subfolder != state.previewImageSubfolder || source.type != state.previewImageType) return null
        return EditingViewerSnapshots.issue(source)
    }

    fun onPreviewBitmapChange(bitmap: Bitmap) {
        comparisonFetchToken++
        _uiState.value = _uiState.value.copy(previewImage = bitmap, comparisonOriginal = null, comparisonSource = null)
        saveLastPreviewImage(bitmap)
    }

    /**
     * Select the result shown in the preview when a job produced several files.
     */
    fun onCurrentResultChange(index: Int) {
        _uiState.update { it.copy(currentResultIndex = index.coerceIn(0, (it.generationOutputs.size - 1).coerceAtLeast(0))) }
    }

    /**
     * Clear preview for a specific execution. Only clears if this is a new promptId
     * to prevent duplicate clears when navigating back to the screen.
     */
    override fun clearPreviewForExecution(promptId: String) {
        if (promptId == lastClearedForPromptId) {
            return // Already cleared for this promptId
        }
        comparisonFetchToken++
        lastClearedForPromptId = promptId
        // Evict from cache so loadSavedImages() won't restore the old preview
        // when navigating back to this screen during generation
        MediaStateHolder.evict(MediaStateHolder.MediaKey.ItiPreview)
        _uiState.value = _uiState.value.copy(
            comparisonOriginal = null, comparisonSource = null,
            previewImage = null,
            generationOutputs = emptyList(),
            currentResultIndex = 0,
            previewImageFilename = null,
            previewImageSubfolder = null,
            previewImageType = null
        )
    }

    fun clearPreview() {
        comparisonFetchToken++
        lastClearedForPromptId = null // Reset tracking when manually clearing
        _uiState.value = _uiState.value.copy(
            comparisonOriginal = null, comparisonSource = null,
            previewImage = null,
            generationOutputs = emptyList(),
            currentResultIndex = 0,
            previewImageFilename = null,
            previewImageSubfolder = null,
            previewImageType = null
        )
    }

    // Event handling

    /**
     * Start listening for generation events from the GenerationViewModel.
     * This registers this ViewModel as the active event handler.
     */
    override fun startListening(generationViewModel: GenerationViewModel) {
        generationViewModelRef = generationViewModel

        // If generation is running for this screen, switch to preview mode
        val state = generationViewModel.generationState.value
        if (state.isGenerating && state.ownerId == OWNER_ID) {
            _uiState.value = _uiState.value.copy(viewMode = ImageToImageViewMode.PREVIEW)
        }

        generationViewModel.registerEventHandler(OWNER_ID) { event ->
            handleGenerationEvent(event)
        }
    }

    /**
     * Stop listening for generation events.
     * Note: We keep the generationViewModelRef if generation is still running,
     * as the handler may still be called for completion events.
     */
    override fun stopListening(generationViewModel: GenerationViewModel) {
        generationViewModel.unregisterEventHandler(OWNER_ID)
        // Only clear ref if no generation is active (handler was actually unregistered)
        // If generation is running, the handler is kept and needs the ref
        if (!generationViewModel.generationState.value.isGenerating) {
            if (generationViewModelRef == generationViewModel) {
                generationViewModelRef = null
            }
        }
    }

    /**
     * Handle generation events from the GenerationViewModel.
     */
    private fun handleGenerationEvent(event: GenerationEvent) {
        when (event) {
            is GenerationEvent.PreviewImage -> {
                onPreviewBitmapChange(event.bitmap)
            }
            is GenerationEvent.ImageGenerated -> {
                val promptId = event.promptId
                DebugLogger.i(TAG, "ImageGenerated: ${Obfuscator.promptId(promptId)}")
                fetchGeneratedImage(promptId) { success ->
                    if (success) {
                        generationViewModelRef?.completeGeneration(promptId)
                    }
                    // If not successful, don't complete - will retry on next return
                }
            }
            is GenerationEvent.VideoGenerated -> {
                _uiState.update { it.copy(comparisonOriginal = null, comparisonSource = null) }
            }
            is GenerationEvent.ConnectionLostDuringGeneration -> {
                DebugLogger.w(TAG, "ConnectionLostDuringGeneration")
                viewModelScope.launch {
                    val message = applicationContext?.getString(R.string.msg_connection_lost_generation_may_continue)
                        ?: "Connection lost. Will check for completion when reconnected."
                    _events.emit(GenerationScreenEvent.ShowToastMessage(message))
                }
                // DON'T clear state - generation may still be running on server
            }
            is GenerationEvent.Error -> {
                viewModelScope.launch {
                    _events.emit(GenerationScreenEvent.ShowToastMessage(event.message))
                }
            }
            is GenerationEvent.ClearPreviewForResume -> {
                // Don't clear - keep the preview visible during navigation
                // New live previews will naturally replace the current one
            }
            else -> {}
        }
    }

    private fun saveLastPreviewImage(bitmap: Bitmap) {
        // Store in cache (memory or disk based on mode)
        MediaStateHolder.putBitmap(MediaStateHolder.MediaKey.ItiPreview, bitmap, applicationContext)
    }
}

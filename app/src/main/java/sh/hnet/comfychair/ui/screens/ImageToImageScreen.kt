package sh.hnet.comfychair.ui.screens

import android.content.ClipData
import android.graphics.Bitmap
import android.content.ClipboardManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import sh.hnet.comfychair.ui.components.mediaOrigin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.hnet.comfychair.MaskEditorActivity
import sh.hnet.comfychair.MediaViewerActivity
import sh.hnet.comfychair.R
import sh.hnet.comfychair.cache.MaskEditorStateHolder
import sh.hnet.comfychair.cache.MediaCache
import sh.hnet.comfychair.connection.ConnectionManager
import sh.hnet.comfychair.model.ScreenType
import sh.hnet.comfychair.navigation.MainRoute
import sh.hnet.comfychair.queue.JobDescription
import sh.hnet.comfychair.queue.JobRegistry
import sh.hnet.comfychair.ui.components.AppMenuDropdown
import sh.hnet.comfychair.ui.components.PromptLibraryDialog
import sh.hnet.comfychair.ui.components.PromptPresetDialog
import sh.hnet.comfychair.ui.components.shared.PromptPresetDropdown
import sh.hnet.comfychair.storage.AppSettings
import sh.hnet.comfychair.repository.GalleryRepository
import sh.hnet.comfychair.ui.components.shared.rememberLastPickedImageUri
import sh.hnet.comfychair.ui.components.shared.rememberOpenDocumentWithInitialUri
import sh.hnet.comfychair.ui.components.GalleryPickerBottomSheet
import sh.hnet.comfychair.ui.components.MaterialLibraryPickerBottomSheet
import sh.hnet.comfychair.materials.MaterialLibrary
import sh.hnet.comfychair.viewmodel.GalleryItem
import sh.hnet.comfychair.viewmodel.MaterialLibraryViewModel
import sh.hnet.comfychair.ui.components.GenerationButton
import sh.hnet.comfychair.ui.components.ServerImageSourceButtons
import sh.hnet.comfychair.ui.components.config.ConfigBottomSheetContent
import sh.hnet.comfychair.ui.components.config.UnifiedCallbacks
import sh.hnet.comfychair.ui.components.LocalMediaViewer
import sh.hnet.comfychair.ui.components.MaskPreview
import sh.hnet.comfychair.ui.components.ResultHeroImage
import sh.hnet.comfychair.ui.components.ResultHeroVideo
import sh.hnet.comfychair.ui.components.ResultsPreview
import sh.hnet.comfychair.ui.components.SourceTarget
import sh.hnet.comfychair.ui.components.config.toBottomSheetConfig
import sh.hnet.comfychair.ui.components.config.workflowGroups
import sh.hnet.comfychair.ui.components.config.workflowIcon
import sh.hnet.comfychair.ui.components.generation.EmptyResultState
import sh.hnet.comfychair.ui.components.generation.EmptySourcePicker
import sh.hnet.comfychair.ui.components.generation.GenerationControls
import sh.hnet.comfychair.ui.components.generation.GenerationOptionsSheet
import sh.hnet.comfychair.ui.components.generation.GenerationPreviewArea
import sh.hnet.comfychair.ui.components.generation.GenerationScreenEffects
import sh.hnet.comfychair.ui.components.generation.GenerationTopBar
import sh.hnet.comfychair.ui.components.generation.MaskLabel
import sh.hnet.comfychair.ui.components.generation.MaskTools
import sh.hnet.comfychair.ui.components.generation.PromptDock
import sh.hnet.comfychair.ui.components.generation.PromptPresetDialogs
import sh.hnet.comfychair.ui.components.generation.RecipeChips
import sh.hnet.comfychair.ui.components.generation.ReferenceSlot
import sh.hnet.comfychair.ui.components.generation.ReferenceSlots
import sh.hnet.comfychair.ui.components.generation.ResultActionsMenu
import sh.hnet.comfychair.ui.components.generation.ResultMedia
import sh.hnet.comfychair.ui.components.generation.ScreenJobStatus
import sh.hnet.comfychair.ui.components.generation.SourceResultSwitcher
import sh.hnet.comfychair.ui.components.generation.SourceToolbar
import sh.hnet.comfychair.ui.components.generation.imageSendActions
import sh.hnet.comfychair.ui.components.generation.rememberJobSubmitter
import sh.hnet.comfychair.ui.components.generation.rememberOutputThumbnail
import sh.hnet.comfychair.ui.components.generation.rememberPromptInputState
import sh.hnet.comfychair.ui.components.loadResultBitmap
import sh.hnet.comfychair.ui.components.rememberResultsViewer
import sh.hnet.comfychair.util.VideoUtils
import sh.hnet.comfychair.viewmodel.GenerationViewModel
import sh.hnet.comfychair.viewmodel.ImageToImageMode
import sh.hnet.comfychair.viewmodel.ImageToImageViewMode
import sh.hnet.comfychair.viewmodel.ImageToImageViewModel
import sh.hnet.comfychair.viewmodel.PromptPresetViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ImageToImageScreen(
    generationViewModel: GenerationViewModel,
    imageToImageViewModel: ImageToImageViewModel,
    onUseAsSource: (SourceTarget, suspend () -> Bitmap?) -> Unit,
    presetViewModel: PromptPresetViewModel = viewModel(),
    materialLibraryViewModel: MaterialLibraryViewModel = viewModel()
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    val ownerId = ImageToImageViewModel.OWNER_ID

    val uiState by imageToImageViewModel.uiState.collectAsState()
    val serverPickerState by imageToImageViewModel.serverImagePickerState.collectAsState()
    DisposableEffect(imageToImageViewModel) {
        onDispose { imageToImageViewModel.closeServerImagePicker() }
    }
    LaunchedEffect(uiState.mode, uiState.editingParams.workflowPlaceholders) {
        imageToImageViewModel.validateServerImagePicker()
    }
    val sourceImage = uiState.sourceImage
    val previewImage = uiState.previewImage
    val useAsReference: ((Bitmap) -> Unit)? =
        if (imageToImageViewModel.freeReferenceSlot(uiState) != null) imageToImageViewModel::useAsReference else null
    val presetUiState by presetViewModel.uiState.collectAsState()
    val queueState by JobRegistry.queueState.collectAsState()

    GenerationScreenEffects(
        imageToImageViewModel, generationViewModel, presetViewModel, ScreenType.IMAGE_TO_IMAGE, ownerId,
        onJobStarted = { imageToImageViewModel.onViewModeChange(ImageToImageViewMode.PREVIEW) }
    )
    val promptInput = rememberPromptInputState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, imageToImageViewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) imageToImageViewModel.closeServerImagePicker()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showOptionsSheet by remember { mutableStateOf(false) }

    val isThisScreenExecuting = queueState.executingOwnerId == ownerId
    // Several results of the last job are shown as a pager with a thumbnail strip
    val showResults = !isThisScreenExecuting && uiState.generationOutputs.size > 1
    val isThisScreenQueued = !isThisScreenExecuting && queueState.hasWaitingJob(ownerId)
    val generationState by generationViewModel.generationState.collectAsState()
    val isConnecting by ConnectionManager.isConnecting.collectAsState()
    val isOfflineMode = remember { AppSettings.isOfflineMode(context) }

    // Gallery picker state
    var showGalleryPicker by remember { mutableStateOf(false) }
    var showMaterialPicker by remember { mutableStateOf(false) }
    var currentPickerSlot by remember { mutableStateOf(1) }
    val galleryPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val materialPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val galleryImages: List<GalleryItem> by GalleryRepository.getInstance().galleryItems.collectAsState()
    val materialUiState by materialLibraryViewModel.uiState.collectAsState()

    var resultInfoVisible by remember(uiState.previewImage) { mutableStateOf(false) }
    var comparisonEnabled by remember(uiState.previewImage, uiState.comparisonOriginal, uiState.mode) { mutableStateOf(false) }

    // Image info overlay state (which slot's info is shown, null = hidden)
    var imageInfoSlot by remember { mutableStateOf<Int?>(null) }

    // Image picker launcher for source image (system file picker - supports Downloads, file managers, gallery)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        rememberOpenDocumentWithInitialUri(rememberLastPickedImageUri(context))
    ) { uri ->
        uri?.let {
            // Take persistable permission so we can read the file later
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            AppSettings.setLastImagePickerUri(context, it.toString())
            imageToImageViewModel.onSourceImageChange(context, it)
            imageToImageViewModel.onViewModeChange(ImageToImageViewMode.SOURCE)
        }
    }

    // Additional source image pickers (slots 2, 3, 4)
    val imagePickerLauncher2 = rememberLauncherForActivityResult(
        rememberOpenDocumentWithInitialUri(rememberLastPickedImageUri(context))
    ) { uri ->
        uri?.let {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            AppSettings.setLastImagePickerUri(context, it.toString())
            imageToImageViewModel.onAdditionalSourceImageChange(context, 2, it)
        }
    }

    val imagePickerLauncher3 = rememberLauncherForActivityResult(
        rememberOpenDocumentWithInitialUri(rememberLastPickedImageUri(context))
    ) { uri ->
        uri?.let {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            AppSettings.setLastImagePickerUri(context, it.toString())
            imageToImageViewModel.onAdditionalSourceImageChange(context, 3, it)
        }
    }

    val imagePickerLauncher4 = rememberLauncherForActivityResult(
        rememberOpenDocumentWithInitialUri(rememberLastPickedImageUri(context))
    ) { uri ->
        uri?.let {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, takeFlags)
            AppSettings.setLastImagePickerUri(context, it.toString())
            imageToImageViewModel.onAdditionalSourceImageChange(context, 4, it)
        }
    }

    // ActivityResultLauncher for MediaViewer replace flow
    val mediaViewerReplaceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Always clear callbacks when MediaViewer closes
        MediaViewerActivity.onBypassToggleCallback = null
        MediaViewerActivity.onUseAsSourceCallback = null
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
                val replaceSlot = data.getIntExtra(MediaViewerActivity.RESULT_SLOT, -1)
                val isReplaceAction = data.getBooleanExtra(MediaViewerActivity.RESULT_REPLACE, false)
                if (replaceSlot > 0) {
                    if (isReplaceAction) {
                        // Replace button → open system file picker
                        when (replaceSlot) {
                            1 -> imagePickerLauncher.launch(arrayOf("image/*"))
                            2 -> imagePickerLauncher2.launch(arrayOf("image/*"))
                            3 -> imagePickerLauncher3.launch(arrayOf("image/*"))
                            4 -> imagePickerLauncher4.launch(arrayOf("image/*"))
                        }
                    } else {
                        // Use as source button → open gallery picker
                        currentPickerSlot = replaceSlot
                        showGalleryPicker = true
                }
            }
        }
    }

    // PagerState for HorizontalPager — initial page 0 (source image 1)
    val activeSlots = sh.hnet.comfychair.viewmodel.activeSourceSlots(uiState.mode, uiState.editingParams.workflowPlaceholders)
    val pagerState = rememberPagerState(initialPage = 0) { activeSlots.size + 1 }

    // Sync pagerState with viewMode when viewMode changes externally
    LaunchedEffect(uiState.viewMode) {
        when (uiState.viewMode) {
            ImageToImageViewMode.PREVIEW -> {
                pagerState.scrollToPage(pagerState.pageCount - 1)
            }
            ImageToImageViewMode.SOURCE -> {
                // Stay on current source page (don't override user's scroll position)
            }
        }
    }

    // Clamp pagerState to valid range when pageCount decreases (workflow with fewer slots)
    LaunchedEffect(pagerState.pageCount) {
        if (pagerState.currentPage >= pagerState.pageCount) {
            pagerState.scrollToPage(maxOf(0, pagerState.pageCount - 1))
        }
    }

    // Compute tab list — source images driven by workflow additionalImageSlotCount + preview
    // Tabs reflect workflow capabilities (how many slots the workflow supports),
    // not whether images are already set (which would break workflow switching UX)
    data class ImagePage(val slot: Int, val title: String) // slot 1-4 for sources, 0 for preview
    val imagePages = activeSlots.map { slot -> ImagePage(slot, "原图$slot") }
    val previewPageIndex = imagePages.size // preview is always last
    val isPreviewPage = pagerState.currentPage == previewPageIndex

    // Event handling
    var pendingWorkflowJson by remember { mutableStateOf<sh.hnet.comfychair.viewmodel.PreparedImageWorkflow?>(null) }
    var showPlaceholderDialog by remember { mutableStateOf(false) }
    var pendingPlaceholders by remember { mutableStateOf(emptyList<String>()) }
    var batchCount by remember { mutableStateOf(AppSettings.getBatchCount(context)) }
    var errorDialogMessage by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }

    // UI composition
    // Opens the mask editor on the current source; its edits come back through the ViewModel
    fun openMaskEditor(sourceImage: Bitmap) {
        MaskEditorStateHolder.initialize(
            sourceImage = sourceImage,
            maskPaths = uiState.maskPaths,
            brushSize = uiState.brushSize,
            isEraserMode = uiState.isEraserMode,
            onPathAdded = { path, isEraser, brushSize ->
                imageToImageViewModel.addMaskPath(path, isEraser, brushSize)
                // Update state holder with new paths
                MaskEditorStateHolder.updateMaskPaths(imageToImageViewModel.uiState.value.maskPaths)
            },
            onClearMask = {
                imageToImageViewModel.clearMask()
                MaskEditorStateHolder.updateMaskPaths(emptyList())
            },
            onInvertMask = {
                imageToImageViewModel.invertMask()
                MaskEditorStateHolder.updateMaskPaths(imageToImageViewModel.uiState.value.maskPaths)
            },
            onBrushSizeChange = { imageToImageViewModel.onBrushSizeChange(it) },
            onEraserModeChange = { imageToImageViewModel.onEraserModeChange(it) }
        )
        context.startActivity(MaskEditorActivity.createIntent(context))
    }

    // Whether the mask is drawn over the source; only a view setting, the mask itself stays
    var maskVisible by rememberSaveable { mutableStateOf(true) }

    val mediaViewer = LocalMediaViewer.current

    // Generate from the dock and from the prompt editor
    fun submitPrepared(prepared: sh.hnet.comfychair.viewmodel.PreparedImageWorkflow, front: Boolean) {
        if (batchCount > 1) {
            AppSettings.setBatchCount(context, batchCount)
            var completed = 0
            val total = batchCount
            fun generateNext() {
                if (completed >= total) return
                imageToImageViewModel.submitPreparedWorkflow(generationViewModel, prepared, front) { success, _, errorMessage ->
                    completed++
                    if (!success && errorMessage != null) {
                        errorDialogMessage = errorMessage
                        showErrorDialog = true
                    }
                    if (completed < total) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ generateNext() }, 300)
                    }
                }
            }
            generateNext()
        } else {
            imageToImageViewModel.submitPreparedWorkflow(generationViewModel, prepared, front) { success, _, errorMessage ->
                if (!success) {
                    errorDialogMessage = errorMessage ?: context.getString(R.string.error_generation_failed)
                    showErrorDialog = true
                }
            }
        }
    }

    // The fork's submit flow: require the mask in inpainting, prepare (uploading sources), confirm any
    // unresolved placeholders, then submit — honouring the batch count and front-of-queue requests.
    val submitJob: (front: Boolean) -> Unit = { front ->
        scope.launch {
            if (uiState.mode == ImageToImageMode.INPAINTING && !imageToImageViewModel.hasMask()) {
                Toast.makeText(context, context.getString(R.string.hint_paint_mask), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val prepared = imageToImageViewModel.prepareWorkflow()
            if (prepared == null) {
                Toast.makeText(context, context.getString(R.string.error_failed_load_workflow), Toast.LENGTH_SHORT).show()
                return@launch
            }
            pendingWorkflowJson = prepared
            pendingPlaceholders = prepared.unresolved
            if (prepared.unresolved.isNotEmpty()) {
                showPlaceholderDialog = true
                return@launch
            }
            submitPrepared(prepared, front)
        }
    }



    Column(modifier = Modifier.fillMaxSize()) {
        GenerationTopBar(
            screen = MainRoute.ImageToImage,
            ownerId = ownerId,
            generationViewModel = generationViewModel,
            // Fork: server image sources share the top bar's icon-action row, alongside the picker
            actions = {
                ServerImageSourceButtons(onSelect = { source ->
                    imageToImageViewModel.openServerImagePicker(
                        imagePages.getOrNull(pagerState.currentPage)?.slot ?: 1, source
                    )
                })
            }
        )

            sh.hnet.comfychair.ui.components.ServerImagePickerPanel(
            state = serverPickerState,
            loadPreview = imageToImageViewModel::loadServerImagePreview,
            onSelect = imageToImageViewModel::selectServerImage,
            onLoadMore = { imageToImageViewModel.loadMoreServerImages() },
            onRetryMore = { imageToImageViewModel.loadMoreServerImages(retry = true) },
            onRefresh = imageToImageViewModel::refreshServerImages,
            onQuery = imageToImageViewModel::changeServerImageQuery,
            onCancelDownload = imageToImageViewModel::cancelServerImageDownload,
            onRetryDownload = imageToImageViewModel::retryServerImageDownload,
            onClose = imageToImageViewModel::closeServerImagePicker,
            onDeleted = { imageToImageViewModel.removeDeletedServerImages(it, serverPickerState.session) }
        )

        // Progress indicator - below app bar, only show if THIS screen's job is executing
        if (isThisScreenExecuting && generationState.maxProgress > 0) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { generationState.progress.toFloat() / generationState.maxProgress.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Image Preview Area — HorizontalPager
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !comparisonEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 150.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) { page ->
            val isThisPreviewPage = page == previewPageIndex
            val sourceSlot = imagePages.getOrNull(page)?.slot ?: 0
            val sourceImage = when (sourceSlot) {
                1 -> uiState.sourceImage
                2 -> uiState.sourceImage2
                3 -> uiState.sourceImage3
                4 -> uiState.sourceImage4
                else -> null
            }
            val sourcePicker = when (sourceSlot) {
                1 -> imagePickerLauncher
                2 -> imagePickerLauncher2
                3 -> imagePickerLauncher3
                4 -> imagePickerLauncher4
                else -> null
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isThisPreviewPage) {
                    // Preview page
                    if (uiState.previewImage != null && !isThisScreenExecuting) {
                        if (comparisonEnabled && uiState.comparisonOriginal != null && uiState.mode == ImageToImageMode.EDITING) {
                            sh.hnet.comfychair.ui.components.EditingImageComparison(
                                original = uiState.comparisonOriginal!!, result = uiState.previewImage!!,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else Image(
                            bitmap = uiState.previewImage!!.asImageBitmap(),
                            contentDescription = stringResource(R.string.content_description_preview),
                                modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    uiState.previewImage?.let { bitmap ->
                                        // Set callbacks before launching MediaViewer
                                        MediaViewerActivity.onBypassToggleCallback =
                                            { slot -> imageToImageViewModel.toggleBypassSourceImage(slot) }
                                        MediaViewerActivity.onUseAsSourceCallback =
                                            { _, _, _, _, bmp -> imageToImageViewModel.onSourceImageFromGallery(context, 1, bmp) }
                                        val intent = MediaViewerActivity.createSingleImageIntent(
                                            context = context,
                                            bitmap = bitmap,
                                            hostname = generationViewModel.getHostname(),
                                            port = generationViewModel.getPort(),
                                            filename = uiState.previewImageFilename,
                                            subfolder = uiState.previewImageSubfolder,
                                            type = uiState.previewImageType,
                                            replaceSlot = 1,
                                            bypassSlot = 1,
                                            isSlotBypassed = false,
                                            comparisonTicket = imageToImageViewModel.editingViewerTicket(),
                                            resultPromptId = uiState.comparisonSource?.prompt
                                        )
                                        // Prefer ActivityResultLauncher for proper result callback, fallback to direct start
                                        if (mediaViewerReplaceLauncher != null) {
                                            mediaViewerReplaceLauncher!!.launch(intent)
                                        } else {
                                            android.util.Log.w("ComfyChair", "mediaViewerReplaceLauncher was null, using direct startActivity")
                                            context.startActivity(intent)
                                        }
                                    }
                                },
                            contentScale = ContentScale.Fit
                        )
                        IconButton(
                            onClick = { resultInfoVisible = !resultInfoVisible },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) {
                            Icon(Icons.Filled.Info, stringResource(R.string.content_description_image_info),
                                tint = Color.White.copy(alpha = 0.85f))
                        }
                        if (resultInfoVisible) {
                            Column(Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 8.dp)
                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text("${uiState.previewImage!!.width} × ${uiState.previewImage!!.height}",
                                    color = Color.White, style = MaterialTheme.typography.bodySmall)
                                // No encoded response bytes retained by fetchImage: never report bitmap RAM as file size.
                                Text(stringResource(R.string.image_file_size_unavailable),
                                    color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                                if (uiState.mode == ImageToImageMode.EDITING && uiState.comparisonOriginal != null) {
                                    TextButton(onClick = { comparisonEnabled = !comparisonEnabled }) {
                                        Text(stringResource(if (comparisonEnabled) R.string.comparison_close else R.string.comparison_open), color = Color.White)
                                    }
                                }
                            }
                        }
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_comfychair_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(300.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    // Source image page
                    if (sourceImage != null) {
                        if (uiState.mode == ImageToImageMode.INPAINTING && sourceSlot == 1) {
                            // Inpainting: source image 1 shows mask overlay
                            Box(modifier = Modifier.fillMaxSize()) {

                                MaskPreview(
                                    sourceImage = sourceImage,
                                    maskPaths = uiState.maskPaths,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Image info (i) button for inpainting slot 1
                                IconButton(
                                    onClick = {
                                        imageInfoSlot = if (imageInfoSlot == sourceSlot) null else sourceSlot
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = stringResource(R.string.content_description_image_info),
                                        tint = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                                // Image info overlay
                                if (imageInfoSlot == sourceSlot) {
                                    val fileSize = uiState.sourceImageSize
                                    val fileSizeText = fileSize?.let {
                                        if (it >= 1024 * 1024) {
                                            String.format("%.1f MB", it / (1024.0 * 1024.0))
                                        } else {
                                            String.format("%.1f KB", it / 1024.0)
                                        }
                                    } ?: "Unknown"
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 52.dp, end = 8.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.65f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = "${sourceImage.width} × ${sourceImage.height}",
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = fileSizeText,
                                                color = Color.White.copy(alpha = 0.8f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            TextButton(
                                                onClick = { imageToImageViewModel.compressSourceImage(sourceSlot) },
                                                enabled = sourceSlot !in uiState.compressedSlots,
                                                modifier = Modifier.height(28.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                            ) {
                                                Text(
                                                    text = if (sourceSlot in uiState.compressedSlots) "Compressed ✓" else "Compress",
                                                    color = if (sourceSlot in uiState.compressedSlots)
                                                        Color.Green.copy(alpha = 0.9f)
                                                    else
                                                        Color.White.copy(alpha = 0.85f),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    bitmap = sourceImage.asImageBitmap(),
                                    contentDescription = stringResource(R.string.content_description_source_image),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            // Set callbacks before launching MediaViewer
                                            MediaViewerActivity.onBypassToggleCallback =
                                                { slot -> imageToImageViewModel.toggleBypassSourceImage(slot) }
                                            MediaViewerActivity.onUseAsSourceCallback =
                                                { _, _, _, _, bmp -> imageToImageViewModel.onSourceImageFromGallery(context, 1, bmp) }
                                            android.util.Log.d("ComfyChair", "Launch MediaViewer sourceSlot=$sourceSlot")
                                            val intent = MediaViewerActivity.createSingleImageIntent(
                                                context = context,
                                                sourceImage,
                                                replaceSlot = sourceSlot,
                                                bypassSlot = sourceSlot,
                                                isSlotBypassed = uiState.bypassedSourceSlots.contains(sourceSlot)
                                            )
                                            // Prefer ActivityResultLauncher for proper result callback, fallback to direct start
                                            if (mediaViewerReplaceLauncher != null) {
                                                mediaViewerReplaceLauncher!!.launch(intent)
                                            } else {
                                                android.util.Log.w("ComfyChair", "mediaViewerReplaceLauncher was null (source), using direct startActivity")
                                                context.startActivity(intent)
                                            }
                                        },
                                    contentScale = ContentScale.Crop
                                )
                                // Bypass overlay — dimmed + icon for slots 2/3/4 that are bypassed
                                if (sourceSlot >= 2 && uiState.bypassedSourceSlots.contains(sourceSlot)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.45f))
                                            .clickable { imageToImageViewModel.toggleBypassSourceImage(sourceSlot) }
                                    )
                                    Icon(
                                        imageVector = Icons.Default.DoNotDisturb,
                                        contentDescription = stringResource(R.string.node_editor_bypass),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .clickable { imageToImageViewModel.toggleBypassSourceImage(sourceSlot) },
                                        tint = Color.White.copy(alpha = 0.85f)
                                    )
                                }

                                // Image info (i) button — shown for all source image slots
                                IconButton(
                                    onClick = {
                                        imageInfoSlot = if (imageInfoSlot == sourceSlot) null else sourceSlot
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(
                                            top = if (sourceSlot >= 2 && uiState.bypassedSourceSlots.contains(sourceSlot)) 48.dp else 8.dp,
                                            end = 8.dp
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = stringResource(R.string.content_description_image_info),
                                        tint = Color.White.copy(alpha = 0.85f)
                                    )
                                }

                                // Image info overlay — semi-transparent box showing dimensions and file size
                                if (imageInfoSlot == sourceSlot) {
                                    val fileSize = when (sourceSlot) {
                                        1 -> uiState.sourceImageSize
                                        2 -> uiState.sourceImage2Size
                                        3 -> uiState.sourceImage3Size
                                        4 -> uiState.sourceImage4Size
                                        else -> null
                                    }
                                    val fileSizeText = fileSize?.let {
                                        if (it >= 1024 * 1024) {
                                            String.format("%.1f MB", it / (1024.0 * 1024.0))
                                        } else {
                                            String.format("%.1f KB", it / 1024.0)
                                        }
                                    } ?: "Unknown"
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 52.dp, end = 8.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.65f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = "${sourceImage.width} × ${sourceImage.height}",
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                text = fileSizeText,
                                                color = Color.White.copy(alpha = 0.8f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            TextButton(
                                                onClick = { imageToImageViewModel.compressSourceImage(sourceSlot) },
                                                enabled = sourceSlot !in uiState.compressedSlots,
                                                modifier = Modifier.height(28.dp),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                            ) {
                                                Text(
                                                    text = if (sourceSlot in uiState.compressedSlots) "Compressed ✓" else "Compress",
                                                    color = if (sourceSlot in uiState.compressedSlots)
                                                        Color.Green.copy(alpha = 0.9f)
                                                    else
                                                        Color.White.copy(alpha = 0.85f),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Empty slot — placeholder with picker
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                sourcePicker?.launch(arrayOf("image/*"))
                            }
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_comfychair_foreground),
                                contentDescription = null,
                                modifier = Modifier.size(300.dp),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                text = stringResource(R.string.msg_no_source_image),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // TabRow — one tab per image page + preview tab
        val allTabs = buildList {
            imagePages.forEach { add(it.title) }
            add("预览")
        }
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.fillMaxWidth()
        ) {
            allTabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch { pagerState.scrollToPage(index) }
                    },
                    text = { Text(title) }
                )
            }
        }

        // The prompt; tapping it opens the full-screen editor (absent for workflows without prompts)
        PromptDock(
            state = uiState,
            onPositiveChange = imageToImageViewModel::onPositivePromptChange,
            onNegativeChange = imageToImageViewModel::onNegativePromptChange,
            preview = uiState.previewImage ?: sourceImage,
            presetViewModel = presetViewModel,
            presetUiState = presetUiState,
            spellCheckEnabled = promptInput.spellCheckEnabled,
            generateEnabled = imageToImageViewModel.hasValidConfiguration(),
            onGenerate = { submitJob(false) }
        )

        RecipeChips(
            state = uiState,
            viewModel = imageToImageViewModel,
            icon = uiState.workflowIcon,
            groups = uiState.workflowGroups(imageToImageViewModel::onModeChange, imageToImageViewModel::onWorkflowChange)
        )
        // Generate and options: the fork's batch count, upload progress and queue actions on the upstream button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            GenerationButton(
                canCancelOwnJob = queueState.executingOwnerId == ownerId &&
                    queueState.executingPromptId in queueState.ownJobs,
                batchCount = batchCount,
                isEnabled = imageToImageViewModel.hasValidConfiguration() && uiState.sourceImage != null,
                isOfflineMode = isOfflineMode,
                isUploading = uiState.isUploading,
                isFetching = uiState.isFetching,
                isConnecting = isConnecting,
                uploadTotalBytes = uiState.uploadTotalBytes,
                uploadProgressBytes = uiState.uploadProgressBytes,
                uploadLabel = uiState.uploadLabel,
                onGenerate = { submitJob(false) },
                onCancelOwnJob = {
                    val running = JobRegistry.queueState.value.executingPromptId
                    if (running != null) generationViewModel.cancelGeneration(running) { }
                },
                onBatchCountChange = { newCount ->
                    batchCount = newCount
                    AppSettings.setBatchCount(context, newCount)
                },
                onAddToFrontOfQueue = { submitJob(true) },
                ownJobs = queueState.ownActiveJobCount,
                otherJobs = queueState.otherJobCount,
                onShowQueue = { sh.hnet.comfychair.ui.components.generation.QueueSheetController.visible = true },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Animate gear icon rotation when options sheet is shown
            val optionsIconRotation by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (showOptionsSheet) 90f else 0f,
                label = "options icon rotation"
            )

            OutlinedIconButton(
                onClick = { showOptionsSheet = true },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.button_options),
                    modifier = Modifier.rotate(optionsIconRotation)
                )
            }
        }
    }

    GenerationOptionsSheet(
        visible = showOptionsSheet,
        onDismiss = { showOptionsSheet = false },
        viewModel = imageToImageViewModel,
        state = uiState,
        // Mode and reference images; the ViewModel applies every other change to the current mode
        extraCallbacks = { callbacks ->
            callbacks.copy(
                onModeChange = imageToImageViewModel::onModeChange,
                onReferenceImage1Change = { uri -> imageToImageViewModel.onReferenceImage1Change(context, uri) },
                onClearReferenceImage1 = imageToImageViewModel::onClearReferenceImage1,
                onReferenceImage2Change = { uri -> imageToImageViewModel.onReferenceImage2Change(context, uri) },
                onClearReferenceImage2 = imageToImageViewModel::onClearReferenceImage2
            )
        }
    ) { callbacks -> uiState.toBottomSheetConfig(callbacks) }

    // Gallery picker bottom sheet
    if (showGalleryPicker) {
        GalleryPickerBottomSheet(
            galleryItems = galleryImages,
            onSelect = { item ->
                val cacheKey = item.toCacheKey()
                val bitmap = MediaCache.getBitmap(cacheKey)
                if (bitmap != null) {
                    imageToImageViewModel.onSourceImageFromGallery(
                        context = context,
                        slot = currentPickerSlot,
                        bitmap = bitmap
                    )
                }
                showGalleryPicker = false
            },
            onDismiss = { showGalleryPicker = false },
            sheetState = galleryPickerSheetState
        )
    }

    if (showMaterialPicker) {
        MaterialLibraryPickerBottomSheet(
            items = materialUiState.items,
            viewModel = materialLibraryViewModel,
            isImporting = materialUiState.isImporting,
            isSelectionMode = materialUiState.isSelectionMode,
            selectedIds = materialUiState.selectedIds,
            onSelect = { item ->
                scope.launch {
                    val bitmap = MaterialLibrary.loadBitmap(context, item)
                    if (bitmap != null) {
                        imageToImageViewModel.onSourceImageFromGallery(context, currentPickerSlot, bitmap)
                    }
                }
                showMaterialPicker = false
            },
            onDismiss = { showMaterialPicker = false },
            sheetState = materialPickerSheetState
        )
    }

    // Error dialog with copy support
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text(text = stringResource(R.string.title_workflow_error)) },
            text = {
                Text(
                    text = errorDialogMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text(text = stringResource(R.string.button_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("error_message", errorDialogMessage))
                        Toast.makeText(context, R.string.msg_error_copied, Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(text = stringResource(R.string.button_copy_to_clipboard))
                }
            }
        )
    }

    // Unresolved placeholders confirmation dialog
    if (showPlaceholderDialog) {
        AlertDialog(
            onDismissRequest = {
                showPlaceholderDialog = false
                pendingPlaceholders = emptyList()
            },
            title = { Text(text = stringResource(R.string.title_unresolved_placeholders)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.msg_unresolved_placeholders,
                        pendingPlaceholders.joinToString(", ") { "{{${it}}}" }
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPlaceholderDialog = false
                        pendingPlaceholders = emptyList()
                        pendingWorkflowJson?.let { json ->
                            scope.launch {
                                imageToImageViewModel.submitPreparedWorkflow(
                                    generationViewModel, json
                                ) { success, _, errorMessage ->
                                    if (!success) {
                                        errorDialogMessage = errorMessage ?: context.getString(R.string.error_generation_failed)
                                        showErrorDialog = true
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.button_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPlaceholderDialog = false
                        pendingPlaceholders = emptyList()
                    }
                ) {
                    Text(text = stringResource(R.string.button_cancel))
                }
            }
        )
    }

    PromptPresetDialogs(presetViewModel, presetUiState)
}

/** Media origins of this screen's single result and its source in the preview. */
private const val RESULT_ORIGIN = "iti_result"
private const val SOURCE_ORIGIN = "iti_source"

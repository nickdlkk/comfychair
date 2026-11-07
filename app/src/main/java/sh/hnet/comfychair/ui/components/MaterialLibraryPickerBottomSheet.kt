package sh.hnet.comfychair.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sh.hnet.comfychair.R
import sh.hnet.comfychair.materials.MaterialItem
import sh.hnet.comfychair.viewmodel.MaterialLibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialLibraryPickerBottomSheet(
    items: List<MaterialItem>,
    viewModel: MaterialLibraryViewModel,
    onSelect: (MaterialItem) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    isImporting: Boolean = false,
    isSelectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var deleteConfirmPending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Auto-reset confirm state after 3 seconds
    LaunchedEffect(deleteConfirmPending) {
        if (deleteConfirmPending) {
            kotlinx.coroutines.delay(3000)
            deleteConfirmPending = false
        }
    }

    // Reset confirm state when selection is cleared
    LaunchedEffect(selectedIds) {
        if (selectedIds.isEmpty()) {
            deleteConfirmPending = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) { }
            }
            viewModel.import(context, uris)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isImporting) {
                viewModel.clearSelection()
                onDismiss()
            }
        },
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (isSelectionMode) {
                    Text(
                        text = stringResource(R.string.materials_selected_count, selectedIds.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                        if (selectedIds.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    if (deleteConfirmPending) {
                                        viewModel.deleteSelected(context)
                                        deleteConfirmPending = false
                                    } else {
                                        deleteConfirmPending = true
                                    }
                                },
                                enabled = selectedIds.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.button_delete_material),
                                    tint = if (deleteConfirmPending)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.errorContainer
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.button_cancel_selection))
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.title_material_picker),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                        if (items.isNotEmpty()) {
                            TextButton(onClick = { viewModel.selectAll() }) {
                                Text(stringResource(R.string.button_materials_select))
                            }
                        }
                        if (!isImporting) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.content_description_close))
                            }
                        }
                    }
                }
            }

            // Content
            Box(modifier = Modifier.weight(1f, fill = false)) {
                if (items.isEmpty() || isImporting) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            if (isImporting) {
                                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                                Text(
                                    text = stringResource(R.string.msg_importing),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = stringResource(R.string.msg_material_library_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    ) {
                        items(items, key = { it.id }) { item ->
                            MaterialPickerTile(
                                item = item,
                                selected = item.id in selectedIds,
                                onLongClick = { viewModel.toggleSelection(item) },
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(item)
                                    } else {
                                        onSelect(item)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Bottom action bar
            if (!isImporting) {
                if (isSelectionMode && selectedIds.isNotEmpty()) {
                    // Delete button — two-click confirm
                    Button(
                        onClick = {
                            if (deleteConfirmPending) {
                                viewModel.deleteSelected(context)
                                deleteConfirmPending = false
                            } else {
                                deleteConfirmPending = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        enabled = selectedIds.isNotEmpty(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = if (deleteConfirmPending)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.errorContainer,
                            contentColor = if (deleteConfirmPending)
                                MaterialTheme.colorScheme.onError
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            if (deleteConfirmPending)
                                stringResource(R.string.button_confirm_delete)
                            else
                                stringResource(R.string.button_delete_material)
                        )
                    }
                } else {
                    // Import button
                    Button(
                        onClick = { importLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(stringResource(R.string.button_import_to_material_library))
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialPickerTile(
    item: MaterialItem,
    selected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(item.id) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(item.id) {
        // loadBitmap is synchronous here for simplicity; actual async load via VM if needed
        bitmap = sh.hnet.comfychair.materials.MaterialLibrary.loadBitmap(context, item)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .pointerInput(item.id) {
                var longPressedJustFired = false
                detectTapGestures(
                    onLongPress = {
                        onLongClick()
                        longPressedJustFired = true
                    },
                    onTap = {
                        if (!longPressedJustFired) onClick()
                        longPressedJustFired = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }

        // Selection indicator
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.label_item_selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

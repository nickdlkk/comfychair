package sh.hnet.comfychair.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sh.hnet.comfychair.R
import sh.hnet.comfychair.materials.MaterialItem
import sh.hnet.comfychair.ui.components.AppMenuDropdown
import sh.hnet.comfychair.ui.components.shared.NoOverscrollContainer
import sh.hnet.comfychair.viewmodel.MaterialLibraryEvent
import sh.hnet.comfychair.viewmodel.MaterialLibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialLibraryScreen(
    viewModel: MaterialLibraryViewModel,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    onMaterialPicked: ((MaterialItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }
            }
            viewModel.import(context, uris)
        }
    }

    LaunchedEffect(Unit) { viewModel.load(context) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MaterialLibraryEvent.ShowToast -> Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                if (uiState.isSelectionMode) {
                    Text(stringResource(R.string.materials_selected_count, uiState.selectedIds.size))
                } else {
                    Text(stringResource(R.string.title_material_library))
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            navigationIcon = {
                if (uiState.isSelectionMode) {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.button_cancel_selection))
                    }
                }
            },
            actions = {
                IconButton(onClick = { pickerLauncher.launch(arrayOf("image/*")) }) {
                    Icon(Icons.Default.Upload, contentDescription = stringResource(R.string.button_import_materials))
                }
                if (uiState.isSelectionMode) {
                    IconButton(onClick = { viewModel.selectAll() }) {
                        Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.button_select_all))
                    }
                    IconButton(onClick = { viewModel.deleteSelected(context) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.button_delete_material), tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    IconButton(onClick = { viewModel.selectAll() }) {
                        Icon(Icons.Default.Checklist, contentDescription = stringResource(R.string.button_materials_select))
                    }
                    AppMenuDropdown(onSettings = onNavigateToSettings, onLogout = onLogout)
                }
            }
        )

        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.load(context) },
            modifier = Modifier.fillMaxSize()
        ) {
            NoOverscrollContainer(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (uiState.isLoading && uiState.items.isEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (uiState.items.isEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = stringResource(R.string.msg_material_library_empty),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(uiState.items, key = { it.id }) { item ->
                            MaterialLibraryTile(
                                item = item,
                                selected = item.id in uiState.selectedIds,
                                isSelectionMode = uiState.isSelectionMode,
                                onToggleSelection = { viewModel.toggleSelection(item) },
                                onClick = {
                                    if (uiState.isSelectionMode) {
                                        viewModel.toggleSelection(item)
                                    } else {
                                        onMaterialPicked?.invoke(item)
                                    }
                                },
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaterialLibraryTile(
    item: MaterialItem,
    selected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit,
    viewModel: MaterialLibraryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(item.id) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(item.id) {
        viewModel.loadBitmap(context, item) { loaded -> bitmap = loaded }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
            )
            .pointerInput(item.id, isSelectionMode) {
                detectTapGestures(
                    onLongPress = { onToggleSelection() },
                    onTap = { onClick() }
                )
            }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }

        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.label_item_selected),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = item.displayName,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

package sh.hnet.comfychair.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import sh.hnet.comfychair.R
import sh.hnet.comfychair.model.ServerImage
import sh.hnet.comfychair.model.ServerImageSource
import sh.hnet.comfychair.model.ServerImageSort
import sh.hnet.comfychair.viewmodel.ServerImagePickerState

/** Material-library square tiles; only visible tiles request bounded previews, never originals. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerImagePickerPanel(
    state: ServerImagePickerState,
    loadPreview: suspend (ServerImage) -> Bitmap,
    onSelect: (ServerImage) -> Unit,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
    onRefresh: () -> Unit,
    onQuery: (String, ServerImageSort) -> Unit,
    onCancelDownload: () -> Unit,
    onRetryDownload: () -> Unit,
    onClose: () -> Unit
) {
    if (state.slot == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gridState = rememberLazyGridState()
    val visible by remember { derivedStateOf { gridState.layoutInfo.visibleItemsInfo.map { it.key }.toSet() } }
    val nearBottom by remember {
        derivedStateOf {
            val layout = gridState.layoutInfo
            layout.totalItemsCount == 0 ||
                (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 9
        }
    }
    var autoPages by remember(state.session) { mutableIntStateOf(0) }
    LaunchedEffect(nearBottom, state.nextOffset, state.nextCursor, state.loading, state.loadingMore, state.moreError, state.downloading) {
        if (nearBottom && autoPages < 5 && (state.nextOffset != null || state.nextCursor != null) && !state.loading && !state.loadingMore &&
            !state.moreError && !state.downloading) { autoPages++; onLoadMore() }
    }
    LaunchedEffect(state.session) { gridState.scrollToItem(0) }
    var showInfo by remember(state.session) { mutableStateOf(false) }
    val sourceName = if (state.source == ServerImageSource.INPUT) "Input" else "Output"
    val scope = stringResource(if (state.assets == true) R.string.server_assets_scope
        else if (state.source == ServerImageSource.INPUT) R.string.server_image_input_scope
        else R.string.server_image_output_scope)
    val hasMore = state.nextOffset != null || state.nextCursor != null
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState,
        dragHandle = null, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).navigationBarsPadding()) {
            // Source/slot and totals share a single header; actions keep 48dp touch targets.
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("$sourceName · ${stringResource(R.string.server_image_target, state.slot)}",
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(when {
                        state.loading -> stringResource(R.string.server_image_loading)
                        state.assets == true -> stringResource(R.string.server_assets_count, state.images.size, state.totalAssets ?: 0L)
                        else -> stringResource(R.string.server_image_legacy_count, state.images.size)
                    }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { showInfo = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Info, stringResource(R.string.server_image_info))
                    }
                    DropdownMenu(expanded = showInfo, onDismissRequest = { showInfo = false },
                        modifier = Modifier.widthIn(max = 300.dp)) {
                        Text(scope + "\n" + stringResource(R.string.server_image_limits),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
                IconButton(onClick = onRefresh, enabled = !state.loading && !state.downloading,
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.server_image_refresh))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.content_description_close))
                }
            }
            if (state.assets == true) {
                var sortMenu by remember(state.session) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(value = state.query, onValueChange = { onQuery(it, state.sort) },
                        placeholder = { Text(stringResource(R.string.server_assets_search),
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        textStyle = MaterialTheme.typography.bodySmall, singleLine = true,
                        enabled = !state.downloading, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { sortMenu = true }, enabled = !state.downloading,
                            modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Sort,
                                stringResource(R.string.server_assets_sort, stringResource(sortLabel(state.sort))))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            ServerImageSort.entries.forEach { sort -> DropdownMenuItem(
                                text = { Text(stringResource(sortLabel(sort)), style = MaterialTheme.typography.bodySmall) },
                                trailingIcon = { if (sort == state.sort) Text("✓") },
                                onClick = { sortMenu = false; onQuery(state.query, sort) }) }
                        }
                    }
                }
            }
            if (state.loading || state.loadingMore) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            if (!state.loading && !state.error && !hasMore && state.images.isEmpty())
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.server_image_empty), style = MaterialTheme.typography.bodySmall)
                }
            else LazyVerticalGrid(columns = GridCells.Fixed(3), state = gridState,
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)) {
                items(state.images, key = { it.stableKey }) { image ->
                    key(state.session, image) {
                        ServerPreviewTile(image, image.stableKey in visible, !state.downloading, state.downloading && state.selected == image, loadPreview,
                            enabled = !state.downloading, onSelect = { onSelect(image) })
                    }
                }
                // End status scrolls with content instead of permanently reducing grid height.
                if (!state.loading && !hasMore && !state.error && state.images.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "picker-end") {
                        Text(stringResource(R.string.server_image_end), style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // No idle footer: busy/error text and its action occupy one contextual row.
            if (state.downloading || state.downloadError || state.error || state.moreError ||
                (!state.loading && !state.loadingMore && hasMore && autoPages >= 5)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.downloading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(when {
                        state.downloading -> R.string.server_assets_download_wait
                        state.downloadError || state.error -> R.string.server_image_error
                        state.moreError -> R.string.server_image_more_error
                        else -> R.string.server_assets_more
                    }), style = MaterialTheme.typography.bodySmall,
                        color = if (state.downloadError || state.error || state.moreError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TextButton(onClick = when {
                        state.downloading -> onCancelDownload
                        state.downloadError -> onRetryDownload
                        state.error -> onRefresh
                        state.moreError -> onRetryMore
                        else -> { { autoPages = 0; onLoadMore() } }
                    }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(when {
                            state.downloading -> R.string.button_cancel
                            state.downloadError || state.error || state.moreError -> R.string.server_image_retry_more
                            else -> R.string.server_assets_more
                        }), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun sortLabel(sort: ServerImageSort): Int = when (sort) {
    ServerImageSort.NEWEST -> R.string.server_assets_newest
    ServerImageSort.OLDEST -> R.string.server_assets_oldest
    ServerImageSort.NAME -> R.string.server_assets_name
    ServerImageSort.SIZE -> R.string.server_assets_size
}

@Composable
private fun ServerPreviewTile(image: ServerImage, visible: Boolean, requestAllowed: Boolean, selected: Boolean,
    loadPreview: suspend (ServerImage) -> Bitmap, enabled: Boolean, onSelect: () -> Unit) {
    var bitmap by remember(image) { mutableStateOf<Bitmap?>(null) }
    var error by remember(image) { mutableStateOf(false) }
    var retry by remember(image) { mutableIntStateOf(0) }
    LaunchedEffect(image, visible, retry, requestAllowed) {
        if (!visible) { bitmap = null; return@LaunchedEffect }
        if (!requestAllowed || bitmap != null) return@LaunchedEffect
        if (error) return@LaunchedEffect
        error = false
        try { bitmap = loadPreview(image) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
    }
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(enabled = enabled) { if (error) { retry++; error = false } else onSelect() },
        contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> Image(bitmap!!.asImageBitmap(), image.path,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            error -> Text(stringResource(R.string.server_image_preview_retry),
                modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
            else -> if (requestAllowed) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        if (selected) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
    }
}

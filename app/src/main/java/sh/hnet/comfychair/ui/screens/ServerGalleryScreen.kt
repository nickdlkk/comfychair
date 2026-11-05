package sh.hnet.comfychair.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import sh.hnet.comfychair.R
import sh.hnet.comfychair.model.ServerImageSource
import sh.hnet.comfychair.storage.DeletionCredentialStorage
import sh.hnet.comfychair.ui.components.*
import sh.hnet.comfychair.viewmodel.ServerGalleryViewModel

@Composable
fun ServerGalleryScreen(onBack: () -> Unit, vm: ServerGalleryViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.picker.state.collectAsState()
    val full by vm.viewer.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    var source by remember { mutableStateOf(ServerImageSource.INPUT) }
    var showCredential by remember { mutableStateOf(false) }
    var secret by remember { mutableStateOf("") } // never saved across process recreation
    var allowLan by remember { mutableStateOf(false) }
    var credentialReady by remember { mutableStateOf(false) }
    var credentialRevision by remember { mutableIntStateOf(0) }
    val owner = currentMediaIdentity()
    LaunchedEffect(showCredential, owner) {
        secret = ""; allowLan = false; credentialReady = false
        if (showCredential && owner != null) {
            try {
                val client = owner.client as sh.hnet.comfychair.ComfyUIClient
                val saved = withContext(Dispatchers.IO) {
                    val storage = DeletionCredentialStorage(context)
                    val key = client.deletionCredentialScope(owner.serverId)
                    storage.get(key) to storage.lan(key)
                }
                if (currentMediaIdentity() == owner) {
                    secret = saved.first; allowLan = saved.second; credentialReady = true
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) vm.stop()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START && vm.picker.state.value.slot == null) vm.open(source)
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); vm.stop() }
    }
    LaunchedEffect(source, owner, credentialRevision) { vm.open(source) }
    BackHandler { if (full != null || loading) vm.closeViewer() else onBack() }
    // Fullscreen gallery owns safe status/cutout/navigation/IME insets; no Settings chrome.
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))) {
        TabRow(selectedTabIndex = source.ordinal) {
            ServerImageSource.entries.forEach { item -> Tab(selected = source == item,
                onClick = { vm.closeViewer(); showCredential = false; source = item },
                text = { Text(item.type.replaceFirstChar { it.uppercase() }) }) }
        }
        val credentialPanel: @Composable () -> Unit = {
            TextButton(onClick = { showCredential = !showCredential; secret = "" }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.server_delete_credential))
            }
            if (showCredential) Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.server_delete_setup), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(secret, { secret = it }, label = { Text(stringResource(R.string.server_delete_credential)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.server_delete_lan), Modifier.weight(1f))
                    Switch(checked = allowLan, onCheckedChange = { allowLan = it }, enabled = credentialReady)
                }
                if (allowLan) Text(stringResource(R.string.server_delete_lan_warning), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                TextButton(enabled = credentialReady && (secret.isEmpty() || secret.length >= 32), onClick = {
                    val expected = owner ?: return@TextButton
                    val value = secret
                    val consent = allowLan
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { runCatching {
                            check(currentMediaIdentity() == expected)
                            val client = expected.client as sh.hnet.comfychair.ComfyUIClient
                            DeletionCredentialStorage(context).save(client.deletionCredentialScope(expected.serverId), value, consent)
                        }.isSuccess }
                        secret = ""
                        Toast.makeText(context, if (saved) R.string.server_delete_credential_saved else R.string.server_delete_failed, Toast.LENGTH_LONG).show()
                        if (saved) { credentialRevision++; showCredential = false }
                    }
                }) { Text(stringResource(R.string.server_delete_store)) }
            }
        }
        // Keep panel composed at the same call site while viewing: LazyGrid state and
        // session survive preview open/close, rather than re-running scrollToItem(0).
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().alpha(if (full == null) 1f else 0f)
                .then(if (full == null) Modifier else Modifier.clearAndSetSemantics {})) {
                if (error) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.server_image_error),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    ServerImagePickerPanel(state, vm::preview, vm::view, { vm.picker.loadMore() }, { vm.picker.loadMore(true) },
                        vm.picker::refresh, vm.picker::changeQuery, vm::closeViewer, {}, onBack, standalone = true,
                        onDeleted = vm::deleted, standaloneInfo = credentialPanel, browsingVisible = full == null && !loading)
                }
            }
        if (loading) MediaLoadingOverlay(onCancel = vm::closeViewer)
        val shown = full
        if (shown != null) Surface(Modifier.fillMaxSize().zIndex(1f)) {
            Column(Modifier.fillMaxSize()) {
            Text(shown.image.path, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            Text("${shown.bitmap.width} × ${shown.bitmap.height} · ${shown.encodedSize} bytes", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
            ImageViewer(shown.bitmap, Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {

                TextButton(onClick = {
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching {
                                val values = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, "ComfyChair_${System.currentTimeMillis()}.png")
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ComfyChair")
                                    put(MediaStore.Images.Media.IS_PENDING, 1)
                                }
                                val resolver = context.contentResolver
                                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("insert")
                                try {
                                    resolver.openOutputStream(uri)?.use { check(shown.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) } ?: error("output")
                                    check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
                                } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
                            }.isSuccess
                        }
                        Toast.makeText(context, if (saved) R.string.msg_image_saved_to_gallery else R.string.error_save_image, Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.server_gallery_save)) }
                TextButton(onClick = {
                    scope.launch {
                        val saved = sh.hnet.comfychair.materials.MaterialLibrary.saveBitmap(context, shown.bitmap, shown.image.filename)
                        Toast.makeText(context, if (saved != null) R.string.msg_materials_imported_success else R.string.error_materials_import, Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.server_gallery_material)) }
                TextButton(enabled = shown.owner == owner, onClick = {
                    val ticket = sh.hnet.comfychair.connection.ServerGallerySourceTransfer.offer(shown)
                    (context as? Activity)?.let { activity ->
                        activity.setResult(102, android.content.Intent().putExtra("server_gallery_source", ticket)); activity.finish()
                    }
                }) { Text(stringResource(R.string.server_gallery_source)) }
            }
            }
        }
        }
    }
}

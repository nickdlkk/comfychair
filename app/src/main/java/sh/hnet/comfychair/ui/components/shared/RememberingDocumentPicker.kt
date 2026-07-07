package sh.hnet.comfychair.ui.components.shared

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import sh.hnet.comfychair.storage.AppSettings

private class OpenDocumentWithInitialUri(
    private val initialUri: Uri?
) : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            initialUri?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
    }
}

@Composable
fun rememberOpenDocumentWithInitialUri(initialUri: Uri?): ActivityResultContracts.OpenDocument {
    return remember(initialUri) { OpenDocumentWithInitialUri(initialUri) }
}

@Composable
fun rememberLastPickedImageUri(context: Context): Uri? {
    return remember(context) {
        AppSettings.getLastImagePickerUri(context)?.let(Uri::parse)
    }
}

@Composable
fun rememberLastPickedDocumentUri(context: Context): Uri? {
    return remember(context) {
        AppSettings.getLastDocumentPickerUri(context)?.let(Uri::parse)
    }
}

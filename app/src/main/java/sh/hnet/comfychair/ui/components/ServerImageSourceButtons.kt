package sh.hnet.comfychair.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import sh.hnet.comfychair.R
import sh.hnet.comfychair.model.ServerImageSource

/** Match the adjacent file/material actions, including Material's 48dp touch targets. */
@Composable
fun ServerImageSourceButtons(onSelect: (ServerImageSource) -> Unit) {
    ServerImageSource.entries.forEach { source ->
        // These are HTTP reads, not generation/WebSocket actions. Always allow opening:
        // the picker validates the authenticated connection and shows a localized error.
        IconButton(onClick = { onSelect(source) }) {
            Icon(
                imageVector = if (source == ServerImageSource.INPUT)
                    Icons.Default.CloudUpload else Icons.Default.CloudDownload,
                contentDescription = stringResource(if (source == ServerImageSource.INPUT)
                    R.string.server_image_input else R.string.server_image_output)
            )
        }
    }
}

package sh.hnet.comfychair.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize

/**
 * Full-screen image with zoom and pan ([zoomable]). The zoom is kept when the bitmap is replaced, e.g. when the
 * full image arrives after its thumbnail.
 *
 * @param onSingleTap Called for a tap that didn't become a double tap (toggles the viewer's UI)
 */
@Composable
fun ImageViewer(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
    comparisonOriginal: Bitmap? = null
) {
    val zoomState = rememberZoomState(IntSize(bitmap.width, bitmap.height), onSingleTap = onSingleTap)
    var showingOriginal by androidx.compose.runtime.remember(bitmap, comparisonOriginal) { androidx.compose.runtime.mutableStateOf(false) }
    val imageBitmap = remember(bitmap, comparisonOriginal, showingOriginal) { (if (showingOriginal) comparisonOriginal ?: bitmap else bitmap).asImageBitmap() }

    var resumed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { resumed = false; showingOriginal = false }
            else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumed = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); showingOriginal = false }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .zoomable(zoomState, if (comparisonOriginal != null && resumed) ({ showingOriginal = it }) else null),
            contentScale = ContentScale.Fit
        )

    }
}

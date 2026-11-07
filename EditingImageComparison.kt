package sh.hnet.comfychair.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import sh.hnet.comfychair.R
import sh.hnet.comfychair.viewmodel.dividerFraction
import sh.hnet.comfychair.viewmodel.fitBounds
import kotlin.math.roundToInt

/** Fit both images into the same full viewport, then clip. No independent crop/zoom gestures. */
@Composable
fun EditingImageComparison(original: Bitmap, result: Bitmap, modifier: Modifier = Modifier) {
    var fraction by remember(original, result) { mutableFloatStateOf(0.5f) }
    var viewportWidth by remember { mutableFloatStateOf(0f) }
    val sliderDescription = stringResource(R.string.comparison_drag_hint)
    val before = remember(original) { original.asImageBitmap() }
    val after = remember(result) { result.asImageBitmap() }
    Column(modifier.background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.comparison_original), color = Color.White)
            Text(stringResource(R.string.comparison_result), color = Color.White)
        }
        Canvas(Modifier.fillMaxWidth().weight(1f).onSizeChanged { viewportWidth = it.width.toFloat() }
            .pointerInput(original, result) {
                detectHorizontalDragGestures(
                    onDragStart = { fraction = dividerFraction(it.x, viewportWidth) }
                ) { change, _ ->
                    change.consume()
                    fraction = dividerFraction(change.position.x, viewportWidth)
                }
            }) {
            val x = size.width * fraction
            fun androidx.compose.ui.graphics.drawscope.DrawScope.fitted(image: androidx.compose.ui.graphics.ImageBitmap) {
                val bounds = fitBounds(image.width, image.height, size.width, size.height)
                drawImage(image, dstOffset = IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()),
                    dstSize = IntSize(bounds.width.roundToInt(), bounds.height.roundToInt()))
            }
            // Draw full opaque background in each half: letterboxes never reveal the other image.
            clipRect(right = x) { fitted(before) }
            clipRect(left = x) { fitted(after) }
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            drawCircle(Color.Black.copy(alpha = 0.65f), 16.dp.toPx(), Offset(x, size.height / 2))
            drawLine(Color.White, Offset(x - 7.dp.toPx(), size.height / 2),
                Offset(x + 7.dp.toPx(), size.height / 2), 3.dp.toPx())
        }
        // Accessible adjustment alternative to the direct on-image divider drag.
        Slider(value = fraction, onValueChange = { fraction = it.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { contentDescription = sliderDescription })
        Text(stringResource(R.string.comparison_drag_hint), color = Color.White,
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))

    }
}

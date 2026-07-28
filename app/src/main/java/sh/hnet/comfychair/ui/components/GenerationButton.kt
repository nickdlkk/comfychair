package sh.hnet.comfychair.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import android.util.Log
import sh.hnet.comfychair.R

/**
 * Split button for generation screens with queue management dropdown.
 *
 * Leading button: Always submits to queue ("Generate" or "Add to queue (X)")
 * Trailing button: Dropdown menu with queue management actions
 *
 * Long press on leading button triggers batch generation panel.
 *
 * Button text priority: Connecting > Uploading > Fetching > Queue size > Generate
 *
 * @param queueSize Total number of jobs in the server queue (all clients)
 * @param isExecuting Whether any job is currently executing on the server
 * @param isEnabled Whether the button should be enabled (valid input to submit)
 * @param isOfflineMode Whether the app is in offline mode (disables all generation)
 * @param isUploading Whether image upload is in progress (shows "Uploading...")
 * @param isFetching Whether result fetch is in progress (shows "Fetching...")
 * @param isConnecting Whether connection check is in progress (shows "Connecting...")
 * @param onGenerate Callback when Generate/Add to queue is clicked (single generation)
 * @param onBatchGenerate Callback when leading button is long-pressed (opens batch panel)
 * @param onCancelCurrent Callback to cancel the currently executing job
 * @param onAddToFrontOfQueue Callback to add to front of queue
 * @param onClearQueue Callback to clear the server queue
 * @param modifier Modifier for the button layout
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GenerationButton(
    queueSize: Int,
    isExecuting: Boolean,
    isEnabled: Boolean,
    isOfflineMode: Boolean = false,
    isUploading: Boolean = false,
    isFetching: Boolean = false,
    isConnecting: Boolean = false,
    uploadTotalBytes: Long? = null,
    uploadProgressBytes: Long? = null,
    uploadLabel: String? = null,
    onGenerate: () -> Unit,
    onBatchGenerate: () -> Unit = {},
    onCancelCurrent: () -> Unit,
    onAddToFrontOfQueue: () -> Unit = {},
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var showMenu by remember { mutableStateOf(false) }

    // Track long-press to prevent tap from firing after long-press
    var longPressedJustFired by remember { mutableStateOf(false) }
    LaunchedEffect(longPressedJustFired) {
        if (longPressedJustFired) {
            delay(50) // Small delay to ensure tap is blocked
            longPressedJustFired = false
        }
    }

    // Button always uses primary color (no more red cancel state)
    val containerColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onPrimary

    // Button text changes based on connection/upload/fetch state and queue size
    val buttonText = when {
        isConnecting && queueSize > 0 -> stringResource(R.string.button_connecting_queue, queueSize)
        isConnecting -> stringResource(R.string.button_connecting)
        isUploading && queueSize > 0 -> stringResource(R.string.button_uploading_queue, queueSize)
        isUploading -> {
            if (uploadTotalBytes != null && uploadProgressBytes != null) {
                val progressText = if (uploadTotalBytes >= 1024 * 1024) {
                    String.format(Locale.US, "%.1f / %.1f MB", uploadProgressBytes / (1024.0 * 1024.0), uploadTotalBytes / (1024.0 * 1024.0))
                } else {
                    String.format(Locale.US, "%d / %d KB", uploadProgressBytes / 1024, uploadTotalBytes / 1024)
                }
                stringResource(
                    R.string.button_uploading_progress,
                    if (uploadLabel.isNullOrBlank()) progressText else "$uploadLabel · $progressText"
                )
            } else {
                stringResource(R.string.button_uploading)
            }
        }
        isFetching && queueSize > 0 -> stringResource(R.string.button_fetching_queue, queueSize)
        isFetching -> stringResource(R.string.button_fetching)
        queueSize > 0 -> stringResource(R.string.button_add_to_queue, queueSize)
        else -> stringResource(R.string.button_generate)
    }

    Row(modifier = modifier) {
        // Leading button - fills available width, always submits to queue
        // Box wrapper with pointerInput handles long press detection before ElevatedButton consumes touch
        // Short press falls through to ElevatedButton's onClick
        Box(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .pointerInput(isEnabled, isOfflineMode) {
                    if (!isEnabled || isOfflineMode) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Long press detection: wait to see if this is a long press
                        val longPressTimeout = 500L
                        var longPressFired = false
                        do {
                            val event = withTimeoutOrNull(longPressTimeout) {
                                awaitPointerEvent()
                            }
                            if (event == null) {
                                // Timeout reached without up → long press
                                Log.d("ComfyChair", "[GenerationButton] Long press detected, calling onBatchGenerate()")
                                longPressFired = true
                                longPressedJustFired = true
                                onBatchGenerate()
                                break
                            }
                            val up = event.changes.firstOrNull()
                            if (up != null && !up.pressed) {
                                // Released before timeout → short tap, let button handle it
                                break
                            }
                        } while (true)
                    }
                }
                .then(
                    if (isEnabled && !isOfflineMode) {
                        Modifier.clickable {
                            Log.d("ComfyChair", "[GenerationButton] Leading button clicked, calling onGenerate()")
                            focusManager.clearFocus()
                            onGenerate()
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            SplitButtonDefaults.ElevatedLeadingButton(
                onClick = { /* handled by Box modifier */ },
                enabled = isEnabled && !isOfflineMode,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = buttonText,
                    fontSize = 18.sp
                )
            }
        }

        // Spacing between buttons (matches SplitButtonDefaults.Spacing)
        Spacer(modifier = Modifier.width(SplitButtonDefaults.Spacing))

        // Trailing button with dropdown - square button matching height of leading
        // Animate icon rotation: 0° when closed, 180° when open (arrow points up)
        val iconRotation by animateFloatAsState(
            targetValue = if (showMenu) 180f else 0f,
            label = "dropdown icon rotation"
        )

        // Trailing button - disabled in offline mode (no queue operations available)
        Box(modifier = Modifier.wrapContentWidth(unbounded = true)) {
            SplitButtonDefaults.ElevatedTrailingButton(
                checked = showMenu,
                onCheckedChange = { showMenu = it },
                enabled = !isOfflineMode,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                modifier = Modifier.size(56.dp)  // Square button to match leading height
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.content_description_queue_menu),
                    modifier = Modifier.rotate(iconRotation)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                // Batch generation - prominent action at top
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_batch_generate)) },
                    onClick = {
                        Log.d("ComfyChair", "[BatchGeneration] Dropdown menu: batch generate clicked")
                        showMenu = false
                        onBatchGenerate()
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, contentDescription = null)
                    }
                )

                // Queue management actions (above gap)
                // Add to front of queue
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_add_to_front_of_queue)) },
                    onClick = {
                        showMenu = false
                        onAddToFrontOfQueue()
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, contentDescription = null)
                    }
                )
                // Clear queue
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.button_clear_queue)) },
                    onClick = {
                        showMenu = false
                        onClearQueue()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                )

                // Gap divider separating queue actions from cancel
                HorizontalDivider()

                // Cancel current (below gap) - destructive action separated
                // Use error/warning color when enabled to indicate destructive action
                val cancelColor = if (isExecuting) {
                    MaterialTheme.colorScheme.error
                } else {
                    MenuDefaults.itemColors().disabledTextColor
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_cancel_current), color = cancelColor) },
                    onClick = {
                        showMenu = false
                        onCancelCurrent()
                    },
                    enabled = isExecuting,
                    leadingIcon = {
                        Icon(Icons.Default.Cancel, contentDescription = null, tint = cancelColor)
                    }
                )
            }
        }
    }
}

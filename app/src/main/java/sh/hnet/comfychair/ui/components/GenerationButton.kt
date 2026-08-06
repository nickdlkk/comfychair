package sh.hnet.comfychair.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import sh.hnet.comfychair.R

/**
 * Split button for generation screens with queue management dropdown.
 *
 * Leading button: Always submits to queue ("Generate" or "Add to queue (X)")
 * Trailing button: Dropdown menu with batch count stepper + queue management
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
 * @param batchCount Current batch count (>= 1)
 * @param onGenerate Callback when Generate/Add to queue is clicked
 * @param onBatchCountChange Callback when batch count changes
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
    batchCount: Int = 1,
    onGenerate: () -> Unit,
    onBatchCountChange: (Int) -> Unit = {},
    onCancelCurrent: () -> Unit,
    onAddToFrontOfQueue: () -> Unit = {},
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    // Button always uses primary color
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

    // Arrow rotates 180° when menu is open (pointing up)
    val iconRotation by animateFloatAsState(
        targetValue = if (showMenu) 180f else 0f,
        label = "dropdown icon rotation"
    )

    Row(modifier = modifier) {
        // Leading button - simple click, no gesture handling
        SplitButtonDefaults.ElevatedLeadingButton(
            onClick = {
                onGenerate()
            },
            enabled = isEnabled && !isOfflineMode,
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
        ) {
            Text(
                text = buttonText,
                fontSize = 18.sp
            )
        }

        // Spacing between buttons (matches SplitButtonDefaults.Spacing)
        Spacer(modifier = Modifier.width(SplitButtonDefaults.Spacing))

        // Trailing button - toggle menu on click, disabled in offline mode
        Box(modifier = Modifier.wrapContentWidth(unbounded = true)) {
            SplitButtonDefaults.ElevatedTrailingButton(
                checked = showMenu,
                onCheckedChange = { showMenu = it },
                enabled = !isOfflineMode,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                modifier = Modifier.size(56.dp)
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
                // Batch count stepper section
                BatchCountStepper(
                    count = batchCount,
                    onCountChange = onBatchCountChange
                )

                HorizontalDivider()

                // Queue management actions
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

                HorizontalDivider()

                // Cancel current
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

/**
 * Batch count stepper with presets (4, 8, 16, 32) and custom input.
 * Adjusting count immediately updates the shared state; no confirm needed.
 */
@Composable
private fun BatchCountStepper(
    count: Int,
    onCountChange: (Int) -> Unit
) {
    val presets = listOf(4, 8, 16, 32)
    val step = 4
    val minCount = 1
    val maxCount = 64

    androidx.compose.foundation.layout.Column(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Header
        Text(
            text = stringResource(R.string.batch_count_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Preset buttons
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(bottom = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            presets.forEach { preset ->
                val isSelected = count == preset
                androidx.compose.material3.FilterChip(
                    selected = isSelected,
                    onClick = { onCountChange(preset) },
                    label = {
                        Text(
                            text = preset.toString(),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Stepper with custom value input
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            // Decrease button
            FilledIconButton(
                onClick = { if (count > minCount) onCountChange((count - step).coerceAtLeast(minCount)) },
                enabled = count > minCount,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
            }

            // Number input
            OutlinedTextField(
                value = count.toString(),
                onValueChange = { raw ->
                    val new = raw.filter { it.isDigit() }.take(3).toIntOrNull()
                    if (new != null && new in minCount..maxCount) {
                        onCountChange(new)
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f).height(48.dp)
            )

            // Increase button
            FilledIconButton(
                onClick = { if (count < maxCount) onCountChange((count + step).coerceAtMost(maxCount)) },
                enabled = count < maxCount,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

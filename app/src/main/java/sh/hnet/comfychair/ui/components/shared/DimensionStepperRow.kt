package sh.hnet.comfychair.ui.components.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sh.hnet.comfychair.R
import sh.hnet.comfychair.workflow.WorkflowConstraintsProvider
import java.util.Locale

/**
 * Common resolution presets for quick selection.
 * Each entry: (displayName, width, height)
 */
private val RESOLUTION_PRESETS = listOf(
    // Square presets
    "512²" to Pair(512, 512),
    "768²" to Pair(768, 768),
    "1024²" to Pair(1024, 1024),
    // Portrait 3:4
    "512×768" to Pair(512, 768),
    "768×1024" to Pair(768, 1024),
    // Portrait 2:3
    "512×768 alt" to Pair(512, 768),
    "768×1152" to Pair(768, 1152),
    // Landscape 4:3
    "640×512" to Pair(640, 512),
    "960×768" to Pair(960, 768),
    // Landscape 16:9
    "960×540" to Pair(960, 540),  // 360p
    "1280×720" to Pair(1280, 720), // 720p
    "1920×1080" to Pair(1920, 1080), // 1080p
    // Portrait 9:16
    "540×960" to Pair(540, 960),   // 360p portrait
    "720×1280" to Pair(720, 1280), // 720p portrait
    "1080×1920" to Pair(1080, 1920), // 1080p portrait
    // Video/Animation common
    "512×512" to Pair(512, 512),
    "512×768" to Pair(512, 768),
    "768×512" to Pair(768, 512),
    "1024×1024" to Pair(1024, 1024),
)

/**
 * A row containing Width and Height stepper fields with constraints
 * dynamically loaded from the actual mapped nodes in the workflow.
 * Includes a resolution preset dropdown for quick selection.
 *
 * @param workflowName The name of the currently selected workflow (for constraint lookup)
 * @param width Current width value as string
 * @param onWidthChange Callback when width changes
 * @param widthError Width validation error message
 * @param height Current height value as string
 * @param onHeightChange Callback when height changes
 * @param heightError Height validation error message
 * @param showWidth Whether to show the width field
 * @param showHeight Whether to show the height field
 * @param modifier Modifier for the row
 */
@Composable
fun DimensionStepperRow(
    workflowName: String,
    width: String,
    onWidthChange: (String) -> Unit,
    widthError: String?,
    height: String,
    onHeightChange: (String) -> Unit,
    heightError: String?,
    showWidth: Boolean = true,
    showHeight: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!showWidth && !showHeight) return

    var showPresetMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Row 1: Width and Height steppers
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (showWidth) {
                val widthConstraints = WorkflowConstraintsProvider.rememberConstraints("width", workflowName)
                val widthRangeHint = stringResource(
                    R.string.node_editor_range_min_max,
                    formatRangeValue(widthConstraints.min, widthConstraints.decimalPlaces),
                    formatRangeValue(widthConstraints.max, widthConstraints.decimalPlaces)
                )

                NumericStepperField(
                    value = width,
                    onValueChange = onWidthChange,
                    label = stringResource(R.string.label_width),
                    min = widthConstraints.min,
                    max = widthConstraints.max,
                    step = widthConstraints.step,
                    decimalPlaces = widthConstraints.decimalPlaces,
                    error = widthError,
                    hint = widthRangeHint,
                    modifier = Modifier.weight(1f)
                )
            }

            if (showWidth && showHeight) {
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (showHeight) {
                val heightConstraints = WorkflowConstraintsProvider.rememberConstraints("height", workflowName)
                val heightRangeHint = stringResource(
                    R.string.node_editor_range_min_max,
                    formatRangeValue(heightConstraints.min, heightConstraints.decimalPlaces),
                    formatRangeValue(heightConstraints.max, heightConstraints.decimalPlaces)
                )

                NumericStepperField(
                    value = height,
                    onValueChange = onHeightChange,
                    label = stringResource(R.string.label_height),
                    min = heightConstraints.min,
                    max = heightConstraints.max,
                    step = heightConstraints.step,
                    decimalPlaces = heightConstraints.decimalPlaces,
                    error = heightError,
                    hint = heightRangeHint,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Row 2: Resolution preset dropdown
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Preset button
            OutlinedButton(
                onClick = { showPresetMenu = true },
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = stringResource(R.string.button_resolution_presets),
                    style = MaterialTheme.typography.labelMedium
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }

            DropdownMenu(
                expanded = showPresetMenu,
                onDismissRequest = { showPresetMenu = false }
            ) {
                // Grouped presets
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "── 1:1 ──",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {},
                    enabled = false
                )
                listOf(
                    "512 × 512" to Pair(512, 512),
                    "768 × 768" to Pair(768, 768),
                    "1024 × 1024" to Pair(1024, 1024),
                ).forEach { (label, resolution) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onWidthChange(resolution.first.toString())
                            onHeightChange(resolution.second.toString())
                            showPresetMenu = false
                        }
                    )
                }

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "── 3:4 Portrait ──",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {},
                    enabled = false
                )
                listOf(
                    "512 × 768" to Pair(512, 768),
                    "768 × 1024" to Pair(768, 1024),
                    "512 × 768 alt" to Pair(512, 768),
                    "768 × 1152" to Pair(768, 1152),
                ).forEach { (label, resolution) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onWidthChange(resolution.first.toString())
                            onHeightChange(resolution.second.toString())
                            showPresetMenu = false
                        }
                    )
                }

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "── 4:3 Landscape ──",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {},
                    enabled = false
                )
                listOf(
                    "640 × 512" to Pair(640, 512),
                    "768 × 576" to Pair(768, 576),
                    "960 × 768" to Pair(960, 768),
                    "1024 × 768" to Pair(1024, 768),
                ).forEach { (label, resolution) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onWidthChange(resolution.first.toString())
                            onHeightChange(resolution.second.toString())
                            showPresetMenu = false
                        }
                    )
                }

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "── 16:9 Landscape ──",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {},
                    enabled = false
                )
                listOf(
                    "960 × 540 (360p)" to Pair(960, 540),
                    "1280 × 720 (720p)" to Pair(1280, 720),
                    "1920 × 1080 (1080p)" to Pair(1920, 1080),
                ).forEach { (label, resolution) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onWidthChange(resolution.first.toString())
                            onHeightChange(resolution.second.toString())
                            showPresetMenu = false
                        }
                    )
                }

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "── 9:16 Portrait ──",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {},
                    enabled = false
                )
                listOf(
                    "540 × 960 (360p)" to Pair(540, 960),
                    "720 × 1280 (720p)" to Pair(720, 1280),
                    "1080 × 1920 (1080p)" to Pair(1080, 1920),
                ).forEach { (label, resolution) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onWidthChange(resolution.first.toString())
                            onHeightChange(resolution.second.toString())
                            showPresetMenu = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Show current resolution
            val currentWidth = width.toIntOrNull()
            val currentHeight = height.toIntOrNull()
            if (currentWidth != null && currentHeight != null) {
                val aspectLabel = when {
                    currentWidth == currentHeight -> "1:1"
                    currentWidth * 9 == currentHeight * 16 -> "9:16"
                    currentWidth * 16 == currentHeight * 9 -> "16:9"
                    currentWidth * 3 == currentHeight * 4 -> "3:4"
                    currentWidth * 4 == currentHeight * 3 -> "4:3"
                    else -> null
                }
                aspectLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Formats a range value for display, showing integers without decimals
 * and floats with the specified number of decimal places.
 */
private fun formatRangeValue(value: Float, decimalPlaces: Int): String {
    return if (decimalPlaces == 0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.${decimalPlaces}f", value)
    }
}

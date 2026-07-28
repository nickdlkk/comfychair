package sh.hnet.comfychair.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sh.hnet.comfychair.R

/**
 * Preset batch counts for quick selection
 */
private val PRESET_COUNTS = listOf(2, 4, 8, 12)

/**
 * Expandable batch generation panel that slides up from above the Generate button.
 *
 * Triggered by long-pressing the Generate button.
 *
 * @param visible Whether the panel is expanded
 * @param defaultCount Default count when panel opens (typically the current batchSize setting)
 * @param isGenerating Whether batch generation is in progress
 * @param batchProgress Current progress if generating (e.g., 2 for "2/5")
 * @param onConfirm Called when user confirms batch generation with the selected count
 * @param onDismiss Called when user dismisses the panel
 */
@Composable
fun BatchGenerationPanel(
    visible: Boolean,
    defaultCount: Int,
    isGenerating: Boolean = false,
    batchProgress: Int? = null,
    onConfirm: (count: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var count by remember(defaultCount) { mutableIntStateOf(defaultCount.coerceIn(1, 99)) }

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.secondaryContainer,
                    RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Count stepper row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_batch_count),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Stepper: decrease button
                IconButton(
                    onClick = { if (count > 1) count-- },
                    enabled = count > 1 && !isGenerating,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = stringResource(R.string.content_description_decrease),
                        tint = if (count > 1) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                // Count display
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(60.dp)
                )

                // Stepper: increase button
                IconButton(
                    onClick = { if (count < 99) count++ },
                    enabled = count < 99 && !isGenerating,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.content_description_increase),
                        tint = if (count < 99) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Custom input field
                var customText by remember(count) { mutableStateOf(count.toString()) }
                OutlinedTextField(
                    value = customText,
                    onValueChange = { newValue ->
                        customText = newValue
                        newValue.toIntOrNull()?.let { parsed ->
                            count = parsed.coerceIn(1, 99)
                        }
                    },
                    modifier = Modifier.width(80.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                    singleLine = true,
                    trailingIcon = {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            customText = count.toString()
                        }
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Preset buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                PRESET_COUNTS.forEach { preset ->
                    OutlinedButton(
                        onClick = { count = preset },
                        enabled = !isGenerating,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = preset.toString(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Dismiss button
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isGenerating,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.button_cancel))
                }

                // Confirm button
                Button(
                    onClick = { onConfirm(count) },
                    enabled = !isGenerating && count >= 1,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isGenerating && batchProgress != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.button_generating_progress, batchProgress))
                    } else {
                        Text(stringResource(R.string.button_confirm_batch, count))
                    }
                }
            }
        }
    }
}

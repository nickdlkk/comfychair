package sh.hnet.comfychair.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import sh.hnet.comfychair.R
import sh.hnet.comfychair.model.LoraSelection
import sh.hnet.comfychair.ui.components.shared.ModelPathText
import java.util.Locale

/**
 * Reusable component for editing a LoRA chain (0-5 LoRAs with per-LoRA strength)
 */
@Composable
fun LoraChainEditor(
    title: String,
    loraChain: List<LoraSelection>,
    availableLoras: List<String>,
    onAddLora: () -> Unit,
    onRemoveLora: (Int) -> Unit,
    onLoraNameChange: (Int, String) -> Unit,
    onLoraStrengthChange: (Int, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Header with title and add button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge
            )

            TextButton(
                onClick = onAddLora,
                enabled = loraChain.size < LoraSelection.MAX_CHAIN_LENGTH && availableLoras.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.button_add_lora))
            }
        }

        if (loraChain.isEmpty()) {
            // Empty state
            Text(
                text = stringResource(R.string.msg_lora_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            // LoRA entries
            loraChain.forEachIndexed { index, lora ->
                key(lora.name, index) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LoraEntryItem(
                        index = index,
                        lora = lora,
                        availableLoras = availableLoras,
                        onNameChange = { name -> onLoraNameChange(index, name) },
                        onStrengthChange = { strength -> onLoraStrengthChange(index, strength) },
                        onRemove = { onRemoveLora(index) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoraEntryItem(
    index: Int,
    lora: LoraSelection,
    availableLoras: List<String>,
    onNameChange: (String) -> Unit,
    onStrengthChange: (Float) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // LoRA selection row with dropdown and remove button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index indicator
            Text(
                text = "${index + 1}.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(24.dp)
            )

            // LoRA dropdown
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = lora.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.label_lora)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableLoras.forEach { option ->
                        DropdownMenuItem(
                            text = { ModelPathText(option) },
                            onClick = {
                                onNameChange(option)
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            // Remove button
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.button_remove_lora)
                )
            }
        }

        // Strength input row with +/- buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_lora_strength),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(64.dp)
            )

            OutlinedTextField(
                value = String.format(Locale.US, "%.2f", lora.strength),
                onValueChange = { text ->
                    text.toFloatOrNull()?.let { onStrengthChange(it) }
                },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            // Decrease button
            IconButton(
                onClick = {
                    val step = 0.1f
                    onStrengthChange(lora.strength - step)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(R.string.content_description_decrease)
                )
            }

            // Increase button
            IconButton(
                onClick = {
                    val step = 0.1f
                    onStrengthChange(lora.strength + step)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.content_description_increase)
                )
            }
        }

        // Preset strength chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val presets = listOf(-1.0f, -0.5f, 0.5f, 1.0f, 1.5f)
            presets.forEach { preset ->
                TextButton(
                    onClick = { onStrengthChange(preset) },
                    modifier = Modifier.height(28.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f", preset),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

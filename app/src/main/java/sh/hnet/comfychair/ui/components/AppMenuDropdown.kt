package sh.hnet.comfychair.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import sh.hnet.comfychair.R
import sh.hnet.comfychair.viewmodel.SettingsViewModel

/**
 * Reusable menu dropdown for navigation and Logout actions.
 * Used in TopAppBar across all screens.
 *
 * @param onNavigate First action (Settings or Generation)
 * @param onLogout Logout action
 * @param navigateLabel Label for the first action (defaults to Settings)
 * @param navigateIcon Icon for the first action (defaults to Settings icon)
 * @param showOfflineToggle Show offline/online toggle menu item
 */
@Composable
fun AppMenuDropdown(
    onNavigate: () -> Unit,
    onLogout: () -> Unit,
    onMaterials: (() -> Unit)? = null,
    navigateLabel: String = stringResource(R.string.action_settings),
    navigateIcon: ImageVector = Icons.Default.Settings,
    showOfflineToggle: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel = remember { SettingsViewModel() }
    val isOfflineMode by settingsViewModel.isOfflineMode.collectAsState()

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.content_description_menu))
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            onMaterials?.let { materialsHandler ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_materials)) },
                    onClick = {
                        showMenu = false
                        materialsHandler()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Image, contentDescription = null)
                    }
                )
            }
            if (showOfflineToggle) {
                DropdownMenuItem(
                    text = {
                        Text(
                            if (isOfflineMode) stringResource(R.string.menu_go_online)
                            else stringResource(R.string.menu_go_offline)
                        )
                    },
                    onClick = {
                        showMenu = false
                        settingsViewModel.setOfflineMode(context, !isOfflineMode)
                    },
                    leadingIcon = {
                        Icon(
                            if (isOfflineMode) Icons.Default.Cloud else Icons.Default.CloudOff,
                            contentDescription = null
                        )
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(navigateLabel) },
                onClick = {
                    showMenu = false
                    onNavigate()
                },
                leadingIcon = {
                    Icon(navigateIcon, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_logout)) },
                onClick = {
                    showMenu = false
                    onLogout()
                },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                }
            )
        }
    }
}

/**
 * Menu dropdown for generation screens (Settings + Logout + Offline Toggle).
 */
@Composable
fun AppMenuDropdown(
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    onMaterials: (() -> Unit)? = null,
    showOfflineToggle: Boolean = false
) {
    AppMenuDropdown(
        onNavigate = onSettings,
        onLogout = onLogout,
        onMaterials = onMaterials,
        navigateLabel = stringResource(R.string.action_settings),
        navigateIcon = Icons.Default.Settings,
        showOfflineToggle = showOfflineToggle
    )
}

/**
 * Menu dropdown for settings screens (Generation + Logout).
 */
@Composable
fun SettingsMenuDropdown(
    onGeneration: () -> Unit,
    onLogout: () -> Unit
) {
    AppMenuDropdown(
        onNavigate = onGeneration,
        onLogout = onLogout,
        navigateLabel = stringResource(R.string.menu_generation),
        navigateIcon = Icons.Default.AutoAwesome
    )
}

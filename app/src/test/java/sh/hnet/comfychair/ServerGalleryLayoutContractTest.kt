package sh.hnet.comfychair

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import sh.hnet.comfychair.ui.navigation.SettingsChromePolicy

/** Executable state/source contracts, not rendered Compose or gesture proof. */
class ServerGalleryLayoutContractTest {
    private fun source(path: String) = File("src/main/java/sh/hnet/comfychair/$path").readText()
    @Test fun galleryRouteAloneHidesSettingsChrome() {
        assertFalse(SettingsChromePolicy.showBottomBar("server_gallery"))
        for (route in listOf(null, "application", "server", "workflows", "about"))
            assertTrue(SettingsChromePolicy.showBottomBar(route))
        val nav = source("ui/navigation/SettingsNavHost.kt")
        assertTrue(nav.contains("if (showSettingsChrome) BottomAppBar("))
        assertTrue(nav.contains("contentWindowInsets = if (showSettingsChrome)"))
        assertTrue(nav.contains("consumeWindowInsets(paddingValues)"))
    }
    @Test fun topTabsHaveNoPrecedingHeaderOrExplicitBack() {
        val screen = source("ui/screens/ServerGalleryScreen.kt")
        assertFalse(screen.contains("server_gallery_back"))
        assertFalse(screen.contains("server_gallery_title"))
        assertTrue(screen.contains("BackHandler"))
        assertTrue(screen.contains("WindowInsets.safeDrawing.union(WindowInsets.ime)"))
        val body = screen.substringAfter("// Fullscreen gallery owns")
        assertTrue(body.indexOf("TabRow(") < body.indexOf("if (showCredential)"))
        assertTrue(body.contains("Modifier.weight(1f)"))
    }
    @Test fun standaloneReplacesPickerHeaderAndSelectionReplacesSearch() {
        val panel = source("ui/components/ServerImagePickerPanel.kt")
        assertTrue(panel.contains("if (!standalone) {"))
        assertTrue(panel.contains("if (!deletion.active && (standalone || state.assets == true))"))
        assertTrue(panel.contains("standaloneInfo: @Composable () -> Unit"))
        assertTrue(panel.contains("if (standalone && showInfo && !deletion.active)"))
        assertTrue(panel.contains("heightIn(max = 240.dp)"))
        assertTrue(panel.contains("verticalScroll(rememberScrollState())"))
        assertTrue(panel.contains("GridCells.Fixed(3)"))
        assertTrue(panel.contains("Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)"))
        assertTrue(panel.contains("if (standalone) 0.dp else 8.dp"))
        assertTrue(panel.contains("Icons.Default.Info"))
        assertTrue(panel.contains("Icons.Default.Refresh"))
    }
    @Test fun securityAndConfirmationRemainAccessible() {
        val screen = source("ui/screens/ServerGalleryScreen.kt")
        for (marker in listOf("standaloneInfo =", "PasswordVisualTransformation", "server_delete_lan_warning", "DeletionCredentialStorage(context).save"))
            assertTrue(marker, screen.contains(marker))
        val controls = source("ui/components/ServerDeletionControls.kt")
        assertTrue(controls.contains("server_delete_confirm"))
        assertTrue(controls.contains("api.prepare(captured)"))
        assertTrue(controls.contains("api.delete(manifest)"))
        assertTrue(controls.contains("horizontalScroll(rememberScrollState())"))
        assertTrue(controls.contains("heightIn(min = 48.dp)"))
    }
}

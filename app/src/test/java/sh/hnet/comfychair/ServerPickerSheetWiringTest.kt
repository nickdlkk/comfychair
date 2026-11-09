package sh.hnet.comfychair

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** The generation screens' server picker: both sources, a bounded sheet, and progress while the original loads. */
class ServerPickerSheetWiringTest {
    private val sheet = File("src/main/java/sh/hnet/comfychair/ui/components/ServerImagePickerSheet.kt").readText()

    @Test fun offersBothSources() {
        assertTrue(sheet.contains("ServerImageSource.entries.forEachIndexed"))
        assertTrue(sheet.contains("viewModel.open(item)"))
    }

    @Test fun contentIsBoundedRatherThanFullScreen() {
        // No second ModalBottomSheet inside the caller's sheet, and a capped grid height
        assertFalse(sheet.contains("ModalBottomSheet("))
        assertTrue(sheet.contains("heightIn(max = 520.dp)"))
    }

    @Test fun sourceToolbarOffersTheServerEntryInBothModes() {
        // v0.9.5: the server source entry moved into the shared SourceSheet (spec §21); the source
        // toolbar and the empty picker open it through pickSource / sourcePicker::open
        val tools = File("src/main/java/sh/hnet/comfychair/ui/components/generation/SourceTools.kt").readText()
        assertTrue(tools.contains("val fromMaterials: (@Composable () -> Unit)? = onPickFromMaterials?.let"))
        for (screen in listOf("ui/screens/ImageToImageScreen.kt", "ui/screens/ImageToVideoScreen.kt")) {
            val text = File("src/main/java/sh/hnet/comfychair/$screen").readText()
            assertTrue(screen, text.contains("sourcePicker::open") || text.contains("pickSource(0)"))
        }
    }

    @Test fun pickingShowsProgressUntilTheOriginalArrives() {
        assertTrue(sheet.contains("var downloading by remember { mutableStateOf<ServerImage?>(null) }"))
        assertTrue(sheet.contains("MediaLoadingOverlay(onCancel = { downloading = null })"))
        assertTrue(sheet.contains("viewModel.loadOriginal(image)"))
        // The sheet closes only after the bitmap is handed over
        val block = sheet.substringAfter("scope.launch {").substringBefore("}")
        assertTrue(block.contains("onPick(bitmap)"))
        assertTrue(block.contains("onClose()"))
    }
}

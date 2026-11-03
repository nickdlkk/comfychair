package sh.hnet.comfychair

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

/** Source/layout contracts only: these do not replace on-device Compose touch tests. */
class ServerImagePickerUiContractTest {
    private fun source(path: String): String {
        val file = listOf(File("src/$path"), File("app/src/$path")).first { it.isFile }
        return file.readText()
    }
    private val screen get() = source("main/java/sh/hnet/comfychair/ui/screens/ImageToImageScreen.kt")

    @Test fun serverChoicesShareExistingTopBarIconActions() {
        val actions = screen.substringAfter("actions = {").substringBefore("// Progress indicator")
        assertTrue(actions.contains("ServerImageSourceButtons("))
        assertTrue(actions.indexOf("ServerImageSourceButtons(") < actions.indexOf("// Edit mask button"))
        assertFalse(actions.contains("TextButton(onClick = {\n                    val slot = imagePages"))
    }

    @Test fun httpPickerIsNotDisabledByGenerationWebSocketState() {
        val buttons = source("main/java/sh/hnet/comfychair/ui/components/ServerImageSourceButtons.kt")
        assertFalse(buttons.contains("ConnectionStatus"))
        assertFalse(buttons.contains("enabled ="))
        assertTrue(buttons.contains("IconButton(onClick = { onSelect(source) })"))
        assertTrue(buttons.contains("contentDescription = stringResource("))
    }

    @Test fun crowdedActionStripScrollsWithoutShrinkingTouchTargets() {
        val actions = screen.substringAfter("actions = {").substringBefore("// Progress indicator")
        assertTrue(actions.contains("widthIn(max = 240.dp)"))
        assertTrue(actions.contains("horizontalScroll(rememberScrollState())"))
        assertFalse(actions.contains("size(24.dp)"))
    }

    @Test fun remoteSheetUsesMaterialPickerGridPatternAndVisibleOnlyPreviewEffects() {
        val panel = source("main/java/sh/hnet/comfychair/ui/components/ServerImagePickerPanel.kt")
        assertTrue(panel.contains("ModalBottomSheet(onDismissRequest = onClose"))
        assertTrue(panel.contains("GridCells.Fixed(3)"))
        assertTrue(panel.contains("items(state.images, key = { it.stableKey })"))
        assertTrue(panel.contains("image.stableKey in visible"))
        assertTrue(panel.contains("RoundedCornerShape(8.dp)"))
        assertTrue(panel.contains("layoutInfo.visibleItemsInfo"))
        assertTrue(panel.contains("LaunchedEffect(image, visible, retry, requestAllowed)"))
        assertFalse(panel.contains("Text(image.path"))
        assertTrue(screen.contains("Lifecycle.Event.ON_STOP"))
    }

    @Test fun compactHeaderReplacesVerboseRowsAndBottomRefresh() {
        val panel = source("main/java/sh/hnet/comfychair/ui/components/ServerImagePickerPanel.kt")
        val header = panel.substringAfter("// Source/slot").substringBefore("if (state.assets == true)")
        assertTrue(header.contains("server_image_target"))
        assertTrue(header.contains("server_assets_count"))
        assertTrue(header.contains("typography.titleSmall"))
        assertTrue(header.contains("typography.labelSmall"))
        assertTrue(header.contains("Icons.Default.Refresh"))
        assertTrue(header.contains("Icons.Default.Info"))
        assertTrue(header.contains("Icons.Default.Close"))
        assertFalse(panel.contains("\n            Button(onClick = onRefresh"))
        assertFalse(panel.contains("padding(bottom = 32.dp)"))
        assertTrue(panel.contains("navigationBarsPadding()"))
        assertTrue(panel.contains("dragHandle = null"))
    }

    @Test fun searchAndSortShareRowAndControlsKeepAccessibleTargets() {
        val panel = source("main/java/sh/hnet/comfychair/ui/components/ServerImagePickerPanel.kt")
        val searchRow = panel.substringAfter("var sortMenu").substringBefore("if (state.loading ||")
        assertTrue(searchRow.contains("Row("))
        assertTrue(searchRow.contains("OutlinedTextField"))
        assertTrue(searchRow.contains("Modifier.weight(1f)"))
        assertTrue(searchRow.contains("Icons.AutoMirrored.Filled.Sort"))
        assertTrue(searchRow.contains("server_assets_sort"))
        assertTrue(searchRow.contains("ServerImageSort.entries"))
        assertTrue(searchRow.contains("Modifier.size(48.dp)"))
        assertTrue(panel.contains("Modifier.heightIn(min = 48.dp)"))
        assertTrue(panel.contains("maxLines = 1, overflow = TextOverflow.Ellipsis"))
    }

    @Test fun idleFooterDoesNotReserveGridSpaceAndFailuresKeepActions() {
        val panel = source("main/java/sh/hnet/comfychair/ui/components/ServerImagePickerPanel.kt")
        assertTrue(panel.contains("Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)"))
        assertTrue(panel.contains("item(span = { GridItemSpan(maxLineSpan) }, key = \"picker-end\")"))
        val footer = panel.substringAfter("// No idle footer").substringBefore("private fun sortLabel")
        assertTrue(footer.contains("if (state.downloading || state.downloadError || state.error || state.moreError ||"))
        assertTrue(footer.contains("autoPages >= 5"))
        for (action in listOf("onCancelDownload", "onRetryDownload", "onRefresh", "onRetryMore", "onLoadMore()"))
            assertTrue(footer.contains(action))
        assertFalse(footer.contains("Spacer("))
        assertTrue(panel.contains("Modifier.aspectRatio(1f)"))
        assertTrue(panel.contains("Arrangement.spacedBy(8.dp)"))
    }

    @Test fun bothLocalesDescribeServerAndScopeForAccessibility() {
        for (locale in listOf("values", "values-zh")) {
            val xml = source("main/res/$locale/strings.xml")
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(xml.byteInputStream()).getElementsByTagName("string")
            val strings = (0 until nodes.length).associate { index ->
                val node = nodes.item(index)
                node.attributes.getNamedItem("name").nodeValue to node.textContent
            }
            assertTrue(strings.getValue("server_image_input").contains("Input"))
            assertTrue(strings.getValue("server_image_output").contains("Output"))
            assertTrue(strings.getValue("server_image_input").contains(if (locale == "values") "server" else "服务器"))
        }
    }
}

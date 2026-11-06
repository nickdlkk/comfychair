package sh.hnet.comfychair

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Source contracts for direct input reuse wiring; not Android bitmap tests. */
class DirectInputWiringTest {
    private fun source(vararg relative: String): String = listOf(File("src/main/java"), File("app/src/main/java"))
        .first { it.exists() }
        .resolve(relative.joinToString("/"))
        .readText()

    private fun iti(): String = source("sh/hnet/comfychair/viewmodel/ImageToImageViewModel.kt")
    private fun viewer(): String = source("sh/hnet/comfychair/viewmodel/MediaViewerViewModel.kt")
    private fun nav(): String = source("sh/hnet/comfychair/ui/navigation/MainNavHost.kt")
    private fun host(): String = source("sh/hnet/comfychair/ui/components/MediaViewerHost.kt")

    @Test
    fun provenanceIsProcessOnlyAndClearedOnEveryNonServerEntry() {
        val text = iti()
        assertTrue(text.contains("private val directSources = mutableMapOf<Int, DirectInputSource>()"))
        // Every entry point that cannot prove provenance drops slot 1's descriptor
        assertTrue(text.contains("directSources.remove(1)"))
        assertEquals(3, Regex("directSources\\.remove\\(1\\)").findAll(text).count())
        // Only the descriptor-carrying entry installs it
        assertTrue(text.contains("directSources[1] = DirectInputSource("))
    }

    @Test
    fun editingPreparationReusesOnlyEligibleOriginalsAndFallsBackToUpload() {
        val text = iti()
        assertTrue(text.contains("DirectInputSource.supports("))
        assertTrue(text.contains("client.reuseInputImage(original.image, epoch)"))
        // Fallback is the content-addressed upload, never a random-name upload
        assertTrue(text.contains("uploadImageWithReuse(client, sourceBytes, \"png\")"))
        assertFalse(Regex("client\\.uploadImage\\(").containsMatchIn(text))
    }

    @Test
    fun viewerDescriptorOnlyForItsOwnServerItemDecodes() {
        val text = viewer()
        assertTrue(text.contains("fun currentItemDescriptor(): ServerImage?"))
        // Videos have no image descriptor; malformed paths validate to null
        assertTrue(text.contains("if (item.isVideo) return null"))
        assertTrue(text.contains("ServerImageDiscovery.descriptor("))
    }

    @Test
    fun viewerToSourceHandoffCarriesTheDescriptor() {
        assertTrue(host().contains("onUseServerImageAsSource"))
        assertTrue(nav().contains("useServerImageAsSource(context, descriptor, bitmap)"))
        // Non-server bitmaps still clear provenance via the plain path
        assertTrue(nav().contains("imageToImageViewModel.useAsSource(context, bitmap)"))
    }
}

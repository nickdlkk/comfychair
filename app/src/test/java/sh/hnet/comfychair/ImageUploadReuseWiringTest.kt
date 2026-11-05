package sh.hnet.comfychair

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Source contracts supplement HTTP tests; these are not Android bitmap/rendering tests. */
class ImageUploadReuseWiringTest {
    private fun source(): String = listOf(File("src/main/java"), File("app/src/main/java"))
        .first { it.exists() }.resolve("sh/hnet/comfychair/viewmodel/ImageToImageViewModel.kt").readText()

    @Test fun everyMappedNonBypassedAdditionalSlotUsesContentReuse() {
        val text = source()
        assertTrue(text.contains("\"image_filename_\$slot\" in state.workflowPlaceholders"))
        assertTrue(text.contains("slot !in state.bypassedSourceSlots"))
        for (slot in 2..4) {
            assertTrue(text.contains("additional(sourceImage$slot, $slot)"))
            assertTrue(text.contains("source$slot = uploadedSource$slot"))
        }
        assertTrue(text.contains("bypassedSourceSlots = state.bypassedSourceSlots"))
        assertTrue(text.contains("slot in state.compressedSlots"))
        assertTrue(text.contains("client.uploadImageReusing(bytes, extension)"))
        assertFalse(text.contains("client.uploadImage("))
    }

    @Test fun referencesAndMaskUseExactPreparedBytesAndFailuresAbort() {
        val text = source()
        assertTrue(text.contains("reference(it, \"reference 1\") ?: return null"))
        assertTrue(text.contains("reference(it, \"reference 2\") ?: return null"))
        assertTrue(text.contains("generateMaskBitmap(state)"))
        assertTrue(text.contains("state.maskPaths.forEach"))
        assertTrue(text.contains("uploadPreparedImage(client, imageBytes, \"png\", \"source\") ?: return null"))
        assertTrue(text.contains("Required source upload failed"))
    }

    @Test fun comparisonAndPreparedSubmissionIdentityRemainIntact() {
        val text = source()
        assertTrue(text.contains("comparisonSources.bind(\"\$serverId:\$promptId\", prepared.sourceSnapshot)"))
        assertTrue(text.contains("PreparedImageWorkflow(workflowJson, snapshot, unresolved)"))
        assertTrue(text.contains("client.imageUploadIdentity() !== uploadOwner"))
        assertTrue(text.contains("preparationServer != MediaStateHolder.getCurrentServerId()"))
    }
}

package sh.hnet.comfychair

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Source contracts supplement HTTP tests; these are not Android bitmap/rendering tests. */
class ImageUploadReuseWiringTest {
    private fun source(vararg relative: String): String = listOf(File("src/main/java"), File("app/src/main/java"))
        .first { it.exists() }
        .resolve(relative.joinToString("/"))
        .readText()

    private fun iti(): String = source("sh/hnet/comfychair/viewmodel/ImageToImageViewModel.kt")
    private fun itv(): String = source("sh/hnet/comfychair/viewmodel/ImageToVideoViewModel.kt")
    private fun client(): String = source("sh/hnet/comfychair/ComfyUIClient.kt")

    @Test
    fun clientCarriesEpochApiWithInvalidationOnProtocolAndCredentialChange() {
        val text = client()
        assertTrue(text.contains("imageReuse?.invalidate()"))
        assertTrue(text.contains("imageReuse = null"))
        assertTrue(text.contains("@Synchronized\n    fun setCredentials"))
        assertTrue(text.contains("if (workingProtocol != protocol)"))
        assertTrue(text.contains("fun imageUploadIdentity()"))
        assertTrue(text.contains("suspend fun uploadImageReusing("))
    }

    @Test
    fun imageToImageUploadsAllGoThroughContentReuse() {
        val text = iti()
        assertTrue(text.contains("uploadImageWithReuse(client, sourceBytes, \"png\")"))
        assertTrue(text.contains("uploadImageWithReuse(client, ref1Bytes, \"png\")"))
        assertTrue(text.contains("uploadImageWithReuse(client, ref2Bytes, \"png\")"))
        assertTrue(text.contains("client.uploadImageReusing(bytes, extension)"))
        // The random-name upload path is fully replaced on this screen
        assertFalse(text.contains("client.uploadImage("))
        assertFalse(text.contains("UuidUtils.generateUniqueUploadFilename"))
    }

    @Test
    fun imageToVideoUploadGoesThroughContentReuse() {
        val text = itv()
        assertTrue(text.contains("client.uploadImageReusing(imageBytes, \"png\")"))
        assertFalse(text.contains("client.uploadImage("))
        assertFalse(text.contains("UuidUtils.generateUniqueUploadFilename"))
    }

    @Test
    fun reuseFailurePathsPreserveUpstreamErrorSurfaces() {
        val text = iti()
        assertTrue(text.contains("ConnectionFailure.AUTHENTICATION"))
        assertTrue(text.contains("R.string.error_save_image"))
        assertTrue(text.contains("catch (e: kotlinx.coroutines.CancellationException)"))
    }
}

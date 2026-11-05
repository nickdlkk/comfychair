package sh.hnet.comfychair.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.hnet.comfychair.connection.ModelCache
import sh.hnet.comfychair.viewmodel.base.GenerationParams
import sh.hnet.comfychair.viewmodel.base.videoScreenDefaults

class NonItiGenerationStateTest {
    @Test
    fun sharedParamsRemainAuthoritativeAcrossStateCopies() {
        val params = GenerationParams(positivePrompt = "first", steps = "12")
        val image = TextToImageUiState(params = params)
        val video = TextToVideoUiState(params = videoScreenDefaults.copy(positivePrompt = "video"))
        val sourceVideo = ImageToVideoUiState(params = videoScreenDefaults.copy(steps = "9"))
        val updatedImage = image.copy(params = params.copy(positivePrompt = "second"))
        assertEquals("first", image.params.positivePrompt)
        assertEquals("second", updatedImage.params.positivePrompt)
        assertEquals("12", updatedImage.params.steps)
        assertEquals("video", video.copy(params = video.params.copy(steps = "10")).params.positivePrompt)
        assertEquals("9", sourceVideo.copy(isUploading = true).params.steps)
    }

    @Test
    fun refreshStateTracksSharedModelCache() {
        val loading = GenerationParams(models = ModelCache(isLoading = true))
        assertTrue(TextToImageUiState(params = loading).isRefreshingModels)
        assertTrue(TextToVideoUiState(params = loading).isRefreshingModels)
        assertTrue(ImageToVideoUiState(params = loading).isRefreshingModels)
        assertFalse(ImageToVideoUiState(params = loading.copy(models = ModelCache())).isRefreshingModels)
    }

    @Test
    fun uploadProgressDoesNotMutateVideoParams() {
        val state = ImageToVideoUiState(params = videoScreenDefaults.copy(seed = "42", randomSeed = false))
        val uploading = state.copy(isUploading = true, uploadTotalBytes = 100, uploadProgressBytes = 40)
        assertEquals(state.params, uploading.params)
        assertEquals(40L, uploading.uploadProgressBytes)
        assertFalse(uploading.params.randomSeed)
    }
}

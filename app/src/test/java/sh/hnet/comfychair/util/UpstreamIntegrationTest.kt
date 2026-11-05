package sh.hnet.comfychair.util

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import sh.hnet.comfychair.WorkflowType
import sh.hnet.comfychair.model.LoraSelection
import java.io.File

class UpstreamIntegrationTest {
    private fun inject(loader: String, modelOutput: Int = 0, input: String = "unet_name"): JSONObject {
        val json = JSONObject().put("nodes", JSONObject()
            .put("1", JSONObject().put("class_type", loader).put("inputs", JSONObject().put(input, "model")))
            .put("2", JSONObject().put("class_type", "KSampler").put("inputs", JSONObject()
                .put("model", JSONArray().put("1").put(modelOutput)))))
        return JSONObject(LoraInjectionUtils.injectLoraChain(json.toString(), listOf(LoraSelection("test.safetensors", 1.25f)), WorkflowType.ITI_EDITING)).getJSONObject("nodes")
    }

    @Test fun ggufWiredModelGetsLoraWithoutObjectInfo() {
        val nodes = inject("UnetLoaderGGUF")
        val insertedId = nodes.getJSONObject("2").getJSONObject("inputs").getJSONArray("model").getString(0)
        assertNotEquals("1", insertedId)
        val lora = nodes.getJSONObject(insertedId)
        assertEquals("LoraLoaderModelOnly", lora.getString("class_type"))
        assertEquals("[\"1\",0]", lora.getJSONObject("inputs").getJSONArray("model").toString())
        assertEquals(1.25, lora.getJSONObject("inputs").getDouble("strength_model"), 0.0)
    }

    @Test fun standardCheckpointAndUnetStillInject() {
        assertEquals(3, inject("CheckpointLoaderSimple", input = "ckpt_name").length())
        assertEquals(3, inject("UNETLoader").length())
    }

    @Test fun customLoaderNonModelOutputIsNotMistakenForModel() {
        assertEquals(2, inject("UnetLoaderGGUF", modelOutput = 1).length())
        assertEquals(2, inject("AudioVAE", input = "vae_name").length())
    }

    private fun source(relative: String): String {
        val root = sequenceOf(File("."), File("..")).first { File(it, "app/src/main").exists() }
        return File(root, "app/src/main/java/sh/hnet/comfychair/$relative").readText()
    }

    @Test fun reconnectRequiresSameEndpointProtocolAndCredentialsAndShutsDownOldEpoch() {
        val code = source("connection/ConnectionManager.kt")
        val connect = code.substringAfter("fun connect(").substringBefore("fun disconnect()")
        for (contract in listOf("current.serverId == serverId", "current.hostname == hostname", "current.port == port",
            "current.protocol == protocol", "_client?.getCredentials() == credentials", "_client?.shutdown()")) {
            assertTrue(contract, connect.contains(contract))
        }
        assertTrue(source("ComfyUIClient.kt").substringAfter("fun shutdown()").contains("imageReuse?.invalidate()"))
    }

    @Test fun clearingGeneratedMediaCannotDeleteImportedWorkflows() {
        val clear = source("viewmodel/SettingsViewModel.kt").substringAfter("fun clearCache(context: Context)").substringBefore("fun restoreDefaults(")
        assertFalse(clear.contains("clearAllUserWorkflows"))
        assertFalse(clear.contains("MaterialLibrary"))
        assertFalse(clear.contains("DeletionCredentialStorage"))
        assertTrue(clear.contains("MediaStateHolder.clearDiskCache(context)"))
    }

    @Test fun galleryResolvesCurrentIndexAndRefreshesGestureCallbacks() {
        val code = source("ui/screens/GalleryScreen.kt")
        assertTrue(code.contains("rememberUpdatedState(onTap)"))
        assertTrue(code.contains("rememberUpdatedState(onLongPress)"))
        assertTrue(code.contains("indexOfFirst"))
        // Existing fork integration must survive upstream's gesture fix.
        assertTrue(code.contains("saveSelectedToMaterialLibrary(context)"))
    }
}

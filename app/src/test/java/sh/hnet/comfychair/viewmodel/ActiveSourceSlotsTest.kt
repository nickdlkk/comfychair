package sh.hnet.comfychair.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveSourceSlotsTest {
    @Test fun sparseMappingsKeepTheirRealSlotNumbers() {
        assertEquals(listOf(1, 4), activeSourceSlots(ImageToImageMode.EDITING, setOf("image_filename_4")))
        assertEquals(listOf(1, 2, 4), activeSourceSlots(ImageToImageMode.EDITING, setOf("image_filename_2", "image_filename_4")))
    }

    @Test fun inpaintingCannotAcceptEditingOnlySlots() {
        assertEquals(listOf(1), activeSourceSlots(ImageToImageMode.INPAINTING, setOf("image_filename_2", "image_filename_4")))
    }

    @Test fun unrelatedPlaceholdersDoNotCreateSlots() {
        assertEquals(listOf(1), activeSourceSlots(ImageToImageMode.EDITING, setOf("image_filename_5", "prompt")))
    }
}

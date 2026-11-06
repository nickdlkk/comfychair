package sh.hnet.comfychair

import org.junit.Assert.*
import org.junit.Test
import sh.hnet.comfychair.connection.DirectInputSource
import sh.hnet.comfychair.model.ServerImage

/** Pure contract tests for direct input provenance: eligibility and native LoadImage mapping. */
class DirectInputSourceTest {
    private val image = ServerImage("input.png", "", "input")
    private val owner = Any()
    private val bitmap = Any()

    private fun direct() = DirectInputSource(image, owner, bitmap, 7)

    @Test
    fun eligibleOnlyForSameOwnerBitmapRevisionWithoutCompressionOrMask() {
        assertTrue(direct().eligible(owner, bitmap, 7, compressed = false, masked = false, nativeMapping = true))
        // A different owner epoch, a different bitmap object, or any mutation revision breaks identity
        assertFalse(direct().eligible(Any(), bitmap, 7, false, false, true))
        assertFalse(direct().eligible(owner, Any(), 7, false, false, true))
        assertFalse(direct().eligible(owner, bitmap, 8, false, false, true))
        // Re-encoding or a mask means the server file no longer matches what would be uploaded
        assertFalse(direct().eligible(owner, bitmap, 7, compressed = true, masked = false, nativeMapping = true))
        assertFalse(direct().eligible(owner, bitmap, 7, compressed = false, masked = true, nativeMapping = true))
        // The workflow does not map the slot natively
        assertFalse(direct().eligible(owner, bitmap, 7, false, false, nativeMapping = false))
        // Output descriptors are never direct inputs
        val output = DirectInputSource(ServerImage("out.png", "", "output"), owner, bitmap, 7)
        assertFalse(output.eligible(owner, bitmap, 7, false, false, true))
    }

    @Test
    fun supportsRequiresEveryOccurrenceToBeAFullNativeLoadImageValue() {
        // Slot 1: bare token as the whole image value
        val bare = """{"prompt":{"10":{"class_type":"LoadImage","inputs":{"image":"{{image_filename}}"}}}}"""
        assertTrue(DirectInputSource.supports(bare, 1))
        // With the [input] suffix
        val suffix = """{"prompt":{"10":{"class_type":"LoadImage","inputs":{"image":"{{image_filename}} [input]"}}}}"""
        assertTrue(DirectInputSource.supports(suffix, 1))
        // The token elsewhere in the node (another input, metadata) is not trusted
        val extra = """{"prompt":{"10":{"class_type":"LoadImage","inputs":{"image":"{{image_filename}}","note":"{{image_filename}}"}}}}"""
        assertFalse(DirectInputSource.supports(extra, 1))
        // A custom class or a different input name is not a native mapping
        val custom = """{"prompt":{"10":{"class_type":"MyLoader","inputs":{"image":"{{image_filename}}"}}}}"""
        assertFalse(DirectInputSource.supports(custom, 1))
        val wrongField = """{"prompt":{"10":{"class_type":"LoadImage","inputs":{"upload":"{{image_filename}}"}}}}"""
        assertFalse(DirectInputSource.supports(wrongField, 1))
        // A token in a node with no inputs object at all
        val noInputs = """{"nodes":{"10":{"class_type":"LoadImage"}}}"""
        assertFalse(DirectInputSource.supports(noInputs, 1))
        // API wrapper format
        val nodesWrapper = """{"nodes":{"10":{"class_type":"LoadImage","inputs":{"image":"{{image_filename}}"}}}}"""
        assertTrue(DirectInputSource.supports(nodesWrapper, 1))
        // Slot token must match the slot: {{image_filename_2}} is not slot 1's mapping
        val slot2 = """{"prompt":{"10":{"class_type":"LoadImage","inputs":{"image":"{{image_filename_2}}"}}}}"""
        assertFalse(DirectInputSource.supports(slot2, 1))
        // No occurrence at all
        val none = """{"prompt":{"10":{"class_type":"LoadImage","inputs":{"image":"fixed.png"}}}}"""
        assertFalse(DirectInputSource.supports(none, 1))
        // Malformed JSON is never supported
        assertFalse(DirectInputSource.supports("not json", 1))
    }
}

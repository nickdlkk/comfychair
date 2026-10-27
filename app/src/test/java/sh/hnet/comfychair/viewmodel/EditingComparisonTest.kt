package sh.hnet.comfychair.viewmodel
import org.junit.Assert.*
import org.junit.Test
class EditingComparisonTest {
    @Test fun dividerClampsAtBothEdges() {
        assertEquals(0f, dividerFraction(-20f, 100f), 0f)
        assertEquals(1f, dividerFraction(120f, 100f), 0f)
        assertEquals(0.37f, dividerFraction(37f, 100f), 0.0001f)
        assertEquals(0.5f, dividerFraction(1f, 0f), 0f)
        assertEquals(0.5f, dividerFraction(Float.NaN, 100f), 0f)
    }
    @Test fun unequalAspectRatiosFitFullViewportBeforeClipping() {
        assertEquals(FitBounds(0f, 25f, 200f, 100f), fitBounds(400, 200, 200f, 150f))
        assertEquals(FitBounds(62.5f, 0f, 75f, 150f), fitBounds(100, 200, 200f, 150f))
        assertEquals(FitBounds(0f, 0f, 0f, 0f), fitBounds(0, 200, 200f, 150f))
    }
    @Test fun queuedResultsResolveByPromptNotCurrentSelectionOrCompletionOrder() {
        val sources = ResultSourceAssociation<String>()
        var selected = "sourceA"
        sources.bind("jobA", selected)
        selected = "sourceB"; sources.bind("jobB", selected)
        selected = "sourceC"
        assertEquals("sourceB", sources.sourceFor("jobB"))
        assertEquals("sourceA", sources.sourceFor("jobA"))
        assertNull(sources.sourceFor("unknown"))
        sources.bind("inpaint", null); assertNull(sources.sourceFor("inpaint"))
        assertEquals("sourceC", selected)
    }
}

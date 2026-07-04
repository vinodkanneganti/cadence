package studio.sparkcube.cadence.core.pacing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComplexityTest {

    @Test
    fun estimateSyllables_handlesEdges() {
        assertEquals(1, estimateSyllables(""))          // empty → 1
        assertEquals(1, estimateSyllables("the"))       // silent-e collapses to 1
        assertEquals(1, estimateSyllables("cat"))
        assertEquals(2, estimateSyllables("rule"))      // "le" ending is NOT silent-e
        assertTrue(estimateSyllables("optimization") >= 4)
    }

    @Test
    fun tokenize_stripsPunctuationAndWhitespace() {
        val tokens = tokenize("  e.g., the  chain-rule!  ")
        assertEquals(listOf("eg", "the", "chainrule"), tokens)
    }

    @Test
    fun complexity_isBoundedZeroToOne() {
        val samples = listOf(
            "",
            "a",
            "The cat sat on the mat.",
            "Backpropagation differentiates multidimensional computational architectures.",
        )
        for (s in samples) {
            val c = complexityOf(s)
            assertTrue(c in 0.0..1.0, "complexity $c out of [0,1] for \"$s\"")
        }
    }

    @Test
    fun complexity_risesWithDensity() {
        val plain = complexityOf("The dog ran to the park.")
        val dense = complexityOf("Heterogeneous computational architectures propagate approximated gradients.")
        assertTrue(dense > plain, "dense $dense should exceed plain $plain")
    }
}

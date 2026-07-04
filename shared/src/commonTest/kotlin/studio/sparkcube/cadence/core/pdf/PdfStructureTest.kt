package studio.sparkcube.cadence.core.pdf

import studio.sparkcube.cadence.core.model.Boundary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfStructureTest {

    // Body lines advance ~12 units of y; headings are taller (20) and short.
    private fun body(text: String, y: Double) = PdfLine(text, height = 12.0, y = y)
    private fun heading(text: String, y: Double) = PdfLine(text, height = 20.0, y = y)

    @Test
    fun tallShortLineBecomesSection() {
        // Body-dominated page so the median height reflects body text (12), as in real PDFs.
        val units = PdfStructure.build(
            listOf(
                heading("1  Introduction", 0.0),
                body("This is the first body sentence.", 24.0),
                body("Here is another body sentence.", 36.0),
                body("And a third body sentence.", 48.0),
            ),
        )
        assertEquals(Boundary.SECTION, units.first().boundary)
        assertEquals("1  Introduction", units.first().text)
    }

    @Test
    fun tallButLongLineIsNotASection() {
        // ≥12 words at heading height should NOT be treated as a heading.
        val longText = (1..14).joinToString(" ") { "word$it" }
        val units = PdfStructure.build(listOf(PdfLine(longText, height = 20.0, y = 0.0)))
        assertTrue(units.none { it.boundary == Boundary.SECTION })
    }

    @Test
    fun lastSentenceOfParagraphCarriesParagraphBoundary() {
        val units = PdfStructure.build(
            listOf(
                body("First sentence here. Second sentence follows.", 0.0),
            ),
        )
        assertEquals(2, units.size)
        assertEquals(Boundary.SENTENCE, units[0].boundary)
        assertEquals(Boundary.PARAGRAPH, units[1].boundary)
    }

    @Test
    fun largeVerticalGapStartsANewParagraph() {
        // Enough normal-advance lines that the median gap = the line advance (12).
        val units = PdfStructure.build(
            listOf(
                body("Alpha one.", 0.0),
                body("Alpha two.", 12.0),
                body("Alpha three.", 24.0),
                body("Beta one.", 60.0),    // big gap (36 ≫ 12) → new paragraph
                body("Beta two.", 72.0),
            ),
        )
        // "Alpha three." ends the first paragraph; "Beta two." ends the second.
        assertEquals(Boundary.PARAGRAPH, units.first { it.text.contains("Alpha three") }.boundary)
        assertEquals(Boundary.PARAGRAPH, units.first { it.text.contains("Beta two") }.boundary)
    }

    @Test
    fun ordersUnitsAndCountsThreeSections() {
        val lines = mutableListOf<PdfLine>()
        var y = 0.0
        repeat(3) { s ->
            lines += heading("Section ${s + 1}", y); y += 24.0
            repeat(3) {
                lines += body("Paragraph ${s + 1} sentence one. And sentence two.", y); y += 12.0
                y += 24.0 // paragraph gap
            }
        }
        val units = PdfStructure.build(lines)

        assertEquals(3, units.count { it.boundary == Boundary.SECTION }, "three headings → three SECTIONs")
        // Section headings appear in order.
        val sectionTexts = units.filter { it.boundary == Boundary.SECTION }.map { it.text }
        assertEquals(listOf("Section 1", "Section 2", "Section 3"), sectionTexts)
        // Every unit is non-empty and sentence-segmented (no unit contains two sentence periods).
        assertTrue(units.all { it.text.isNotBlank() })
    }

    @Test
    fun emptyInputYieldsNoUnits() {
        assertEquals(emptyList(), PdfStructure.build(emptyList()))
        assertEquals(emptyList(), PdfStructure.build(listOf(PdfLine("   ", 12.0, 0.0))))
    }
}

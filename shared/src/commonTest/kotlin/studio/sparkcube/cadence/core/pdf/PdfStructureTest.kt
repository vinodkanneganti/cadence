package studio.sparkcube.cadence.core.pdf

import studio.sparkcube.cadence.core.model.Boundary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfStructureTest {

    // Body lines advance ~12 units of y; headings are taller (20) and short.
    private fun body(text: String, y: Double, page: Int = 1) = PdfLine(text, height = 12.0, y = y, page = page)
    private fun heading(text: String, y: Double, page: Int = 1) = PdfLine(text, height = 20.0, y = y, page = page)

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
        ).units
        assertEquals(Boundary.SECTION, units.first().boundary)
        assertEquals("1  Introduction", units.first().text)
    }

    @Test
    fun tallButLongLineIsNotASection() {
        val longText = (1..14).joinToString(" ") { "word$it" }
        val units = PdfStructure.build(listOf(PdfLine(longText, height = 20.0, y = 0.0))).units
        assertTrue(units.none { it.boundary == Boundary.SECTION })
    }

    @Test
    fun lastSentenceOfParagraphCarriesParagraphBoundary() {
        val units = PdfStructure.build(
            listOf(body("First sentence here. Second sentence follows.", 0.0)),
        ).units
        assertEquals(2, units.size)
        assertEquals(Boundary.SENTENCE, units[0].boundary)
        assertEquals(Boundary.PARAGRAPH, units[1].boundary)
    }

    @Test
    fun largeVerticalGapStartsANewParagraph() {
        val units = PdfStructure.build(
            listOf(
                body("Alpha one.", 0.0),
                body("Alpha two.", 12.0),
                body("Alpha three.", 24.0),
                body("Beta one.", 60.0),    // big gap (36 ≫ 12) → new paragraph
                body("Beta two.", 72.0),
            ),
        ).units
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
                y += 24.0
            }
        }
        val units = PdfStructure.build(lines).units

        assertEquals(3, units.count { it.boundary == Boundary.SECTION })
        assertEquals(
            listOf("Section 1", "Section 2", "Section 3"),
            units.filter { it.boundary == Boundary.SECTION }.map { it.text },
        )
        assertTrue(units.all { it.text.isNotBlank() })
    }

    @Test
    fun tracksPagePerUnit() {
        // Body-dominated so headings are detected (median height = body).
        val result = PdfStructure.build(
            listOf(
                heading("Page one head", 0.0, page = 1),
                body("Body one on page one.", 24.0, page = 1),
                body("Body two on page one.", 36.0, page = 1),
                body("Body three on page one.", 48.0, page = 1),
                heading("Page two head", 0.0, page = 2),
                body("Body one on page two.", 24.0, page = 2),
                body("Body two on page two.", 36.0, page = 2),
                body("Body three on page two.", 48.0, page = 2),
            ),
        )
        assertEquals(result.units.size, result.pages.size, "pages parallels units")
        assertEquals(1, result.pages[result.units.indexOfFirst { it.text == "Page one head" }])
        assertEquals(2, result.pages[result.units.indexOfFirst { it.text == "Page two head" }])
        assertEquals(2, result.pages[result.units.indexOfFirst { it.text.contains("page two") }])
    }

    @Test
    fun firstUnitIndexForPage_findsPageStart() {
        val pages = listOf(1, 1, 1, 2, 2, 3)
        assertEquals(0, PdfStructure.firstUnitIndexForPage(pages, 1))
        assertEquals(3, PdfStructure.firstUnitIndexForPage(pages, 2))
        assertEquals(5, PdfStructure.firstUnitIndexForPage(pages, 3))
        // A page past the end clamps to the last unit; an empty list → 0.
        assertEquals(5, PdfStructure.firstUnitIndexForPage(pages, 9))
        assertEquals(0, PdfStructure.firstUnitIndexForPage(emptyList(), 4))
    }

    @Test
    fun emptyInputYieldsNoUnits() {
        assertEquals(emptyList(), PdfStructure.build(emptyList()).units)
        assertEquals(emptyList(), PdfStructure.build(listOf(PdfLine("   ", 12.0, 0.0))).units)
    }
}

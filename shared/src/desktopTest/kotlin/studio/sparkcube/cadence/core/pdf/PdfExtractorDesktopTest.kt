package studio.sparkcube.cadence.core.pdf

import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import studio.sparkcube.cadence.core.model.Boundary
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** End-to-end extraction against PDFs generated in-memory by PDFBox. */
class PdfExtractorDesktopTest {

    private val extractor = PdfExtractor()

    // AC1: text-layer PDF with headings + paragraphs → ordered, sentence-segmented, ≥3 SECTION.
    @Test
    fun extractsStructuredTextPdf() = runBlocking {
        val pdf = buildTextPdf()
        val result = extractor.extract(pdf)

        assertTrue(result.hasTextLayer, "generated PDF has a text layer")
        assertTrue(result.units.isNotEmpty(), "should produce units")

        val sections = result.units.filter { it.boundary == Boundary.SECTION }
        assertTrue(sections.size >= 3, "expected ≥3 SECTION units, got ${sections.size}")
        assertEquals(
            listOf("Section One", "Section Two", "Section Three"),
            sections.map { it.text },
            "headings in document order",
        )
        // Sentence segmentation actually happened (both sentence-level and paragraph-level units exist).
        assertTrue(result.units.any { it.boundary == Boundary.SENTENCE }, "some SENTENCE units")
        assertTrue(result.units.any { it.boundary == Boundary.PARAGRAPH }, "some PARAGRAPH units")
    }

    // AC9: image-only PDF → hasTextLayer=false, no units, no silent failure.
    @Test
    fun detectsScannedImageOnlyPdf() = runBlocking {
        val pdf = buildImageOnlyPdf()
        val result = extractor.extract(pdf)

        assertFalse(result.hasTextLayer, "image-only PDF must report no text layer")
        assertTrue(result.units.isEmpty(), "no reading units from an image-only PDF")
    }

    // --- PDF builders --------------------------------------------------------

    private fun buildTextPdf(): ByteArray {
        val doc = PDDocument()
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val headings = listOf("Section One", "Section Two", "Section Three")

        PDPageContentStream(doc, page).use { cs ->
            var y = 800f
            for (h in headings) {
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, 20f)
                cs.newLineAtOffset(50f, y)
                cs.showText(h)
                cs.endText()
                y -= 30f

                // two short paragraphs under each heading, separated by a gap
                repeat(2) {
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 12f)
                    cs.newLineAtOffset(50f, y)
                    cs.showText("First sentence of a paragraph. And here is a second one.")
                    cs.endText()
                    y -= 40f // paragraph gap
                }
                y -= 20f
            }
        }
        return doc.toBytes()
    }

    private fun buildImageOnlyPdf(): ByteArray {
        val doc = PDDocument()
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val img = BufferedImage(240, 120, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().run { color = Color.LIGHT_GRAY; fillRect(0, 0, 240, 120); dispose() }
        }
        val pdImage = LosslessFactory.createFromImage(doc, img)
        PDPageContentStream(doc, page).use { cs ->
            cs.drawImage(pdImage, 60f, 600f)
        }
        return doc.toBytes()
    }

    private fun PDDocument.toBytes(): ByteArray = use { doc ->
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }
}

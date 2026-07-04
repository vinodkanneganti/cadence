package studio.sparkcube.cadence.core.pdf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition

/**
 * Desktop PDF extraction via Apache PDFBox (Apache-2.0). Thin by design: PDFBox
 * only turns the document into positioned [PdfLine]s; all structure/sentence logic
 * lives in the pure [PdfStructure] / [SentenceSplitter].
 */
actual class PdfExtractor actual constructor() {

    actual suspend fun extract(bytes: ByteArray): ExtractResult = withContext(Dispatchers.IO) {
        PDDocument.load(bytes).use { doc ->
            val collector = LineCollector().apply {
                sortByPosition = true
                startPage = 1
                endPage = doc.numberOfPages
            }
            collector.getText(doc) // drives writeString(...)

            val lines = collector.lines.map { PdfLine(it.text, it.height.toDouble(), it.y.toDouble(), it.page) }
            val hasText = lines.any { it.text.isNotBlank() }
            val built = if (hasText) PdfStructure.build(lines) else BuildResult(emptyList(), emptyList())
            ExtractResult(
                units = built.units,
                hasTextLayer = hasText,
                pages = built.pages,
                pageCount = doc.numberOfPages,
            )
        }
    }

    private class LineCollector : PDFTextStripper() {
        data class RawLine(val text: String, val height: Float, val y: Float, val page: Int)

        val lines = mutableListOf<RawLine>()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (text.isBlank()) return
            val height = textPositions.maxOfOrNull { it.fontSizeInPt } ?: 0f
            val y = textPositions.firstOrNull()?.yDirAdj ?: 0f
            lines += RawLine(text.trim(), height, y, currentPageNo)
        }
    }
}

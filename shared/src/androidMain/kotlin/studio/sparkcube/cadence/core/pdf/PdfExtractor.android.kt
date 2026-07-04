package studio.sparkcube.cadence.core.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android PDF extraction via PdfBox-Android (Apache-2.0). Same thin design as the
 * desktop actual: PDFBox only produces positioned [PdfLine]s; all structure and
 * sentence logic is the pure [PdfStructure] / [SentenceSplitter].
 *
 * Requires `PDFBoxResourceLoader.init(context)` once at app start (done in MainActivity).
 */
actual class PdfExtractor actual constructor() {

    actual suspend fun extract(bytes: ByteArray): ExtractResult = withContext(Dispatchers.IO) {
        PDDocument.load(bytes).use { doc ->
            val collector = LineCollector().apply {
                sortByPosition = true
                startPage = 1
                endPage = doc.numberOfPages
            }
            collector.getText(doc)

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

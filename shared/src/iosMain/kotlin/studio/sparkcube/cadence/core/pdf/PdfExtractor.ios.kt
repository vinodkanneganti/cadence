package studio.sparkcube.cadence.core.pdf

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument

/**
 * iOS PDF extraction via PDFKit. PDFKit exposes page text (`PDFPage.string`) but not
 * per-glyph font metrics without heavier interop, so this v1 infers structure from
 * the text itself: a short line without terminal punctuation (or starting with a
 * digit) is treated as a heading (tagged with a larger synthetic height so the pure
 * [PdfStructure] detects it as a SECTION), and blank lines become paragraph gaps.
 *
 * All structure/sentence logic stays in the pure [PdfStructure] / [SentenceSplitter].
 */
@OptIn(ExperimentalForeignApi::class)
actual class PdfExtractor actual constructor() {

    actual suspend fun extract(bytes: ByteArray): ExtractResult = withContext(Dispatchers.Default) {
        val data = bytes.toNSData()
        val doc = PDFDocument(data) ?: return@withContext ExtractResult(emptyList(), false, emptyList(), 0)
        val pageCount = doc.pageCount.toInt()

        val lines = mutableListOf<PdfLine>()
        var y = 0.0
        for (i in 0 until pageCount) {
            val page = doc.pageAtIndex(i.toULong()) ?: continue
            val text = page.string ?: continue
            for (raw in text.split("\n")) {
                val line = raw.trim()
                if (line.isEmpty()) {
                    y += 30.0 // blank line → paragraph gap
                    continue
                }
                val words = line.split(Regex("\\s+")).size
                val endsSentence = line.endsWith(".") || line.endsWith("!") || line.endsWith("?")
                val looksHeading = words < 12 && (!endsSentence || line.first().isDigit())
                lines += PdfLine(line, if (looksHeading) 20.0 else 12.0, y, i + 1)
                y += 12.0
            }
            y += 24.0
        }

        val hasText = lines.isNotEmpty()
        val built = if (hasText) PdfStructure.build(lines) else BuildResult(emptyList(), emptyList())
        ExtractResult(built.units, hasText, built.pages, pageCount)
    }

    private fun ByteArray.toNSData(): NSData {
        if (isEmpty()) return NSData()
        return usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }
}

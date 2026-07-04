package studio.sparkcube.cadence.core.pdf

import studio.sparkcube.cadence.core.model.Boundary
import studio.sparkcube.cadence.core.model.Unit

/**
 * Turns positioned PDF lines into ordered, boundary-tagged reading [Unit]s, plus a
 * parallel page list. Pure; stdlib only, so it is exhaustively unit-testable.
 *
 * Heuristics (PRD R1 / T4):
 *  - Heading → SECTION: line height ≥ 1.25× the page median AND < 12 words.
 *  - Paragraph break: a vertical gap larger than ~1.6× the median line advance.
 *  - Within a paragraph, each sentence is a SENTENCE unit except the last, which
 *    carries the PARAGRAPH boundary.
 */
object PdfStructure {

    private const val HEADING_HEIGHT_RATIO = 1.25
    private const val HEADING_MAX_WORDS = 12
    private const val PARA_GAP_RATIO = 1.6

    fun build(lines: List<PdfLine>): BuildResult {
        val clean = lines.filter { it.text.isNotBlank() }
        if (clean.isEmpty()) return BuildResult(emptyList(), emptyList())

        val medianHeight = median(clean.map { it.height })
        val medianGap = median(lineGaps(clean)).takeIf { it > 0.0 } ?: medianHeight

        val units = mutableListOf<Unit>()
        val pages = mutableListOf<Int>()
        val paragraph = StringBuilder()
        var paragraphPage = clean.first().page

        fun flushParagraph() {
            val text = paragraph.toString().trim()
            paragraph.clear()
            if (text.isEmpty()) return
            val sentences = SentenceSplitter.split(text)
            sentences.forEachIndexed { idx, s ->
                val boundary = if (idx == sentences.lastIndex) Boundary.PARAGRAPH else Boundary.SENTENCE
                units += Unit(s, boundary)
                pages += paragraphPage
            }
        }

        clean.forEachIndexed { index, line ->
            val words = line.text.trim().split(Regex("\\s+")).size
            val isHeading = line.height >= HEADING_HEIGHT_RATIO * medianHeight && words < HEADING_MAX_WORDS

            if (isHeading) {
                flushParagraph()
                units += Unit(line.text.trim(), Boundary.SECTION)
                pages += line.page
                return@forEachIndexed
            }

            if (paragraph.isNotEmpty()) {
                val gap = line.y - clean[index - 1].y
                if (gap > PARA_GAP_RATIO * medianGap) flushParagraph()
            }
            if (paragraph.isEmpty()) paragraphPage = line.page
            else paragraph.append(' ')
            paragraph.append(line.text.trim())
        }
        flushParagraph()
        return BuildResult(units, pages)
    }

    /** Index of the first unit at or after [page]; clamps to the last unit. */
    fun firstUnitIndexForPage(pages: List<Int>, page: Int): Int {
        if (pages.isEmpty()) return 0
        val idx = pages.indexOfFirst { it >= page }
        return if (idx >= 0) idx else pages.lastIndex
    }

    private fun lineGaps(lines: List<PdfLine>): List<Double> =
        lines.zipWithNext { a, b -> b.y - a.y }.filter { it > 0.0 }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}

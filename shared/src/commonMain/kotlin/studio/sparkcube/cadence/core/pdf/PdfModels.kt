package studio.sparkcube.cadence.core.pdf

import studio.sparkcube.cadence.core.model.Unit

/**
 * One extracted line of text with the metrics the structure heuristics need:
 * [height] (a font-size proxy, for heading detection), [y] (top-down position,
 * for paragraph-gap detection), and the 1-based [page] it came from.
 */
data class PdfLine(val text: String, val height: Double, val y: Double, val page: Int = 1)

/**
 * Result of building structure: ordered reading [units] and a parallel [pages]
 * list giving the 1-based source page of each unit.
 */
data class BuildResult(val units: List<Unit>, val pages: List<Int>)

/**
 * Result of extracting a PDF: ordered reading [units], their per-unit [pages],
 * the document [pageCount], and whether a text layer existed.
 */
data class ExtractResult(
    val units: List<Unit>,
    val hasTextLayer: Boolean,
    val pages: List<Int> = emptyList(),
    val pageCount: Int = 0,
)

package studio.sparkcube.cadence.core.pdf

import studio.sparkcube.cadence.core.model.Unit

/**
 * One extracted line of text with the two metrics the structure heuristics need:
 * [height] (a font-size proxy, for heading detection) and [y] (top-down position,
 * for paragraph-gap detection). Produced by the platform PdfExtractor, consumed by
 * the pure [PdfStructure] builder.
 */
data class PdfLine(val text: String, val height: Double, val y: Double)

/** Result of extracting a PDF: ordered reading [units] and whether a text layer existed. */
data class ExtractResult(val units: List<Unit>, val hasTextLayer: Boolean)

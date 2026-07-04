package studio.sparkcube.cadence.core.pdf

/**
 * The ONLY PDF platform boundary. No platform PDF API may be referenced elsewhere.
 * Reads a document's bytes and returns ordered reading units plus whether the PDF
 * carried a text layer (scanned/image-only PDFs return `hasTextLayer = false`).
 */
expect class PdfExtractor() {
    suspend fun extract(bytes: ByteArray): ExtractResult
}

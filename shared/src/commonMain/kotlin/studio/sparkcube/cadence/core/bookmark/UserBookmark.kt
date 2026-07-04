package studio.sparkcube.cadence.core.bookmark

/**
 * A user-created bookmark: an important spot the reader curates and can jump back
 * to. Distinct from [Bookmark] (the single automatic resume point per document) —
 * a document can have many of these. On-device only (PRD R8).
 *
 * @param id stable unique id for removal
 * @param label the user's name for the spot (falls back to a text snippet)
 * @param snippet a short preview of the text at that position
 * @param unitIndex reading unit to resume at
 * @param page 1-based source page
 */
data class UserBookmark(
    val id: String,
    val docId: String,
    val label: String,
    val snippet: String,
    val unitIndex: Int,
    val page: Int,
    val createdAt: Long,
)

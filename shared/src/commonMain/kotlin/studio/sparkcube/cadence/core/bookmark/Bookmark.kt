package studio.sparkcube.cadence.core.bookmark

/**
 * A saved reading position for a document, so a session can resume after restart.
 * On-device only (PRD R8): stored locally, never transmitted.
 *
 * @param docId stable identity of the document (desktop: absolute file path)
 * @param docName display name
 * @param filePath path to reopen on launch, if the platform can (desktop)
 * @param unitIndex the reading unit to resume at
 * @param updatedAt monotonic-ish ordering key so `loadLast` can pick the newest
 */
data class Bookmark(
    val docId: String,
    val docName: String,
    val filePath: String?,
    val unitIndex: Int,
    val updatedAt: Long,
)

/**
 * Persists reading positions. The only real implementation is the platform
 * file-backed store; tests and non-desktop targets use [NoopBookmarkStore].
 */
interface BookmarkStore {
    fun save(bookmark: Bookmark)
    fun loadForDoc(docId: String): Bookmark?
    fun loadLast(): Bookmark?
}

object NoopBookmarkStore : BookmarkStore {
    override fun save(bookmark: Bookmark) {}
    override fun loadForDoc(docId: String): Bookmark? = null
    override fun loadLast(): Bookmark? = null
}

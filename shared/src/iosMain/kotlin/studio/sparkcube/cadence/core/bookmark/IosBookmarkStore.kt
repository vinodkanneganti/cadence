package studio.sparkcube.cadence.core.bookmark

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * iOS bookmark store: two tab-separated files in the app's Documents directory.
 * On-device only. Reuses the pure [BookmarkCodec].
 */
@OptIn(ExperimentalForeignApi::class)
class IosBookmarkStore : BookmarkStore {

    private val dir: String =
        (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String)
            ?: NSTemporaryDirectory()
    private val file = "$dir/bookmarks.tsv"
    private val userFile = "$dir/user-bookmarks.tsv"

    override fun save(bookmark: Bookmark) {
        writeText(file, BookmarkCodec.encode(BookmarkCodec.upsert(readAll(), bookmark)))
    }

    override fun loadForDoc(docId: String): Bookmark? = readAll().firstOrNull { it.docId == docId }

    override fun loadLast(): Bookmark? = readAll().maxByOrNull { it.updatedAt }

    override fun listBookmarks(docId: String): List<UserBookmark> =
        readUser().filter { it.docId == docId }.sortedBy { it.unitIndex }

    override fun addBookmark(bookmark: UserBookmark) {
        writeText(userFile, BookmarkCodec.encodeUser(readUser().filterNot { it.id == bookmark.id } + bookmark))
    }

    override fun removeBookmark(id: String) {
        writeText(userFile, BookmarkCodec.encodeUser(readUser().filterNot { it.id == id }))
    }

    private fun readAll(): List<Bookmark> = readText(file)?.let { BookmarkCodec.decode(it) } ?: emptyList()
    private fun readUser(): List<UserBookmark> = readText(userFile)?.let { BookmarkCodec.decodeUser(it) } ?: emptyList()

    private fun readText(path: String): String? =
        NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)

    private fun writeText(path: String, text: String) {
        (text as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }
}

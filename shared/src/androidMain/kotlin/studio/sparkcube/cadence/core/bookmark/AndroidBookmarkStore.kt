package studio.sparkcube.cadence.core.bookmark

import studio.sparkcube.cadence.AndroidApp
import java.io.File

/**
 * Android bookmark store: two tab-separated files under the app's private
 * `filesDir` (resume point + user bookmark list). On-device only. Mirrors the
 * desktop `FileBookmarkStore`; the pure [BookmarkCodec] is shared.
 */
class AndroidBookmarkStore(
    private val file: File = File(AndroidApp.context.filesDir, "bookmarks.tsv"),
    private val userFile: File = File(AndroidApp.context.filesDir, "user-bookmarks.tsv"),
) : BookmarkStore {

    override fun save(bookmark: Bookmark) {
        runCatching { file.writeText(BookmarkCodec.encode(BookmarkCodec.upsert(readAll(), bookmark))) }
    }

    override fun loadForDoc(docId: String): Bookmark? = readAll().firstOrNull { it.docId == docId }

    override fun loadLast(): Bookmark? = readAll().maxByOrNull { it.updatedAt }

    override fun listBookmarks(docId: String): List<UserBookmark> =
        readUser().filter { it.docId == docId }.sortedBy { it.unitIndex }

    override fun addBookmark(bookmark: UserBookmark) {
        writeUser(readUser().filterNot { it.id == bookmark.id } + bookmark)
    }

    override fun removeBookmark(id: String) {
        writeUser(readUser().filterNot { it.id == id })
    }

    private fun readAll(): List<Bookmark> =
        runCatching { if (file.exists()) BookmarkCodec.decode(file.readText()) else emptyList() }
            .getOrDefault(emptyList())

    private fun readUser(): List<UserBookmark> =
        runCatching { if (userFile.exists()) BookmarkCodec.decodeUser(userFile.readText()) else emptyList() }
            .getOrDefault(emptyList())

    private fun writeUser(items: List<UserBookmark>) {
        runCatching { userFile.writeText(BookmarkCodec.encodeUser(items)) }
    }
}

package studio.sparkcube.cadence.core.bookmark

import java.io.File

/**
 * Desktop bookmark store: a single tab-separated file under the user's home
 * (`~/.cadence/bookmarks.tsv`). On-device only — nothing leaves the machine.
 * Reads/writes are best-effort; any I/O failure degrades to "no bookmark" rather
 * than disrupting reading.
 */
class FileBookmarkStore(
    private val file: File = File(System.getProperty("user.home"), ".cadence/bookmarks.tsv"),
    private val userFile: File = File(System.getProperty("user.home"), ".cadence/user-bookmarks.tsv"),
) : BookmarkStore {

    override fun save(bookmark: Bookmark) {
        runCatching {
            val updated = BookmarkCodec.upsert(readAll(), bookmark)
            file.parentFile?.mkdirs()
            file.writeText(BookmarkCodec.encode(updated))
        }
    }

    override fun loadForDoc(docId: String): Bookmark? =
        readAll().firstOrNull { it.docId == docId }

    override fun loadLast(): Bookmark? =
        readAll().maxByOrNull { it.updatedAt }

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
        runCatching {
            userFile.parentFile?.mkdirs()
            userFile.writeText(BookmarkCodec.encodeUser(items))
        }
    }
}

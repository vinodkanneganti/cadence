package studio.sparkcube.cadence.core.bookmark

import java.io.File

/**
 * Desktop bookmark store: a single tab-separated file under the user's home
 * (`~/.cadence/bookmarks.tsv`). On-device only — nothing leaves the machine.
 * Reads/writes are best-effort; any I/O failure degrades to "no bookmark" rather
 * than disrupting reading.
 */
class FileBookmarkStore(
    file: File = File(System.getProperty("user.home"), ".cadence/bookmarks.tsv"),
) : BookmarkStore {

    private val file = file

    override fun save(bookmark: Bookmark) {
        runCatching {
            val current = readAll()
            val updated = BookmarkCodec.upsert(current, bookmark)
            file.parentFile?.mkdirs()
            file.writeText(BookmarkCodec.encode(updated))
        }
    }

    override fun loadForDoc(docId: String): Bookmark? =
        readAll().firstOrNull { it.docId == docId }

    override fun loadLast(): Bookmark? =
        readAll().maxByOrNull { it.updatedAt }

    private fun readAll(): List<Bookmark> =
        runCatching { if (file.exists()) BookmarkCodec.decode(file.readText()) else emptyList() }
            .getOrDefault(emptyList())
}

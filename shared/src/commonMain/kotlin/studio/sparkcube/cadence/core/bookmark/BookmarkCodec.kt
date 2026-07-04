package studio.sparkcube.cadence.core.bookmark

/**
 * Pure serialization for the bookmark file — one tab-separated record per line:
 * `docId \t unitIndex \t updatedAt \t docName \t filePath`. Kept pure (stdlib only)
 * so the parsing/formatting is unit-testable; the platform store only does file I/O.
 *
 * `docId`, `docName`, and `filePath` are escaped to survive tabs/newlines.
 */
object BookmarkCodec {

    fun encode(bookmarks: Collection<Bookmark>): String =
        bookmarks.joinToString("\n") { b ->
            listOf(
                esc(b.docId),
                b.unitIndex.toString(),
                b.updatedAt.toString(),
                esc(b.docName),
                esc(b.filePath ?: ""),
            ).joinToString("\t")
        }

    fun decode(text: String): List<Bookmark> =
        text.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 5) return@mapNotNull null
                val index = parts[1].toIntOrNull() ?: return@mapNotNull null
                val updated = parts[2].toLongOrNull() ?: return@mapNotNull null
                Bookmark(
                    docId = unesc(parts[0]),
                    unitIndex = index,
                    updatedAt = updated,
                    docName = unesc(parts[3]),
                    filePath = unesc(parts[4]).ifEmpty { null },
                )
            }
            .toList()

    /** Upsert by docId, returning the new record set (newest wins per doc). */
    fun upsert(existing: List<Bookmark>, bookmark: Bookmark): List<Bookmark> =
        existing.filterNot { it.docId == bookmark.docId } + bookmark

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unesc(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> { sb.append('\t'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }
}

package studio.sparkcube.cadence.core.bookmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookmarkCodecTest {

    @Test
    fun roundTripsRecords() {
        val bookmarks = listOf(
            Bookmark("/a/b.pdf", "b.pdf", "/a/b.pdf", 12, 100),
            Bookmark("/c/d.pdf", "d.pdf", null, 0, 200),
        )
        val decoded = BookmarkCodec.decode(BookmarkCodec.encode(bookmarks))
        assertEquals(bookmarks, decoded)
    }

    @Test
    fun preservesTabsAndNewlinesInNames() {
        val b = Bookmark("id\twith\ttabs", "name\nwith\nnewlines", "/p", 3, 5)
        val decoded = BookmarkCodec.decode(BookmarkCodec.encode(listOf(b)))
        assertEquals(listOf(b), decoded)
    }

    @Test
    fun upsertReplacesSameDocKeepsOthers() {
        val existing = listOf(
            Bookmark("/a.pdf", "a", null, 1, 10),
            Bookmark("/b.pdf", "b", null, 2, 20),
        )
        val updated = BookmarkCodec.upsert(existing, Bookmark("/a.pdf", "a", null, 99, 30))
        assertEquals(2, updated.size)
        assertEquals(99, updated.first { it.docId == "/a.pdf" }.unitIndex)
        assertEquals(2, updated.first { it.docId == "/b.pdf" }.unitIndex)
    }

    @Test
    fun skipsMalformedLines() {
        val text = "not enough fields\n/a\t5\t10\tname\t/a\n\n"
        val decoded = BookmarkCodec.decode(text)
        assertEquals(1, decoded.size)
        assertEquals("/a", decoded[0].docId)
        assertEquals(5, decoded[0].unitIndex)
    }

    @Test
    fun userBookmarks_roundTripWithSpecialChars() {
        val items = listOf(
            UserBookmark("id1", "/a.pdf", "Key result", "The main theorem states…", 12, 3, 100),
            UserBookmark("id2", "/a.pdf", "tab\there\nand newline", "snip\tpet", 40, 8, 200),
        )
        val decoded = BookmarkCodec.decodeUser(BookmarkCodec.encodeUser(items))
        assertEquals(items, decoded)
    }

    @Test
    fun userBookmarks_skipMalformedLines() {
        val good = BookmarkCodec.encodeUser(listOf(UserBookmark("i", "/d", "L", "S", 5, 2, 9)))
        val decoded = BookmarkCodec.decodeUser("garbage line\n$good\n")
        assertEquals(1, decoded.size)
        assertEquals(5, decoded[0].unitIndex)
        assertEquals(2, decoded[0].page)
    }

    @Test
    fun loadLastPicksNewestByUpdatedAt() {
        val text = BookmarkCodec.encode(
            listOf(
                Bookmark("/old.pdf", "old", null, 1, 100),
                Bookmark("/new.pdf", "new", null, 4, 300),
            ),
        )
        val newest = BookmarkCodec.decode(text).maxByOrNull { it.updatedAt }
        assertEquals("/new.pdf", newest?.docId)
        assertNull(BookmarkCodec.decode("").maxByOrNull { it.updatedAt })
    }
}

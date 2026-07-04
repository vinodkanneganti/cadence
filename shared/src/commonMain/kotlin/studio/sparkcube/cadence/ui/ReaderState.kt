package studio.sparkcube.cadence.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import studio.sparkcube.cadence.core.bookmark.Bookmark
import studio.sparkcube.cadence.core.bookmark.BookmarkStore
import studio.sparkcube.cadence.core.bookmark.NoopBookmarkStore
import studio.sparkcube.cadence.core.bookmark.UserBookmark
import studio.sparkcube.cadence.core.model.Density
import studio.sparkcube.cadence.core.model.Mode
import studio.sparkcube.cadence.core.model.Step
import studio.sparkcube.cadence.core.model.Unit as ReadUnit
import studio.sparkcube.cadence.core.pacing.PacingEngine
import studio.sparkcube.cadence.core.pdf.PdfExtractor
import studio.sparkcube.cadence.core.pdf.PdfStructure
import studio.sparkcube.cadence.core.player.Player
import studio.sparkcube.cadence.core.recall.RecallScheduler
import studio.sparkcube.cadence.core.tts.Speaker
import studio.sparkcube.cadence.core.tts.VoiceInfo
import kotlin.time.TimeSource

/**
 * A picked document: display name, raw bytes, and (if the platform knows it) the
 * absolute file path — used as the stable bookmark id and to reopen on restart.
 */
class PickedPdf(val name: String, val bytes: ByteArray, val path: String? = null)

/**
 * Coordinates the engine, player, PDF extractor, and recall scheduler, and exposes
 * observable state to the Compose UI. Lives in commonMain; platform specifics enter
 * only through the [Speaker] / [PdfExtractor] boundaries and the picked bytes.
 */
class ReaderState(
    private val scope: CoroutineScope,
    private val speaker: Speaker,
    private val extractor: PdfExtractor,
    private val bookmarks: BookmarkStore = NoopBookmarkStore,
    private val now: () -> Long = { 0L },
) {
    var docName by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var hasTextLayer by mutableStateOf(true); private set
    var units by mutableStateOf<List<ReadUnit>>(emptyList()); private set
    var steps by mutableStateOf<List<Step>>(emptyList()); private set
    var activeIndex by mutableStateOf(0); private set
    var playing by mutableStateOf(false); private set
    var section by mutableStateOf(0); private set
    var elapsedSeconds by mutableStateOf(0L); private set
    var recallDue by mutableStateOf(false); private set
    var errorMessage by mutableStateOf<String?>(null); private set   // fatal: gates the reader
    var playbackNote by mutableStateOf<String?>(null); private set   // transient: a small banner

    var pageCount by mutableStateOf(0); private set
    var resumeHint by mutableStateOf<String?>(null); private set
    var bookmarkList by mutableStateOf<List<UserBookmark>>(emptyList()); private set
    private var pages: List<Int> = emptyList()
    private var docId: String? = null
    private var docPath: String? = null

    var density by mutableStateOf(Density.STANDARD); private set
    var mode by mutableStateOf(Mode.LEARNING); private set
    var basePaceOffset by mutableStateOf(0); private set

    // Appearance (on-screen only; independent of narration)
    var dark by mutableStateOf(false); private set
    var readingSizeSp by mutableStateOf(19f); private set
    var readingFont by mutableStateOf(ReadingFont.Serif); private set

    var voices by mutableStateOf<List<VoiceInfo>>(emptyList()); private set
    var selectedVoice by mutableStateOf<VoiceInfo?>(null); private set
    var sayAvailable by mutableStateOf(true); private set

    val currentWpm: Int get() = steps.getOrNull(activeIndex)?.targetWpm ?: 0
    val progress: Float get() = if (steps.isEmpty()) 0f else (activeIndex + 1f) / steps.size
    val currentPage: Int get() = pages.getOrNull(activeIndex) ?: 1

    private val player = Player(speaker, scope)
    private var startMark = TimeSource.Monotonic.markNow()
    private var ticker: Job? = null

    private val recall = RecallScheduler(
        nowMs = { startMark.elapsedNow().inWholeMilliseconds },
        onRecallDue = {
            player.pause(); playing = false; recallDue = true; stopTicker()
        },
    )

    init {
        player.onUnitStart = { activeIndex = it }
        player.onSectionBoundary = { n -> section = n; recall.onSectionBoundary() }
        player.onFinished = { playing = false; stopTicker() }
        // A speech error must NOT tear down the reader — surface it as a transient note.
        player.onError = { msg -> playing = false; stopTicker(); playbackNote = msg }

        val vs = speaker.voices()
        voices = vs.sortedWith(
            compareByDescending<VoiceInfo> { it.locale.startsWith("en") }.thenBy { it.name },
        )
        sayAvailable = vs.isNotEmpty()
    }

    fun open(picked: PickedPdf) {
        scope.launch {
            loading = true
            errorMessage = null
            player.pause(); playing = false; stopTicker()
            try {
                val result = extractor.extract(picked.bytes)
                docName = picked.name
                docId = picked.path ?: picked.name
                docPath = picked.path
                hasTextLayer = result.hasTextLayer
                units = result.units
                pages = result.pages
                pageCount = result.pageCount
                section = 0; activeIndex = 0; elapsedSeconds = 0
                recallDue = false; resumeHint = null
                startMark = TimeSource.Monotonic.markNow()
                recall.reset()
                rebuild()
                restoreBookmark()
                refreshBookmarkList()
            } catch (e: Throwable) {
                docName = picked.name
                errorMessage = "Couldn't open this PDF: ${e.message ?: "unknown error"}"
                hasTextLayer = true; units = emptyList(); steps = emptyList()
            } finally {
                loading = false
            }
        }
    }

    fun togglePlay() {
        if (recallDue || steps.isEmpty()) return
        if (player.isPlaying) {
            player.pause(); playing = false; stopTicker(); saveBookmark()
        } else {
            resumeHint = null; playbackNote = null
            player.play(); playing = true; startTicker()
        }
    }

    fun next() { player.next(); playing = player.isPlaying; clearHintAndSave() }
    fun prev() { player.prev(); playing = player.isPlaying; clearHintAndSave() }
    fun nextSection() { player.nextSection(); playing = player.isPlaying; clearHintAndSave() }

    /** Jump so playback begins at the first unit of [page] (1-based). */
    fun jumpToPage(page: Int) {
        if (steps.isEmpty()) return
        val target = PdfStructure.firstUnitIndexForPage(pages, page.coerceIn(1, maxOf(1, pageCount)))
        player.seekTo(target)
        activeIndex = target
        playing = false
        stopTicker()
        resumeHint = null
        saveBookmark()
    }

    fun selectDensity(d: Density) { density = d; rebuildPreservingPause() }
    fun selectMode(m: Mode) { mode = m; rebuildPreservingPause() }
    fun nudgeBasePace(offset: Int) { basePaceOffset = offset.coerceIn(-25, 25); rebuildPreservingPause() }
    fun chooseVoice(v: VoiceInfo) { selectedVoice = v; speaker.setVoice(v.id) }

    fun toggleDark() { dark = !dark }
    fun setReadingSize(sp: Float) { readingSizeSp = sp.coerceIn(15f, 26f) }
    fun selectReadingFont(f: ReadingFont) { readingFont = f }

    /** Re-query installed voices (Android TTS reports them only after async init). */
    fun refreshVoices() {
        val vs = speaker.voices()
        if (vs.isNotEmpty()) {
            voices = vs.sortedWith(
                compareByDescending<VoiceInfo> { it.locale.startsWith("en") }.thenBy { it.name },
            )
        }
    }

    fun previewVoice() {
        player.pause(); playing = false; stopTicker()
        speaker.speak("This is the selected voice.", targetWpm = 160, onDone = {}, onError = {})
    }

    /** Add a bookmark at the current position; [label] blank → uses a text snippet. */
    fun addBookmark(label: String) {
        val id = docId ?: return
        if (steps.isEmpty()) return
        val snippet = steps.getOrNull(activeIndex)?.text?.take(80) ?: ""
        val name = label.trim().ifBlank { snippet.take(40).ifBlank { "Page $currentPage" } }
        bookmarks.addBookmark(
            UserBookmark(
                id = "${now()}-$activeIndex",
                docId = id,
                label = name,
                snippet = snippet,
                unitIndex = activeIndex,
                page = currentPage,
                createdAt = now(),
            ),
        )
        refreshBookmarkList()
    }

    fun jumpToBookmark(b: UserBookmark) {
        if (steps.isEmpty()) return
        player.seekTo(b.unitIndex.coerceIn(0, steps.size - 1))
        activeIndex = player.index
        playing = false
        stopTicker()
        resumeHint = null
    }

    fun removeBookmark(b: UserBookmark) {
        bookmarks.removeBookmark(b.id)
        refreshBookmarkList()
    }

    private fun refreshBookmarkList() {
        bookmarkList = docId?.let { bookmarks.listBookmarks(it) } ?: emptyList()
    }

    fun continueRecall() {
        recallDue = false
        recall.continueReading()
        player.play(); playing = true; startTicker()
    }

    private fun clearHintAndSave() {
        resumeHint = null
        saveBookmark()
    }

    private fun restoreBookmark() {
        val id = docId ?: return
        val mark = bookmarks.loadForDoc(id) ?: return
        if (mark.unitIndex in steps.indices && mark.unitIndex > 0) {
            player.seekTo(mark.unitIndex)
            activeIndex = mark.unitIndex
            resumeHint = "Resumed at page ${currentPage} of $pageCount"
        }
    }

    private fun saveBookmark() {
        val id = docId ?: return
        val name = docName ?: return
        if (steps.isEmpty()) return
        bookmarks.save(
            Bookmark(
                docId = id,
                docName = name,
                filePath = docPath,
                unitIndex = activeIndex,
                updatedAt = now(),
            ),
        )
    }

    private fun rebuild() {
        steps = if (units.isEmpty()) emptyList()
        else PacingEngine.schedule(units, density, mode, basePaceOffset)
        player.load(steps)
        activeIndex = 0
    }

    /** Changing settings re-schedules; that stops playback, so reflect the pause. */
    private fun rebuildPreservingPause() {
        val idx = activeIndex.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
        steps = if (units.isEmpty()) emptyList()
        else PacingEngine.schedule(units, density, mode, basePaceOffset)
        player.load(steps)
        activeIndex = idx.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
        playing = false
        stopTicker()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive && player.isPlaying) {
                delay(1000)
                if (player.isPlaying) {
                    elapsedSeconds += 1
                    recall.onTick()
                    if (elapsedSeconds % 5 == 0L) saveBookmark() // periodic checkpoint
                }
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }
}

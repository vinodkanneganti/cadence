package studio.sparkcube.cadence.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import studio.sparkcube.cadence.core.model.Density
import studio.sparkcube.cadence.core.model.Mode
import studio.sparkcube.cadence.core.model.Step
import studio.sparkcube.cadence.core.model.Unit as ReadUnit
import studio.sparkcube.cadence.core.pacing.PacingEngine
import studio.sparkcube.cadence.core.pdf.PdfExtractor
import studio.sparkcube.cadence.core.player.Player
import studio.sparkcube.cadence.core.recall.RecallScheduler
import studio.sparkcube.cadence.core.tts.Speaker
import studio.sparkcube.cadence.core.tts.VoiceInfo
import kotlin.time.TimeSource

/** A picked document: display name + raw bytes (supplied by the platform file picker). */
class PickedPdf(val name: String, val bytes: ByteArray)

/**
 * Coordinates the engine, player, PDF extractor, and recall scheduler, and exposes
 * observable state to the Compose UI. Lives in commonMain; platform specifics enter
 * only through the [Speaker] / [PdfExtractor] boundaries and the picked bytes.
 */
class ReaderState(
    private val scope: CoroutineScope,
    private val speaker: Speaker,
    private val extractor: PdfExtractor,
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
    var errorMessage by mutableStateOf<String?>(null); private set

    var density by mutableStateOf(Density.STANDARD); private set
    var mode by mutableStateOf(Mode.LEARNING); private set
    var basePaceOffset by mutableStateOf(0); private set

    var voices by mutableStateOf<List<VoiceInfo>>(emptyList()); private set
    var selectedVoice by mutableStateOf<VoiceInfo?>(null); private set
    var sayAvailable by mutableStateOf(true); private set

    val currentWpm: Int get() = steps.getOrNull(activeIndex)?.targetWpm ?: 0
    val progress: Float get() = if (steps.isEmpty()) 0f else (activeIndex + 1f) / steps.size

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
        player.onError = { msg -> playing = false; errorMessage = msg; stopTicker() }

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
                hasTextLayer = result.hasTextLayer
                units = result.units
                section = 0; activeIndex = 0; elapsedSeconds = 0
                recallDue = false
                startMark = TimeSource.Monotonic.markNow()
                recall.reset()
                rebuild()
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
            player.pause(); playing = false; stopTicker()
        } else {
            player.play(); playing = true; startTicker()
        }
    }

    fun next() { player.next(); playing = player.isPlaying }
    fun prev() { player.prev(); playing = player.isPlaying }
    fun nextSection() { player.nextSection(); playing = player.isPlaying }

    fun selectDensity(d: Density) { density = d; rebuildPreservingPause() }
    fun selectMode(m: Mode) { mode = m; rebuildPreservingPause() }
    fun nudgeBasePace(offset: Int) { basePaceOffset = offset.coerceIn(-25, 25); rebuildPreservingPause() }
    fun chooseVoice(v: VoiceInfo) { selectedVoice = v; speaker.setVoice(v.id) }

    fun previewVoice() {
        player.pause(); playing = false; stopTicker()
        speaker.speak("This is the selected voice.", targetWpm = 160, onDone = {}, onError = {})
    }

    fun continueRecall() {
        recallDue = false
        recall.continueReading()
        player.play(); playing = true; startTicker()
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
                }
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }
}

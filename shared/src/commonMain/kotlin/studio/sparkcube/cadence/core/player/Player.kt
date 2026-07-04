package studio.sparkcube.cadence.core.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import studio.sparkcube.cadence.core.model.Boundary
import studio.sparkcube.cadence.core.model.Step
import studio.sparkcube.cadence.core.tts.SpeechOutput
import kotlin.coroutines.resume

/**
 * Walks a [Step] schedule: speak → onDone → wait `pauseMsAfter` → next.
 * The engine owns the pauses; this just honours them. Platform-agnostic:
 * all speech goes through [Speaker], all timing through coroutines.
 *
 * Callbacks let the UI follow along ([onUnitStart]) and let the recall
 * scheduler subscribe to section boundaries ([onSectionBoundary]).
 */
class Player(
    private val speaker: SpeechOutput,
    private val scope: CoroutineScope,
) {
    var steps: List<Step> = emptyList()
        private set

    /** Index of the current unit. */
    var index: Int = 0
        private set

    var isPlaying: Boolean = false
        private set

    var onUnitStart: (Int) -> Unit = {}
    var onSectionBoundary: (Int) -> Unit = {}
    var onFinished: () -> Unit = {}
    var onError: (String) -> Unit = {}

    private var job: Job? = null
    private var sectionCount = 0

    fun load(steps: List<Step>) {
        stopInternal()
        this.steps = steps
        index = 0
        sectionCount = 0
    }

    fun play() {
        if (isPlaying || steps.isEmpty()) return
        isPlaying = true
        job = scope.launch { runFrom(index) }
    }

    /** Pause holds at the current unit; play() resumes by re-speaking it. */
    fun pause() {
        isPlaying = false
        job?.cancel()
        job = null
        speaker.stop()
    }

    fun toggle() = if (isPlaying) pause() else play()

    fun next() = skipTo(index + 1)
    fun prev() = skipTo(index - 1)

    /** Move the playhead to [target] without auto-playing (used by page-jump / resume). */
    fun seekTo(target: Int) {
        if (steps.isEmpty()) return
        pause()
        index = target.coerceIn(0, steps.size - 1)
        onUnitStart(index)
    }

    fun nextSection() {
        val target = (index + 1 until steps.size)
            .firstOrNull { steps[it].boundary == Boundary.SECTION }
        if (target != null) skipTo(target)
    }

    private fun skipTo(target: Int) {
        if (steps.isEmpty()) return
        val wasPlaying = isPlaying
        pause()
        index = target.coerceIn(0, steps.size - 1)
        onUnitStart(index)
        if (wasPlaying) play()
    }

    private suspend fun runFrom(start: Int) {
        var i = start
        while (isPlaying && i < steps.size) {
            index = i
            val step = steps[i]
            onUnitStart(i)

            val spoken = speakSuspending(step)
            if (!spoken || !isPlaying) return

            if (step.boundary == Boundary.SECTION) {
                sectionCount++
                onSectionBoundary(sectionCount)
            }
            delay(step.pauseMsAfter.toLong())
            if (!isPlaying) return
            i++
        }
        if (i >= steps.size && isPlaying) {
            isPlaying = false
            onFinished()
        }
    }

    private suspend fun speakSuspending(step: Step): Boolean =
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { speaker.stop() }
            speaker.speak(
                text = step.text,
                targetWpm = step.targetWpm,
                onDone = { if (cont.isActive) cont.resume(true) },
                onError = { msg ->
                    onError(msg)
                    if (cont.isActive) cont.resume(false)
                },
            )
        }

    private fun stopInternal() {
        isPlaying = false
        job?.cancel()
        job = null
        speaker.stop()
    }
}

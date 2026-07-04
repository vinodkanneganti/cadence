package studio.sparkcube.cadence.core.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import studio.sparkcube.cadence.core.model.Boundary
import studio.sparkcube.cadence.core.model.Density
import studio.sparkcube.cadence.core.model.Mode
import studio.sparkcube.cadence.core.model.Unit
import studio.sparkcube.cadence.core.pacing.PacingEngine
import studio.sparkcube.cadence.core.tts.SpeechOutput
import studio.sparkcube.cadence.core.tts.VoiceInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Records what it was asked to speak; completes utterances on demand. */
private class FakeSpeech(val autoComplete: Boolean = true) : SpeechOutput {
    val spoken = mutableListOf<Pair<String, Int>>()
    var stops = 0
    private var pending: (() -> kotlin.Unit)? = null

    override fun voices() = listOf(VoiceInfo("test", "Test", "en_US"))
    override fun setVoice(id: String) {}
    override fun speak(text: String, targetWpm: Int, onDone: () -> kotlin.Unit, onError: (String) -> kotlin.Unit) {
        spoken += text to targetWpm
        if (autoComplete) onDone() else pending = onDone
    }
    fun complete() { pending?.invoke(); pending = null }
    override fun stop() { stops++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerTest {

    private val doc = listOf(
        Unit("First sentence here.", Boundary.SENTENCE),
        Unit("End of paragraph now.", Boundary.PARAGRAPH),
        Unit("Section two.", Boundary.SECTION),
        Unit("Another line follows.", Boundary.SENTENCE),
        Unit("Section three.", Boundary.SECTION),
    )

    @Test
    fun walksEveryStepInOrder_andCountsSections() = runTest {
        val speech = FakeSpeech()
        val player = Player(speech, this)
        val starts = mutableListOf<Int>()
        val sections = mutableListOf<Int>()
        player.onUnitStart = { starts += it }
        player.onSectionBoundary = { sections += it }

        val steps = PacingEngine.schedule(doc, Density.STANDARD, Mode.LEARNING)
        player.load(steps)
        player.play()
        advanceUntilIdle()

        assertEquals(listOf(0, 1, 2, 3, 4), starts, "every unit should start once, in order")
        assertEquals(steps.map { it.text }, speech.spoken.map { it.first }, "spoken text in order")
        assertEquals(listOf(1, 2), sections, "two SECTION boundaries, counted 1 then 2")
        assertFalse(player.isPlaying, "playback should end after the last step")
    }

    @Test
    fun honorsInterUnitPauses_inVirtualTime() = runTest {
        val speech = FakeSpeech()
        val player = Player(speech, this)
        val steps = PacingEngine.schedule(doc, Density.STANDARD, Mode.LEARNING)
        player.load(steps)

        val start = testScheduler.currentTime
        player.play()
        advanceUntilIdle()
        val elapsed = testScheduler.currentTime - start

        // Fake speech is instantaneous, so all elapsed virtual time is the pauses.
        assertEquals(steps.sumOf { it.pauseMsAfter.toLong() }, elapsed)
    }

    @Test
    fun pauseStopsSpeechAndHalts() = runTest {
        val speech = FakeSpeech(autoComplete = false) // hold on the first utterance
        val player = Player(speech, this)
        player.load(PacingEngine.schedule(doc, Density.STANDARD, Mode.LEARNING))

        player.play()
        advanceUntilIdle()
        assertEquals(1, speech.spoken.size, "should be speaking the first unit and waiting")

        player.pause()
        assertFalse(player.isPlaying)
        assertTrue(speech.stops >= 1, "pause must stop the speaker")
    }

    @Test
    fun nextSkipsToTheFollowingUnit() = runTest {
        val speech = FakeSpeech(autoComplete = false)
        val player = Player(speech, this)
        player.load(PacingEngine.schedule(doc, Density.STANDARD, Mode.LEARNING))

        player.play()
        advanceUntilIdle()
        player.next()
        advanceUntilIdle()

        assertEquals(1, player.index, "next() advances the playhead")
        assertEquals(doc[1].text, speech.spoken.last().first, "and speaks the next unit")

        player.pause() // cancel the still-suspended utterance before the test ends
    }

    @Test
    fun nextSectionJumpsToUpcomingSectionBoundary() = runTest {
        val speech = FakeSpeech(autoComplete = false)
        val player = Player(speech, this)
        player.load(PacingEngine.schedule(doc, Density.STANDARD, Mode.LEARNING))

        player.play()
        advanceUntilIdle()
        player.nextSection() // from index 0, next SECTION is index 2
        advanceUntilIdle()

        assertEquals(2, player.index)
        assertEquals(doc[2].text, speech.spoken.last().first)

        player.pause() // cancel the still-suspended utterance before the test ends
    }
}

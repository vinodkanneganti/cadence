package studio.sparkcube.cadence.core.recall

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecallSchedulerTest {

    private class Clock(var now: Long = 0L) { fun read() = now }

    // AC5: recall = 2 sections → fires on the 2nd section boundary.
    @Test
    fun firesAfterTwoSections() {
        val clock = Clock()
        var fired = 0
        val s = RecallScheduler(everyNSections = 2, nowMs = clock::read, onRecallDue = { fired++ })

        s.onSectionBoundary()
        assertFalse(s.isDue, "not due after 1 section")
        assertEquals(0, fired)

        s.onSectionBoundary()
        assertTrue(s.isDue, "due after 2 sections")
        assertEquals(1, fired)
    }

    @Test
    fun firesAfterElapsedTime() {
        val clock = Clock()
        var fired = 0
        val s = RecallScheduler(
            everyNSections = 99,
            everyMillis = 5 * 60_000L,
            nowMs = clock::read,
            onRecallDue = { fired++ },
        )

        clock.now = 4 * 60_000L
        s.onTick()
        assertFalse(s.isDue, "not due before 5 min")

        clock.now = 5 * 60_000L
        s.onTick()
        assertTrue(s.isDue, "due at 5 min")
        assertEquals(1, fired)
    }

    @Test
    fun whicheverFiresFirstWins_sectionsBeforeTime() {
        val clock = Clock()
        var fired = 0
        val s = RecallScheduler(everyNSections = 2, everyMillis = 5 * 60_000L, nowMs = clock::read, onRecallDue = { fired++ })

        clock.now = 60_000L // 1 min in — time trigger not yet reached
        s.onSectionBoundary()
        s.onSectionBoundary()
        assertTrue(s.isDue)
        assertEquals(1, fired, "sections fired it once; time trigger must not double-fire")

        // Further triggers while due are ignored.
        clock.now = 10 * 60_000L
        s.onTick()
        s.onSectionBoundary()
        assertEquals(1, fired)
    }

    @Test
    fun continueResetsAndCanFireAgain() {
        val clock = Clock()
        var fired = 0
        val s = RecallScheduler(everyNSections = 2, nowMs = clock::read, onRecallDue = { fired++ })

        s.onSectionBoundary(); s.onSectionBoundary()
        assertTrue(s.isDue)
        assertEquals(1, fired)

        s.continueReading()
        assertFalse(s.isDue, "no longer due after continue")

        s.onSectionBoundary()
        assertEquals(1, fired, "counter reset — one section isn't enough yet")
        s.onSectionBoundary()
        assertTrue(s.isDue)
        assertEquals(2, fired, "fires again after another 2 sections")
    }

    @Test
    fun timeTriggerCountsFromLastContinue() {
        val clock = Clock()
        var fired = 0
        val s = RecallScheduler(everyNSections = 99, everyMillis = 5 * 60_000L, nowMs = clock::read, onRecallDue = { fired++ })

        clock.now = 5 * 60_000L
        s.onTick()
        assertTrue(s.isDue)

        clock.now = 6 * 60_000L
        s.continueReading()          // resets lastRecallAt to now (6 min)
        clock.now = 10 * 60_000L     // only 4 min since continue
        s.onTick()
        assertFalse(s.isDue, "4 min since continue < 5 min")

        clock.now = 11 * 60_000L     // 5 min since continue
        s.onTick()
        assertTrue(s.isDue)
        assertEquals(2, fired)
    }
}

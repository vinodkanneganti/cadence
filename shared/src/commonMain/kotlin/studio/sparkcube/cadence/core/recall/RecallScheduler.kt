package studio.sparkcube.cadence.core.recall

import studio.sparkcube.cadence.core.pacing.RecallConfig

/**
 * Decides when an active-recall prompt is due: every N section boundaries OR every
 * M minutes, whichever fires first (PRD R5). Pure and clock-injectable so it is
 * fully unit-testable; the app wires [onRecallDue] to pause the player and show the
 * recall overlay, and calls [continueReading] when the user dismisses it.
 *
 * Drive the time trigger by calling [onTick] periodically (e.g. on each unit start
 * or from a 1 Hz ticker). Once due, further triggers are ignored until the reader
 * continues.
 */
class RecallScheduler(
    private val everyNSections: Int = RecallConfig.EVERY_N_SECTIONS,
    private val everyMillis: Long = RecallConfig.EVERY_MINUTES * 60_000L,
    private val nowMs: () -> Long,
    private val onRecallDue: () -> Unit,
) {
    private var sectionsSinceRecall = 0
    private var lastRecallAt: Long = nowMs()
    private var due = false

    val isDue: Boolean get() = due

    /** Notify that a SECTION boundary was crossed. */
    fun onSectionBoundary() {
        if (due) return
        sectionsSinceRecall++
        if (sectionsSinceRecall >= everyNSections) fire()
    }

    /** Periodic check of the elapsed-time trigger. */
    fun onTick() {
        if (due) return
        if (nowMs() - lastRecallAt >= everyMillis) fire()
    }

    /** User dismissed the recall overlay; resume counting from now. */
    fun continueReading() {
        due = false
        sectionsSinceRecall = 0
        lastRecallAt = nowMs()
    }

    /** Reset all counters (e.g. when a new document is loaded). */
    fun reset() = continueReading()

    private fun fire() {
        due = true
        onRecallDue()
    }
}

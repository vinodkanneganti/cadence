package studio.sparkcube.cadence.core.pacing

import studio.sparkcube.cadence.core.model.Boundary
import studio.sparkcube.cadence.core.model.Density
import studio.sparkcube.cadence.core.model.Mode
import studio.sparkcube.cadence.core.model.Unit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacingEngineTest {

    // --- Seed fixtures (PRD §4 "Engine unit-test seeds (T1)") -----------------

    /** 26 long, technical words → complexity ≥ 0.7 → slow. */
    private val denseSentence = Unit(
        "Backpropagation systematically differentiates multidimensional computational " +
            "architectures, propagating approximated gradient information throughout " +
            "interconnected representational subsystems, enabling optimization algorithms " +
            "iteratively minimizing regularized objective functionals across distributions.",
        Boundary.SENTENCE,
    )

    /** 6 short, plain words → complexity ≤ 0.25 → fast. */
    private val lightSentence = Unit("The cat sat on the mat.", Boundary.SENTENCE)

    // --- T1 seed assertions ---------------------------------------------------

    @Test
    fun denseSeed_scoresHigh_andReadsSlow() {
        val c = complexityOf(denseSentence.text)
        assertTrue(c >= 0.7, "dense complexity should be ≥ 0.7, was $c")

        val step = PacingEngine.schedule(listOf(denseSentence), Density.STANDARD, Mode.LEARNING).single()
        assertTrue(step.targetWpm <= 130, "dense targetWpm should be ≤ 130, was ${step.targetWpm}")
    }

    @Test
    fun lightSeed_scoresLow_andReadsFast() {
        val c = complexityOf(lightSentence.text)
        assertTrue(c <= 0.25, "light complexity should be ≤ 0.25, was $c")

        val step = PacingEngine.schedule(listOf(lightSentence), Density.STANDARD, Mode.LEARNING).single()
        assertTrue(step.targetWpm >= 190, "light targetWpm should be ≥ 190, was ${step.targetWpm}")
    }

    // --- AC2: STANDARD + LEARNING → every unit's targetWpm ∈ [110, 225] -------

    @Test
    fun ac2_standardLearning_staysWithinBand() {
        val units = listOf(
            denseSentence,
            lightSentence,
            Unit("It is the workhorse of modern deep learning.", Boundary.PARAGRAPH),
            Unit("2  Optimization", Boundary.SECTION),
        )
        val steps = PacingEngine.schedule(units, Density.STANDARD, Mode.LEARNING)
        steps.forEach {
            assertTrue(
                it.targetWpm in 110..225,
                "targetWpm ${it.targetWpm} out of [110,225] for \"${it.text.take(30)}…\"",
            )
        }
    }

    // --- AC3: dense unit reads slower than light unit -------------------------

    @Test
    fun ac3_denseSlowerThanLight() {
        val steps = PacingEngine.schedule(
            listOf(denseSentence, lightSentence), Density.STANDARD, Mode.LEARNING,
        )
        assertTrue(
            steps[0].targetWpm < steps[1].targetWpm,
            "dense ${steps[0].targetWpm} should be < light ${steps[1].targetWpm}",
        )
    }

    // --- AC4: pause ordering SECTION > PARAGRAPH > SENTENCE > 0 ----------------

    @Test
    fun ac4_pauseOrdering_learning() {
        val steps = PacingEngine.schedule(
            listOf(
                Unit("a", Boundary.SENTENCE),
                Unit("b", Boundary.PARAGRAPH),
                Unit("c", Boundary.SECTION),
            ),
            Density.STANDARD, Mode.LEARNING,
        )
        val sentence = steps[0].pauseMsAfter
        val paragraph = steps[1].pauseMsAfter
        val section = steps[2].pauseMsAfter
        assertTrue(section > paragraph, "section $section should exceed paragraph $paragraph")
        assertTrue(paragraph > sentence, "paragraph $paragraph should exceed sentence $sentence")
        assertTrue(sentence > 0, "sentence pause should be > 0, was $sentence")
        // Exact LEARNING values (scale 1.0).
        assertEquals(250, sentence)
        assertEquals(700, paragraph)
        assertEquals(1500, section)
    }

    @Test
    fun ac4_pauseOrdering_freeIsScaledButStillOrdered() {
        val steps = PacingEngine.schedule(
            listOf(
                Unit("a", Boundary.SENTENCE),
                Unit("b", Boundary.PARAGRAPH),
                Unit("c", Boundary.SECTION),
            ),
            Density.STANDARD, Mode.FREE,
        )
        assertTrue(steps[2].pauseMsAfter > steps[1].pauseMsAfter)
        assertTrue(steps[1].pauseMsAfter > steps[0].pauseMsAfter)
        assertTrue(steps[0].pauseMsAfter > 0)
        // FREE scale 0.4.
        assertEquals(100, steps[0].pauseMsAfter)
        assertEquals(280, steps[1].pauseMsAfter)
        assertEquals(600, steps[2].pauseMsAfter)
    }

    // --- AC10: cap wins over user (max base-pace still ≤ 225 in STANDARD) ------

    @Test
    fun ac10_learningCapWinsOverUser() {
        // The lightest possible unit would push toward the cap; LEARNING must clamp.
        val step = PacingEngine.schedule(listOf(lightSentence), Density.STANDARD, Mode.LEARNING).single()
        assertTrue(step.targetWpm <= 225, "learning cap should hold, was ${step.targetWpm}")
    }

    @Test
    fun basePaceOffset_liftsRateButCapStillWins() {
        val preset = DENSITY_PRESETS.getValue(Density.STANDARD)
        // A dense-ish unit read slower; a positive nudge raises it...
        val slow = PacingEngine.targetWpm(0.6, preset, Mode.LEARNING, basePaceOffset = 0)
        val nudged = PacingEngine.targetWpm(0.6, preset, Mode.LEARNING, basePaceOffset = 20)
        assertTrue(nudged > slow, "base-pace nudge should raise a mid-complexity rate")

        // ...but even a huge nudge on the easiest unit cannot pass the Learning cap (225).
        val maxed = PacingEngine.targetWpm(0.0, preset, Mode.LEARNING, basePaceOffset = 500)
        assertTrue(maxed <= 225, "cap wins over base-pace, was $maxed")
    }

    @Test
    fun freeMode_canExceedLearningCap() {
        // Sanity: FREE mode is allowed above the learning ceiling (cap = base·2.0).
        val learning = PacingEngine.schedule(listOf(lightSentence), Density.STANDARD, Mode.LEARNING).single()
        val free = PacingEngine.schedule(listOf(lightSentence), Density.STANDARD, Mode.FREE).single()
        assertTrue(free.targetWpm >= learning.targetWpm)
        assertTrue(free.targetWpm <= 300, "FREE cap is base·2.0 = 300, was ${free.targetWpm}")
    }

    // --- Boundary/monotonicity properties ------------------------------------

    @Test
    fun targetWpm_isMonotonicInComplexity() {
        val preset = DENSITY_PRESETS.getValue(Density.STANDARD)
        var prev = Int.MAX_VALUE
        var c = 0.0
        while (c <= 1.0) {
            val wpm = PacingEngine.targetWpm(c, preset, Mode.LEARNING)
            assertTrue(wpm <= prev, "wpm should not increase as complexity rises (c=$c)")
            prev = wpm
            c += 0.05
        }
    }

    @Test
    fun everyDensity_respectsItsOwnFloorAndCeiling() {
        for (density in listOf(Density.DENSE, Density.STANDARD, Density.LIGHT)) {
            val preset = DENSITY_PRESETS.getValue(density)
            val ceiling = (preset.base * preset.capMult)
            val floor = PacingEngine.targetWpm(1.0, preset, Mode.LEARNING)
            val top = PacingEngine.targetWpm(0.0, preset, Mode.LEARNING)
            assertTrue(floor >= preset.min, "$density floor $floor below min ${preset.min}")
            assertTrue(top <= ceiling + 0.5, "$density top $top above learning ceiling $ceiling")
        }
    }
}

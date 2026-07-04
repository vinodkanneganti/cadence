package studio.sparkcube.cadence.core.pacing

import studio.sparkcube.cadence.core.model.Density
import studio.sparkcube.cadence.core.model.Mode
import studio.sparkcube.cadence.core.model.Step
import studio.sparkcube.cadence.core.model.Unit
import kotlin.math.roundToInt

/**
 * The pacing engine — the product. Pure Kotlin, stdlib only.
 *
 * Turns structured [Unit]s into a timed [Step] schedule per the PRD §Pacing
 * Algorithm. In LEARNING mode the effective rate is hard-capped at the density
 * learning ceiling; the engine REFUSES to exceed it regardless of user settings.
 */
object PacingEngine {

    fun schedule(
        units: List<Unit>,
        density: Density,
        mode: Mode,
        basePaceOffset: Int = 0,
    ): List<Step> {
        val preset = resolvePreset(units, density)
        val pauseScale = PacingConstants.PAUSE_SCALE.getValue(mode)
        return units.map { unit ->
            val complexity = complexityOf(unit.text)
            val wpm = targetWpm(complexity, preset, mode, basePaceOffset)
            val basePause = PacingConstants.PAUSE_MS.getValue(unit.boundary)
            Step(
                text = unit.text,
                targetWpm = wpm,
                pauseMsAfter = (basePause * pauseScale).roundToInt(),
                boundary = unit.boundary,
            )
        }
    }

    /**
     * Target wpm for one unit given the active preset and mode.
     * Dense units (complexity ≥ 0.5) slow toward `min`; light units rise toward
     * the mode cap. In LEARNING the cap is min(base·1.5, base·capMult).
     */
    fun targetWpm(complexity: Double, preset: DensityPreset, mode: Mode, basePaceOffset: Int = 0): Int {
        // The user's base-pace nudge shifts the narration baseline, but the safety
        // ceiling stays anchored to the ORIGINAL density baseline — so the Learning
        // cap always wins over user preference (AC10).
        val origBase = preset.base.toDouble()
        val base = (preset.base + basePaceOffset).toDouble()
        var cap = when (mode) {
            Mode.LEARNING -> origBase * PacingConstants.LEARNING_CAP_MULT
            Mode.FREE -> origBase * PacingConstants.FREE_CAP_MULT
        }
        val learningCap = origBase * preset.capMult
        if (mode == Mode.LEARNING) cap = minOf(cap, learningCap)

        val wpm = if (complexity >= 0.5) {
            base - ((complexity - 0.5) / 0.5) * (base - preset.min)   // base → min
        } else {
            base + ((0.5 - complexity) / 0.5) * (cap - base)          // base → cap
        }
        return wpm.coerceIn(preset.min.toDouble(), cap).roundToInt()
    }

    private fun resolvePreset(units: List<Unit>, density: Density): DensityPreset {
        val resolved = if (density == Density.AUTO) autoDensity(units) else density
        return DENSITY_PRESETS.getValue(resolved)
    }

    /**
     * AUTO: pick a concrete density from the document's median unit complexity.
     * Thresholds are a v1 choice (PRD leaves them unspecified beyond "median").
     */
    fun autoDensity(units: List<Unit>): Density {
        if (units.isEmpty()) return Density.STANDARD
        val sorted = units.map { complexityOf(it.text) }.sorted()
        val median = sorted[sorted.size / 2]
        return when {
            median >= 0.55 -> Density.DENSE
            median <= 0.35 -> Density.LIGHT
            else -> Density.STANDARD
        }
    }
}

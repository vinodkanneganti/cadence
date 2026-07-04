package studio.sparkcube.cadence.core.pacing

import studio.sparkcube.cadence.core.model.Boundary
import studio.sparkcube.cadence.core.model.Density
import studio.sparkcube.cadence.core.model.Mode

/**
 * Pacing constants — transcribed verbatim from the PRD §Pacing Algorithm.
 * `core.pacing` is PURE: nothing here imports Compose, platform, TTS, or PDF.
 */
object PacingConstants {
    const val BASELINE_WPM = 150     // comfortable narration anchor (STANDARD)
    const val MIN_WPM = 110
    const val LEARNING_CAP_MULT = 1.5 // Learning Mode hard ceiling = baseline * 1.5
    const val FREE_CAP_MULT = 2.0     // non-learning mode ceiling
    const val BASE_VOICE_WPM = 175    // assumed wpm of a mobile voice at rate 1.0
    const val RATE_MIN = 0.4
    const val RATE_MAX = 2.2

    /** Base structural pause, in ms, by the boundary that follows a unit. */
    val PAUSE_MS: Map<Boundary, Int> = mapOf(
        Boundary.SENTENCE to 250,
        Boundary.PARAGRAPH to 700,
        Boundary.SECTION to 1500,
    )

    /** Multiplier applied to PAUSE_MS by mode. */
    val PAUSE_SCALE: Map<Mode, Double> = mapOf(
        Mode.LEARNING to 1.0,
        Mode.FREE to 0.4,
    )
}

/** Per-density envelope: baseline wpm, learning cap multiplier, floor wpm. */
data class DensityPreset(val base: Int, val capMult: Double, val min: Int)

val DENSITY_PRESETS: Map<Density, DensityPreset> = mapOf(
    Density.DENSE to DensityPreset(base = 130, capMult = 1.3, min = 100),
    Density.STANDARD to DensityPreset(base = 150, capMult = 1.5, min = 110),
    Density.LIGHT to DensityPreset(base = 165, capMult = 1.6, min = 130),
)

/** Fire an active-recall prompt on whichever comes first. */
object RecallConfig {
    const val EVERY_N_SECTIONS = 2
    const val EVERY_MINUTES = 5
}

package studio.sparkcube.cadence.core.model

/**
 * A [Unit] plus the engine-assigned narration rate and trailing pause.
 * Output of the pacing engine; consumed by the Player.
 */
data class Step(
    val text: String,
    val targetWpm: Int,
    val pauseMsAfter: Int,
    val boundary: Boundary,
)

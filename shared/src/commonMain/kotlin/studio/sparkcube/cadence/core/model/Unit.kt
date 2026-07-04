package studio.sparkcube.cadence.core.model

/**
 * One segmented text piece with a boundary tag. Input to the pacing engine.
 *
 * Note the name deliberately matches the PRD glossary ("Unit"). Files that use
 * `() -> kotlin.Unit` callbacks (e.g. Speaker, Player) must NOT import this type,
 * to avoid shadowing `kotlin.Unit`.
 */
data class Unit(
    val text: String,
    val boundary: Boundary,
)

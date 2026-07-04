package studio.sparkcube.cadence.core.model

/**
 * LEARNING clamps the effective rate to a retention-safe ceiling (1.5× baseline)
 * and uses full-size structural pauses. FREE relaxes the cap and shrinks pauses.
 */
enum class Mode { LEARNING, FREE }

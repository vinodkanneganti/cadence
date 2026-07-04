package studio.sparkcube.cadence.core.model

/**
 * Density preset selecting the baseline / cap / floor wpm envelope.
 * AUTO derives the preset from the document's median complexity.
 */
enum class Density { AUTO, DENSE, STANDARD, LIGHT }

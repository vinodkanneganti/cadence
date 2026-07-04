package studio.sparkcube.cadence.core.model

/**
 * Boundary tag on a [Unit], and the thing that follows a spoken unit.
 * Ordering matters: SECTION implies a larger pause than PARAGRAPH than SENTENCE.
 */
enum class Boundary { SENTENCE, PARAGRAPH, SECTION }

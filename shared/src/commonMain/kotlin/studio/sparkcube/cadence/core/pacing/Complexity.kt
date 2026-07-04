package studio.sparkcube.cadence.core.pacing

import kotlin.math.max

/**
 * Complexity scoring — implements the PRD §Pacing Algorithm "Complexity score"
 * and "estimateSyllables" exactly. Pure; stdlib only.
 */

private val VOWELS = "aeiouy"

/** Split on whitespace; strip punctuation (keep letters and digits). */
fun tokenize(text: String): List<String> =
    text.split(Regex("\\s+"))
        .map { token -> token.filter { it.isLetterOrDigit() } }
        .filter { it.isNotEmpty() }

/** English syllable-count heuristic (per PRD). */
fun estimateSyllables(word: String): Int {
    val w = word.lowercase().filter { it.isLetter() }
    if (w.isEmpty()) return 1
    var groups = 0
    var prevVowel = false
    for (c in w) {
        val isVowel = c in VOWELS
        if (isVowel && !prevVowel) groups++
        prevVowel = isVowel
    }
    if (w.endsWith("e") && !w.endsWith("le")) groups -= 1 // silent e
    return max(1, groups)
}

/** Complexity score in [0,1] for a unit's text. */
fun complexityOf(text: String): Double {
    val words = tokenize(text)
    if (words.isEmpty()) return 0.0

    val meanWordLen = words.sumOf { it.length }.toDouble() / words.size
    val syllPerWord = words.sumOf { estimateSyllables(it) }.toDouble() / words.size
    val tokenCount = words.size

    val wordLenNorm = ((meanWordLen - 4.0) / (7.0 - 4.0)).coerceIn(0.0, 1.0)
    val syllNorm = ((syllPerWord - 1.3) / (2.2 - 1.3)).coerceIn(0.0, 1.0)
    val lengthNorm = ((tokenCount - 8).toDouble() / (30 - 8)).coerceIn(0.0, 1.0)

    return 0.45 * syllNorm + 0.35 * wordLenNorm + 0.20 * lengthNorm
}

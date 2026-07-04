package studio.sparkcube.cadence.core.pdf

/**
 * Splits a block of prose into sentences. Pure; stdlib only.
 *
 * Guards (PRD failure-mode: "err toward under-splitting"):
 *  - decimals — "3.14" does not split
 *  - abbreviations — "Dr.", "e.g.", "i.e.", "etc." do not split
 *  - single initials — "J. Smith" does not split
 *  - a boundary requires the next non-space char to start a new sentence
 *    (uppercase, digit, or opening quote), else we keep the text joined.
 */
object SentenceSplitter {

    private val ABBREVIATIONS = setOf(
        "dr", "mr", "mrs", "ms", "prof", "sr", "jr", "st", "vs", "etc",
        "e.g", "i.e", "al", "fig", "eq", "no", "vol", "pp", "cf", "inc", "ltd",
    )

    fun split(text: String): List<String> {
        val t = text.trim()
        if (t.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        val sb = StringBuilder()
        for (i in t.indices) {
            val c = t[i]
            sb.append(c)
            if ((c == '.' || c == '!' || c == '?') && isBoundary(t, i)) {
                result += sb.toString().trim()
                sb.clear()
            }
        }
        if (sb.isNotBlank()) result += sb.toString().trim()
        return result.filter { it.isNotEmpty() }
    }

    private fun isBoundary(t: String, i: Int): Boolean {
        val c = t[i]
        val next = t.getOrNull(i + 1)

        // Must be end-of-text or immediately followed by whitespace.
        if (next != null && !next.isWhitespace()) return false

        // The following sentence should start with a capital / digit / opening quote.
        var j = i + 1
        while (j < t.length && t[j].isWhitespace()) j++
        val following = t.getOrNull(j)
        if (following != null &&
            !(following.isUpperCase() || following.isDigit() ||
                following == '"' || following == '\'' || following == '“')
        ) {
            return false
        }

        if (c == '.') {
            // Decimal: digit '.' digit
            val prev = t.getOrNull(i - 1)
            if (prev != null && prev.isDigit() && next != null && next.isDigit()) return false

            val token = precedingToken(t, i)
            if (token.length == 1 && token[0].isUpperCase()) return false // "J."
            if (token.lowercase() in ABBREVIATIONS) return false // "Dr."

            val dotted = precedingDotted(t, i)
            if (dotted != null && dotted.lowercase() in ABBREVIATIONS) return false // "e.g."
        }
        return true
    }

    /** The alphanumeric run immediately before position [i]. */
    private fun precedingToken(t: String, i: Int): String {
        var k = i - 1
        val sb = StringBuilder()
        while (k >= 0 && t[k].isLetterOrDigit()) {
            sb.append(t[k]); k--
        }
        return sb.reverse().toString()
    }

    /** The run before [i] including internal dots, e.g. "e.g" for "…e.g.". */
    private fun precedingDotted(t: String, i: Int): String? {
        var k = i - 1
        val sb = StringBuilder()
        while (k >= 0 && (t[k].isLetterOrDigit() || t[k] == '.')) {
            sb.append(t[k]); k--
        }
        if (sb.isEmpty()) return null
        return sb.reverse().toString().trim('.')
    }
}

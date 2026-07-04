package studio.sparkcube.cadence.core.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class SentenceSplitterTest {

    @Test
    fun splitsPlainSentences() {
        val out = SentenceSplitter.split("The cat sat. The dog ran. Birds flew away.")
        assertEquals(listOf("The cat sat.", "The dog ran.", "Birds flew away."), out)
    }

    @Test
    fun doesNotSplitOnDecimals() {
        val out = SentenceSplitter.split("Pi is about 3.14 for our purposes. That is enough.")
        assertEquals(listOf("Pi is about 3.14 for our purposes.", "That is enough."), out)
    }

    @Test
    fun doesNotSplitOnAbbreviations() {
        val out = SentenceSplitter.split("Ask Dr. Smith about it. He knows.")
        assertEquals(listOf("Ask Dr. Smith about it.", "He knows."), out)
    }

    @Test
    fun doesNotSplitOnEg() {
        val out = SentenceSplitter.split("Use a token, e.g. a word. Then continue.")
        assertEquals(listOf("Use a token, e.g. a word.", "Then continue."), out)
    }

    @Test
    fun doesNotSplitOnSingleInitial() {
        val out = SentenceSplitter.split("The author J. Smith wrote it. It sold well.")
        assertEquals(listOf("The author J. Smith wrote it.", "It sold well."), out)
    }

    @Test
    fun handlesQuestionAndExclamation() {
        val out = SentenceSplitter.split("Really? Yes! Absolutely.")
        assertEquals(listOf("Really?", "Yes!", "Absolutely."), out)
    }

    @Test
    fun singleSentenceStaysWhole() {
        assertEquals(listOf("No trailing period here"), SentenceSplitter.split("No trailing period here"))
        assertEquals(emptyList(), SentenceSplitter.split("   "))
    }
}

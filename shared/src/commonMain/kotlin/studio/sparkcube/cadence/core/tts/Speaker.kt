package studio.sparkcube.cadence.core.tts

/** An installed native voice. */
data class VoiceInfo(val id: String, val name: String, val locale: String)

/**
 * The ONLY text-to-speech platform boundary. No platform speech API may be
 * referenced anywhere else in the codebase.
 *
 * Contract: one sentence per [speak] call — the Player owns inter-unit pauses.
 * `speak` is non-blocking; it invokes [onDone] when the utterance finishes or
 * [onError] on failure. A [stop] (or a subsequent [speak]) cancels the current
 * utterance silently (no callback fires for a cancelled utterance).
 */
expect class Speaker() : SpeechOutput {
    override fun voices(): List<VoiceInfo>
    override fun setVoice(id: String)
    override fun speak(text: String, targetWpm: Int, onDone: () -> Unit, onError: (String) -> Unit)
    override fun stop()
}

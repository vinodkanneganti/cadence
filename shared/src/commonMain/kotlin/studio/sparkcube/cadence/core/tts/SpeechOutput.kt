package studio.sparkcube.cadence.core.tts

/**
 * The speech capability the Player depends on. [Speaker] (the platform boundary)
 * is the only production implementation; tests supply a fake. Keeping the Player
 * behind this interface makes its scheduling logic testable without audio, while
 * the single real speech path still lives entirely inside `Speaker`.
 */
interface SpeechOutput {
    fun voices(): List<VoiceInfo>
    fun setVoice(id: String)
    fun speak(text: String, targetWpm: Int, onDone: () -> Unit, onError: (String) -> Unit)
    fun stop()
}

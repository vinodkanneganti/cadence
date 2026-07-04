package studio.sparkcube.cadence.core.tts

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import studio.sparkcube.cadence.AndroidApp
import studio.sparkcube.cadence.core.pacing.PacingConstants
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Android TTS via [TextToSpeech]. One sentence per utterance (QUEUE_FLUSH); the
 * Player owns inter-unit pauses. Rate maps the engine's wpm through the shared
 * calibration constant: `setSpeechRate(targetWpm / BASE_VOICE_WPM)`.
 */
actual class Speaker actual constructor() : SpeechOutput {

    private val callbacks = ConcurrentHashMap<String, Pair<() -> Unit, (String) -> Unit>>()
    @Volatile private var ready = false
    private val tts: TextToSpeech

    init {
        tts = TextToSpeech(AndroidApp.context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) runCatching { tts.setLanguage(Locale.getDefault()) }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                utteranceId?.let { callbacks.remove(it)?.first?.invoke() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { callbacks.remove(it)?.second?.invoke("tts error") }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                utteranceId?.let { callbacks.remove(it)?.second?.invoke("tts error $errorCode") }
            }
        })
    }

    actual override fun voices(): List<VoiceInfo> = runCatching {
        tts.voices.orEmpty()
            .map { v -> VoiceInfo(id = v.name, name = v.name, locale = v.locale.toString()) }
            .sortedBy { it.name }
    }.getOrDefault(emptyList())

    actual override fun setVoice(id: String) {
        runCatching { tts.voices.orEmpty().firstOrNull { it.name == id }?.let { tts.voice = it } }
    }

    actual override fun speak(text: String, targetWpm: Int, onDone: () -> Unit, onError: (String) -> Unit) {
        if (!ready) { onError("TTS not ready yet"); return }
        val id = "u${System.nanoTime()}"
        callbacks[id] = onDone to onError
        tts.setSpeechRate(targetWpm.toFloat() / PacingConstants.BASE_VOICE_WPM)
        val res = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (res != TextToSpeech.SUCCESS) {
            callbacks.remove(id)
            onError("speak failed")
        }
    }

    actual override fun stop() {
        runCatching { tts.stop() }
        callbacks.clear()
    }
}

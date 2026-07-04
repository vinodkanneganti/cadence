package studio.sparkcube.cadence.core.tts

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import studio.sparkcube.cadence.AndroidApp
import studio.sparkcube.cadence.core.pacing.PacingConstants
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Android TTS via [TextToSpeech]. One sentence per utterance (QUEUE_FLUSH); the
 * Player owns inter-unit pauses. Rate maps the engine's wpm through the shared
 * calibration constant: `setSpeechRate(targetWpm / BASE_VOICE_WPM)`.
 *
 * TextToSpeech initializes asynchronously. If [speak] is called before it's ready
 * (e.g. Play pressed right after launch), the request is held and fired on init —
 * rather than surfaced as an error.
 */
actual class Speaker actual constructor() : SpeechOutput {

    private class Pending(
        val text: String,
        val wpm: Int,
        val onDone: () -> Unit,
        val onError: (String) -> Unit,
    )

    private val callbacks = ConcurrentHashMap<String, Pair<() -> Unit, (String) -> Unit>>()
    @Volatile private var ready = false
    @Volatile private var pending: Pending? = null
    private val tts: TextToSpeech

    init {
        // Some devices (this Samsung) have no *default* engine set even when one is
        // installed → default-constructed TextToSpeech fails to init. Select an
        // installed engine explicitly (prefer Google TTS).
        val engine = pickEngine()
        val listener = TextToSpeech.OnInitListener { status ->
            ready = status == TextToSpeech.SUCCESS
            Log.i("CadenceTTS", "init status=$status engine=$engine ready=$ready")
            val queued = pending
            pending = null
            if (ready) {
                runCatching { tts.setLanguage(Locale.getDefault()) }
                queued?.let { doSpeak(it.text, it.wpm, it.onDone, it.onError) }
            } else {
                queued?.onError?.invoke("Text-to-speech engine unavailable")
            }
        }
        tts = if (engine != null) TextToSpeech(AndroidApp.context, listener, engine)
        else TextToSpeech(AndroidApp.context, listener)
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
        if (!ready) {
            pending = Pending(text, targetWpm, onDone, onError) // fire on init
            return
        }
        doSpeak(text, targetWpm, onDone, onError)
    }

    private fun doSpeak(text: String, targetWpm: Int, onDone: () -> Unit, onError: (String) -> Unit) {
        val id = "u${System.nanoTime()}"
        callbacks[id] = onDone to onError
        tts.setSpeechRate(targetWpm.toFloat() / PacingConstants.BASE_VOICE_WPM)
        val res = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (res != TextToSpeech.SUCCESS) {
            callbacks.remove(id)
            onError("speak failed (code $res)")
        }
    }

    actual override fun stop() {
        pending = null
        runCatching { tts.stop() }
        callbacks.clear()
    }

    private fun pickEngine(): String? = runCatching {
        val pm = AndroidApp.context.packageManager
        val services = pm.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
        val pkgs = services.mapNotNull { it.serviceInfo?.packageName }
        pkgs.firstOrNull { it == "com.google.android.tts" } ?: pkgs.firstOrNull()
    }.getOrNull()
}

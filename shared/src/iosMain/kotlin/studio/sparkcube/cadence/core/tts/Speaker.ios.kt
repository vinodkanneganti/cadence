package studio.sparkcube.cadence.core.tts

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMaximumSpeechRate
import platform.AVFAudio.AVSpeechUtteranceMinimumSpeechRate
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.darwin.NSObject
import studio.sparkcube.cadence.core.pacing.PacingConstants

/**
 * iOS TTS via AVSpeechSynthesizer. One sentence per utterance; the Player owns pauses.
 *
 * Rate trap (PRD): AVSpeechUtterance.rate is 0..1, NOT a wpm multiplier. Map it
 * relative to the platform default and clamp to the platform min/max. The audio
 * session is set to .playback so speech is audible even in silent mode.
 */
@OptIn(ExperimentalForeignApi::class)
actual class Speaker actual constructor() : SpeechOutput {

    private val synth = AVSpeechSynthesizer()
    private var selectedVoice: String? = null
    private var onDoneCb: (() -> Unit)? = null

    private val delegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didFinishSpeechUtterance: AVSpeechUtterance,
        ) {
            val cb = onDoneCb
            onDoneCb = null
            cb?.invoke()
        }
    }

    init {
        synth.delegate = delegate
        // .playback so speech is audible even with the silent switch on. The synth
        // activates the session itself when it starts speaking.
        runCatching { AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null) }
    }

    actual override fun voices(): List<VoiceInfo> =
        AVSpeechSynthesisVoice.speechVoices().mapNotNull { any ->
            (any as? AVSpeechSynthesisVoice)?.let {
                VoiceInfo(id = it.identifier, name = it.name, locale = it.language)
            }
        }

    actual override fun setVoice(id: String) {
        selectedVoice = id
    }

    actual override fun speak(text: String, targetWpm: Int, onDone: () -> Unit, onError: (String) -> Unit) {
        synth.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        onDoneCb = onDone
        val utterance = AVSpeechUtterance(string = text)
        val mapped = AVSpeechUtteranceDefaultSpeechRate * (targetWpm.toFloat() / PacingConstants.BASE_VOICE_WPM)
        utterance.rate = mapped.coerceIn(AVSpeechUtteranceMinimumSpeechRate, AVSpeechUtteranceMaximumSpeechRate)
        selectedVoice?.let { id ->
            AVSpeechSynthesisVoice.voiceWithIdentifier(id)?.let { utterance.voice = it }
        }
        synth.speakUtterance(utterance)
    }

    actual override fun stop() {
        onDoneCb = null
        synth.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }
}

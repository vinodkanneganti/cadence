package studio.sparkcube.cadence.core.tts

import java.util.concurrent.atomic.AtomicReference

/**
 * Desktop (macOS) TTS via the `say` command. `say -r <wpm>` takes an exact words
 * per minute, so the engine's targetWpm passes straight through — no calibration.
 *
 * If `say` is unavailable (non-macOS JVM), [voices] returns empty and [speak]
 * reports an error; the UI is expected to detect this at startup (PRD trap row).
 */
actual class Speaker actual constructor() : SpeechOutput {

    private var selectedVoice: String? = null
    private val current = AtomicReference<Process?>(null)

    actual override fun voices(): List<VoiceInfo> = try {
        val proc = ProcessBuilder("say", "-v", "?").redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        out.lineSequence().mapNotNull(::parseVoiceLine).toList()
    } catch (e: Exception) {
        emptyList()
    }

    actual override fun setVoice(id: String) {
        selectedVoice = id
    }

    actual override fun speak(text: String, targetWpm: Int, onDone: () -> Unit, onError: (String) -> Unit) {
        stop() // cancel any in-flight utterance
        try {
            val cmd = mutableListOf("say", "-r", targetWpm.toString())
            selectedVoice?.let { cmd += listOf("-v", it) }
            cmd += text
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            current.set(proc)
            Thread {
                val code = try {
                    proc.waitFor()
                } catch (e: InterruptedException) {
                    -1
                }
                // Only fire a callback if this proc is still the active one. If it
                // was cancelled by stop()/a new speak(), current no longer points
                // at it and we stay silent.
                if (current.compareAndSet(proc, null)) {
                    if (code == 0) onDone() else onError("say exited with code $code")
                }
            }.apply { isDaemon = true }.start()
        } catch (e: Exception) {
            onError(e.message ?: "failed to launch `say` (macOS only)")
        }
    }

    actual override fun stop() {
        current.getAndSet(null)?.destroy()
    }

    private companion object {
        // e.g. "Alex                en_US    # Most people recognize me by my voice."
        val VOICE_RE = Regex("""^(.+?)\s+([a-zA-Z]{2}[-_][a-zA-Z]{2})\s+#""")

        fun parseVoiceLine(line: String): VoiceInfo? {
            val m = VOICE_RE.find(line.trim()) ?: return null
            val name = m.groupValues[1].trim()
            return VoiceInfo(id = name, name = name, locale = m.groupValues[2])
        }
    }
}

package studio.sparkcube.cadence

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.sparkcube.cadence.core.model.Boundary
import studio.sparkcube.cadence.core.model.Density
import studio.sparkcube.cadence.core.model.Mode
import studio.sparkcube.cadence.core.model.Unit as ReadUnit
import studio.sparkcube.cadence.core.pacing.PacingEngine
import studio.sparkcube.cadence.core.player.Player
import studio.sparkcube.cadence.core.tts.Speaker
import studio.sparkcube.cadence.core.tts.VoiceInfo

/** Hardcoded sample document (Phase 0 gate: "a hardcoded sentence list reads aloud"). */
private val SAMPLE = listOf(
    ReadUnit("Backpropagation computes gradients via the chain rule.", Boundary.SENTENCE),
    ReadUnit("It is the workhorse of modern deep learning.", Boundary.PARAGRAPH),
    ReadUnit("Section two. Optimization.", Boundary.SECTION),
    ReadUnit("Gradient descent nudges each weight against its gradient.", Boundary.SENTENCE),
    ReadUnit("A learning rate that is too large will diverge.", Boundary.PARAGRAPH),
    ReadUnit("Section three. Regularization.", Boundary.SECTION),
    ReadUnit("Weight decay penalizes large parameters to curb overfitting.", Boundary.SENTENCE),
)

private val Paper = Color(0xFFF6F1E7)
private val Ink = Color(0xFF2B2620)
private val Faint = Color(0xFF9A8F7C)
private val Muted = Color(0xFF6B6255)
private val Highlight = Color(0xFFFFE9A8)

@Composable
fun App() {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val speaker = remember { Speaker() }
        val steps = remember { PacingEngine.schedule(SAMPLE, Density.STANDARD, Mode.LEARNING) }
        val player = remember { Player(speaker, scope) }

        var activeIndex by remember { mutableStateOf(0) }
        var playing by remember { mutableStateOf(false) }
        var section by remember { mutableStateOf(0) }
        var sayAvailable by remember { mutableStateOf(true) }

        var voices by remember { mutableStateOf(emptyList<VoiceInfo>()) }
        var selectedVoice by remember { mutableStateOf<VoiceInfo?>(null) }
        var voiceMenuOpen by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            player.onUnitStart = { activeIndex = it }
            player.onSectionBoundary = { section = it }
            player.onFinished = { playing = false }
            player.onError = { playing = false }
            player.load(steps)
            val all = speaker.voices()
            sayAvailable = all.isNotEmpty()
            // Keep the list usable: English voices first, then everything else.
            voices = all.sortedWith(
                compareByDescending<VoiceInfo> { it.locale.startsWith("en") }.thenBy { it.name },
            )
        }

        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }

        fun sync() { playing = player.isPlaying }
        fun toggle() { player.toggle(); sync() }

        // Speak a short sample in the current voice. Pauses playback first so the
        // player's in-flight utterance isn't orphaned by the preview's `stop()`.
        fun preview() {
            player.pause(); playing = false
            speaker.speak("This is the selected voice.", targetWpm = 160, onDone = {}, onError = {})
        }

        fun chooseVoice(v: VoiceInfo?) {
            selectedVoice = v
            v?.let { speaker.setVoice(it.id) }
            voiceMenuOpen = false
            if (!playing) preview()
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(Paper)
                .padding(24.dp)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.Spacebar -> { toggle(); true }
                        Key.DirectionRight -> { player.next(); sync(); true }
                        Key.DirectionLeft -> { player.prev(); sync(); true }
                        else -> false
                    }
                },
        ) {
            Text("Cadence", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text("Phase 0 — learning-paced read-aloud (desktop demo)", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))

            if (!sayAvailable) {
                Text(
                    "macOS `say` unavailable — playback disabled on this JVM.",
                    color = Color(0xFFB3261E),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                steps.forEachIndexed { i, step ->
                    val active = i == activeIndex
                    val isSection = step.boundary == Boundary.SECTION
                    Text(
                        text = step.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (active) Highlight else Color.Transparent)
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        fontSize = if (isSection) 20.sp else 17.sp,
                        fontWeight = if (isSection || active) FontWeight.SemiBold else FontWeight.Normal,
                        color = Ink,
                    )
                    Text(
                        "${step.targetWpm} wpm · ${step.boundary} · pause ${step.pauseMsAfter}ms",
                        fontSize = 11.sp,
                        color = Faint,
                        modifier = Modifier.padding(bottom = 6.dp, start = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voice", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Box {
                    Button(onClick = { voiceMenuOpen = true }, enabled = sayAvailable) {
                        Text(selectedVoice?.let { "${it.name} · ${it.locale}" } ?: "Default (system)")
                    }
                    DropdownMenu(
                        expanded = voiceMenuOpen,
                        onDismissRequest = { voiceMenuOpen = false },
                    ) {
                        voices.forEach { v ->
                            DropdownMenuItem(
                                text = { Text("${v.name} · ${v.locale}") },
                                onClick = { chooseVoice(v) },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { preview() }, enabled = sayAvailable) { Text("Preview") }
                Spacer(Modifier.width(16.dp))
                Text("${voices.size} voices", color = Faint, fontSize = 12.sp)
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { player.prev(); sync() }) { Text("◀ Prev") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { toggle() }) { Text(if (playing) "Pause" else "Play") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { player.next(); sync() }) { Text("Next ▶") }
                Spacer(Modifier.width(16.dp))
                Text(
                    "sec $section   ·   space = play/pause · ←/→ = prev/next",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

package studio.sparkcube.cadence.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.sparkcube.cadence.core.model.Density
import studio.sparkcube.cadence.core.model.Mode

/** Appearance + narration controls, shown in the "Aa" popover. */
@Composable
fun SettingsPanel(state: ReaderState) {
    val pal = LocalPalette.current
    Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(18.dp)) {
        SectionLabel("Appearance")

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("☀  Light", !state.dark) { if (state.dark) state.toggleDark() }
            Spacer(Modifier.width(8.dp))
            Chip("☾  Dark", state.dark) { if (!state.dark) state.toggleDark() }
        }

        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("A", fontSize = 13.sp, color = pal.muted)
            Slider(
                value = state.readingSizeSp,
                onValueChange = { state.setReadingSize(it) },
                valueRange = 15f..26f,
                colors = SliderDefaults.colors(thumbColor = pal.accent, activeTrackColor = pal.accent, inactiveTrackColor = pal.hairline),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("A", fontSize = 22.sp, color = pal.muted)
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ReadingFont.entries.forEach { f ->
                Chip(f.label, state.readingFont == f) { state.selectReadingFont(f) }
                Spacer(Modifier.width(8.dp))
            }
        }

        Divider()
        SectionLabel("Narration")

        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Density.entries.forEach { d -> Chip(d.name, state.density == d) { state.selectDensity(d) } }
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Chip("LEARNING", state.mode == Mode.LEARNING) { state.selectMode(Mode.LEARNING) }
            Spacer(Modifier.width(8.dp))
            Chip("FREE", state.mode == Mode.FREE) { state.selectMode(Mode.FREE) }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Pace", fontSize = 12.sp, color = pal.muted)
            Spacer(Modifier.width(6.dp))
            Text((if (state.basePaceOffset >= 0) "+" else "") + "${state.basePaceOffset}", fontSize = 12.sp, color = pal.ink, fontWeight = FontWeight.SemiBold)
            Slider(
                value = state.basePaceOffset.toFloat(),
                onValueChange = { state.nudgeBasePace(it.toInt()) },
                valueRange = -25f..25f,
                steps = 9,
                colors = SliderDefaults.colors(thumbColor = pal.accent, activeTrackColor = pal.accent, inactiveTrackColor = pal.hairline),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            if (state.mode == Mode.LEARNING) Text("cap", fontSize = 10.sp, color = pal.faint)
        }

        VoiceRow(state)
    }
}

@Composable
private fun VoiceRow(state: ReaderState) {
    val pal = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Voice", fontSize = 12.sp, color = pal.muted)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            Chip(state.selectedVoice?.name ?: "Default", false) { state.refreshVoices(); open = true }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (state.voices.isEmpty()) DropdownMenuItem(text = { Text("No voices found") }, onClick = { open = false })
                state.voices.forEach { v ->
                    DropdownMenuItem(
                        text = { Text("${v.name} · ${v.locale}") },
                        onClick = { state.chooseVoice(v); open = false; if (!state.playing) state.previewVoice() },
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Chip("Preview", false) { state.previewVoice() }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val pal = LocalPalette.current
    Text(text.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, color = pal.faint, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Divider() {
    val pal = LocalPalette.current
    androidx.compose.material3.HorizontalDivider(color = pal.hairline, modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = if (selected) pal.accent else pal.card,
        border = if (selected) null else BorderStroke(1.dp, pal.hairline),
    ) {
        Text(
            text,
            color = if (selected) pal.onAccent else pal.ink,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

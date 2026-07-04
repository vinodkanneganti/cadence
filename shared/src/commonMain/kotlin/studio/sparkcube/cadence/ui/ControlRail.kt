package studio.sparkcube.cadence.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.sparkcube.cadence.core.model.Density
import studio.sparkcube.cadence.core.model.Mode

/** Density / mode / base-pace / voice controls (PRD R9, R10). */
@Composable
fun ControlRail(state: ReaderState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Density")
            Spacer(Modifier.width(8.dp))
            Density.entries.forEach { d ->
                Segment(text = d.name, selected = state.density == d) { state.selectDensity(d) }
                Spacer(Modifier.width(6.dp))
            }

            Spacer(Modifier.width(20.dp))
            Label("Mode")
            Spacer(Modifier.width(8.dp))
            Segment("LEARNING", state.mode == Mode.LEARNING) { state.selectMode(Mode.LEARNING) }
            Spacer(Modifier.width(6.dp))
            Segment("FREE", state.mode == Mode.FREE) { state.selectMode(Mode.FREE) }
        }

        Spacer(Modifier.width(8.dp))
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Label("Base pace")
            Spacer(Modifier.width(8.dp))
            Text(
                (if (state.basePaceOffset >= 0) "+" else "") + "${state.basePaceOffset} wpm",
                fontSize = 13.sp, color = CadenceColors.Ink, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Slider(
                value = state.basePaceOffset.toFloat(),
                onValueChange = { state.nudgeBasePace(it.toInt()) },
                valueRange = -25f..25f,
                steps = 9,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            if (state.mode == Mode.LEARNING) {
                Spacer(Modifier.width(12.dp))
                Text("Learning cap active", fontSize = 11.sp, color = CadenceColors.Faint)
            }

            Spacer(Modifier.width(20.dp))
            VoicePicker(state)
        }
    }
}

@Composable
private fun VoicePicker(state: ReaderState) {
    var open by remember { mutableStateOf(false) }
    Label("Voice")
    Spacer(Modifier.width(8.dp))
    Box {
        OutlinedButton(onClick = { open = true }, enabled = state.sayAvailable) {
            Text(state.selectedVoice?.let { "${it.name} · ${it.locale}" } ?: "Default")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.voices.forEach { v ->
                DropdownMenuItem(
                    text = { Text("${v.name} · ${v.locale}") },
                    onClick = { state.chooseVoice(v); open = false; if (!state.playing) state.previewVoice() },
                )
            }
        }
    }
    Spacer(Modifier.width(8.dp))
    OutlinedButton(onClick = { state.previewVoice() }, enabled = state.sayAvailable) { Text("Preview") }
}

@Composable
private fun Label(text: String) =
    Text(text, fontSize = 12.sp, color = CadenceColors.Muted)

@Composable
private fun Segment(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = CadenceColors.Accent),
        ) { Text(text, color = Color.White, fontSize = 12.sp) }
    } else {
        OutlinedButton(onClick = onClick) { Text(text, color = CadenceColors.Ink, fontSize = 12.sp) }
    }
}

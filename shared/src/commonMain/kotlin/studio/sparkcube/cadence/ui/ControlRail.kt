package studio.sparkcube.cadence.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

/** Density / mode / base-pace / voice controls. Wraps on narrow (phone) screens. */
@Composable
fun ControlRail(state: ReaderState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Density.entries.forEach { d ->
                Segment(d.name, state.density == d) { state.selectDensity(d) }
            }
            Segment("LEARNING", state.mode == Mode.LEARNING) { state.selectMode(Mode.LEARNING) }
            Segment("FREE", state.mode == Mode.FREE) { state.selectMode(Mode.FREE) }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Label("Pace")
            Spacer(Modifier.width(6.dp))
            Text(
                (if (state.basePaceOffset >= 0) "+" else "") + "${state.basePaceOffset}",
                fontSize = 12.sp, color = CadenceColors.Ink, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Slider(
                value = state.basePaceOffset.toFloat(),
                onValueChange = { state.nudgeBasePace(it.toInt()) },
                valueRange = -25f..25f,
                steps = 9,
                modifier = Modifier.weight(1f),
            )
            if (state.mode == Mode.LEARNING) {
                Spacer(Modifier.width(8.dp))
                Text("cap on", fontSize = 10.sp, color = CadenceColors.Faint)
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            VoicePicker(state)
        }
    }
}

@Composable
private fun RowScope.VoicePicker(state: ReaderState) {
    var open by remember { mutableStateOf(false) }
    Label("Voice")
    Spacer(Modifier.width(6.dp))
    Box(Modifier.weight(1f)) {
        OutlinedButton(onClick = { state.refreshVoices(); open = true }) {
            Text(
                state.selectedVoice?.let { "${it.name} · ${it.locale}" } ?: "Default",
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (state.voices.isEmpty()) {
                DropdownMenuItem(text = { Text("No voices found") }, onClick = { open = false })
            }
            state.voices.forEach { v ->
                DropdownMenuItem(
                    text = { Text("${v.name} · ${v.locale}") },
                    onClick = { state.chooseVoice(v); open = false; if (!state.playing) state.previewVoice() },
                )
            }
        }
    }
    Spacer(Modifier.width(6.dp))
    OutlinedButton(onClick = { state.previewVoice() }) { Text("Preview") }
}

@Composable
private fun Label(text: String) =
    Text(text, fontSize = 12.sp, color = CadenceColors.Muted)

@Composable
private fun Segment(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CadenceColors.Accent),
        ) { Text(text, color = Color.White, fontSize = 11.sp) }
    } else {
        OutlinedButton(
            onClick = onClick,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) { Text(text, color = CadenceColors.Ink, fontSize = 11.sp) }
    }
}

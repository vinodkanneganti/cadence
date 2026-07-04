package studio.sparkcube.cadence.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Play/pause + skip, with live wpm / elapsed / section readouts. Wraps on phones. */
@Composable
fun TransportBar(state: ReaderState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth(),
            color = CadenceColors.Accent,
            trackColor = CadenceColors.AccentSoft,
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { state.prev() }, contentPadding = tight) { Text("◀") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { state.togglePlay() },
                colors = ButtonDefaults.buttonColors(containerColor = CadenceColors.Accent),
            ) { Text(if (state.playing) "Pause" else "Play", color = Color.White) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { state.next() }, contentPadding = tight) { Text("▶") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { state.nextSection() }, contentPadding = tight) { Text("§ ▶") }
        }

        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Readout("PACE", "${state.currentWpm} wpm")
            Readout("ELAPSED", formatElapsed(state.elapsedSeconds))
            Readout("SECTION", state.section.toString())
        }
    }
}

private val tight = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

@Composable
private fun Readout(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = CadenceColors.Faint)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CadenceColors.Ink)
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${if (s < 10) "0$s" else "$s"}"
}

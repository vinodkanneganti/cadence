package studio.sparkcube.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Active-recall prompt (PRD R5 / AC5). Blocks the reading surface until the reader
 * chooses to continue. Retrieval practice is the primary retention multiplier.
 */
@Composable
fun RecallOverlay(onContinue: () -> Unit) {
    val pal = LocalPalette.current
    Column(
        Modifier.fillMaxSize().background(Color(0xCC000000)).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(color = pal.card, shape = RoundedCornerShape(18.dp), tonalElevation = 4.dp) {
            Column(
                Modifier.widthIn(max = 520.dp).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("Rest.", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = pal.ink)
                Text(
                    "Before you continue — what were the key points of the last section?",
                    fontSize = 17.sp,
                    color = pal.muted,
                    textAlign = TextAlign.Center,
                )
                Surface(onClick = onContinue, shape = RoundedCornerShape(12.dp), color = pal.accent) {
                    Text("Continue", color = pal.onAccent, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp))
                }
            }
        }
    }
}

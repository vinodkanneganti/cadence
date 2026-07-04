package studio.sparkcube.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Active-recall prompt (PRD R5 / AC5). Blocks the reading surface until the user
 * chooses to continue. Retrieval practice is the primary retention multiplier.
 */
@Composable
fun RecallOverlay(onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xCC2B2620)).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = CadenceColors.Card,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp,
        ) {
            Column(
                Modifier.widthIn(max = 520.dp).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Rest.", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = CadenceColors.Ink)
                Text(
                    "Before you continue — what were the key points of the last section?",
                    fontSize = 17.sp,
                    color = CadenceColors.Muted,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(containerColor = CadenceColors.Accent),
                ) {
                    Text("Continue", color = Color.White)
                }
            }
        }
    }
}

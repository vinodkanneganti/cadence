package studio.sparkcube.cadence.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** First-run / no-document state. */
@Composable
fun EmptyState(onOpen: () -> Unit) {
    CenterColumn {
        Text("Cadence", fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = CadenceColors.Ink)
        Text(
            "Read a PDF aloud at a learning-optimized pace — with structural pauses\nand active-recall prompts that help you retain, not just consume.",
            fontSize = 15.sp,
            color = CadenceColors.Muted,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onOpen,
            colors = ButtonDefaults.buttonColors(containerColor = CadenceColors.Accent),
        ) {
            Text("Open a PDF", color = Color.White)
        }
    }
}

/** Scanned / image-only PDF message (PRD R12 / AC9 — never fail silently). */
@Composable
fun ScannedMessage(onOpen: () -> Unit) {
    CenterColumn {
        Text("No selectable text", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = CadenceColors.Ink)
        Text(
            "This PDF has no selectable text — OCR isn't supported yet.\nTry a PDF that was exported from text (not a scan or photo).",
            fontSize = 15.sp,
            color = CadenceColors.Muted,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onOpen) { Text("Open a different PDF") }
    }
}

@Composable
fun ErrorMessage(message: String, onOpen: () -> Unit) {
    CenterColumn {
        Text("Couldn't open that PDF", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = CadenceColors.Error)
        Text(message, fontSize = 15.sp, color = CadenceColors.Muted, textAlign = TextAlign.Center)
        Button(onClick = onOpen) { Text("Try another PDF") }
    }
}

@Composable
private fun CenterColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Column(
            Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

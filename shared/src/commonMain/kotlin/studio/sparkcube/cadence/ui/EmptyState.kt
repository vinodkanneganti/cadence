package studio.sparkcube.cadence.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EmptyState(onOpen: () -> Unit) {
    val pal = LocalPalette.current
    CenterColumn {
        Text("Cadence", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = pal.ink)
        Text(
            "Read a PDF aloud at a learning-optimized pace — with structural pauses\nand active-recall prompts that help you retain, not just consume.",
            fontSize = 15.sp,
            color = pal.muted,
            textAlign = TextAlign.Center,
        )
        Surface(onClick = onOpen, shape = RoundedCornerShape(12.dp), color = pal.accent) {
            Text("Open a PDF", color = pal.onAccent, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
        }
    }
}

@Composable
fun ScannedMessage(onOpen: () -> Unit) {
    val pal = LocalPalette.current
    CenterColumn {
        Text("No selectable text", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = pal.ink)
        Text(
            "This PDF has no selectable text — OCR isn't supported yet.\nTry a PDF exported from text (not a scan or photo).",
            fontSize = 15.sp,
            color = pal.muted,
            textAlign = TextAlign.Center,
        )
        Surface(onClick = onOpen, shape = RoundedCornerShape(12.dp), color = pal.accent) {
            Text("Open a different PDF", color = pal.onAccent, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp))
        }
    }
}

@Composable
fun ErrorMessage(message: String, onOpen: () -> Unit) {
    val pal = LocalPalette.current
    CenterColumn {
        Text("Couldn't open that PDF", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = pal.error)
        Text(message, fontSize = 15.sp, color = pal.muted, textAlign = TextAlign.Center)
        Surface(onClick = onOpen, shape = RoundedCornerShape(12.dp), color = pal.accent) {
            Text("Try another PDF", color = pal.onAccent, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp))
        }
    }
}

@Composable
private fun CenterColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Column(
            Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) { content() }
    }
}

package studio.sparkcube.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.sparkcube.cadence.core.model.Boundary
import studio.sparkcube.cadence.core.model.Step

/** The whole reader: header, body (empty/scanned/error/reading), controls, transport, overlay. */
@Composable
fun ReaderScreen(state: ReaderState, onOpen: () -> Unit) {
    Box(Modifier.fillMaxSize().background(CadenceColors.Paper)) {
        Column(Modifier.fillMaxSize()) {
            Header(state, onOpen)
            when {
                state.loading -> Centered { CircularProgressIndicator(color = CadenceColors.Accent) }
                state.errorMessage != null -> ErrorMessage(state.errorMessage!!, onOpen)
                state.docName == null -> EmptyState(onOpen)
                !state.hasTextLayer -> ScannedMessage(onOpen)
                else -> {
                    ReadingSurface(state, Modifier.weight(1f))
                    ControlRail(state)
                    TransportBar(state)
                }
            }
        }
        if (state.recallDue) RecallOverlay(onContinue = { state.continueRecall() })
    }
}

@Composable
private fun Header(state: ReaderState, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Cadence", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = CadenceColors.Ink)
        Spacer(Modifier.width(16.dp))
        state.docName?.let {
            Text(it, fontSize = 13.sp, color = CadenceColors.Muted)
        }
        Spacer(Modifier.weight(1f))
        if (state.docName != null) {
            Text("${(state.progress * 100).toInt()}%", fontSize = 13.sp, color = CadenceColors.Faint)
            Spacer(Modifier.width(12.dp))
        }
        if (!state.sayAvailable) {
            Text("`say` unavailable", fontSize = 12.sp, color = CadenceColors.Error)
            Spacer(Modifier.width(12.dp))
        }
        Button(
            onClick = onOpen,
            colors = ButtonDefaults.buttonColors(containerColor = CadenceColors.Accent),
        ) { Text("Open PDF", color = Color.White) }
    }
}

@Composable
private fun ReadingSurface(state: ReaderState, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.activeIndex) {
        if (state.steps.isNotEmpty()) listState.animateScrollToItem(state.activeIndex)
    }
    Box(modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(CadenceColors.Card),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(28.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(state.steps) { i, step ->
                    SentenceRow(step, active = i == state.activeIndex)
                }
            }
        }
    }
}

@Composable
private fun SentenceRow(step: Step, active: Boolean) {
    val isSection = step.boundary == Boundary.SECTION
    Column {
        Text(
            text = step.text,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (active) CadenceColors.Highlight else Color.Transparent)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            fontSize = if (isSection) 20.sp else 17.sp,
            fontWeight = if (isSection || active) FontWeight.SemiBold else FontWeight.Normal,
            color = CadenceColors.Ink,
        )
        // Upcoming-pause marker sized by the boundary.
        if (step.boundary != Boundary.SENTENCE) {
            PauseMarker(step.boundary)
        }
    }
}

@Composable
private fun PauseMarker(boundary: Boundary) {
    val width = if (boundary == Boundary.SECTION) 64.dp else 32.dp
    Row(Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(width).background(CadenceColors.AccentSoft).clip(RoundedCornerShape(2.dp)).padding(2.dp)) {
            Spacer(Modifier.width(width))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            if (boundary == Boundary.SECTION) "section pause" else "paragraph pause",
            fontSize = 10.sp,
            color = CadenceColors.Faint,
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

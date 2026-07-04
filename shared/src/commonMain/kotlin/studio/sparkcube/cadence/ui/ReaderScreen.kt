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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
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
                    MetaBar(state)
                    if (state.pageCount > 1) PageBar(state)
                    state.resumeHint?.let { ResumeBanner(it) }
                    state.playbackNote?.let { PlaybackBanner(it) }
                    BookmarkBar(state)
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
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Cadence", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = CadenceColors.Ink)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onOpen,
            colors = ButtonDefaults.buttonColors(containerColor = CadenceColors.Accent),
        ) { Text("Open", color = Color.White) }
    }
}

/** Document name + progress %. Name ellipsizes; nothing overflows on a phone. */
@Composable
private fun MetaBar(state: ReaderState) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            state.docName ?: "",
            fontSize = 13.sp,
            color = CadenceColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text("${(state.progress * 100).toInt()}%", fontSize = 13.sp, color = CadenceColors.Faint)
    }
}

@Composable
private fun PageBar(state: ReaderState) {
    var field by remember { mutableStateOf("") }
    fun go() {
        field.trim().toIntOrNull()?.let { state.jumpToPage(it) }
        field = ""
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Page ${state.currentPage} / ${state.pageCount}", fontSize = 12.sp, color = CadenceColors.Muted)
        Spacer(Modifier.weight(1f))
        OutlinedTextField(
            value = field,
            onValueChange = { v -> field = v.filter { it.isDigit() }.take(5) },
            singleLine = true,
            placeholder = { Text("Pg", fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { go() }),
            modifier = Modifier.width(96.dp),
        )
        Spacer(Modifier.width(6.dp))
        OutlinedButton(onClick = { go() }, enabled = field.isNotBlank()) { Text("Go") }
    }
}

@Composable
private fun ResumeBanner(text: String) {
    Row(
        Modifier.fillMaxWidth().background(CadenceColors.AccentSoft).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("↻  $text", fontSize = 12.sp, color = CadenceColors.Accent, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PlaybackBanner(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚠ $text", fontSize = 12.sp, color = CadenceColors.Error)
    }
}

@Composable
private fun ReadingSurface(state: ReaderState, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.activeIndex) {
        if (state.steps.isNotEmpty()) listState.animateScrollToItem(state.activeIndex)
    }
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(CadenceColors.Card)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
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
            fontSize = if (isSection) 19.sp else 17.sp,
            fontWeight = if (isSection || active) FontWeight.SemiBold else FontWeight.Normal,
            color = CadenceColors.Ink,
        )
        if (step.boundary != Boundary.SENTENCE) {
            Text(
                if (isSection) "— section pause —" else "— paragraph pause —",
                fontSize = 10.sp,
                color = CadenceColors.Faint,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

package studio.sparkcube.cadence.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.sparkcube.cadence.core.model.Boundary
import studio.sparkcube.cadence.core.model.Step

@Composable
fun ReaderScreen(state: ReaderState, onOpen: () -> Unit) {
    val pal = LocalPalette.current
    var settingsOpen by remember { mutableStateOf(false) }
    var bookmarksOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(pal.paper)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                state,
                onOpen = onOpen,
                onSettings = { settingsOpen = true },
                onBookmarks = { bookmarksOpen = true },
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(pal.hairline))

            when {
                state.loading -> Centered { CircularProgressIndicator(color = pal.accent) }
                state.errorMessage != null -> ErrorMessage(state.errorMessage!!, onOpen)
                state.docName == null -> EmptyState(onOpen)
                !state.hasTextLayer -> ScannedMessage(onOpen)
                else -> {
                    state.resumeHint?.let { Banner("↻  $it", pal.accent, pal.accentSoft) }
                    state.playbackNote?.let { Banner("⚠  $it", pal.error, pal.card) }
                    ReadingSurface(state, Modifier.weight(1f))
                    BottomBar(state)
                }
            }
        }

        if (state.recallDue) RecallOverlay(onContinue = { state.continueRecall() })
        if (settingsOpen) Popover(onDismiss = { settingsOpen = false }) { SettingsPanel(state) }
        if (bookmarksOpen) Popover(onDismiss = { bookmarksOpen = false }) { BookmarksPanel(state) }
    }
}

@Composable
private fun TopBar(state: ReaderState, onOpen: () -> Unit, onSettings: () -> Unit, onBookmarks: () -> Unit) {
    val pal = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            state.docName ?: "Cadence",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = if (state.docName == null) pal.ink else pal.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (state.docName != null && state.hasTextLayer) {
            PillButton("☆ ${state.bookmarkList.size}", onBookmarks)
            Spacer(Modifier.width(8.dp))
            AaButton(onSettings)
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            onClick = onOpen,
            shape = RoundedCornerShape(10.dp),
            color = pal.accent,
        ) {
            Text("Open", color = pal.onAccent, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun ReadingSurface(state: ReaderState, modifier: Modifier) {
    val pal = LocalPalette.current
    val listState = rememberLazyListState()
    LaunchedEffect(state.activeIndex) {
        if (state.steps.isNotEmpty()) listState.animateScrollToItem(state.activeIndex)
    }
    Box(modifier.fillMaxWidth().background(pal.paper)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().widthIn(max = 720.dp).fillMaxHeight()
                .padding(horizontal = 28.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 40.dp),
        ) {
            itemsIndexed(state.steps) { i, step ->
                SentenceRow(step, active = i == state.activeIndex, sizeSp = state.readingSizeSp, font = state.readingFont)
            }
        }
    }
}

@Composable
private fun SentenceRow(step: Step, active: Boolean, sizeSp: Float, font: ReadingFont) {
    val pal = LocalPalette.current
    val isSection = step.boundary == Boundary.SECTION
    val topPad = if (isSection) 22.dp else 0.dp
    val botPad = when (step.boundary) {
        Boundary.SECTION -> 10.dp
        Boundary.PARAGRAPH -> 14.dp
        Boundary.SENTENCE -> 2.dp
    }
    Text(
        text = step.text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPad, bottom = botPad)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) pal.highlight else Color.Transparent)
            .padding(horizontal = if (active) 10.dp else 0.dp, vertical = if (active) 6.dp else 2.dp),
        fontSize = (if (isSection) sizeSp * 1.32f else sizeSp).sp,
        lineHeight = (sizeSp * 1.62f).sp,
        fontFamily = font.family,
        fontWeight = if (isSection) FontWeight.Bold else if (active) FontWeight.Medium else FontWeight.Normal,
        color = if (active) pal.highlightInk else pal.ink,
    )
}

@Composable
private fun BottomBar(state: ReaderState) {
    val pal = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(pal.paper)) {
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = pal.accent,
            trackColor = pal.hairline,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Text(
                    if (state.pageCount > 0) "Page ${state.currentPage} / ${state.pageCount}" else "",
                    fontSize = 12.sp, color = pal.faint, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundIcon("◀") { state.prev() }
                PlayButton(playing = state.playing) { state.togglePlay() }
                RoundIcon("▶") { state.next() }
                RoundIcon("⤓") { state.nextSection() } // next section
            }
            Box(Modifier.weight(1f)) {
                Text(
                    if (state.currentWpm > 0) "${state.currentWpm} wpm" else "",
                    fontSize = 12.sp, color = pal.faint, textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// --- small building blocks --------------------------------------------------

@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Surface(onClick = onClick, shape = CircleShape, color = pal.accent, modifier = Modifier.size(52.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (playing) "❚❚" else "▶", color = pal.onAccent, fontSize = if (playing) 15.sp else 18.sp)
        }
    }
}

@Composable
private fun RoundIcon(glyph: String, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Surface(onClick = onClick, shape = CircleShape, color = pal.card, border = androidx.compose.foundation.BorderStroke(1.dp, pal.hairline), modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(glyph, color = pal.ink, fontSize = 14.sp) }
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = pal.card, border = androidx.compose.foundation.BorderStroke(1.dp, pal.hairline)) {
        Text(text, color = pal.ink, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

@Composable
private fun AaButton(onClick: () -> Unit) {
    val pal = LocalPalette.current
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = pal.card, border = androidx.compose.foundation.BorderStroke(1.dp, pal.hairline)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.Bottom) {
            Text("A", color = pal.ink, fontSize = 12.sp)
            Text("A", color = pal.ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Banner(text: String, ink: Color, bg: Color) {
    Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(text, fontSize = 12.sp, color = ink, fontWeight = FontWeight.Medium)
    }
}

/** Scrim + top-right card. Tap outside to dismiss. */
@Composable
private fun Popover(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val pal = LocalPalette.current
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(12.dp).widthIn(max = 340.dp).fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)).background(pal.card)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            content()
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

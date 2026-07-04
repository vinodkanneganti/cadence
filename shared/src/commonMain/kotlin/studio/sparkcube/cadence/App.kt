package studio.sparkcube.cadence

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import studio.sparkcube.cadence.core.bookmark.BookmarkStore
import studio.sparkcube.cadence.core.bookmark.NoopBookmarkStore
import studio.sparkcube.cadence.core.pdf.PdfExtractor
import studio.sparkcube.cadence.core.tts.Speaker
import studio.sparkcube.cadence.ui.DarkPalette
import studio.sparkcube.cadence.ui.LightPalette
import studio.sparkcube.cadence.ui.LocalPalette
import studio.sparkcube.cadence.ui.PickedPdf
import studio.sparkcube.cadence.ui.ReaderScreen
import studio.sparkcube.cadence.ui.ReaderState

/**
 * App root. [pickPdf] is supplied by the platform (desktop: an AWT file dialog),
 * keeping file access out of commonMain. [initialPdf] auto-reopens the last
 * bookmarked document on launch. Keyboard transport per PRD R7:
 * space = play/pause, ←/→ = prev/next sentence.
 */
@Composable
fun App(
    pickPdf: suspend () -> PickedPdf?,
    bookmarks: BookmarkStore = NoopBookmarkStore,
    now: () -> Long = { 0L },
    initialPdf: suspend () -> PickedPdf? = { null },
) {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val speaker = remember { Speaker() }
        val extractor = remember { PdfExtractor() }
        val state = remember { ReaderState(scope, speaker, extractor, bookmarks, now) }

        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            focus.requestFocus()
            initialPdf()?.let { state.open(it) } // resume last session, if any
        }

        fun openPdf() = scope.launch { pickPdf()?.let { state.open(it) } }

        CompositionLocalProvider(
            LocalPalette provides if (state.dark) DarkPalette else LightPalette,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.Spacebar -> { state.togglePlay(); true }
                            Key.DirectionRight -> { state.next(); true }
                            Key.DirectionLeft -> { state.prev(); true }
                            else -> false
                        }
                    },
            ) {
                ReaderScreen(state, onOpen = { openPdf() })
            }
        }
    }
}

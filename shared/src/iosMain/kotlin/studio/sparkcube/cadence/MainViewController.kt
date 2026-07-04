package studio.sparkcube.cadence

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFAbsoluteTimeGetCurrent
import platform.UIKit.UIViewController
import studio.sparkcube.cadence.core.bookmark.IosBookmarkStore
import studio.sparkcube.cadence.ui.PickedPdf

/**
 * iOS entry point — the Xcode app hosts this UIViewController. Bookmarks persist to
 * the Documents directory; `now` is wall-clock millis. The document picker
 * (UIDocumentPicker) is wired in stage 2; until then [pickPdf] returns null.
 */
@OptIn(ExperimentalForeignApi::class)
fun MainViewController(): UIViewController = ComposeUIViewController {
    App(
        pickPdf = { pickPdf() },
        bookmarks = IosBookmarkStore(),
        // CFAbsoluteTime is seconds since 2001 — monotonic enough to order bookmarks.
        now = { (CFAbsoluteTimeGetCurrent() * 1000).toLong() },
        initialPdf = { null },
    )
}

private suspend fun pickPdf(): PickedPdf? = null

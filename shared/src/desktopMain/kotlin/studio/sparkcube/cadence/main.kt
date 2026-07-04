package studio.sparkcube.cadence

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import studio.sparkcube.cadence.core.bookmark.FileBookmarkStore
import studio.sparkcube.cadence.ui.PickedPdf
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

private val bookmarks = FileBookmarkStore()

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Cadence",
        state = rememberWindowState(width = 1000.dp, height = 720.dp),
    ) {
        App(
            pickPdf = ::pickPdfFile,
            bookmarks = bookmarks,
            now = { System.currentTimeMillis() },
            initialPdf = ::lastSessionPdf,
        )
    }
}

/** Native file dialog on the EDT; bytes read off the IO dispatcher. */
private suspend fun pickPdfFile(): PickedPdf? {
    val chosen = withContext(Dispatchers.Swing) {
        val dialog = FileDialog(null as Frame?, "Open a PDF", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.endsWith(".pdf", ignoreCase = true) }
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) File(dir, file) else null
    } ?: return null

    return withContext(Dispatchers.IO) {
        PickedPdf(name = chosen.name, bytes = chosen.readBytes(), path = chosen.absolutePath)
    }
}

/** On launch, reopen the most recently read document (if its file still exists). */
private suspend fun lastSessionPdf(): PickedPdf? = withContext(Dispatchers.IO) {
    val mark = bookmarks.loadLast() ?: return@withContext null
    val path = mark.filePath ?: return@withContext null
    val file = File(path)
    if (file.exists()) PickedPdf(name = file.name, bytes = file.readBytes(), path = file.absolutePath) else null
}

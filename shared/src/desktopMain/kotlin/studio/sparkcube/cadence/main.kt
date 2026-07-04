package studio.sparkcube.cadence

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import studio.sparkcube.cadence.ui.PickedPdf
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Cadence",
        state = rememberWindowState(width = 1000.dp, height = 720.dp),
    ) {
        App(pickPdf = ::pickPdfFile)
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

    return withContext(Dispatchers.IO) { PickedPdf(chosen.name, chosen.readBytes()) }
}

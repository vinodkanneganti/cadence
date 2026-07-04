package studio.sparkcube.cadence

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Cadence",
        state = rememberWindowState(width = 900.dp, height = 640.dp),
    ) {
        App()
    }
}

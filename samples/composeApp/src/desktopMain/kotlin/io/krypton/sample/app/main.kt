package io.krypton.sample.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

actual fun platformName(): String = "Desktop (JVM, ${System.getProperty("os.arch")})"

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 760.dp, height = 660.dp),
        title = "Krypton — Encrypted Chat (KMP)",
    ) {
        App()
    }
}

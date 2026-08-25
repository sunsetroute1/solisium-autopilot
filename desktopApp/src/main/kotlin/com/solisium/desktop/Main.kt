package com.solisium.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.solisium.desktop.theme.Palette
import com.solisium.desktop.theme.SolisiumTheme
import com.solisium.desktop.ui.AppShell

fun main() = application {
    val state = rememberWindowState(
        size = DpSize(1240.dp, 820.dp),
        position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = "Solisium Autopilot",
        state = state,
    ) {
        window.minimumSize = java.awt.Dimension(960, 640)
        SolisiumTheme {
            val scope = rememberCoroutineScope()
            val model = remember { AppModel(scope) }
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().background(Palette.Base),
            ) {
                AppShell(model)
            }
        }
    }
}

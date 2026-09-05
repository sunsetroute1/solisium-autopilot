package com.solisium.desktop

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

object DesktopClipboard {
    fun readText(): String? = runCatching {
        val clip = Toolkit.getDefaultToolkit().systemClipboard
        if (clip.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            clip.getData(DataFlavor.stringFlavor) as? String
        } else {
            null
        }
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

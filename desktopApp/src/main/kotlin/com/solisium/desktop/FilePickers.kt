package com.solisium.desktop

import java.awt.FileDialog
import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser

/**
 * Native file choosers. These block the AWT event thread while open, which is the
 * normal contract for a modal dialog, so they must be called from a click handler
 * rather than from a background coroutine.
 */
object FilePickers {
    fun pickFile(title: String, extension: String, startIn: Path? = null): Path? {
        val dialog = FileDialog(null as java.awt.Frame?, title, FileDialog.LOAD)
        startIn?.let { dialog.directory = it.toString() }
        dialog.setFilenameFilter { _, name -> name.endsWith(extension, ignoreCase = true) }
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return File(dir, file).toPath()
    }

    /** `FileDialog` cannot select directories on Windows, so this one uses Swing. */
    fun pickDirectory(title: String, startIn: Path? = null): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            startIn?.let { currentDirectory = it.toFile() }
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.toPath()
        } else {
            null
        }
    }
}

package ru.fixprice.tools.terminal_pinned_tab_guard.detector

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal object TerminalPinnedTabDetector {

    fun shouldBlockClose(
        project: Project,
        file: VirtualFile?,
    ): Boolean {
        if (file == null) {
            return false
        }

        val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
        if (!isPinned(fileEditorManager, file)) {
            return false
        }

        return isTerminalVirtualFile(file)
    }

    private fun isPinned(
        fileEditorManager: FileEditorManagerEx,
        file: VirtualFile,
    ): Boolean {
        val currentWindow = fileEditorManager.currentWindow
        if (currentWindow != null && currentWindow.isFileOpen(file)) {
            return currentWindow.isFilePinned(file)
        }

        return fileEditorManager.windows
            .firstOrNull { it.isFileOpen(file) }
            ?.isFilePinned(file)
            ?: false
    }

    internal fun isTerminalVirtualFile(file: VirtualFile): Boolean {
        val fileTypeName = file.fileType.name
        if (fileTypeName.equals("Terminal", ignoreCase = true)) {
            return true
        }

        val fileClassName = file.javaClass.name
        if (fileClassName.contains("terminal", ignoreCase = true)) {
            return true
        }

        val fileTypeClassName = file.fileType.javaClass.name
        if (fileTypeClassName.contains("terminal", ignoreCase = true)) {
            return true
        }

        val protocol = file.fileSystem.protocol
        if (protocol.contains("terminal", ignoreCase = true)) {
            return true
        }

        val path = file.path
        if (path.startsWith("terminal://") || path.startsWith("terminal:")) {
            return true
        }

        return false
    }
}

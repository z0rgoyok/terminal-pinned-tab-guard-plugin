package ru.fixprice.tools.terminal_pinned_tab_guard

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import ru.fixprice.tools.terminal_pinned_tab_guard.detector.TerminalPinnedTabDetector

internal object TerminalEditorTabPinning {

    fun pinFileIfTerminal(project: Project, file: VirtualFile) {
        if (!TerminalPinnedTabDetector.isTerminalVirtualFile(file)) {
            return
        }

        val manager = FileEditorManagerEx.getInstanceEx(project)
        val window = manager.windows.firstOrNull { it.isFileOpen(file) } ?: return
        window.setFilePinned(file, true)
    }

    fun pinOpenTerminalTabs(project: Project) {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        for (window in manager.windows) {
            for (file in window.files) {
                if (TerminalPinnedTabDetector.isTerminalVirtualFile(file)) {
                    window.setFilePinned(file, true)
                }
            }
        }
    }
}

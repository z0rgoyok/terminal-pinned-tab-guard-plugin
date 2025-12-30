package ru.fixprice.tools.terminal_pinned_tab_guard

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import ru.fixprice.tools.terminal_pinned_tab_guard.detector.TerminalPinnedTabDetector

internal object TerminalEditorTabSelector {

    fun selectTerminal(project: Project, index: Int) {
        val target = findTerminalAtIndex(project, index) ?: return
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val options = FileEditorOpenOptions()
            .withSelectAsCurrent(true)
            .withRequestFocus(true)
            .withReuseOpen(true)
        manager.openFile(target.second, target.first, options)
        target.first.requestFocus(true)
    }

    fun hasTerminalAtIndex(project: Project, index: Int): Boolean {
        return findTerminalAtIndex(project, index) != null
    }

    private fun findTerminalAtIndex(project: Project, index: Int): Pair<EditorWindow, VirtualFile>? {
        if (index <= 0) {
            return null
        }

        val manager = FileEditorManagerEx.getInstanceEx(project)
        val windows = buildList {
            val current = manager.currentWindow
            if (current != null) {
                add(current)
            }
            manager.windows.filterNot { it == current }.forEach { add(it) }
        }

        if (windows.isEmpty()) {
            return null
        }

        val terminalTabs = mutableListOf<Pair<EditorWindow, VirtualFile>>()
        for (window in windows) {
            for (file in window.fileList) {
                if (TerminalPinnedTabDetector.isTerminalVirtualFile(file)) {
                    terminalTabs.add(window to file)
                }
            }
        }

        return terminalTabs.getOrNull(index - 1)
    }
}

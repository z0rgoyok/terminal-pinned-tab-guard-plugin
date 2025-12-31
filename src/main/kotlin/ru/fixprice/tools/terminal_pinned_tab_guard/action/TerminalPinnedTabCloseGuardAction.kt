package ru.fixprice.tools.terminal_pinned_tab_guard.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile
import ru.fixprice.tools.terminal_pinned_tab_guard.detector.TerminalPinnedTabDetector

internal class TerminalPinnedTabCloseGuardAction(
    private val originalAction: AnAction,
) : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            originalAction.actionPerformed(e)
            return
        }

        val fileToClose = getFileToClose(project, e)
        if (TerminalPinnedTabDetector.shouldBlockClose(project, fileToClose)) {
            if (fileToClose != null) {
                selectNextUnpinnedTab(project, fileToClose)
            }
            return
        }

        originalAction.actionPerformed(e)
    }

    override fun update(e: AnActionEvent) {
        originalAction.update(e)
    }

    private fun getFileToClose(
        project: Project,
        e: AnActionEvent,
    ): VirtualFile? {
        val manager = FileEditorManager.getInstance(project)
        val managerEx = FileEditorManagerEx.getInstanceEx(project)
        val inputEvent = e.inputEvent
        val preferSelectedFile = inputEvent == null || inputEvent is java.awt.event.KeyEvent
        if (preferSelectedFile) {
            return managerEx.currentWindow?.selectedComposite?.file
                ?: manager.selectedFiles.firstOrNull()
        }

        val eventFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (eventFile != null && manager.isFileOpen(eventFile)) {
            return eventFile
        }

        val eventFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
            ?.firstOrNull { manager.isFileOpen(it) }
        if (eventFiles != null) {
            return eventFiles
        }

        return managerEx.currentWindow?.selectedComposite?.file
            ?: manager.selectedFiles.firstOrNull()
    }

    private fun selectNextUnpinnedTab(
        project: Project,
        fileToClose: VirtualFile,
    ) {
        val manager = FileEditorManagerEx.getInstanceEx(project)
        val window = manager.currentWindow?.takeIf { it.isFileOpen(fileToClose) }
            ?: manager.windows.firstOrNull { it.isFileOpen(fileToClose) }
            ?: return
        if (window.selectedComposite?.file != fileToClose) {
            return
        }

        val siblings = manager.getSiblings(fileToClose).toList()
        if (siblings.size < 2) {
            return
        }

        val currentIndex = siblings.indexOf(fileToClose)
        if (currentIndex == -1) {
            return
        }

        val nextFile = findNextUnpinnedFile(window, siblings, currentIndex) ?: return
        val composite = window.getComposite(nextFile) ?: return
        window.setSelectedComposite(composite, true)
    }

    private fun findNextUnpinnedFile(
        window: com.intellij.openapi.fileEditor.impl.EditorWindow,
        siblings: List<VirtualFile>,
        currentIndex: Int,
    ): VirtualFile? {
        for (offset in 1 until siblings.size) {
            val nextIndex = (currentIndex + offset) % siblings.size
            val candidate = siblings[nextIndex]
            if (!window.isFilePinned(candidate)) {
                return candidate
            }
        }

        return null
    }
}

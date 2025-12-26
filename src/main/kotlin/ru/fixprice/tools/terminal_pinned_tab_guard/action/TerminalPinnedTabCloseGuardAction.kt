package ru.fixprice.tools.terminal_pinned_tab_guard.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
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
            return
        }

        originalAction.actionPerformed(e)
    }

    override fun update(e: AnActionEvent) {
        originalAction.update(e)
    }

    private fun getFileToClose(
        project: com.intellij.openapi.project.Project,
        e: AnActionEvent,
    ): VirtualFile? {
        val eventFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (eventFile != null) {
            return eventFile
        }

        val eventFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.firstOrNull()
        if (eventFiles != null) {
            return eventFiles
        }

        return FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
    }
}

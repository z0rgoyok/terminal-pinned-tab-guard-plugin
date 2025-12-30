package ru.fixprice.tools.terminal_pinned_tab_guard.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager
import ru.fixprice.tools.terminal_pinned_tab_guard.TerminalPinnedTabEditorOpener

internal class OpenTerminalInEditorAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        TerminalPinnedTabEditorOpener.openNow(project)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null &&
            ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) != null
    }

    private companion object {
        private const val TOOL_WINDOW_ID = "Terminal"
    }
}

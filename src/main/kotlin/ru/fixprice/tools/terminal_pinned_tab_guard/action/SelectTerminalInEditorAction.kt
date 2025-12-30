package ru.fixprice.tools.terminal_pinned_tab_guard.action

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import ru.fixprice.tools.terminal_pinned_tab_guard.TerminalEditorTabSelector

internal class SelectTerminalInEditorAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val index = resolveIndex() ?: return
        TerminalEditorTabSelector.selectTerminal(project, index)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val index = resolveIndex()
        e.presentation.isEnabledAndVisible = project != null &&
            index != null &&
            TerminalEditorTabSelector.hasTerminalAtIndex(project, index)
    }

    private fun resolveIndex(): Int? {
        val actionId = ActionManager.getInstance().getId(this) ?: return null
        val suffix = actionId.takeLastWhile { it.isDigit() }
        return suffix.toIntOrNull()
    }
}

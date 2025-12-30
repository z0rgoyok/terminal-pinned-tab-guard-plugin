package ru.fixprice.tools.terminal_pinned_tab_guard.action

import com.intellij.openapi.actionSystem.ActionManager

internal object TerminalPinnedTabCloseGuardActionReplacer {

    private val actionIds = listOf(
        "CloseContent",
        "CloseActiveTab",
        "CloseEditor",
        "Terminal.CloseTab",
        "Terminal.CloseSession",
    )

    fun replaceCloseActions() {
        val actionManager = ActionManager.getInstance()
        for (actionId in actionIds) {
            replaceAction(actionManager, actionId)
        }
    }

    private fun replaceAction(
        actionManager: ActionManager,
        actionId: String,
    ) {
        val originalAction = actionManager.getAction(actionId) ?: return
        if (originalAction is TerminalPinnedTabCloseGuardAction) {
            return
        }

        actionManager.replaceAction(
            actionId,
            TerminalPinnedTabCloseGuardAction(originalAction),
        )
    }
}

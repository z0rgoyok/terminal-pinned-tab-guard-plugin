package ru.fixprice.tools.terminal_pinned_tab_guard.action

import com.intellij.openapi.actionSystem.ActionManager
import java.util.concurrent.atomic.AtomicBoolean

internal object TerminalPinnedTabCloseGuardActionReplacer {

    private val isReplaced = AtomicBoolean(false)

    fun replaceCloseActions() {
        if (!isReplaced.compareAndSet(false, true)) {
            return
        }

        val actionManager = ActionManager.getInstance()
        replaceAction(actionManager, "CloseContent")
        replaceAction(actionManager, "CloseActiveTab")
        replaceAction(actionManager, "CloseEditor")
        replaceAction(actionManager, "Terminal.CloseTab")
        replaceAction(actionManager, "Terminal.CloseSession")
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

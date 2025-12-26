package ru.fixprice.tools.terminal_pinned_tab_guard

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import ru.fixprice.tools.terminal_pinned_tab_guard.action.TerminalPinnedTabCloseGuardActionReplacer

internal class TerminalPinnedTabCloseGuardStartupActivity : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            TerminalPinnedTabCloseGuardActionReplacer.replaceCloseActions()
        }
    }
}

package ru.fixprice.tools.terminal_pinned_tab_guard

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import ru.fixprice.tools.terminal_pinned_tab_guard.action.TerminalPinnedTabCloseGuardActionReplacer
import ru.fixprice.tools.terminal_pinned_tab_guard.detector.TerminalPinnedTabDetector

internal class TerminalPinnedTabCloseGuardStartupActivity : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            onTerminalAvailable(project)
            TerminalEditorFileDropSupport.installForOpenFiles(project)
            TerminalEditorTabPinning.pinOpenTerminalTabs(project)
        }

        project.messageBus.connect().subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                @Suppress("OVERRIDE_DEPRECATION")
                override fun toolWindowRegistered(id: String) {
                    if (id == TERMINAL_TOOL_WINDOW_ID) {
                        onTerminalAvailable(project)
                    }
                }

                @Suppress("OVERRIDE_DEPRECATION")
                override fun toolWindowShown(toolWindow: ToolWindow) {
                    if (toolWindow.id == TERMINAL_TOOL_WINDOW_ID) {
                        onTerminalAvailable(project)
                    }
                }
            },
        )

        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(manager: FileEditorManager, file: com.intellij.openapi.vfs.VirtualFile) {
                    TerminalEditorFileDropSupport.installForEditors(file, manager.getAllEditors(file))
                    TerminalEditorTabPinning.pinFileIfTerminal(project, file)
                    if (TerminalPinnedTabDetector.isTerminalVirtualFile(file)) {
                        TerminalEditorRestoreTracker.markTerminalEditorOpen(project)
                    }
                }
            },
        )

        ApplicationManager.getApplication().messageBus.connect(project).subscribe(
            ProjectManager.TOPIC,
            object : ProjectManagerListener {
                override fun projectClosingBeforeSave(closingProject: Project) {
                    if (closingProject == project) {
                        TerminalEditorRestoreTracker.updateFromOpenFiles(project)
                    }
                }
            },
        )
    }

    private fun onTerminalAvailable(project: Project) {
        TerminalPinnedTabCloseGuardActionReplacer.replaceCloseActions()
        TerminalPinnedTabEditorOpener.openOnStartup(project)
    }

    private companion object {
        private const val TERMINAL_TOOL_WINDOW_ID = "Terminal"
    }
}

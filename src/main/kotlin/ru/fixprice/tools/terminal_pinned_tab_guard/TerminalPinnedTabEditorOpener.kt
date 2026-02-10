package ru.fixprice.tools.terminal_pinned_tab_guard

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import java.util.concurrent.atomic.AtomicBoolean
import ru.fixprice.tools.terminal_pinned_tab_guard.detector.TerminalPinnedTabDetector

internal object TerminalPinnedTabEditorOpener {

    private const val TOOL_WINDOW_ID = "Terminal"
    private const val NEW_TAB_ACTION_ID = "Terminal.NewTab"
    private const val MOVE_TO_EDITOR_ACTION_ID = "Terminal.MoveToEditor"
    private val TOOL_WINDOW_CONTENT_MANAGER_KEY =
        DataKey.create<ContentManager>("toolWindowContentManager")
    private val CONTENT_KEY = DataKey.create<Content>("content")
    private val openedOnStartup = AtomicBoolean(false)
    private const val MOVE_TO_EDITOR_ATTEMPTS = 3

    fun openNow(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            openNewTerminalInEditor(project)
        }
    }

    fun openOnStartup(project: Project) {
        if (!TerminalEditorRestoreTracker.shouldRestore(project)) {
            return
        }

        if (openedOnStartup.get()) {
            return
        }

        ApplicationManager.getApplication().invokeLater {
            if (openedOnStartup.get()) {
                return@invokeLater
            }

            if (openIfNeeded(project)) {
                openedOnStartup.set(true)
            }
        }
    }

    private fun openIfNeeded(project: Project): Boolean {
        val existingTerminalFile = findTerminalEditorFile(project)
        if (existingTerminalFile != null) {
            TerminalEditorRestoreTracker.markTerminalEditorOpen(project)
            TerminalEditorTabPinning.pinFileIfTerminal(project, existingTerminalFile)
            return true
        }

        return openNewTerminalInEditor(project)
    }

    private fun openNewTerminalInEditor(project: Project): Boolean {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return false
        val actionManager = ActionManager.getInstance()
        val newTabAction = actionManager.getAction(NEW_TAB_ACTION_ID) ?: return false
        val moveAction = actionManager.getAction(MOVE_TO_EDITOR_ACTION_ID) ?: return false
        val contentManager = toolWindow.contentManager
        val existingContents = contentManager.contents.toSet()

        val dataContext = createDataContext(project, toolWindow, null)

        val newTabEvent = AnActionEvent.createFromDataContext(
            ActionPlaces.UNKNOWN,
            newTabAction.templatePresentation.clone(),
            dataContext,
        )
        newTabAction.actionPerformed(newTabEvent)

        ApplicationManager.getApplication().invokeLater {
            moveNewTerminalToEditor(
                project,
                toolWindow,
                moveAction,
                existingContents,
                MOVE_TO_EDITOR_ATTEMPTS,
            )
        }

        return true
    }

    private fun moveNewTerminalToEditor(
        project: Project,
        toolWindow: ToolWindow,
        moveAction: com.intellij.openapi.actionSystem.AnAction,
        existingContents: Set<Content>,
        remainingAttempts: Int,
    ) {
        val contentManager = toolWindow.contentManager
        val content = findContentToMove(contentManager, existingContents, remainingAttempts)
        if (content == null) {
            if (remainingAttempts > 1) {
                ApplicationManager.getApplication().invokeLater {
                    moveNewTerminalToEditor(
                        project,
                        toolWindow,
                        moveAction,
                        existingContents,
                        remainingAttempts - 1,
                    )
                }
            }
            return
        }

        val moveContext = createDataContext(project, toolWindow, content)

        val moveEvent = AnActionEvent.createFromDataContext(
            ActionPlaces.UNKNOWN,
            moveAction.templatePresentation.clone(),
            moveContext,
        )

        val moved = invokeMoveToEditor(moveAction, moveEvent, project, content)
        if (moved) {
            TerminalEditorRestoreTracker.markTerminalEditorOpen(project)
            TerminalEditorTabPinning.pinOpenTerminalTabs(project)
            return
        }

        if (remainingAttempts > 1) {
            ApplicationManager.getApplication().invokeLater {
                moveNewTerminalToEditor(
                    project,
                    toolWindow,
                    moveAction,
                    existingContents,
                    remainingAttempts - 1,
                )
            }
        }
    }

    private fun invokeMoveToEditor(
        moveAction: com.intellij.openapi.actionSystem.AnAction,
        moveEvent: AnActionEvent,
        project: Project,
        content: Content,
    ): Boolean {
        val terminalWidget = findTerminalWidgetByContent(content)
        val moveMethod = moveAction.javaClass.methods.firstOrNull { method ->
            method.name == "actionPerformedInTerminalToolWindow" &&
                method.parameterTypes.size == 4 &&
                method.parameterTypes[0] == AnActionEvent::class.java &&
                method.parameterTypes[1] == Project::class.java &&
                method.parameterTypes[2] == Content::class.java
        }

        val movedWithTerminalContext = if (terminalWidget != null && moveMethod != null) {
            runCatching {
                moveMethod.trySetAccessible()
                moveMethod.invoke(moveAction, moveEvent, project, content, terminalWidget)
            }.isSuccess
        } else {
            false
        }

        if (!movedWithTerminalContext) {
            moveAction.actionPerformed(moveEvent)
        }

        return findTerminalEditorFile(project) != null
    }

    private fun findContentToMove(
        contentManager: ContentManager,
        existingContents: Set<Content>,
        remainingAttempts: Int,
    ): Content? {
        val newContent = contentManager.contents.firstOrNull { it !in existingContents }
        if (newContent != null) {
            return newContent
        }
        if (remainingAttempts > 1) {
            return null
        }

        return contentManager.selectedContent
            ?: contentManager.contents.lastOrNull()
    }

    private fun createDataContext(
        project: Project,
        toolWindow: ToolWindow,
        content: Content?,
    ): DataContext {
        val builder = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(PlatformDataKeys.TOOL_WINDOW, toolWindow)
            .add(TOOL_WINDOW_CONTENT_MANAGER_KEY, toolWindow.contentManager)
        if (content != null) {
            builder.add(CONTENT_KEY, content)
        }
        return builder.build()
    }

    private fun findTerminalWidgetByContent(content: Content): Any? {
        return runCatching {
            val managerClass = Class.forName("org.jetbrains.plugins.terminal.TerminalToolWindowManager")
            val method = managerClass.getMethod("findWidgetByContent", Content::class.java)
            method.invoke(null, content)
        }.getOrNull()
    }

    private fun findTerminalEditorFile(project: Project): com.intellij.openapi.vfs.VirtualFile? {
        return FileEditorManager.getInstance(project)
            .openFiles
            .firstOrNull { TerminalPinnedTabDetector.isTerminalVirtualFile(it) }
    }
}

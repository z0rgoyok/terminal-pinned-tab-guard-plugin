package ru.fixprice.tools.terminal_pinned_tab_guard

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import ru.fixprice.tools.terminal_pinned_tab_guard.detector.TerminalPinnedTabDetector

internal object TerminalEditorRestoreTracker {

    fun shouldRestore(project: Project): Boolean {
        return TerminalPinnedTabGuardState.getInstance(project).shouldRestoreTerminalInEditor()
    }

    fun markTerminalEditorOpen(project: Project) {
        TerminalPinnedTabGuardState.getInstance(project).setRestoreTerminalInEditor(true)
    }

    fun updateFromOpenFiles(project: Project) {
        val hasTerminalEditor = FileEditorManager.getInstance(project)
            .openFiles
            .any { TerminalPinnedTabDetector.isTerminalVirtualFile(it) }
        TerminalPinnedTabGuardState.getInstance(project).setRestoreTerminalInEditor(hasTerminalEditor)
    }
}

package ru.fixprice.tools.terminal_pinned_tab_guard

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@State(
    name = "TerminalPinnedTabGuardState",
    storages = [Storage("terminalPinnedTabGuard.xml")],
)
internal class TerminalPinnedTabGuardState :
    PersistentStateComponent<TerminalPinnedTabGuardState.State> {

    data class State(
        var restoreTerminalInEditor: Boolean = false,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun shouldRestoreTerminalInEditor(): Boolean = state.restoreTerminalInEditor

    fun setRestoreTerminalInEditor(value: Boolean) {
        state.restoreTerminalInEditor = value
    }

    companion object {
        fun getInstance(project: Project): TerminalPinnedTabGuardState =
            project.getService(TerminalPinnedTabGuardState::class.java)
    }
}

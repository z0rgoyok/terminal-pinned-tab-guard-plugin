package ru.fixprice.tools.terminal_pinned_tab_guard

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities
import ru.fixprice.tools.terminal_pinned_tab_guard.detector.TerminalPinnedTabDetector

internal object TerminalCloseShortcutGuard {

    fun install(project: Project) {
        val dispatcher = KeyEventDispatcher { event ->
            handleEvent(project, event)
        }
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        focusManager.addKeyEventDispatcher(dispatcher)
        Disposer.register(project) {
            focusManager.removeKeyEventDispatcher(dispatcher)
        }
    }

    private fun handleEvent(project: Project, event: KeyEvent): Boolean {
        if (project.isDisposed) {
            return false
        }
        if (event.id != KeyEvent.KEY_PRESSED) {
            return false
        }
        if (!isCloseShortcut(event)) {
            return false
        }

        val manager = FileEditorManagerEx.getInstanceEx(project)
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
        if (!SwingUtilities.isDescendingFrom(focusOwner, manager.component)) {
            return false
        }

        val window = manager.currentWindow ?: return false
        val selectedFile = window.selectedComposite?.file ?: return false
        if (!TerminalPinnedTabDetector.isTerminalVirtualFile(selectedFile)) {
            return false
        }
        if (!window.isFilePinned(selectedFile)) {
            return false
        }

        event.consume()
        selectNextUnpinnedTab(manager, window, selectedFile)
        return true
    }

    private fun isCloseShortcut(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.VK_W) {
            return false
        }

        val modifiers = event.modifiersEx
        val hasShift = modifiers and InputEvent.SHIFT_DOWN_MASK != 0
        val hasAlt = modifiers and InputEvent.ALT_DOWN_MASK != 0
        if (hasShift || hasAlt) {
            return false
        }

        return if (SystemInfo.isMac) {
            modifiers and InputEvent.META_DOWN_MASK != 0
        } else {
            modifiers and InputEvent.CTRL_DOWN_MASK != 0
        }
    }

    private fun selectNextUnpinnedTab(
        manager: FileEditorManagerEx,
        window: com.intellij.openapi.fileEditor.impl.EditorWindow,
        currentFile: com.intellij.openapi.vfs.VirtualFile,
    ) {
        val siblings = manager.getSiblings(currentFile).toList()
        if (siblings.size < 2) {
            return
        }

        val currentIndex = siblings.indexOf(currentFile)
        if (currentIndex == -1) {
            return
        }

        for (offset in 1 until siblings.size) {
            val nextIndex = (currentIndex + offset) % siblings.size
            val candidate = siblings[nextIndex]
            if (window.isFilePinned(candidate)) {
                continue
            }

            val composite = window.getComposite(candidate) ?: continue
            window.setSelectedComposite(composite, true)
            return
        }
    }
}

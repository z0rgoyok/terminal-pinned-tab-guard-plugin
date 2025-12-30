package ru.fixprice.tools.terminal_pinned_tab_guard

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.ui.TerminalWidget
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.io.IOException
import javax.swing.JComponent

internal object TerminalEditorFileDropSupport {

    private const val DROP_TARGET_PROPERTY = "TerminalPinnedTabGuard.FileDropTargetInstalled"

    fun installForOpenFiles(project: Project) {
        val manager = FileEditorManager.getInstance(project)
        for (file in manager.openFiles) {
            installForEditors(file, manager.getAllEditors(file))
        }
    }

    fun installForEditors(file: VirtualFile, editors: Array<FileEditor>) {
        for (editor in editors) {
            installForEditor(file, editor)
        }
    }

    private fun installForEditor(file: VirtualFile, editor: FileEditor) {
        val component = editor.component
        if (component.getClientProperty(DROP_TARGET_PROPERTY) == true) {
            return
        }

        if (component.dropTarget != null) {
            return
        }

        val terminalView = getTerminalView(file)
        if (terminalView != null) {
            installDropTarget(
                component,
                terminalView.focusComponent,
                editor,
            ) { text ->
                terminalView.sendText(text)
            }
            return
        }

        val terminalWidget = getClassicTerminalWidget(file)
        if (terminalWidget != null) {
            installDropTarget(
                component,
                terminalWidget.component,
                editor,
            ) { text ->
                sendToClassicTerminal(terminalWidget, text)
            }
        }
    }

    private fun installDropTarget(
        component: JComponent,
        focusComponent: JComponent?,
        disposable: com.intellij.openapi.Disposable,
        sendText: (String) -> Unit,
    ) {
        val dropTarget = DropTarget(
            component,
            DnDConstants.ACTION_COPY,
            TerminalFileDropTarget(focusComponent, sendText),
            true,
        )
        component.dropTarget = dropTarget
        component.putClientProperty(DROP_TARGET_PROPERTY, true)
        Disposer.register(disposable) {
            component.dropTarget = null
            component.putClientProperty(DROP_TARGET_PROPERTY, null)
        }
    }

    private fun sendToClassicTerminal(terminalWidget: TerminalWidget, text: String) {
        terminalWidget.ttyConnectorAccessor.executeWithTtyConnector { connector ->
            if (connector.isConnected) {
                try {
                    connector.write(text)
                } catch (_: IOException) {
                }
            }
        }
    }

    private class TerminalFileDropTarget(
        private val focusComponent: JComponent?,
        private val sendText: (String) -> Unit,
    ) : DropTargetAdapter() {

        override fun dragEnter(dtde: DropTargetDragEvent) {
            if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY)
            } else {
                dtde.rejectDrag()
            }
        }

        override fun drop(dtde: DropTargetDropEvent) {
            if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                dtde.rejectDrop()
                return
            }

            dtde.acceptDrop(DnDConstants.ACTION_COPY)
            val files = extractFiles(dtde)
            if (files.isEmpty()) {
                dtde.dropComplete(false)
                return
            }

            val text = files.joinToString(" ") { quoteForShell(it.path) }
            ApplicationManager.getApplication().invokeLater {
                focusComponent?.requestFocusInWindow()
                sendText(text)
            }
            dtde.dropComplete(true)
        }

        private fun extractFiles(event: DropTargetDropEvent): List<File> {
            return try {
                val data = event.transferable.getTransferData(DataFlavor.javaFileListFlavor)
                (data as? List<*>)?.filterIsInstance<File>().orEmpty()
            } catch (_: IOException) {
                emptyList()
            } catch (_: UnsupportedOperationException) {
                emptyList()
            }
        }
    }

    private fun quoteForShell(path: String): String {
        if (path.isEmpty()) {
            return "''"
        }

        val needsQuoting = path.any { ch ->
            !(ch.isLetterOrDigit() || ch == '/' || ch == '.' || ch == '_' || ch == '-')
        }
        if (!needsQuoting) {
            return path
        }

        return "'" + path.replace("'", "'\\''") + "'"
    }

    private fun getTerminalView(file: VirtualFile): TerminalViewAccess? {
        val viewFileClass = terminalViewFileClass ?: return null
        if (!viewFileClass.isInstance(file)) {
            return null
        }

        val terminalView = runCatching {
            viewFileClass.getMethod("getTerminalView").invoke(file)
        }.getOrNull() ?: return null

        val focusComponent = runCatching {
            terminalView.javaClass.getMethod("getPreferredFocusableComponent").invoke(terminalView) as? JComponent
        }.getOrNull()

        val sendMethod = runCatching {
            terminalView.javaClass.getMethod("sendText", String::class.java)
        }.getOrNull() ?: return null

        return TerminalViewAccess(
            focusComponent = focusComponent,
            sendText = { text -> sendMethod.invoke(terminalView, text) },
        )
    }

    private fun getClassicTerminalWidget(file: VirtualFile): TerminalWidget? {
        val classicFileClass = classicTerminalFileClass ?: return null
        if (!classicFileClass.isInstance(file)) {
            return null
        }

        return runCatching {
            classicFileClass.getMethod("getTerminalWidget").invoke(file) as? TerminalWidget
        }.getOrNull()
    }

    private data class TerminalViewAccess(
        val focusComponent: JComponent?,
        val sendText: (String) -> Unit,
    )

    private val terminalViewFileClass: Class<*>? = runCatching {
        Class.forName("com.intellij.terminal.frontend.editor.TerminalViewVirtualFile")
    }.getOrNull()

    private val classicTerminalFileClass: Class<*>? = runCatching {
        Class.forName("org.jetbrains.plugins.terminal.vfs.TerminalSessionVirtualFileImpl")
    }.getOrNull()
}

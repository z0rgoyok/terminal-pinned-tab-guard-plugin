package ru.fixprice.tools.terminal_pinned_tab_guard

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.terminal.ui.TerminalWidget
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.Image
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal object TerminalEditorFileDropSupport {

    private const val DROP_TARGET_PROPERTY = "TerminalPinnedTabGuard.FileDropTargetInstalled"
    private const val PASTE_HANDLER_PROPERTY = "TerminalPinnedTabGuard.ImagePasteHandlerInstalled"
    private const val PASTE_DEDUP_KEY = "terminal.pinned.tab.guard.lastPasteAt"
    private const val PASTE_DEDUP_WINDOW_MS = 100L
    private val imageExtensions = setOf(
        "png",
        "jpg",
        "jpeg",
        "gif",
        "bmp",
        "tiff",
        "tif",
        "webp",
        "heic",
        "heif",
    )
    private val pasteTargetsLock = Any()
    private val pasteTargets = mutableListOf<PasteTarget>()
    @Volatile
    private var pasteDispatcherInstalled = false
    private val pasteDispatcher = ImagePasteDispatcher()

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
        val terminalView = getTerminalView(file)
        if (terminalView != null) {
            installDropTarget(
                component,
                terminalView.focusComponent,
                editor,
            ) { text ->
                terminalView.sendText(text)
            }
            installImagePasteHandler(
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
            installImagePasteHandler(
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
        if (component.getClientProperty(DROP_TARGET_PROPERTY) == true) {
            return
        }

        if (component.dropTarget != null) {
            return
        }

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

    private fun installImagePasteHandler(
        component: JComponent,
        focusComponent: JComponent?,
        disposable: com.intellij.openapi.Disposable,
        sendText: (String) -> Unit,
    ) {
        if (component.getClientProperty(PASTE_HANDLER_PROPERTY) == true) {
            return
        }

        val rootComponent = focusComponent ?: component
        val target = PasteTarget(rootComponent, focusComponent, sendText)
        synchronized(pasteTargetsLock) {
            pasteTargets.add(target)
            if (!pasteDispatcherInstalled) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(pasteDispatcher)
                pasteDispatcherInstalled = true
            }
        }
        component.putClientProperty(PASTE_HANDLER_PROPERTY, true)
        Disposer.register(disposable) {
            synchronized(pasteTargetsLock) {
                pasteTargets.remove(target)
                if (pasteTargets.isEmpty() && pasteDispatcherInstalled) {
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(pasteDispatcher)
                    pasteDispatcherInstalled = false
                }
            }
            component.putClientProperty(PASTE_HANDLER_PROPERTY, null)
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
            if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
                dtde.isDataFlavorSupported(DataFlavor.imageFlavor)
            ) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY)
            } else {
                dtde.rejectDrag()
            }
        }

        override fun drop(dtde: DropTargetDropEvent) {
            if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor) &&
                !dtde.isDataFlavorSupported(DataFlavor.imageFlavor)
            ) {
                dtde.rejectDrop()
                return
            }

            dtde.acceptDrop(DnDConstants.ACTION_COPY)
            val text = when {
                dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
                    val files = extractFiles(dtde)
                    if (files.isEmpty()) {
                        dtde.dropComplete(false)
                        return
                    }
                    files.joinToString(" ") { quoteForShell(it.path) }
                }
                dtde.isDataFlavorSupported(DataFlavor.imageFlavor) -> {
                    val imageFile = extractImageFile(dtde.transferable)
                    if (imageFile == null) {
                        dtde.dropComplete(false)
                        return
                    }
                    quoteForShell(imageFile.path)
                }
                else -> {
                    dtde.dropComplete(false)
                    return
                }
            }
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
            } catch (_: UnsupportedFlavorException) {
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

    private class ImagePasteDispatcher : java.awt.KeyEventDispatcher {

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.id != KeyEvent.KEY_PRESSED) {
                return false
            }
            if (!isPasteShortcut(event)) {
                return false
            }
            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return false
            val target = synchronized(pasteTargetsLock) {
                pasteTargets.firstOrNull { pasteTarget ->
                    SwingUtilities.isDescendingFrom(focusOwner, pasteTarget.rootComponent)
                }
            } ?: return false

            val transferable = getClipboardTransferable() ?: return false
            if (!supportsImageContent(transferable)) {
                return false
            }
            if (shouldSkipDuplicatePaste(event.`when`)) {
                event.consume()
                return true
            }

            val imageFile = extractImageFile(transferable) ?: return false
            val text = quoteForShell(imageFile.path)
            ApplicationManager.getApplication().invokeLater {
                target.focusComponent?.requestFocusInWindow()
                target.sendText(text)
            }
            event.consume()
            return true
        }
    }

    private data class PasteTarget(
        val rootComponent: JComponent,
        val focusComponent: JComponent?,
        val sendText: (String) -> Unit,
    )

    private fun getClipboardTransferable(): Transferable? {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        return try {
            clipboard.getContents(null)
        } catch (_: IllegalStateException) {
            return null
        }
    }

    private fun supportsImageContent(transferable: Transferable): Boolean {
        if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            return true
        }
        if (extractImageFileFromFileList(transferable) != null) {
            return true
        }
        return findImageStreamFlavor(transferable) != null
    }

    private fun extractImageFile(transferable: Transferable): File? {
        val fileFromList = extractImageFileFromFileList(transferable)
        if (fileFromList != null) {
            return fileFromList
        }

        val bufferedImage = extractBufferedImage(transferable) ?: return null
        return writeImageToTemp(bufferedImage)
    }

    private fun extractImageFileFromFileList(transferable: Transferable): File? {
        if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return null
        }

        val files = try {
            val data = transferable.getTransferData(DataFlavor.javaFileListFlavor)
            (data as? List<*>)?.filterIsInstance<File>().orEmpty()
        } catch (_: IOException) {
            emptyList()
        } catch (_: UnsupportedFlavorException) {
            emptyList()
        } catch (_: UnsupportedOperationException) {
            emptyList()
        }

        return files.firstOrNull { file -> isImageFile(file) }
    }

    private fun isImageFile(file: File): Boolean {
        val name = file.name
        val dotIndex = name.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == name.lastIndex) {
            return false
        }
        val extension = name.substring(dotIndex + 1).lowercase()
        return extension in imageExtensions
    }

    private fun extractBufferedImage(transferable: Transferable): BufferedImage? {
        if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            val image = try {
                transferable.getTransferData(DataFlavor.imageFlavor) as? Image
            } catch (_: IOException) {
                null
            } catch (_: UnsupportedFlavorException) {
                null
            } catch (_: UnsupportedOperationException) {
                null
            } ?: return null

            return toBufferedImage(image)
        }

        val flavor = findImageStreamFlavor(transferable) ?: return null
        val data = try {
            transferable.getTransferData(flavor)
        } catch (_: IOException) {
            return null
        } catch (_: UnsupportedFlavorException) {
            return null
        } catch (_: UnsupportedOperationException) {
            return null
        }

        val image = when (data) {
            is InputStream -> data.use { ImageIO.read(it) }
            is ByteArray -> ImageIO.read(ByteArrayInputStream(data))
            is ByteBuffer -> {
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                ImageIO.read(ByteArrayInputStream(bytes))
            }
            else -> null
        } ?: return null

        return toBufferedImage(image)
    }

    private fun toBufferedImage(image: Image): BufferedImage? {
        if (image is BufferedImage) {
            return image
        }

        val width = image.getWidth(null)
        val height = image.getHeight(null)
        if (width <= 0 || height <= 0) {
            return null
        }

        val converted = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = converted.createGraphics()
        graphics.drawImage(image, 0, 0, null)
        graphics.dispose()
        return converted
    }

    private fun findImageStreamFlavor(transferable: Transferable): DataFlavor? {
        return transferable.transferDataFlavors.firstOrNull { flavor ->
            flavor.primaryType.equals("image", ignoreCase = true) &&
                (InputStream::class.java.isAssignableFrom(flavor.representationClass) ||
                    ByteArray::class.java == flavor.representationClass ||
                    ByteBuffer::class.java.isAssignableFrom(flavor.representationClass))
        }
    }

    private fun writeImageToTemp(bufferedImage: BufferedImage): File? {
        val preferredTempDir = if (SystemInfo.isMac) {
            File("/tmp").takeIf { it.isDirectory && it.canWrite() }
        } else {
            null
        }

        val file = kotlin.runCatching {
            if (preferredTempDir != null) {
                File.createTempFile("terminal-pinned-tab-guard-", ".png", preferredTempDir)
            } else {
                File.createTempFile("terminal-pinned-tab-guard-", ".png")
            }
        }.getOrNull() ?: return null
        file.deleteOnExit()

        return if (kotlin.runCatching { ImageIO.write(bufferedImage, "png", file) }.getOrDefault(false)) {
            file
        } else {
            file.delete()
            null
        }
    }

    private fun shouldSkipDuplicatePaste(eventTime: Long): Boolean {
        synchronized(System.getProperties()) {
            val lastTime = System.getProperty(PASTE_DEDUP_KEY)?.toLongOrNull() ?: 0L
            if (eventTime - lastTime < PASTE_DEDUP_WINDOW_MS) {
                return true
            }
            System.setProperty(PASTE_DEDUP_KEY, eventTime.toString())
            return false
        }
    }

    private fun isPasteShortcut(event: KeyEvent): Boolean {
        val primaryDown = if (SystemInfo.isMac) event.isMetaDown else event.isControlDown
        if (!primaryDown || event.isAltDown) {
            return false
        }
        return event.keyCode == KeyEvent.VK_V
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

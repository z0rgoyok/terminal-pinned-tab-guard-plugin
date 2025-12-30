# Terminal Pinned Tab Guard

An IntelliJ/Android Studio plugin that enhances the terminal-in-editor experience by protecting pinned terminal tabs from accidental closure and providing additional convenience features.

## Features

### Pinned Tab Close Protection

The core feature of this plugin prevents closing terminal tabs that have been moved to the editor area and pinned. When you press `Cmd+W` (macOS) or `Ctrl+W` (Windows/Linux) on a pinned terminal tab, the close action is blocked.

The plugin intercepts the following IDE actions:
- `CloseContent`
- `CloseActiveTab`
- `CloseEditor`
- `Terminal.CloseTab`
- `Terminal.CloseSession`

### Open Terminal in Editor

Adds a new action **Tools > Open Terminal in Editor** that:
1. Creates a new terminal tab
2. Automatically moves it to the editor area
3. Pins it so it won't be accidentally closed

### Auto-Pin Terminal Tabs

When any terminal tab is opened in the editor area, the plugin automatically pins it. This ensures all editor-based terminals are protected from accidental closure by default.

### Session Restore

The plugin remembers when you had a terminal open in the editor area. On the next project startup, it will automatically restore a terminal in the editor if one was previously open.

### File & Image Drag & Drop

Drag files or images from the project tree (or Finder/Explorer) directly onto a terminal tab in the editor. The plugin will:
- Insert the file path(s) at the cursor position
- Properly quote paths containing special characters
- Handle multiple files (space-separated)
- For dragged images: save as a temporary PNG file and insert the path

### Clipboard Image Paste

When the clipboard contains an image, pressing `Cmd+V` (macOS) or `Ctrl+V` (Windows/Linux) in a terminal tab in the editor will:
- Save the image as a temporary PNG
- Insert the file path at the cursor position

## Requirements

- IntelliJ IDEA, Android Studio, or any JetBrains IDE (2023.1+)
- Terminal plugin (bundled with most JetBrains IDEs)

## Installation

### From Disk

1. Build the plugin (see below) or download from releases
2. Go to **Settings > Plugins > ⚙️ > Install Plugin from Disk...**
3. Select the ZIP file from `build/distributions/`

## Building

From the repository root:

```bash
./gradlew buildPlugin
```

The plugin archive will be created at:
```
build/distributions/terminal-pinned-tab-guard-plugin-<version>.zip
```

## Usage

1. Open a terminal in your IDE
2. Right-click the terminal tab and select **Move to Editor** (or use the **Tools > Open Terminal in Editor** action)
3. The terminal tab will be automatically pinned
4. Pressing `Cmd+W` / `Ctrl+W` will no longer close the terminal tab
5. To close the tab, unpin it first (right-click > Unpin Tab)

## How It Works

The plugin works by:
1. Wrapping the standard IDE close actions with custom handlers
2. Detecting if the target file is a terminal virtual file
3. Checking if the tab is pinned in the editor
4. Blocking the close action only for pinned terminal tabs

## License

MIT

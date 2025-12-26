# Terminal Pinned Tab Guard (Android Studio / IntelliJ)

Плагин запрещает закрытие вкладки Terminal, перемещённой в редактор через **Move to Editor**, если вкладка закреплена (pinned).

Технически перехватывается стандартное действие закрытия (`Cmd+W`) через обёртку над IDE action’ами `CloseContent`/`CloseActiveTab`/`CloseEditor`, а также terminal action’ами `Terminal.CloseTab`/`Terminal.CloseSession`.

## Сборка

Из корня репозитория:

```bash
./gradlew -p tools/terminal-pinned-tab-guard-plugin buildPlugin
```

Готовый архив плагина появится в:

`tools/terminal-pinned-tab-guard-plugin/build/distributions/`

## Установка в Android Studio

1. `Settings | Plugins | ⚙️ | Install Plugin from Disk...`
2. Выберите ZIP из `build/distributions`.

# Cobblemon OneBlock

Eine eigenständige **Core Mod** für Fabric 1.21.1: OneBlock-Skyblock-Gameplay, voll integriert
mit [Cobblemon](https://cobblemon.com/) — Islands, Progression, Partys, Permissions,
Trigger-Events, Biome-Editor und Setup-Wizard in einer einzigen Drag-and-Drop-Server-Mod.

> Arbeitstitel — finaler Projektname noch offen.

## Module

| Modul | Sprache | Zweck |
|---|---|---|
| `oneblock-api` | Java | Öffentliche API für Addon-Entwickler (Events, Manager-Interfaces, Extension-Points). Addons hängen nur von diesem Modul ab. |
| `oneblock-core` | Kotlin | Vollständige Implementierung. Harte Dependency auf Cobblemon. |
| `example-addon` | Java | Referenz-Addon für Dritt-Entwickler. Baut nur gegen `oneblock-api`. |

## Für Server-Admins

Installation, Wizard, Config-Dateien, Permission-Nodes, Backups und MySQL-Umstellung:
[`ADMIN.md`](ADMIN.md)

## Für Addon-Entwickler

Vollständige Dokumentation: [`API.md`](API.md) — Events, Manager, Extension-Points
(eigene Trigger-Events, eigene Loot-Provider) und die Semver-Strategie.
Lauffähiges Beispiel: [`example-addon/`](example-addon)

## Status

In Entwicklung. Roadmap und alle Design-Entscheidungen: [`PROJECT_PLAN.md`](PROJECT_PLAN.md)

## Build

```bash
./gradlew build
```

Benötigt Java 21. Storage läuft per Default über eingebettetes SQLite (zero-config);
MySQL/MariaDB ist optional über `config/oneblock/database.json5` konfigurierbar.

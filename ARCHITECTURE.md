# Architektur

> Stand: 2026-10-19, Phase 1 (Foundation) implementiert. Spielinhalte gibt es noch nicht. Dieses Dokument beschreibt den Code auf `phase/1-foundation` und kennzeichnet Geplantes als solches.

## Überblick

Ein serverseitiger Fabric-Mod (`cobblemon_oneblock`, Java 21) in **einem** JAR (`oneblock-core`, Gradle-Modul). Clients brauchen nur den passenden Cobblemon-Client. Gebündelt ist nur `sqlite-jdbc` (Jar-in-Jar).

```text
 Fabric / Minecraft 1.21.1 / Cobblemon 1.8.2
        │  ServerLifecycleEvents, CommandRegistrationCallback
        ▼
 bootstrap.OneBlockMod ──► bootstrap.ModRuntime (eine Instanz pro Serverlauf)
        │                      │
        │                      ├─ config.ConfigManager      (Datei, Prüfung, Reload)
        │                      ├─ storage.DbExecutor        (DB-Thread, begrenzte Queue)
        │                      │    └─ storage.StorageBootstrap → MigrationRunner, WorldBinding,
        │                      │                                  operation.OperationJournal, OperationReconciler
        │                      ├─ bootstrap.Lifecycle       (Zustand + Gründe)
        │                      ├─ diagnostics.Diagnostics   (Diagnose-IDs)
        │                      └─ cobblemon.ShutdownRecall  (ADR 005)
        └─ command.ObCommand   (/ob status, /ob reload) ── localization.Messages (de_de, en_us)

 domain.grid (GridGeometry, SpiralIndex): reines Java, noch von keinem Feature benutzt (Phase 2)
```

## Pakete

| Paket | Inhalt | Minecraft-Abhängigkeit |
|---|---|---|
| `bootstrap` | Entrypoint, `ModRuntime`, `Lifecycle`, `LifecycleState` | ja (`ModRuntime`, `OneBlockMod`) |
| `config` | Schema, JSON5-Leser, Loader, Manager | nein (Gson) |
| `diagnostics` | Fehlerklassen, Diagnose-IDs | nein |
| `domain.grid` | Slot-Grid, Spirale | nein |
| `storage` | DB-Thread, Verbindungen, Migrationen, Welt-Bindung, Speicher-Start | nein |
| `operation` | Vorgangs-Journal, Audit-Log, Abgleich | nein |
| `localization` | Serverseitige Texte | nein (Gson) |
| `command` | Brigadier-Befehle | ja |
| `cobblemon` | Cobblemon-Zugriffe (nur `ShutdownRecall`) | ja |
| `util` | Atomares Schreiben | nein |

Abweichung von der Vorschlagsstruktur in Spec 13: Es gibt noch keine Ports/Interfaces. Journal und Speicher sprechen direkt SQLite an und werden gegen echte temporäre SQLite-Dateien getestet. Ein Port entsteht erst, wenn eine zweite Implementierung oder eine Test-Naht ihn braucht (Spec 13 [v4.1]).

## Threads

| Thread | Darf | Darf nicht |
|---|---|---|
| Server-Thread | Welt, Entities, Cobblemon, Befehle, Lifecycle-Entscheidungen | auf DB oder Dateien warten (einzige Ausnahme: Shutdown, s. u.) |
| `cobblemon-oneblock-db-N` (Daemon, 1 Thread) | SQL, eigene Dateien (DB, Marker, Backups) | Minecraft- oder Cobblemon-Objekte anfassen; gibt nur unveränderliche Records zurück |
| `cobblemon-oneblock-io` (Daemon, 1 Thread) | Config-Datei lesen und prüfen bei `/ob reload` | Zustand ändern |

- Ergebnisse des DB-Threads kommen per `whenCompleteAsync(…, server)` bzw. `handleAsync(…, server)` zurück auf den Server-Thread.
- Die DB-Queue ist begrenzt (`storage.queueCapacity`). Ist sie voll, schlägt die Aufgabe sofort mit `StorageException` fehl.
- **Shutdown-Ausnahme:** `DbExecutor.shutdown` wartet blockierend bis `storage.shutdownTimeoutSeconds`. Ein Deadlock ist ausgeschlossen, weil der DB-Thread nie auf den Server-Thread wartet.
- Die Config wird beim Boot einmal synchron gelesen, vor dem ersten Tick. Das Spiel läuft zu dem Zeitpunkt noch nicht.

## Boot (SERVER_STARTING)

1. `Lifecycle = STARTING`.
2. Config laden; fehlt die Datei, wird die kommentierte Standarddatei atomar geschrieben. Bei Fehlern: Standardwerte + `OB-CONFIG`-Diagnose.
3. Weltpfad (`LevelResource.ROOT`) und Seed (`WorldData.worldGenOptions().seed()`) bestimmen.
4. Auf dem DB-Thread: fehlt die DB, ist aber der Marker vorhanden → Abbruch **vor** dem Öffnen. Sonst öffnen (WAL, `synchronous=FULL`, `foreign_keys`, `busy_timeout`) → `quick_check` → Migrationen (bei bestehender DB vorher geprüftes Backup) → Welt-Bindung → offene Vorgänge abgleichen.
5. Zurück auf dem Server-Thread: Fehler → `RECOVERY_MODE`; blockierende Vorgänge → `RECOVERY_MODE`; Config ungültig oder Setup offen → `SETUP_PENDING`; sonst `READY`.

## Shutdown (SERVER_STOPPING)

1. `Lifecycle = STOPPING`, keine neuen Aktionen.
2. Alle geladenen Welten: besessene, ausgesandte `PokemonEntity` → `Pokemon.recall()` (ADR 005).
3. DB-Queue abarbeiten, Verbindung schließen (Timeout → `OB-STORAGE`-Diagnose).
4. I/O-Thread beenden.

Danach speichert und schließt Minecraft die Welten.

## Datenmodell (Schema v1)

| Tabelle | Zweck |
|---|---|
| `schema_migrations` | angewandte Migrationen mit SHA-256 |
| `world_meta` | Installations-ID, Seed, Erstellzeit und -version, später `setup_completed` |
| `operations` | durable Vorgänge: `request_id` UNIQUE, `stage`, `status` (CHECK), `revision`, `intent`, `result`, `error` |
| `audit_log` | Audit-Einträge, optional mit Fremdschlüssel auf `operations` |

Alle Tabellen sind `STRICT`. `PRAGMA application_id = 0x4F42434D` kennzeichnet die Datei, `user_version` = Schema-Version.

## Verifizierte Integrationspunkte

| Zweck | API | Beleg |
|---|---|---|
| Lebenszyklus | `ServerLifecycleEvents.SERVER_STARTING/STOPPING/STOPPED` | Phase 1, S1–S10 |
| Befehle | `CommandRegistrationCallback.EVENT` | S4 |
| Client-Sprache | `ServerPlayer.clientInformation().language()` | kompiliert; Laufzeit: Owner-Test O1 |
| Seed | `MinecraftServer.getWorldData().worldGenOptions().seed()` | S1, S3 |
| Rückruf | `PokemonEntity.getOwnerUUID()`, `Pokemon.isWild()`, `Pokemon.recall()` | S8 (2/2) |
| Geplant für Phase 2+ | Void-Dimension, Spawn-Influence, Kampf/Fang | `docs/POC_RESULTS.md` |

## Testbarkeit

- `domain`, `config`, `storage`, `operation`, `localization`, `diagnostics`: JUnit/jqwik ohne Minecraft, SQLite gegen temporäre Dateien.
- Durability: Kindprozess + `kill -9` (`CrashDurabilityTest`).
- Minecraft-Teile (`ModRuntime`, `ObCommand`, `ShutdownRecall`): Dedicated-Server-Szenarien S1–S10 (`docs/TEST_MATRIX.md`). Fabric GameTests kommen mit den Welt-Features in Phase 2.

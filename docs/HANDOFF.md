# Übergabe: Wo es weitergeht

> Stand: 2026-10-20. Dieses Dokument ersetzt den Chatverlauf. Eine neue Sitzung liest **zuerst diese Datei**, dann `docs/PROGRESS.md`.

## In einem Satz

Phase 0A, 0B und 1 sind fertig. **Phase 1 wartet auf die Owner-Tests und die Abnahme.** Danach: Merge nach `main`, Tag `phase-1`, Start von Phase 2.

## Neue Sitzung starten

Diesen Text in eine neue Claude-Code-Sitzung auf dem Repository einfügen:

```text
Lies docs/HANDOFF.md und docs/PROGRESS.md und arbeite nach CLAUDE.md weiter.
Arbeite auf dem Branch phase/1-foundation (oder, falls Phase 1 schon abgenommen ist, auf main).
Stand: <hier eintragen, z. B. "Owner-Tests Phase 1: alle ok, Phase 1 abgenommen, starte Phase 2">
```

## Git

| Branch | Inhalt | Stand |
|---|---|---|
| `main` | Spec, Entscheidungen, Phase-0B-Doku | Phase 0B; Phase-1-Code ist **noch nicht** gemergt |
| `phase/1-foundation` | Phase-1-Code + Doku (aktueller Stand) | gepusht, CI grün |
| `spike/0b-feasibility` | Wegwerf-Spike aus Phase 0B (nie in Produktionscode mergen) | gepusht |
| `claude/tender-keller-7hqmwy` | alter Sitzungs-Branch aus Phase 0A, veraltet | kann gelöscht werden |

Nach der Abnahme von Phase 1:
1. `phase/1-foundation` nach `main` mergen (normaler Merge, kein Force-Push).
2. Tag `phase-1` auf den Merge-Commit setzen und pushen.
3. Für Phase 2 einen Branch `phase/2-safe-mvp` von `main` anlegen.

## Offene Punkte für den Owner

1. **Owner-Tests Phase 1** mit `OneBlock-Testserver-Phase1.zip` (Anleitung in `tools/testserver/ANLEITUNG.txt`):
   1. `/ob status` → „Einrichtung ausstehend“
   2. Client auf Englisch → `/ob status` auf Englisch
   3. `maxMembers: 99` → `/ob reload` → Fehler mit Zeilennummer
   4. `maxMembers: 6` → `/ob reload` → „Übernommen: setup.maxMembers“
   5. Kampf, Pokémon draußen lassen, `stop` → im Log „Recalled 1 sent-out Pokémon“, „Database closed cleanly“, keine „Exception stopping the server“; Fenster schließt sich
2. **Abnahme Phase 1** (OK im Chat).

Das Testpaket lässt sich jederzeit neu bauen: `tools/testserver/build-package.sh` → `build/testserver/OneBlock-Testserver.zip`.

## Was fertig ist

| Phase | Ergebnis | Nachweis |
|---|---|---|
| 0A | Spec v4.1 eingecheckt, Owner-Entscheidungen Nr. 1, 2, 9–11, 13–16 | `docs/DECISIONS.md` |
| 0B | Spikes: Void-Dimension, natürliche biomabhängige Cobblemon-Spawns, Spawn-Filter pro Insel, Kampf/Fang/NPC, SQLite, Grid, Hologramm. Owner-Tests am 2026-10-19 bestanden. Entscheidung Nr. 12 (Spawn-Weg) | `docs/POC_RESULTS.md`, `docs/adr/001`–`007` |
| 1 | Gradle, Config, SQLite (Migrationen, Backup, Welt-Bindung), Vorgangs-Journal, Zustände, Shutdown-Rückruf, `/ob status`, `/ob reload`, de/en, CI | 78 Tests grün, Server-Szenarien S1–S10 bestanden (`docs/TEST_MATRIX.md`) |

## Phase 2 (nächste Phase, erst nach Freigabe)

Inhalt laut `CLAUDE.md`: Void-Dimension, Hub, Setup-Wizard, Grid/Slots, Grundschutz, Create/Home, OneBlock mit Block-Loot. Leseplan: Spec 04, 05, 06, 07, 12.

Bausteine, die schon bereitliegen:
- Void-Dimension: Datapack-JSON aus dem Spike (`spike/0b-feasibility`, `src/main/resources/data/cobblemon_oneblock/`), ADR 002
- Grid: `domain.grid.GridGeometry`, `SpiralIndex` (mit Property-Tests)
- Durable Vorgänge: `operation.OperationJournal`, `OperationReconciler` (Phase 2 registriert den ersten Handler, z. B. `CREATE`)
- Setup-Flag: `world_meta.setup_completed` wird gelesen; der Wizard muss es schreiben
- Config: `setup.*` und `grid.*` existieren; „locked“ muss ab der ersten Insel greifen (`ConfigManager.apply(…, worldLocked=true)`)

## Wichtige Erkenntnisse (nicht im Code offensichtlich)

- **Cobblemon aus Java:** Kotlin-Interfaces ohne `jvm-default` → alle Methoden implementieren, Defaults über `DefaultImpls`. Bei `Observable.subscribe` ist die Überladung mit Ausdrucks-Lambda mehrdeutig, deshalb `subscribe(Consumer)` nutzen.
- **Kämpfe abbrechen nur mit `PokemonBattle.stop()`**, nie `end()`.
- **Shutdown-Fehler in Cobblemon 1.8.2** (ausgesandte Pokémon nach einem Kampf): mit Fake-Spielern 4/4, mit echtem Client 0/1. Abgesichert durch `ShutdownRecall` (ADR 005).
- **Fallende Blöcke und Wasser im Void:** Sand fällt ohne Unterbau weg, Wasser fließt ab. Relevant für OneBlock-Loot und Insel-Start (Risiko R-07).
- **Biomgrenzen** sind serverseitig um ±1 Biomzelle unscharf (R-08). `/fillbiome` schafft max. 32 768 Blöcke pro Aufruf.
- **Stein zählt für Cobblemon als natürlicher Boden** (Spawns auf Steininseln).
- **`/checkspawn`** lässt sich nicht von der Konsole nutzen; für Diagnose eigene Befehle bauen.
- **Testwerkzeuge:** Headless-Server mit Carpet-Fake-Spielern funktioniert für Spawns und Kämpfe; das Server-Skript `srv.sh` liegt auf `spike/0b-feasibility`.

## Dokumente

| Datei | Wofür |
|---|---|
| `CLAUDE.md` | Arbeitsregeln (immer gültig) |
| `docs/DECISIONS.md` | Owner-Entscheidungen (Vorrang vor der Spec) |
| `docs/spec/` | Spezifikation, Leseplan in `00-INDEX.md` |
| `docs/PROGRESS.md` | aktive Phase, Plan, erledigt, offen |
| `ARCHITECTURE.md`, `docs/adr/` | Architektur und Entscheidungen |
| `docs/POC_RESULTS.md` | geprüfte APIs und Spike-Messungen |
| `docs/TEST_MATRIX.md` | Tests, Invarianten, Akzeptanz |
| `docs/RISK_REGISTER.md`, `docs/DEPENDENCY_MATRIX.md` | Risiken, Versionen mit Hashes |
| `docs/CONFIG.md`, `docs/COMMANDS.md`, `docs/RECOVERY.md` | Admin-Doku |
| `tools/testserver/` | Startskripte und Paket-Builder für Owner-Testserver (auf `phase/1-foundation`, nach dem Merge auch auf `main`) |

# Fortschritt

> Stand: 2026-10-20 · Spec v4.1 · Übergabe und Neustart einer Sitzung: `docs/HANDOFF.md`

## Aktive Phase

**1 – Foundation: umgesetzt, wartet auf Owner-Abnahme.** Branch `phase/1-foundation`. Nach Abnahme: Merge nach `main` und Tag `phase-1`.

## Phase-1-Plan

Abnahmebedingung laut Spec 02 §22: Clean-Server-Boot mit Pflichtmods, Client-Join, DB-Migration, Crash- und Fail-Closed-Basistests.

| # | Feature | Anforderung | Abhängigkeit | Akzeptanztest | Rollback / Rollforward |
|---|---|---|---|---|---|
| 1 | Gradle-Projekt | Reproduzierbarer Build, gepinnte Versionen, ein Mod-JAR mit SQLite | ADR 001, 004, 007; `DEPENDENCY_MATRIX` | `./gradlew build` grün; JAR enthält genau `sqlite-jdbc` als Jar-in-Jar | Reiner Code, kein Datenzustand |
| 2 | Versionsprüfung | Falsche Cobblemon-Version → verständlicher Ladefehler | `fabric.mod.json` `depends` | Server mit Cobblemon 1.8.1 startet nicht und nennt die Ursache | – |
| 3 | Config | `config/oneblock/config.json5` mit Schema, Bereichen, Reload-Klassen, strikter Prüfung unbekannter Felder, atomarem Schreiben | Spec 12 §16 | Unit-Tests: Defaults, Bereichsfehler mit Feldpfad, Tippfehler, defekte Datei → letzte gültige Version bleibt | Ungültige Datei wird nie überschrieben; Default-Datei nur, wenn keine existiert |
| 4 | SQLite-Speicher | Eine DB pro Welt, ein DB-Thread mit begrenzter Queue, WAL, `synchronous=FULL`, Integritätsprüfung | ADR 004 | Unit-Tests: Queue voll → sichtbarer Fehler; Ergebnisse kommen nicht auf dem Aufrufer-Thread | DB-Datei wird nie automatisch gelöscht oder neu erstellt, wenn sie existiert |
| 5 | Migrationen | Nummerierte SQL-Migrationen mit Prüfsumme, Backup vor jeder Migration einer bestehenden DB, Downgrade-Schutz | Spec 11 §17 | Tests: frische DB, Wiederholung idempotent, manipulierte Migration → Abbruch, neueres Schema → Abbruch, Backup-Datei vorhanden und lesbar | Backup per `VACUUM INTO` vor der Migration; Restore = Backup-Datei zurückkopieren (dokumentiert in `RECOVERY.md`) |
| 6 | Welt-Bindung | DB gehört nachweislich zu dieser Welt | Spec 11 §5 | Tests: fremder Seed → `RECOVERY_MODE`; Marker vorhanden, DB fehlt → `RECOVERY_MODE` | Nur Lesen und Melden, keine automatische Reparatur |
| 7 | Vorgangs-Journal | Durable Vorgänge mit `requestId`, Stage, Revision; Audit-Log | Spec 11, Spec 13 | Tests: gleiche `requestId` → derselbe Vorgang; veraltete Stage → abgelehnt; offener Vorgang ohne Handler → `RECOVERY_MODE` | Journal ist nur anfügend; Vorgänge werden nie stillschweigend gelöscht |
| 8 | Startzustände | `STARTING`, `SETUP_PENDING`, `READY`, `READ_ONLY`, `RECOVERY_MODE`, `STOPPING` | Spec 13 Boot/Shutdown | Server bootet bei kaputter DB trotzdem, meldet `RECOVERY_MODE`, andere Mods laufen weiter | – |
| 9 | Shutdown | Mutationen sperren, Pokémon zurückrufen (ADR 005), DB-Queue leeren, Verbindung schließen | ADR 005 | Dedicated Server: Stopp nach Kampf ohne Exception; Stopp mit voller Queue innerhalb des Timeouts | – |
| 10 | Diagnose und Logging | Fehlerklassen mit Diagnose-ID, keine Stacktraces an Spieler | Spec 12 §18 | Unit-Test für ID-Format; Logzeile enthält ID und Klasse | – |
| 11 | Texte de/en | Serverseitige Übersetzung nach Client-Sprache, Fallback `en_us` | Spec 03 | Unit-Test: alle Schlüssel in beiden Sprachen vorhanden | – |
| 12 | Befehle | `/ob status`, `/ob reload` | Spec 12 | Dedicated Server: beide Befehle, Rechteprüfung | – |
| 13 | Grid-Domain | Produktive Grid-Mathematik mit Property-Tests | `POC_RESULTS` §8 | jqwik-Tests grün, Seed im Bericht | – |
| 14 | CI | GitHub Actions: JDK 21, Build, Tests, JAR-Artefakt | Spec 14 §21 | Workflow läuft grün auf dem Branch | – |
| 15 | Crash-Basistests | Hard-Kill während DB-Schreibvorgängen | Spec 11, 14 | Test-Prozess wird mit `kill -9` beendet; danach `integrity_check = ok`, bestätigte Zeilen vollständig | – |

Bewusst **nicht** in Phase 1: Void-Dimension, Hub, Wizard, Inseln, Schutz (alles Phase 2). Ohne Wizard bleibt der Zustand nach dem Start `SETUP_PENDING`.

## Erledigt

### Phase 0A und 0B
- Spec, Entscheidungen, Spikes, ADR 001–007, `POC_RESULTS`, Risiken, Abhängigkeiten. Owner-Tests am 2026-10-19 bestanden. `DECISIONS` Nr. 12 entschieden (ADR 003 akzeptiert).

### Phase 1
- Alle 15 Plan-Punkte umgesetzt. Feature-Status: `DEDICATED_SERVER_VERIFIED` außer Client-Sprache (`IMPLEMENTED`, wartet auf Owner-Test O1).
- 78 automatisierte Tests grün (lokal und GitHub Actions), 10 Dedicated-Server-Szenarien S1–S10 bestanden. Details: `docs/TEST_MATRIX.md`.
- Neu: `docs/CONFIG.md`, `docs/COMMANDS.md`, `docs/RECOVERY.md`, `docs/TEST_MATRIX.md`, `README.md`; aktualisiert: `ARCHITECTURE.md`, `docs/POC_RESULTS.md`, `docs/RISK_REGISTER.md`, `docs/DEPENDENCY_MATRIX.md`.

## Offen

- Owner-Test O1 (Join, `/ob status` auf Deutsch und Englisch, Reload, Stopp nach Kampf).
- Owner-Abnahme Phase 1, danach Merge nach `main` und Tag.

## Blocker

Keine.

## Nächste Aktion

Nach Abnahme: Phase 2 (Safe MVP). Leseplan: Spec 04, 05, 06, 07, 12.

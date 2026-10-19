# Spezifikation Cobblemon OneBlock — Übersicht (v4.1)

> **Status:** Verbindliche Produktspezifikation; kein Nachweis einer fertigen Implementierung. Stand 2026-10-19.
> **Ziel:** Stabiler, veröffentlichungsreifer, serverseitiger Fabric-Mod für Minecraft **1.21.1**, Java **21** und **Cobblemon 1.8.2**.
> **Priorität:** Nicht verhandelbare Gates: Datensicherheit, Security, Korrektheit und reale Kompatibilität. Innerhalb dieser Grenzen: Gameplay, Wartbarkeit, Performance und Bedienbarkeit gemeinsam optimieren.
> **Projektname / Mod-ID:** Siehe `docs/DECISIONS.md` Nr. 1; bis dahin `oneblock` als provisorischer Namespace. Nach dem ersten persistenten Release nicht ohne Migration ändern.
> **Sprache:** Projektkommunikation mit dem Owner auf Deutsch. Code, Bezeichner, Kommentare, Commits, API-Dokumentation und technische Fehlermeldungen auf Englisch. Spielertexte auf Deutsch und Englisch.

Diese Spec ist der Inhalt von **Masterprompt v4**, nach Themen aufgeteilt. Der v4-Text ist unverändert übernommen; Ergänzungen sind mit **[v4.1]** markiert. Arbeitsregeln in Kurzform stehen in `CLAUDE.md`, Owner-Entscheidungen in `docs/DECISIONS.md` (Vorrang vor dieser Spec).

## Dateien

| Datei | Inhalt | v4-Abschnitte | Lesen in Phase |
|---|---|---|---|
| `01-produkt-und-scope.md` | Produktvision, Muss-Ziele, Produktvertrag, Designprinzipien, Scope v1 / nach Spike / v1.x | §2, §25, §36 | 0A, bei jeder Scope-Frage |
| `02-arbeitsvertrag-und-phasen.md` | Rolle, Freigaben, Engineering-Regeln, Dokumentensatz, Phasenplan, Phasenbericht | §1, §4, §22 | 0A (danach genügt `CLAUDE.md`) |
| `03-technik-und-kompatibilitaet.md` | Technikbasis, Dependency-Disziplin, Kompatibilitäts-Tiers, Quellen | §3, §31, §37 | 0A, 0B, 1, 8 |
| `04-welt-grid-chunks.md` | Void-Dimension, Hub, Grid-Mathematik, Region-Dateien, Chunk-Lifecycle | §6, §7, §30 | 0B, 2 |
| `05-insel-lebenszyklus-und-coop.md` | Zustandsmodell, Reset/Archiv/Restore, Wipe, Saga-Protokoll, Co-op | §8, §10 | 2, 5 |
| `06-schutz-und-sicherheit.md` | Rollen, Schutzmatrix, Bewegung/Mounts, ActionContext, Threat Model | §9, §28 | 2, 4, 5, 8 |
| `07-oneblock-loot-progression-content.md` | OneBlock-Kern, Loot, acht Phasen, Progression, Balancing, Endgame, Content-Packs | §11, §12, §26 | 0B, 2, 3 |
| `08-cobblemon.md` | Spawn-Gate, Runtime-Regeln, Adapter-Vertrag, Plan B | §13 | 0B, 4, 6 |
| `09-biom-editor.md` | Biom-Editor | §14 | 0B, 4, 7 |
| `10-events.md` | Events, Fairness, Belohnungsgrenzen | §15 | 6 |
| `11-daten-speicher-recovery-ops.md` | Datenmodell, SQLite, Fail-Closed, Backup, Threading, Betrieb | §5, §17, §29, §32 | 0B, 1, 5, 6, 8 |
| `12-config-ux-admin.md` | Wizard, Config, Reload, Admin-Tools, Bedienung, Onboarding | §16, §18, §27 | 1, 2, 3, 7 |
| `13-architektur-und-api.md` | API, Projektstruktur, Referenzarchitektur, Datenflüsse, Boot/Shutdown | §19, §38 | 0B, 1, 7 |
| `14-qualitaet-performance-release.md` | Performance, Tests, Definition of Done, Release-Gates, KPIs | §20, §21, §23, §33, §34, §35 | 1, 8 |

v4 §24 (offene Produktfragen) steht als Entscheidungstabelle in `docs/DECISIONS.md`. v4 §39 (erste Anweisung) steht in `CLAUDE.md`.

## Leseplan pro Phase

Immer: `CLAUDE.md`, `docs/DECISIONS.md`, `docs/PROGRESS.md`. Zusätzlich:

| Phase | Spec-Dateien |
|---|---|
| 0A Inspect | 01, 02, 03 |
| 0B Feasibility | 03, 04, 07 (Hologramm), 08, 09, 11 (Threading), 13 |
| 1 Foundation | 02, 03, 11, 12, 13, 14 |
| 2 Safe MVP | 04, 05, 06, 07, 12 |
| 3 Progression | 07, 12 |
| 4 Cobblemon Core | 08, 09, 06 |
| 5 Co-op & Lifecycle | 05, 11, 06 |
| 6 Events | 10, 08, 11 |
| 7 Extension & UX | 09, 12, 13 |
| 8 Release | 14, 06, 11, 03 |

## Änderungen gegenüber v4

1. **Aufgeteilt** in `CLAUDE.md`, `docs/DECISIONS.md` und 14 Themen-Dateien, damit pro Sitzung nur der relevante Teil gelesen wird.
2. **Dokumentensatz zusammengelegt und bedarfsweise angelegt** (Tabelle in `02-arbeitsvertrag-und-phasen.md`). Zuordnung:

| v4-Dokument | v4.1 |
|---|---|
| `docs/THREAT_MODEL.md` | `docs/SECURITY.md`, Abschnitt „Threat Model“ |
| `docs/INVARIANTS.md` | `docs/TEST_MATRIX.md`, Abschnitt „Invarianten“ |
| `docs/ACCEPTANCE_MATRIX.md` | `docs/TEST_MATRIX.md`, Abschnitt „Akzeptanz“ |
| `docs/PERFORMANCE_BUDGET.md` | `docs/TEST_MATRIX.md`, Abschnitt „Performance-Budget“ |
| `docs/BALANCING.md` | `docs/GAME_DESIGN.md`, Abschnitt „Balancing“ |
| `PLAYTEST_REPORT.md` | `docs/GAME_DESIGN.md`, Abschnitt „Playtests“ |

3. **Regel 14** (v4 §4.3): Ältere Spezifikationen liegen nicht im Repository; Rangfolge `DECISIONS` vor `spec`.
4. **Schutz-Priorisierung** nach der Void-Lücke zwischen Inseln (`06-schutz-und-sicherheit.md`).
5. **Plan B** für biomabhängige Spawns, falls das Spawn-Gate negativ ausfällt (`08-cobblemon.md`).
6. **Archiv bei Owner-Wechsel** und Cooldown-Bindung gegen Transfer-Ping-Pong (`05-insel-lebenszyklus-und-coop.md`, `DECISIONS` Nr. 10).
7. **Realistische Testumfänge** für ein Ein-Personen-Projekt (`14-qualitaet-performance-release.md`).
8. **Architektur pragmatisch:** Ports nur bei echter Test-Naht, Saga nur für kritische Operationen (`13-architektur-und-api.md`).
9. **Cobblemon-Laufzeitabhängigkeiten** (u. a. Fabric Language Kotlin) explizit in Dev, CI und Testserver spiegeln (`03-technik-und-kompatibilitaet.md`).
10. **Abschnitt 24 beantwortet:** Fragen 3–8 entschieden; Name, Betriebsmodell, Lizenz und Spawn-Weg offen (`docs/DECISIONS.md`).

# CLAUDE.md — Cobblemon OneBlock

Serverseitiger Fabric-Mod für Minecraft **1.21.1**, Java **21**, **Cobblemon 1.8.2 Fabric**. Ein vollständiger OneBlock-Spielmodus in einer eigenen Void-Dimension, Speicher in **SQLite pro Welt**, kein zusätzlicher Clientmod. Ziel ist ein nachweisbar stabiler, veröffentlichungsreifer Release, keine Demo.

**Sprache:** Mit dem Owner Deutsch. Code, Bezeichner, Kommentare, Commits, API-Doku und technische Fehlermeldungen Englisch. Spielertexte `de_de` und `en_us`.

## Maßgebliche Quellen

1. `docs/DECISIONS.md` – Owner-Entscheidungen, hat Vorrang.
2. `docs/spec/` – die vollständige Spezifikation, aufgeteilt nach Themen. Einstieg und Leseplan: `docs/spec/00-INDEX.md`.
3. Diese Datei – Zusammenfassung. Ein Widerspruch zur Spec ist ein Fehler: nachfragen, nicht raten.

Ältere Spezifikationen (v2, v3, v4-Volltext) gehören nicht ins Repository und werden nicht herangezogen.

## Prioritäten

Harte Gates: **Datensicherheit, Security, Korrektheit, reale Kompatibilität.** Innerhalb dieser Grenzen Gameplay, Wartbarkeit, Performance und Bedienbarkeit gemeinsam optimieren. Eine einfache, verifizierte Lösung schlägt eine elegante, ungetestete.

## Absolute Regeln

1. **Keine erfundenen APIs.** Minecraft-, Fabric- und Cobblemon-Signaturen im gepinnten Quellstand prüfen und in `docs/POC_RESULTS.md` belegen. `TODO(verify)` ist kein Freifahrtschein für produktive Platzhalter.
2. **Persistenz vor Bestätigung.** Kritische Aktionen melden erst Erfolg, wenn der persistente Zustand bestätigt ist. Vorher `PENDING`.
3. **Threading:** Welt-, Block-, Entity- und Cobblemon-Aufrufe nur auf dem Server-Thread. SQL nur auf dem DB-Executor, der nie die Welt anfasst und nur unveränderliche Snapshots herausgibt. Nie `Future.get()`/`join()` auf dem Server-Thread. Nie einen Lock halten und auf den jeweils anderen Thread warten.
4. **SQLite und Weltdateien sind nicht gemeinsam atomar.** Kritische Operationen (`CREATE`, `RESET`, `RESTORE`, `DELETE`, `TRANSFER`, Reward-Claims) laufen als durable Vorgang mit Stages, `requestId`, Revision und Reconciliation nach Crash.
5. **Jede Aktion ist potenziell gleichzeitig, wiederholt oder bösartig.** Rechte werden in der Geschäftslogik geprüft, nicht nur in UIs. Unbekannter Verursacher heißt nicht „erlaubt“.
6. **Keine unbegrenzten Queues**, keine stummen Fallbacks. Unlösbares wird `BLOCKED` und mit 2–3 Alternativen eskaliert, nie still gestrichen.
7. **Mixins nur mit Begründung**, Risikoangabe und Integrationstest, dokumentiert in `docs/mixins.md`.
8. **Tests nie auskommentieren.** Build-grün ist nicht fertig. Fertig heißt: im echten Dedicated Server getestet.
9. **Nie Weltdateien löschen, die geladen sein könnten.** Vor Migrationen und destruktiven Aktionen Backup- und Restore-Pfad nachweisen.
10. **Produktentscheidungen nicht eigenmächtig ändern.** Konflikt als ADR mit Alternativen vorlegen und warten.
11. **Git:** Ein Branch pro Phase, kleine Commits, Tag nach Abnahme. Kein Force-Push, keine destruktiven Operationen. Spikes nur in Wegwerf-Kopien oder `spike/*`-Branches, nie in Produktionscode mergen.
12. **Nichts erfinden:** keine Tests, Benchmarks, Prozentwerte, Commits oder Nachweise, die es nicht gibt. Synthetische Messungen als solche kennzeichnen.

## Feature-Status

`PROPOSED` → `FEASIBILITY_VERIFIED` → `IMPLEMENTED` → `AUTOMATED_TESTED` → `DEDICATED_SERVER_VERIFIED` → `OWNER_ACCEPTED` → `RELEASED`, oder `BLOCKED`. `IMPLEMENTED` ist nicht `RELEASED`.

## Phasen und Leseplan

Lies pro Phase **nur** die genannten Spec-Dateien (plus immer `docs/DECISIONS.md` und `docs/PROGRESS.md`). Andere Themen findest du über `docs/spec/00-INDEX.md`.

| Phase | Inhalt | Spec-Dateien |
|---|---|---|
| 0A Inspect | Repo, `/reference`, Abgleich, Rückfragen | 01, 02, 03 |
| 0B Feasibility | Spikes: Dimension, natürliche Spawns, Biome, Capture/Battle, Trainer, Hologramm; ADRs, Architektur | 03, 04, 07 (Hologramm), 08, 09, 11 (Threading), 13 |
| 1 Foundation | Gradle, Pinning, Config/Reload, SQLite, Migrationen, Operation-Journal, Logging, CI | 02, 03, 11, 12, 13, 14 |
| 2 Safe MVP | Void-Dimension, Hub, Wizard, Grid, Grundschutz, Create/Home, OneBlock + Block-Loot | 04, 05, 06, 07, 12 |
| 3 Progression | Acht Phasen, Loot-Pools, Border-Level, Resource-Audit, Challenges, Guide | 07, 12 |
| 4 Cobblemon Core | Void-Spawns, lokale Spawnregeln, Biom-Editor-Minimum, Fangrechte, Budgets | 08, 09, 06 |
| 5 Co-op & Lifecycle | Party, Transfer, Reset/Archiv/Restore, Wipe, Recovery, Backup | 05, 11, 06 |
| 6 Events | READY-System, Burst, Schwarm, Alpha, ggf. Trainer, Meilensteine, Ledger | 10, 08, 11 |
| 7 Extension & UX | Biom-Editor fertig, Guide, Admin-Tools, Menüs, API, `example-addon` | 09, 12, 13 |
| 8 Release | Audits, Testmatrix, Playtests, Doku, Packaging | 14, 06, 11, 03 |

Phase 2 ist der erste wirklich spielbare Stand. Schutz, Persistenz und Recovery werden in **jeder** Feature-Phase mitgebaut, nicht erst in Phase 5.

## Ablauf jeder Phase

1. Vor jedem Feature kurz: Anforderung → Abhängigkeit → Akzeptanztest → Rollback/Rollforward.
2. Umsetzen, testen (JUnit, GameTests, Dedicated Server), Doku auf den **implementierten** Stand bringen.
3. Bericht mit genau diesen Punkten: **Implemented**, **Decisions**, **Verification** (tatsächlich ausgeführte Befehle und Ergebnisse), **Game test for owner** (nummerierte Ingame-Schritte), **Known issues**, **Data safety**, **Next phase**.
4. **STOP.** Auf das OK des Owners warten. Keine Phase beginnt ohne Freigabe.

`P0` (Datenverlust, Dupe, Inselübernahme, korrupter Save) und `P1` (häufiger Crash, unspielbarer Kern) stoppen jede neue Feature-Arbeit, bis sie behoben sind.

## Scope

Was v1-Pflicht ist, was erst nach positivem Spike kommt und was v1.x ist, steht in `docs/spec/01-produkt-und-scope.md`. Keine Scope-Erweiterung ohne Freigabe.

## Projektdokumente

- **Ab Phase 0:** `docs/PROGRESS.md` (aktive Phase, erledigt, offen, Blocker, nächste Aktion), `ARCHITECTURE.md`, `docs/adr/`, `docs/POC_RESULTS.md`, `docs/RISK_REGISTER.md`, `docs/DEPENDENCY_MATRIX.md`.
- **Erst wenn die Phase sie braucht:** `docs/TEST_MATRIX.md` (inkl. Invarianten, Akzeptanz, Performance-Budget), `docs/SECURITY.md` (inkl. Threat Model), `docs/GAME_DESIGN.md` (inkl. Balancing, Playtests), `docs/RECOVERY.md`, `docs/CONFIG.md`, `docs/COMMANDS.md`, `docs/API.md`, `docs/mixins.md`, `docs/TROUBLESHOOTING.md`. Zeitpunkte: `docs/spec/02-arbeitsvertrag-und-phasen.md`.

## Kontext sparen

Lies nicht in jeder Sitzung die ganze Spec. Starte mit `docs/PROGRESS.md`, dann die Spec-Dateien der aktiven Phase. Aktualisiere `docs/PROGRESS.md` am Ende jeder Phase und **bevor der Kontext voll wird**, damit eine neue Sitzung nahtlos weitermachen kann.

## Erste Anweisung

**Beginne ausschließlich mit Phase 0A.**

1. Lies das Repository, das `README`, die Build-Dateien, `docs/DECISIONS.md`, `docs/spec/00-INDEX.md`, die Spec-Dateien 01–03 und den Ordner `/reference`.
2. Vergleiche den Ist-Zustand mit der Spec: vorhandene Implementierungen, falsche Abhängigkeiten, technische Risiken, offene Cobblemon-1.8.2-Kompatibilitätsfragen.
3. Stelle **höchstens zehn** Rückfragen in **einer** Nachricht: die `OFFEN`- und `VORSCHLAG`-Punkte aus `docs/DECISIONS.md` plus wirklich neue Fragen aus dem Repository.
4. **Stoppe und warte auf die Antworten.** Kein Feature-Code, kein Refactor, keine Datenlöschung, kein eigenmächtiger Start von Phase 0B.

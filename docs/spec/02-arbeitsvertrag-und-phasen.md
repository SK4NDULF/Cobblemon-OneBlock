# Arbeitsvertrag, Dokumente und Phasenplan

> **Spec v4.1** · Teil von `docs/spec/` (Übersicht: `00-INDEX.md`) · Quelle: Masterprompt v4 §1, §4, §22  
> **Lesen in Phase:** 0A (vollständig); danach genügt `CLAUDE.md`  
> Abschnitte mit **[v4.1]** sind Ergänzungen gegenüber v4. Alles andere ist der v4-Text unverändert (nur Dokumentnamen an den zusammengelegten Dokumentensatz angepasst).

## 1. Deine Rolle und dein Auftrag

Du bist Lead-Engineer, Minecraft/Fabric-Architekt, Cobblemon-Integrationsentwickler, Security-Reviewer, Performance-Engineer und QA-Verantwortlicher. Entwickle **mit mir** einen vollständigen Cobblemon-OneBlock-Mod, nicht bloß eine Demo. Übernimm Verantwortung für korrekte Abhängigkeiten, reproduzierbare Builds, Tests, Persistenz, Wiederherstellbarkeit, Multiplayer-Sicherheit, nachvollziehbare Architektur und Release-Qualität.

**Entwickle nichts einfach nur, weil es sich plausibel anhört.** Untersuche die tatsächliche Implementierung der gepinnten Versionen, mache Abhängigkeiten sichtbar, führe Tests aus und dokumentiere technische Unsicherheiten. Fertig bedeutet **im Dedicated Server getestet**, nicht nur „kompiliert“.

Arbeite **schrittweise**. Du darfst umfangreich analysieren, recherchieren und Test-Spikes ausführen, aber **keinen produktiven Phasencode ohne Freigabe** entwickeln. Nach jedem abgenommenen Meilenstein stoppst du und wartest auf meine Freigabe für den nächsten.

## 4. Arbeitsvertrag für den Coding-Agenten

### 4.1 Start und Freigaben

1. **Phase 0A — Repository-Inspektion:** Repo-Status, Build, vorhandene Specs, Branches, `/reference`, Abhängigkeiten und vorhandenen Code lesen. Ungeklärte Punkte sammeln; **maximal zehn wichtige Fragen in einer Nachricht stellen und auf Antworten warten**. Nichts löschen oder überschreiben.
2. **Phase 0B — Architektur & technische Spikes:** Nach meinen Antworten isolierte Machbarkeits-Spikes (kein Produktionsfeature-Code) für Dimension, natürliches Cobblemon-Spawning, Biomwechsel, Capture-/Battle-Lifecycle und serverseitiges Hologramm durchführen. Danach `ARCHITECTURE.md`, `docs/adr/`, Risiko-/Abhängigkeitsmatrix, `docs/POC_RESULTS.md` und detaillierte Phasenplanung erstellen.
3. **Phase-0-Gate:** Risiken, Blocker und Entscheidungen in einer verständlichen Review-Zusammenfassung präsentieren. **Auf mein OK warten**, bevor Phase 1 beginnt.
4. **Jede Phase separat:** Kleiner Branch, kleine Commits, Tests und Dedicated-Server-Check, Dokumentation, Phasenbericht, manuelle Ingame-Testliste. Danach **stoppen**, bis ich freigebe. Nach Abnahme einen Tag erstellen.
5. Bestehende Spielerdaten haben Vorrang vor jedem Zeitplan. Keine destruktiven Git-Operationen, kein Force-Push, kein stilles Überschreiben alter Daten oder aktiver Saves.

### 4.2 Projektgedächtnis und Nachvollziehbarkeit

Lege an und pflege:

- `CLAUDE.md` — kompakte, stabile Arbeitsregeln; gilt auch für neue Sessions. Falls der verwendete Agent ein anderes Instructions-Format erwartet, ergänze eine dazu passende Datei und halte beide konsistent.
- `docs/PROGRESS.md` — erledigt, offen, Tests, Blocker, Änderungen, nächste Aktion und aktive Phase.
- `ARCHITECTURE.md` — tatsächliche Architektur, Lebenszyklen, Haupt-Threads, Datengrenzen, Diagramme und Abhängigkeiten.
- `docs/adr/NNN-*.md` — Motivation, Alternativen, Entscheidung, Trade-offs, Risiken und Migrationskonsequenzen.
- `docs/POC_RESULTS.md` — getestete Cobblemon-/Minecraft-APIs mit Klasse, Signatur, Version, Test und Ergebnis.
- `docs/TEST_MATRIX.md` — geplante, automatisierte und manuelle Tests mit Status.
- `docs/SECURITY.md` — Trust-Boundaries, Exploits, Schutz- und Dupe-Risiken.
- `docs/RECOVERY.md` — Shutdown, Crash, Backup, Restore, Insel-Wipe und Reparatur.
- `docs/CONFIG.md`, `docs/COMMANDS.md`, `docs/API.md`, `docs/mixins.md` nach Bedarf.

Die Dokumentation muss den **implementierten** Zustand abbilden. Fehlendes darf nicht als erledigt ausgegeben werden.

### 4.3 Absolute Engineering-Regeln

1. **Keine erfundenen APIs.** Minecraft-/Fabric-/Cobblemon-Signaturen aus dem gepinnten Quellstand überprüfen. Ein `TODO(verify)` ist kein Freifahrtschein für produktive Platzhalter.
2. **Persistenz vor Bestätigung.** Ein kritischer, für Spieler sichtbarer Zustand wird erst als erfolgreich gemeldet, wenn die erforderliche persistente Zustandsänderung bestätigt ist.
3. **Keine blockierenden DB-Zugriffe auf dem Server-Thread.** Kritische Operationen dürfen bis zum asynchronen Commit in einem expliziten `PENDING`-Zustand bleiben. Weltoperationen zurück auf den Server-Thread.
4. **Jede Operation hat einen Zustandsautomaten** oder eine dokumentierte atomare Grenze: Erfolg, Konflikt, Fehler, Timeout, Abbruch, Servercrash und Wiederanlauf.
5. **Weltdateien und SQLite sind nicht gemeinsam atomar.** Entwerfe dafür durable Intent-/Journal-Einträge und einen reconciliationsfähigen Ablauf, statt Transaktionsgarantien zu behaupten, die technisch nicht existieren.
6. **Alle externen Aktionen sind potenziell gleichzeitig, wiederholt oder bösartig.** Keine Berechtigungsprüfung nur in GUIs; sie gehört in die ausführende Geschäftslogik.
7. **Keine unbegrenzten Queues.** Speicher, Task-Anzahl, Event-Queue und Spawn-Budgets besitzen Limits und Diagnostik.
8. **Keine stummen Fallbacks.** Fehlende Pflichtfunktion → nachvollziehbarer Blocker; optionales Feature → dokumentiert deaktiviert.
9. **Keine unnötigen Mixins.** Zuerst offizielle oder stabile APIs. Jedes Mixin hat Begründung, Inkompatibilitätsrisiko und Integrationstest.
10. **Keine Tests auskommentieren, um CI grün zu bekommen.** Jede geänderte Funktion braucht passende Regressionstests.
11. **Build-Grün reicht nicht.** Ein Feature ist erst fertig, wenn es im realen Dedicated-Server-Lifecycle getestet wurde.
12. **Vor Datenmigrationen und destruktiven Aktionen** Kompatibilität, Backup-/Restore-Pfad und Rollback bzw. Forward-Recovery nachweisen.
13. **Nicht eigenmächtig feststehende Produktentscheidungen ändern.** Technischen Konflikt mit Alternativen im ADR vorstellen und Entscheidung abwarten.
14. **[v4.1] Maßgeblich sind ausschließlich `docs/DECISIONS.md`, `docs/spec/` und `CLAUDE.md`.** Ältere Spezifikationen (v2, v3, v4-Volltext) liegen nicht im Repository und werden nicht herangezogen. Rangfolge bei Widerspruch: `docs/DECISIONS.md` vor `docs/spec/`; `CLAUDE.md` ist eine Zusammenfassung – ein Widerspruch zwischen ihr und der Spec ist ein Fehler und wird nachgefragt, nicht geraten.


### Review-, Branch- und Änderungsstrategie

- Bevor ein Feature umgesetzt wird, schreibe kurz: **Anforderung → Abhängigkeit → Akzeptanztest → Rollback/Rollforward**. Eine Phase enthält keine unerklärte Nebenfunktion.
- Änderungen an persistenten Schemas, der Worldgen, den Slotformeln, dem OneBlock-Drop-Vertrag und der Spawnsemantik benötigen ein ADR und einen expliziten Versions-/Kompatibilitätsnachweis.
- Für jeden Phasen-PR: Liste der geänderten Dateien, Risikostufe (`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`), Test-Evidenz, Save-Kompatibilität, bekannte Einschränkungen. Keine künstliche Prozent-Fertigmeldung ohne nachprüfbare Kriterien.
- Führe `docs/RISK_REGISTER.md` mit **Eintrittswahrscheinlichkeit, Auswirkung, Gegenmaßnahme, Test, Owner, Restrisiko**. Offene kritische Risiken blockieren den Release.
- Keine Automatisierung, die ungefragt produktive Welten, Repos oder Git-History löscht, überschreibt oder migriert. Alle Spikes laufen in disposable Kopien.
- Erzeuge auf Wunsch für Phase 0 konkrete, niedrigriskante Code-Spikes. **Auch der Spike benötigt Freigabe**, sobald er bestehende Projektdateien verändert; er bleibt klar getrennt von Produktionscode.

### [v4.1] Verbindlicher Dokumentensatz: zusammengelegt und bedarfsweise angelegt

Diese Regel ersetzt die Einzeldokumente, die an verschiedenen Stellen der Spec genannt werden. Dokumente entstehen erst, wenn die Phase sie wirklich braucht, und bilden immer den **implementierten** Zustand ab.

| Dokument | Enthält | Anlegen ab |
|---|---|---|
| `CLAUDE.md` | Kompakte Arbeitsregeln (existiert bereits) | – |
| `docs/DECISIONS.md` | Owner-Entscheidungen und offene Produktfragen (existiert bereits) | – |
| `docs/PROGRESS.md` | Aktive Phase, erledigt, offen, Blocker, nächste Aktion | Phase 0A |
| `ARCHITECTURE.md` + `docs/adr/` | Tatsächliche Architektur, Entscheidungen | Phase 0B |
| `docs/POC_RESULTS.md` | Verifizierte APIs mit Klasse, Signatur, Version, Test, Ergebnis | Phase 0B |
| `docs/RISK_REGISTER.md` | Risiken mit Wahrscheinlichkeit, Auswirkung, Gegenmaßnahme, Restrisiko | Phase 0B |
| `docs/DEPENDENCY_MATRIX.md` | Exakte Abhängigkeiten, Hashes, Lizenzen | Phase 0B |
| `docs/TEST_MATRIX.md` | Tests mit Status **plus** Abschnitte „Invarianten“, „Akzeptanz“, „Performance-Budget“ | Phase 1 |
| `docs/SECURITY.md` | Trust-Boundaries, Exploits **plus** Abschnitt „Threat Model“ | Phase 2 |
| `docs/CONFIG.md`, `docs/COMMANDS.md` | Sobald die erste Config-Option bzw. der erste Befehl existiert | Phase 1/2 |
| `docs/GAME_DESIGN.md` | Phasen, Ziele **plus** Abschnitte „Balancing“ und „Playtests“ | Phase 3 |
| `docs/RECOVERY.md` | Shutdown, Crash, Backup, Restore, Wipe, Reparatur | Phase 5 (Grundlagen ab Phase 1) |
| `docs/API.md` | Öffentliche API | Phase 7 |
| `docs/mixins.md` | Begründung jedes Mixins | Beim ersten Mixin |
| `docs/TROUBLESHOOTING.md` | Häufige Admin-Probleme | Phase 8 |

Es gibt **keine** separaten Dateien `THREAT_MODEL.md`, `INVARIANTS.md`, `ACCEPTANCE_MATRIX.md`, `PERFORMANCE_BUDGET.md`, `BALANCING.md` oder `PLAYTEST_REPORT.md`.

## 22. Phasenplan v4 — Abhängigkeiten in sinnvoller Reihenfolge

| Phase | Inhalt | Harte Abnahmebedingung |
|---|---|---|
| **0A – Inspect** | Repo lesen, `/reference` prüfen, Anforderungen und bis zu 10 Fragen sammeln | Meine Antworten liegen vor; kein Produktionscode angefasst |
| **0B – Feasibility** | Cobblemon-1.8.2-Spikes (Dimension, natürliche Spawns, Biome, Battle/Capture, Trainer, Hologramm), ADRs, Architektur, Risikoliste | Ergebnisse reproduzierbar dokumentiert; ich gebe Go |
| **1 – Foundation** | Gradle, Modulgrenzen, Versions-Pinning, Config/Reload-Klassen, SQLite, Migrationen, Basis-Operation-Journal, Logging, CI | Clean-Server-Boot mit Pflichtmods, Client-Join, DB-Migration, Crash-/Fail-Closed-Basistests |
| **2 – Safe MVP** | Void-Dimension, Hub/Wizard, Grid/Slots, minimale Owner-Rechte, Grundschutz, Create/Home, Single-OneBlock + sichere BLOCK-Lootregeneration | Solospieler kann sicher bauen/abbauen, Fremde nicht griefen, Restart behält aktive Insel; minimaler MVP wirklich spielbar |
| **3 – Progression** | Acht Phasen, typisierte Loot-Pools, Border-Level, Resource-Audit, erste Challenges, Guide-Info | Acht Phasen über Daten konfigurierbar, keine Softlocks, Level-Up korrekt, persistente Punkte |
| **4 – Cobblemon Core** | Void-Spawns, lokale Spawnregeln, Biome-Editor-Minimum, Catch-Permissions, Spawn-Budgets, Pokémon-Lifecycle | Nachgewiesene reale biomeabhängige Spawns, keine Auswirkungen auf andere Dimensionen, Capture und Restart funktionieren |
| **5 – Co-op & Lifecycle** | Party, Owner-Transfer, Reset/Archiv/Restore, Wipe/Recovery, Diagnostik, Backup/Export | Zwei Spieler, gleichzeitige Aktionen, Restore-Tausch, Crash-Recovery, kein Slot-Wipe während geladener Welt |
| **6 – Events** | READY-System, Resource Burst, Swarm, Alpha, ggf. geprüfter Trainer, Milestones, Rewards/Ledger, Cleanup | Jeder freigeschaltete Eventtyp inkl. Crash/Battle/Reward-Regression getestet |
| **7 – Extension & UX** | Biom-Editor fertig mit Undo/Freischaltung, Challenges/Guide poliert, Admin-Tools, Menüs, API, `example-addon`, optionale Placeholders | Beispiel-Addon läuft; Recovery-/Admin-/API-Doku aktuell; alle v1-Commands getestet |
| **8 – Release** | Sicherheits-/Daten-/Performance-Audit, Testmatrix, vollständiger Gameplay-Durchlauf, Übersetzungen, README, CHANGELOG, Admin-Doku, Release-Packaging | Abnahmetest, Crash-Matrix, Multiplayer-Tests und Clean-Install bestanden; Owner gibt Release frei |

**Wichtig:** Die Aspekte **Schutz**, **Persistenz**, **Backup-Fähigkeit** und **Recovery** werden bereits in der jeweiligen Feature-Phase implementiert und getestet. Phase 5 ergänzt und vervollständigt den komplexen Insel-Lebenszyklus; sie ist kein Vorwand, in Phase 2 ungesicherten Code zu veröffentlichen.

### Phasenformat für jede Abnahme

Antworte am Ende **jeder** Phase mit:

1. **Implemented:** Konkrete Änderungen und relevante Dateien.
2. **Decisions:** Neue ADRs, bewusste Abweichungen, Risiken.
3. **Verification:** tatsächlich ausgeführte Befehle, automatisierte Tests und Ergebnisse.
4. **Game test for owner:** nummerierte, einfach nachvollziehbare Ingame-Testschritte.
5. **Known issues:** bekannte Bugs, Workarounds und nicht verifizierte Features.
6. **Data safety:** geänderte DB-/Welt-/Migrationspfade und Recovery-Status.
7. **Next phase:** vorgeschlagene nächsten Schritte; anschließend **STOP und auf mein OK warten**.

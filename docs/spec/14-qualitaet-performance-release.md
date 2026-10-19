# Qualität, Performance und Release

> **Spec v4.1** · Teil von `docs/spec/` (Übersicht: `00-INDEX.md`) · Quelle: Masterprompt v4 §20, §21, §23, §33, §34, §35  
> **Lesen in Phase:** 1 (CI, Teststrategie), 8  
> Abschnitte mit **[v4.1]** sind Ergänzungen gegenüber v4. Alles andere ist der v4-Text unverändert (nur Dokumentnamen an den zusammengelegten Dokumentensatz angepasst).

## 20. Performance, Betrieb und Grenzen

- Reaktive/eventbasierte Schutzlogik statt teurer globaler Vollscans. Lokale Tick-Tasks (Biome-Job, Events, Cleanup) mit klaren Budgets und fairer Verteilung zwischen aktiven Inseln.
- Kein permanentes Laden leerer Inseln. Keine globale Liste aller Inselchunks im Heap. Mathematisches Grid-Lookup darf O(1) bleiben; DB-/Membership-Lookup über vernünftig dimensionierten Cache mit Invalidierung.
- Spawn-Caps: per Insel, per Chunk, ggf. per Spieler und **serverweit**, mit Monitoring und klarer Unterscheidung zwischen wild, eventbezogen und spielereigen.
- Default `2049` maximale Kantenlänge; größere Inseln und Partygrößen mit Warnung. Limits beruhen auf Testdaten, nicht allein auf Hoffnung.
- Ziel: **mod-eigene** durchschnittliche Tick-Zeit möglichst <1 ms bei einer dokumentierten Referenzlast, aber kein ungedecktes SLA. Messe median, p95 und p99, Max-Spitzen, DB-Wartezeit, Memory/GC und ausgelastete Chunk-/Spawn-Queues. Ergebnisse einschließlich Hardware und Szenario protokollieren.
- Lasttests mindestens: 1/20/100 simultane aktive Inseln, viele Mining-Aktionen, 100 aktive Spieler als Zielscenario, viele Biome-Änderungen, Spawn-Grenzfälle, Archive/Wipe und DB-Störung. Synthetische Fake-Spieler nur verwenden, wenn deren Grenzen ausdrücklich dokumentiert werden; echte Clients ergänzend testen.
- Keine teuren Profiling-Funktionen automatisch auf Produktionsservern einschalten.


### Messbare Performance- und Schutzbudgets

- Führe in `docs/TEST_MATRIX.md` einen Abschnitt „Performance-Budget“ mit Hardwareprofil, Spielerzahl, aktiven Inseln, sichtbarer Simulation Distance, Cobblemon-/Spawn-Config, Content-Preset, Tick-Sampling-Fenster und Messinstrumenten.
- Prüfe mindestens Main-Thread-Last **Idle / normales Mining / 100 Spieler im Peak / 50 gleichzeitige Events simuliert / Biome-Edit / Recovery-Anlauf**. 50 Events sind **synthetischer Extremtest**, nicht zugelassene Betriebs-Default-Konfiguration.
- Zielwerte sind **SLO-Kandidaten**, keine blind garantierten SLAs: durchschnittlich <1 ms mod-eigene Tickzeit unter definierter Referenzlast; p95/p99 und Peak-Lag ohne unkontrollierte Spikes; Spawn-/Biom-Tasks fair verteilt und begrenzt.
- Kernoperationen müssen **O(1) oder O(log n)** in Spieler-/Insel-Lookups anstreben. Beweise oder messe tatsächliche Kosten inkl. Cache-Miss. Keine Query pro Tick/Block/Bewegung.
- `maxWildPokemonPerIsland=40` ist eine **Obergrenze** und kein Spawn-Ziel. Zusätzlicher globaler Deckel verhindert, dass 100 Inseln automatisch 4.000 aktive Pokémon erzeugen.
- Bei Überlast graceful degradation: weniger **neue** Spawns, langsamere nichtkritische Preview-/Sync-Arbeiten, gedrosselte Events; niemals Permission-Checks oder Persistenzgarantien aus Performancegründen abschalten.

## 21. Qualitätsstrategie und Tests

### Testpyramide

1. **JUnit / Property-based:** Grid-Mathematik, negative Koordinaten, Zell-/Region-Ausrichtung, Spiral-Mapping, Border-Radien, Progressionsformeln, gewichtete Loot-Auswahl, Config-Validierung/Migrationen, Permission-Matrix, UUID-/Owner-Invarianten, Event- und Island-Zustandsautomaten, Backpressure.
2. **Integrationstests (Storage):** DB-Schema, SQLite-Migrationen, gleichzeitige Reads/Writes, Transaktionskonflikte, Owner-Wechsel, Reset/Restore, Ledger-Deduplizierung, Rollforward nach unvollständigen Operationen.
3. **Fabric GameTests:** tatsächlicher OneBlock-Break, Ersatzblock, Flüssigkeiten/Falling Blocks, Grenzschutz, Player-/Mount-Interaktion soweit automatisierbar, Border-Wachstum, Void-Rettung, Biome-Änderung.
4. **Dedicated-Server-Smoke-Tests:** Client mit Standard-Cobblemon-Pack verbindet sich ohne OneBlock-Clientmod, Setup, Join, Chunkload, Inselanlage, Commands, Save/Restart, Config-Reload.
5. **Mehrspieler-/Chaos-Tests:** konkurrierende Resets, Invite/Transfer-Race, gleichzeitige OneBlock-Breaks, Capture während Event-Cleanup, Hard-Kill vor/nach SQLite-Commit, defekte Regiondateien, volle Disk, invalides Backup, Abbruch während `PENDING_DELETE`.
6. **Last-/Profiltests:** reproduzierbar mit Konfiguration, Hardware, Messprotokoll und akzeptierten Schwellen.

### Besonders wichtige Regressionen

- Zwei `reset`-Kommandos gleichzeitig → genau ein sicherer neuer Aktivzustand; kein doppelter Slot.
- Crash direkt vor/nach Slot-Reservierung, Weltanlage, Archivierung, Restore und Wipe → idempotenter Wiederanlauf.
- Der Spieler kann durch Reset/Restore/Alt-Accounts **keine** einmaligen Legendary-/Milestone-Rewards mehrfach erhalten.
- Ein aktives Battle/Capture/Mount wird nicht von automatischem Pokémon-Cleanup zerstört.
- PvP/Projektile/Flüssigkeiten/Pistons können keine anderen Inseln beschädigen.
- Negativer Weltkoordinatenbereich verhält sich exakt wie positiver.
- Ungültige Hubgröße/Maximalgröße wird beim Wizard abgefangen, keine unsichere Config akzeptiert.
- Biome-Änderung während Restart lässt keinen halben nicht-reparierbaren Job zurück.
- DB-/Disk-Fehler erzeugen sichtbare Warnung und **keinen** scheinbar erfolgreichen Inselwechsel.
- Server mit existierender Overworld wird niemals als Kollateralschaden umgebaut.

### CI & Freigabegates

- GitHub Actions: JDK 21, Gradle Build, Tests, Lint/Style falls konfiguriert, Reproduzierbarkeits-/Dependency-Check, veröffentlichbares JAR als Artefakt.
- Veröffentlichungs-JAR im cleanen Dedicated Server booten und Clientjoin testen, nicht nur `runClient` verwenden.
- In den Phasenberichten: Welche Tests ausgeführt wurden, wo, mit welchem Ergebnis? Was war nur manuell möglich? Was ist noch nicht implementiert? Keine erfundenen Erfolgsnachweise.


### Invarianten als ausführbare Property-/Chaos-Tests

- Führe in `docs/TEST_MATRIX.md` einen Abschnitt „Invarianten“ mit nummerierten Aussagen (`INV-001` usw.) und Test-ID. Beispiele: **nie zwei aktive Inseln pro Slot; nie ein Mitglied in zwei aktiven Inseln; kein Pending-Wipe-Slot neu vergeben; kein Reward-Key zweimal als abgeschlossen; kein unerlaubter Cross-Border-Blockeffekt; keine kritische Bestätigung vor Commit**.
- Für jede kritische Operation automatisierte **Fault Injection an jeder Durable-/World-Boundary**; teste nicht nur „Crash mitten drin“, sondern genau davor und danach, inklusive Retry mit derselben `requestId`.
- **Property-based Grid-Tests** mit positiven/negativen Grenzkoordinaten, zufälligen Slotindizes bis zur World-Border, Stride-Varianten und Region-Overlap-Nachweis. Seed im Testbericht sichern.
- Release-Matrix `Windows/Linux` mindestens für offizielle Supportziele (weitere Systeme nur, wenn verifiziert); unterschiedliche SQLite-Native-Libs, sauberes JAR, Server-Join ohne OneBlock-Clientmod.
- **Security-Regression**: alle Public-Flags, Owner, Member, Admin-Bypass, Spieler mit verzögerten Paketen, fremde Dimensionen, Item-Transfers, Fahrzeuge/Mounts, Spawn-/Capture-Konflikte.
- Automatische Tests dürfen echte Weltdaten niemals ohne Kopie verändern; CI-Saves sind vollständig disposable.

### [v4.1] Realistische Testumfänge für ein Ein-Personen-Projekt

Die Testziele dieser Spec bleiben gültig. Wo eine Anforderung mit echten Menschen oder Hardware nicht erreichbar ist, gilt diese Umsetzung:

| Anforderung | Umsetzung |
|---|---|
| 100 aktive Spieler | **Synthetisch** (Fake-Player-/Bot-Harness, Grenzen dokumentiert) plus echte Clients: 2–5 in Internal Alpha (Owner, Staff, Zweitaccounts), 10–20 in Closed Beta |
| 50 gleichzeitige Events | Nur synthetischer Extremtest, kein Betriebsziel |
| 1/20/100 aktive Inseln | Per Test-Befehl erzeugte Inseln auf der **echten Ziel-Hardware** des Owners |
| 100.000 Loot-Ziehungen pro Phase | Normaler JUnit-Test, kein Server nötig |
| Windows/Linux-Matrix | Siehe `docs/DECISIONS.md` Nr. 11 (Vorschlag: Linux x64 Pflicht, Windows x64 Boot-Smoke-Test, ARM64 nur nach verifizierten SQLite-Natives) |
| Drei unvorbereitete Anfänger | Bleibt Pflicht für den Release Candidate (Freunde, Staff oder Community) |

Ergebnisse, die nur synthetisch erhoben wurden, werden in Berichten **ausdrücklich so gekennzeichnet**.

## 23. Definition of Done — kompletter Mod

Ein vollständiger Release-Test wird auf **frischem** und **bestehendem** Fabric-1.21.1-Server mit Java 21, Fabric API, Cobblemon 1.8.2 und diesem Mod durchgeführt. Keine externe DB, kein zusätzlicher OneBlock-Clientmod, keine manuellen Datapack-Installationsschritte.

### Happy Path

1. Server bootet ohne Fehler und bietet einem OP den Wizard an.
2. Wizard mit Default-Werten abschließen; Server erzeugt eine sichere Void-Dimension samt Hub.
3. Spieler erstellt Insel, teleportiert sich sicher hin und baut den einen Block mehrfach ab; Block und Belohnungen sind korrekt, keine Doppel-Ausgabe.
4. Spieler erreicht Level 2 bis 8 in reproduzierbarer Testumgebung; Loot-Phasen, Border und Freischaltungen sind korrekt.
5. Spieler editiert legal ein Biom und sieht nachvollziehbar passende **natürliche** Cobblemon-Spawns unter erfüllten Zusatzbedingungen.
6. Zwei Spieler bilden eine Party und zählen legitim zum Inselfortschritt; Fremder darf nicht griefen oder unberechtigt fangen.
7. Resource-Burst/Schwarm und freigegebene weitere Event-Typen funktionieren, einschließlich Reward-Deduplizierung.
8. Ein fremder Spieler versucht zu Fuß, mit Elytra, Fahrzeug, per Teleport und mit einem unterstützten Pokémon-Mount einzudringen: Schutz greift ohne Item-/Entity-Dupe.
9. Owner archiviert durch Reset, erhält neuen Slot, stellt vorherige Insel wieder her, alte/neue Inseln behalten die korrekte Zuordnung und Fortschrittsstände.
10. Server wird hart beendet und wieder gestartet; Slots, DB, Inseln, Pokémon-Zuordnung und Event-/Biome-Zustände sind konsistent. Keine duplizierten Hologramme oder Claims.

### Failure Path (ebenfalls Pflicht)

- Hard-Kill vor und nach kritischem DB-Commit; trotzdem keine widersprüchlichen Active-/Archive-Zuordnungen.
- DB vorübergehend unzugänglich / Datenträger voll: riskante Schreibaktionen werden sicher verhindert und diagnostiziert.
- Wipe unterbrochen: Slot bleibt reserviert und wird erst nach erfolgreichem Cleanup wiederverwendet.
- Crash mitten in Event/Belohnung, Biome-Edit, Restore und Owner-Transfer: keine verlorenen/duplizierten Claims oder Rewards.
- Backup wird erstellt und in einer getrennten Testumgebung **wirklich** restored.
- Große Anzahl Inseln und Pokémon belastet den Server innerhalb dokumentierter Grenzen; Admin kann Engpässe über `profile`/`doctor` nachvollziehen.

**Das Projekt gilt erst als veröffentlichungsreif, wenn Happy Path, Failure Path, Doku, Release-JAR und Multiplayer-Test erfüllt und von mir abgenommen sind.**

## 33. Release-/Qualitätspolitik mit messbaren Gates

### P0-P3 Severity-Regeln

- `P0`: Datenverlust, Inselübernahme, reproduzierbarer Dupe, fremde Weltzerstörung, strukturell korrupter Save → **Release-Blocker**.
- `P1`: häufiger Dedicated-Server-Crash, unspielbarer OneBlock, unbrauchbarer Capture-/Spawn-Core, schwerer Grief → **Release-Blocker**.
- `P2`: fehlerhafte optionale Events, kosmetische Hologramme, seltene verständlich umgehbare UX-Probleme → dokumentiert nur mit Owner-Freigabe.
- `P3`: kleinere Text-/Layoutfehler → ins Backlog, wenn nicht sicherheitsrelevant.

### Getrennte Freigabekanäle

- **Internal Alpha:** Phase-2-MVP inklusive Protection/Persistenz; jede neue Systemklasse bei Solo und 2-Spieler-Test.
- **Closed Beta:** Phase 4/5, echtes Cobblemon-Spawn-/Co-op-/Recovery-Spiel; Testdaten klar getrennt von Produktion.
- **Release Candidate:** alle v1-Pflichtfunktionen fertig, Default-Content und Admin-Docs vollständig, Crash-/Exploit-/Lasttests, saubere Serverinstallation, Lizenzen/Notices geprüft.
- **Release:** Owner-Freigabe nach bestandenem RC-Check, versioniertes JAR mit Checksums und Changelog. Supportfenster und bekannte Restrisiken offen dokumentiert.

**Stop-the-line:** Bei `P0`/`P1` keine neue Feature-Phase, sondern Regression reproduzieren und beheben. Ein Build ohne Tests ist **niemals** eine Freigabe.

## 34. Release-Paket und Wartbarkeit

Das tatsächliche Release-Artefakt enthält beziehungsweise referenziert:

1. **Ein** klares, ausführbares Mod-JAR für den gepinnten Fabric-/Cobblemon-Stand, keine irreführenden Dev-/Sources-JARs als Hauptdownload.
2. `README.md` mit Voraussetzungen, Installation, Erststart, Kompatibilität, Befehlen, Backups und bekannten Grenzen.
3. `CHANGELOG.md`, Semantic-Versioning-/Compat-Regel, Upgrade- und Downgrade-Warnungen.
4. `LICENSE`, Drittbibliotheks-/Asset-Notices, falls rechtlich erforderlich; Credits für Referenzprojekte. **Keine urheberrechtlich unzulässigen Pokémon-Assets kopieren.**
5. `docs/CONFIG.md`, `docs/COMMANDS.md`, `docs/RECOVERY.md`, `docs/SECURITY.md`, `docs/API.md`, `docs/TROUBLESHOOTING.md`.
6. `example-addon` getrennt testbar, API-Artefakt falls technisch sinnvoll, und minimale Entwicklerbeispiele.
7. Reproduzierbare CI-Ausgabe, Version/Commit/Hash, prüfbare Java-/MC-/Loader-/Fabric-/Cobblemon-Versionen.
8. Laufende Serverdaten werden nicht ins Release-JAR gepackt; Upgrades garantieren keinen Downgrade ohne Backup.

## 35. Qualitäts-KPI und projektweite Definition of Excellence

| Qualitätsdimension | Nachweis statt Behauptung |
|---|---|
| Funktionalität | Jeder v1-Command + alle acht Phasen im Dedicated Server getestet |
| Datensicherheit | Hard-Kill-/Retry-Matrix, echte Backup-/Restore-Übung |
| Anti-Dupe | Reward-Ledger, Replay, Reset/Restore, concurrent mining Tests |
| Sicherheit | Threat-Model-Matrix für indirekte Cross-Border-Aktionen |
| Cobblemon | Natürliche Spawn-/Capture-Tests in mindestens drei Biomen und Negativkontrolle |
| Gameplay | Standard-Content spielbar ohne Editieren von JSON; dokumentierte Playtests |
| Skalierung | Reproduzierbarer 1/20/100-Insel-Test und Warnschwellen |
| Benutzerfreundlichkeit | Neue Spieler bewältigen Kernaktionen ohne Erklärung |
| API | Example-Addon läuft ohne interne Core-Abhängigkeiten |
| Wartung | Migration von letzter stabiler Version, Docs/Changelog, Recovery Runbook |

### Akzeptanzmatrix (in `docs/TEST_MATRIX.md`, Abschnitt „Akzeptanz“)

Jede Hauptanforderung erhält: **`REQ-ID`, User Story, Safety Invariant, Implementierungsphase, Test-ID, Testart, Testdaten, Expected Result, Evidence Link, Status, Owner Signoff.** Eine Anforderung ohne Test ist `UNVERIFIED`; eine Anforderung ohne tatsächlichen Evidence-Link ist **nicht bestanden**. Der Agent darf keine Tests, Benchmarks, Prozentwerte oder Git-Commits erfinden.

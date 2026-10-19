# Datenmodell, Speicher, Threading, Recovery und Betrieb

> **Spec v4.1** · Teil von `docs/spec/` (Übersicht: `00-INDEX.md`) · Quelle: Masterprompt v4 §5, §17, §29, §32  
> **Lesen in Phase:** 0B (Threading), 1, 5, 6 (Reward-Ledger), 8  
> Abschnitte mit **[v4.1]** sind Ergänzungen gegenüber v4. Alles andere ist der v4-Text unverändert (nur Dokumentnamen an den zusammengelegten Dokumentensatz angepasst).

## 5. Kern-Datenmodell und Source of Truth

Implementiere getrennte Domänenmodelle, auch wenn einzelne Klassen intern zusammengelegt werden:

- `Island` (stabile UUID, active-slot-ID, owner UUID, creation timestamp, phase, border level, progress, settings, lifecycle state, revision)
- `IslandSlot` (stabile Slot-ID, Grid-Koordinaten, Slot-Status, zugeordnete Region-Koordinaten, jemals belegt, Wipe-Generation)
- `IslandMembership` (player UUID, island UUID, role, joined time, status)
- `ArchivedIsland` / `IslandRevision` (Quell-Island, previous slot, Snapshot von Inselmetadaten, Ablaufzeit, Besitzer-/Mitgliederzuordnung)
- `IslandOperation` (UUID, Typ, Status, Actor, Ziel, persistierter Intent, Revision, Timestamps, Result)
- `ProgressLedger` und `RewardLedger` (Event-/Milestone-IDs, per-Island- und per-Player-Deduplizierung)
- `EventInstance` (ID, Typ, Phase, Zustand, Start-/Endzeit, betroffene Pokémon-/NPC-IDs, Reward-Status)
- `BiomeEdit` (Region/Position, vorher/nachher, Commit-Status, bounded Undo)
- `AuditEntry` (Actor, Ziel, Handlung, Grund, Zeitpunkt, Operation-ID, Erfolg/Fehler)

**Regeln:**

- SQLite ist die Source of Truth für Inselzuordnung und dauerhafte Metadaten; Minecraft-Savedaten sind Source of Truth für geladene/gespeicherte Weltblöcke, Entity-Persistenz und Spieler-Inventare. Konflikte werden über den persistenten Vorgangsstatus abgeglichen, niemals per Zufall entschieden.
- Pro Spieler höchstens **eine aktive Inselmitgliedschaft** (Owner oder Member). Datenbank-Constraints erzwingen diese Invariante, zusätzlich zu Anwendungstests.
- Jede aktive Insel hat genau einen Owner; Mitglieder dürfen nicht unabsichtlich Owner zweier Inseln werden.
- Slotvergabe, Reset, Restore, Löschen und Besitzerwechsel sind serialisierte, widerspruchsfreie Operationen mit optimistischem Revisionscheck oder einer gleichwertigen Mechanik.
- Zeitpunkt und Bedeutung von „fertig“ sind für alle schreibenden APIs definiert. Callbacks dürfen nicht vor dem DB-Commit Erfolg melden.
- Sensible Aktionen sind auditierbar. Logs speichern keine Secrets/Passwörter im Klartext.
- Reward-Deduplizierung ist explizit. **Keine unrealistische Behauptung globaler Exactly-once-Garantien** für Minecraft-Inventare, Pokémon-PC und SQLite gleichzeitig: Wo keine atomare Transaktion möglich ist, wird ein sicherer, wiederanlauffähiger Workflow mit eindeutiger Reward-ID, Ledger, Nachprüfung und konservativer Fehlerbehandlung implementiert.


### Datenmodell: zusätzliche unverhandelbare Invarianten

- Verwende drei getrennte Identitäten: **`islandId` (Objekt-/Content-Identität)**, **`slotId` (Ort/Dateibesitz)**, **`playerId` (Minecraft-UUID)**. Ein Wechsel von Active/Archived darf keine neue UUID für dieselbe physische Insel erzeugen. Ein neuer Reset-Slot erhält dagegen immer eine neue Insel-UUID.
- `worldId`/`dimensionKey` und eine **World-Installation-ID** binden Metadaten an das richtige Save. Verhindere, dass eine DB aus Welt A still einer Welt B zugeordnet wird. Import/Klonen nur über dokumentierten Workflow.
- `generation` und `revision` sind monoton. Stale Commands, UI-Buttons, Async-Callbacks und Addon-Events sind bei falscher Revision abzulehnen.
- **Constraint-Beispiele:** unique active membership per player; unique active island per slot; unique owner relation per active island; unique reward-issuance key `(scope, rewardDefinition, milestone, recipient)`; unique operation request-id; delete state kein aktiver Slot.
- Zulässige Online-Operationen pro Insel mit `single-writer`-Koordination. Speicher-Worker führen keinen Minecraft-Weltzugriff aus. Weder auf dem Main Thread wartende Futures noch Locks quer über Server-/DB-Threads.
- **Kein kostenloser Welt-Snapshot bei Reset:** Inventare, Party/PC und private Enderchests liegen außerhalb des Insel-Snapshots. Ein Restore gibt nur den *unveränderten, eingefrorenen* physischen Inselstand zurück; es rekonstruiert keine während der Archivierung entnommenen Items.
- `schema_version`, `data_version` und `content_pack_version` auseinanderhalten. Eine geänderte Lootdefinition verändert nicht rückwirkend historische Ledger-Einträge.

## 17. Speicher, Concurrency, Backup und Disaster Recovery

### SQLite und Asynchronität

- Ein dokumentierter SQLite-Thread-/Connection-Ansatz, PRAGMA-Konfiguration (einschließlich WAL je nach nachgewiesener Umgebung), Version-Migrationen und geordnete Writes pro Island.
- Server-Thread blockiert niemals auf Dateisystem-/DB-I/O. Domain-Kommandos verwenden `PENDING` und asynchrone Operationen; Weltänderungen nach persistiertem Intent über `server.execute(...)`.
- DB-Writes und Weltoperationen sind durch `operation_id`/Revision aufeinander bezogen. Gleichzeitige Resets, Invites, Owner-Transfers, Biome-Edits und Reward-Grants dürfen nicht dieselbe Insel inkonsistent mutieren.
- **Kritische Writes** (Create, Slot-Reserve, Reset, Restore, Delete, Owner/Membership, Event-Reward-Claim) benötigen vor erfolgreicher UI-Bestätigung einen nachgewiesenen Persistenzpunkt.
- **Häufige unkritische Zähler** dürfen kontrolliert gebündelt werden, wenn ein dokumentiertes maximal akzeptables Verlustfenster und definierte Crash-Recovery bestehen; keine pauschale Behauptung, jedes Mining sei bei Hard-Kill verlustfrei.
- Write-behind/Queue mit Obergrenze, Backpressure, Monitoring und Warnungen. Keine unendlichen RAM-Recovery-Listen.

### DB-Ausfall und Fail-Closed

- Kritische DB nicht verfügbar → Create/Reset/Restore/Transfer/Reward-Commit **gesperrt**, Spieler erhalten genaue Fehlermeldung; keine Erfolgsmeldung und kein unsicherer RAM-only-Fortschritt.
- Bereits sichere Read-Operationen können nach expliziter Policy weiterlaufen. Riskante Weltänderungen/OneBlock-Loot vorübergehend pausieren, wenn der erforderliche Persistenz-/Dupe-Vertrag nicht gewährleistet ist.
- Recoverable lokale Journale nur, wenn ihr eigener Crash-/Durability-Pfad getestet ist, mit Dateigrößenlimit, Checksums, atomarem Commit und idempotentem Replay. Kein blindes „beim Shutdown in eine Recovery-Datei schreiben“ als Ersatz für persistente Laufzeitdaten.
- Nach Recovery Reconciliation aller offenen Vorgänge; Adminbericht über unklare Fälle statt automatischer Erfindung fehlender Rewards.

### Backups, Restore, Reparatur

- Backups müssen **SQLite und Weltregionen zum selben logisch konsistenten Zeitpunkt** erfassen. Sicherer Online-Snapshot nur, wenn nachgewiesen; andernfalls dokumentierter Wartungsmodus / serverseitiges Quiescing oder Cold Backup. Bloßes Kopieren einer aktiven `.db` und `.mca` genügt nicht.
- Automatisches Backup-System: konfigurierbares Intervall, Aufbewahrung, Platz-/Fehlerlimits, Integritätschecks, optional Kompression. In v1 mindestens sicherer Backup-Befehl plus dokumentierter Offline-Backup-Workflow; vollautomatische Online-Backups nur bei erfolgreicher Verifikation.
- `/ob admin doctor`: Read-only Standarddiagnose von verwaisten Slots, unvollständigen Operationen, fehlenden DB-Referenzen, invaliden Owner-Daten, fehlenden Weltdateien, hängenden Events, beschädigten Einstellungen.
- `/ob admin repair <id> --dry-run`: vollständige Vorschau. Mutierende Reparatur nur mit Backup, Bestätigung, Audit-Log, dokumentierter Wiederanlaufstrategie.
- Mindestens ein kompletter, dokumentierter Restore-Test von einer absichtlich beschädigten/intern unterbrochenen Testwelt.
- Kein automatisches Löschen/Neu-Erstellen einer defekten DB, das Nutzerinformationen verlieren könnte.


### Präziser Reliability-Vertrag (auch gegen falsche Perfektionsbehauptungen)

- **Acknowledged critical metadata:** Ziel `RPO = 0` für bereits erfolgreich bestätigte, transaktional gespeicherte Insel-/Slot-/Owner-/Ledger-Änderungen unter der dokumentierten SQLite-/Filesystem-Durability-Konfiguration. Gilt **nicht** automatisch für Weltblöcke, Vanilla-Inventare und Cobblemon-PC.
- **World-/Inventory-Recovery:** Definiere separat, welche Veränderungen bis zum letzten bestätigten Chunk-/Player-Save und unter welchen Crash-Szenarien verloren gehen können. Keine erfundene globale `RPO=0`-Garantie für mehrere Persistenzsysteme.
- **RTO:** Startup-Reconciliation muss mit Testdaten gemessen werden. Kein fester Zeitwert versprechen, solange kein Lasttest existiert. Bei großer Recovery-Queue dürfen sichere, nicht betroffene Inseln gemäß definierter Isolation weiterlaufen.
- Verwende SQLite-WAL **nur wenn nachweislich unterstützt**, mit dokumentierter `synchronous`-Policy, `busy_timeout`, Integritätscheck, WAL-Checkpoint und Disk-Space-Monitoring. Datenbank/WAL **nicht** auf unsicheren verteilten Dateisystemen betreiben.
- SQLite **Online Backup API** sichert nur die DB, nicht automatisch `.mca`/Player/Cobblemon-Dateien. Globales Disaster-Recovery benötigt eine geprüfte Quiesce-/Save-Grenze und Manifest für DB + alle benötigten Weltdaten.
- Hard-Kill, Disk-Full, `SQLITE_BUSY`, DB-Lock, beschädigter DB-Header, beschädigte `.mca`, read-only volume, fehlerhafte Migration, unterbrochene WAL-Checkpoint- und Restore-Szenarien getrennt simulieren.
- Führe ein Recovery-Manifest mit `backupId`, World-ID, Versions-/Schema-Hashes, Zeitpunkt, enthaltenen Dateien, Checksums, erfolgreichen/fehlgeschlagenen Operationen und Restore-Anleitung. Kein Backup gilt als erfolgreich, bevor sein **Testrestore** nachweislich funktioniert.
- Commit-Reihenfolge, `fsync`-/Durability-Annahmen und Prozesseigenschaften werden nach OS überprüft. Eventuelle Einschränkungen sind Release-Notes-Pflicht.

## 29. Concurrency- und Threading-Modell

### Ownership der Mutationen

- **Server-Thread:** World-/Block-/Entity-Mutationen, Teleports, Minecraft-/Cobblemon-API-Aufrufe, für die keine eindeutige Thread-Safety nachgewiesen wurde.
- **DB-Executor:** SQL/Transaktionen/Migrationen, persistente Queues; keine Live-Minecraft-Objekte nach außen, nur unveränderliche Records/Snapshots.
- **Worker:** reine Berechnungen (Loot-Simulation, Content-Validierung, sichere Datei-Checksums und zugelassene Cold-Tasks), niemals ungenehmigter Weltzugriff.
- **Scheduler:** bounded Tasks mit Deadline, Cancellation, Backpressure, Fairness und klarer Shutdown-Reihenfolge. Keine `Future.get()`/`join()` auf Server-Thread bei DB-/World-Abhängigkeit.

### Reihenfolge eines gemeinsamen State-Changes

1. Auf Server-Thread Request parsen, validieren, actor/scope/revision snapshotten.
2. Kritische DB-Intent-Transaktion asynchron ausführen.
3. Callback **auf Server-Thread**, Revisions-/Existenzprüfung erneut durchführen, dann erlaubte Weltoperationen ausführen.
4. Bei Weltänderung erforderlichen Save-/Outcome-Status persistieren oder `RECOVERY_REQUIRED` setzen; nur korrekt abgeschlossene Operation als `SUCCESS` an Client melden.
5. Bei Stop den Scheduler in **Drain / Quiesce / Persist / Shutdown** überführen; bei Timeout fail-closed und klarer Recovery-State, niemals fröhliches Weiterarbeiten.

**Deadlock-Invariante:** Nie Server-Lock halten und auf DB warten; nie DB-Lock halten und synchron auf Welt-Thread warten. Per-Island-Serialisierung hat definierte Fairness und Timeout.

## 32. Operations, Monitoring und Incident Response

- **Startup Preconditions:** Versionen/Registry, gültige Config, Datenträgerrechte/-platz, DB-Integrität, World-ID, offene Operationen, gefährliche `PENDING_WIPE`-Fälle. Startup-Status `READY`, `SETUP_PENDING`, `RECOVERY_MODE`, `READ_ONLY` mit klaren erlaubten Befehlen.
- **Shutdown:** geordnetes Stoppen neuer Spieleroperationen, Quiesce von Spawn-/Biome-/Event-Jobs, bestätigte Flush-/Save-Punkte, Abschluss-/Recovery-Protokoll. Reboot während fehlender Datenspeicherung darf nicht als clean gemeldet werden.
- **Health:** Admins sehen Last, Backpressure, Spawn-Gründe, offene Jobs, Reward-Claims, DB-Wartezeit, Save-/Backup-Status. Kein ständig aktives heavy tracing.
- **Incident Runbook:** Beweise sichern, betroffenes Insel-/World-Scope isolieren, sicher stoppen, Backup prüfen, `doctor` lesen, Recovery-/Restore-Weg wählen, Ergebnis in Audit dokumentieren. Keine automatische „Reparatur“ via Löschen ganzer Inselordner.
- **Rollback:** Rollback nur nach Formatkompatibilitätsprüfung; bei Schema-/Worldgen-Upgrade stattdessen Backup-Restore oder passende Forward-Migration.
- **Backup-Retention:** konfigurierbare tägliche/periodische Sicherung erst bei erfolgreichem Backupmechanismus; nie vorgeben, eine DB-Kopie allein sei eine vollständige Insel-Sicherung.

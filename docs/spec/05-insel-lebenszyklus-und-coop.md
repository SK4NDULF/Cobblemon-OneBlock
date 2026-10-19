# Insel-Lebenszyklus, Reset/Archiv/Restore und Co-op

> **Spec v4.1** · Teil von `docs/spec/` (Übersicht: `00-INDEX.md`) · Quelle: Masterprompt v4 §8, §10  
> **Lesen in Phase:** 2 (Create/Home), 5  
> Abschnitte mit **[v4.1]** sind Ergänzungen gegenüber v4. Alles andere ist der v4-Text unverändert (nur Dokumentnamen an den zusammengelegten Dokumentensatz angepasst).

## 8. Insel-Lebenszyklus, Reset, Archiv und Restore

### 8.1 Verbindliches Zustandsmodell

`CREATING → ACTIVE → ARCHIVED → PENDING_DELETE → DELETING → DELETED`

Zusätzliche transiente/persistente Operationszustände mindestens `RESTORING`, `FAILED`, `RECOVERY_REQUIRED`; die tatsächlichen Übergänge als Diagramm und Tests dokumentieren. Nie einen Slot freigeben, bevor sein Wipe vollständig und überprüfbar abgeschlossen ist.

### 8.2 Reset-/Archiv-Semantik

- Jeder **Reset** reserviert einen **neuen Slot**; keine Insel wird am alten Ort überschrieben.
- Das bisherige `ACTIVE`-Island wird **für 7 Tage** wiederherstellbar archiviert. Die neu aktive Insel erhält eine **neue Island-UUID**, einen eigenen Fortschritt und eigene Event-Zustände. Spieler behalten ihre Vanilla-Inventare und Cobblemon-Party/PC; **Island-Blöcke und islandgebundene Belohnungen werden nicht automatisch in das Inventar transferiert**.
- Co-op-Owner und Mitglieder werden beim Reset entsprechend ihrer gültigen Zugehörigkeit auf die neue aktive Insel übernommen; offline Mitglieder und pending Invites werden ausdrücklich konsistent behandelt. Gespeicherte Betten/Home-Positionen werden validiert bzw. zurückgesetzt.
- **Maximal ein wiederherstellbares Archiv pro Island-Owner** (präzise: pro aktuellem Owner-Account, nicht pro Member). Vor erneutem Reset bei vorhandenem Archiv: entweder bisheriges Archiv wiederherstellen oder es nach expliziter Bestätigung unwiderruflich in die Löschwarteschlange verschieben. Nie still löschen.
- **Restore** wechselt vom aktuell aktiven Island B zurück auf das archivierte Island A. Die aktuell aktive B-Insel wird wiederum das **eine** Archiv, mit neuer Ablaufzeit. A bleibt physisch unverändert; kein Copy/Paste von Chunks. Dieser Tausch ist serialisiert, crash-wiederanlauffähig und benötigt Bestätigung. Keine Doppelmitgliedschaften.
- Insel-Profil-/Fortschrittsdaten werden zusammen mit dem jeweiligen Island wiederhergestellt; **spielergebundene Einmal-Belohnungen/Account-Sperren niemals zurückgesetzt**.
- Reset-Cooldown default **24 h**, Restore- und Reset-Rate-Limits konfigurierbar. Admin-Force-Aktionen umgehen ggf. Cooldowns, **niemals** Daten- oder Sicherheitschecks.
- Laufende Events, offene Rewards, Pokémon-Battles, Edit-Jobs und ausstehende Speicheraktionen haben klar definierte Abbruch-/Drain-Regeln, bevor die aktive Insel wechselt.

### 8.3 Löschen und Wipe

- Ablaufende Archive → `PENDING_DELETE` mit persistentem Wipe-Intent. Ein pro Slot eindeutiger Wipe-Vorgang besitzt Generation/ID und Fortschrittsjournal.
- **Dateien nur bei garantiert ungeladener Dimension entfernen** (bevor die betroffenen Weltregionen geöffnet werden; Implementierungszeitpunkt anhand realer Lifecycle-APIs nachweisen). Kann das beim normalen Modstart nicht sicher garantiert werden, ein geprüftes Offline-Cleanup-Verfahren anbieten und Startup blockieren, statt gefährlich zu löschen.
- Wipe umfasst ausschließlich die dem Slot gehörenden Weltdatei-Bereiche und relevanten persistenten Insel-/Eventdaten. Fremde und Spieler-Inventare sowie Pokémon in Party/PC bleiben unberührt.
- Fehlgeschlagener Wipe → Slot gesperrt, `FAILED/RECOVERY_REQUIRED`, Diagnose + manueller Reparaturpfad. Beim Restart idempotent fortsetzbar.
- `forcereset`/`forcedelete` verlangen zweistufige Bestätigung mit kurzlebiger, an Actor/Target/Operation gebundener ID. Admin darf die Aktion überprüfen und abbrechen, solange kein irreversibler Schritt begonnen hat.
- Insel-Mitglieder vor gesperrtem oder deaktiviertem Zugriff sicher in Hub teleportieren; Offline-Spieler erhalten Join-Time-Reconciliation und Benachrichtigung.
- Explizite Befehle für `status`, `doctor`, `repair --dry-run`, `backup`, `restore` unter Admin-Berechtigung. Reparatur niemals ohne Vorschau/Backup mutierend durchführen.


### Crash-Konsistenz für Insel-Operationen: Saga-Protokoll

Definiere für `CREATE`, `RESET`, `RESTORE`, `DELETE`, `TRANSFER` einen **durablen Vorgangsdatensatz** mit `operationId`, `requestId`, `actor`, `expectedRevision`, `sourceIsland`, `targetIsland`, `sourceSlot`, `targetSlot`, `stage`, `startedAt`, `lastError`, `recoverability`. Prozessbeispiel für Reset:

1. Request autorisieren, Revisions-/Cooldown-/Mitglieder-/Event-/Edit-Prüfungen durchführen; nur **ein** aktiver Reset pro Insel.
2. `RESET_PREPARED` + neue Slot-Reservierung in **einer SQLite-Transaktion** bestätigen. Alte Insel wird dabei noch nicht als endgültig ausgetauscht gemeldet.
3. Kritische laufende Aktionen quiescen und Inselzugriff temporär sperren; Spieler auf sicheren Zwischenort bringen; World-State an kontrollierter Save-Grenze sichern.
4. Neue Insel/OneBlock/Metadaten in wiederholbaren Teilschritten aufbauen; jedem Teilschritt durable Stage zuweisen. Weltoperationen nur auf Server-Thread.
5. In einer konsistenten SQLite-Transaktion Active/Archived-Zuordnung, Membership-Revision, Archiv-Ablauf und Cooldown festschreiben.
6. Join-/Teleport-Routing auf neuen Zustand schalten; Spieler informieren; Operation `COMPLETED` dokumentieren. Wird ein Schritt vor der Bestätigung unterbrochen, Recovery führt aus nachweislichem Zustand **nur erlaubte Schritte** weiter.
7. Jeder Stage-Übergang besitzt Tests **Crash davor, Crash danach, gleichzeitige Retry, doppelte Command-ID**.

**Ein Commit, das Metadaten umschaltet, macht nicht automatisch alle `.mca`-Daten synchron.** Wenn das Save-/World-Gate nicht beweisbar ist, baue ausdrücklich einen Wartungs-/Cold-Path und kennzeichne die Aktion als nicht online-sicher; keine falschen Crash-Garantien. Recovery bei nicht eindeutigem Ergebnis → `RECOVERY_REQUIRED`, weder raten noch Spielerinventare kompensatorisch verändern.

**Archiv-Verfall:** 7 Tage werden per Echtzeit berechnet, aber Löschung ist nie ein harter Timer. Beim Ablauf wird nur ein Löschintent gesetzt; physische Entfernung erfolgt ausschließlich im technisch nachgewiesen sicheren Wartungsfenster. Der Server darf aktive Inseln weiter betreiben, solange abgelaufene Slots sicher gesperrt bleiben.

### [v4.1] Archiv bei Owner-Wechsel (Vorschlag, Status siehe `docs/DECISIONS.md` Nr. 10)

- Das Archiv gehört zur **Insel-Linie** (der aktiven Insel und ihrer Mitgliedschaft), nicht zu einer Person. Bei `/ob transfer` gehen aktive Insel **und** Archiv gemeinsam an den neuen Owner. Nur der aktuelle Owner darf wiederherstellen.
- Damit bleibt „maximal ein Archiv pro Owner“ widerspruchsfrei, weil ein Owner genau eine aktive Insel hat.
- Besitzt der neue Owner bereits ein eigenes, älteres Archiv, wird der Transfer **nicht** still ausgeführt: Die UI erklärt den Konflikt, und der Empfänger muss sein altes Archiv vorher ausdrücklich verwerfen.
- **Reset-Cooldown und Restore-Limits binden an Insel-Linie und Akteur.** Transfer-Ping-Pong (übertragen → resetten → zurückübertragen → resetten) darf keinen Cooldown umgehen. Regressionstest Pflicht.

## 10. Co-op- und Owner-Regeln

- Ein Spieler gehört zu genau einer aktiven Insel (Owner oder Member).
- `/ob invite`, `accept`, `deny`, `kick`, `leave`, `transfer`, `members`, `settings`.
- Einladungen default **2 Minuten**, maximale Partygröße vom Wizard (Default **4**, Limit **20**). Einladungen referenzieren die erwartete Island-Revision, damit eine Einladung aus einer archivierten Insel nicht fälschlich einer neuen beitritt.
- Owner darf nur nach erfolgreichem `/ob transfer` verlassen. Bei Admin-Entfernung geht Ownership an das am längsten zugehörige **aktive** Mitglied; falls keine Mitglieder existieren: Island wird eingefroren und Admin-Entscheid nötig, statt einen ungültigen Owner zu erzeugen.
- `transfer`, `kick`, `accept` und `reset` zur gleichen Zeit müssen konsistent enden. Jeder Schritt hat DB-Constraints und Race-Tests.
- Reset-/Restore-Folgen für Co-op werden im UI vor Bestätigung erklärt; keine unangekündigte Löschung von gemeinschaftlich gebauten Inhalten.
- **V1 bewusst einfach halten:** Keine zusätzlichen Builder-/Manager-Rollen. Erweiterbarkeit für solche Rollen dokumentieren, ohne v1 zu überfrachten.


### Co-op-Abläufe als explizite Produktentscheidungen

- `invite` an Spieler mit eigener Insel oder Mitgliedschaft: **keine stille Zusammenführung**. UI zeigt Konflikt; Spieler muss seine aktuelle Zugehörigkeit verlassen/übergeben oder Admin-Workflow auslösen. Keine automatische Löschung der bisherigen Insel.
- `kick/leave/transfer` löst einen sicheren Join-/Respawn-Pfad aus; Offline-Spieler bei nächstem Join neu zuordnen. Eingeladene, gebannte, abgemeldete oder gleichzeitig akzeptierende Nutzer können keine veralteten Permissions wiederverwenden.
- Owner darf die letzte aktive Insel nicht durch `leave` unsichtbar machen; `delete` benötigt getrennte, ausdrückliche Bestätigung.
- Jeder Member erhält klare Information: Reset und Restore tauschen **gemeinsame Bauten** aus. Bei Discord-/Offline-Mitgliedern bleiben Events als Benachrichtigung gespeichert, ohne eine externe Plattform vorauszusetzen.

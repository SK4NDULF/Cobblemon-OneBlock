# Testmatrix

> Stand: Phase 1 (2026-10-19). Status: `PASS` = ausgeführt und bestanden · `OPEN` = geplant · `OWNER` = braucht Owner mit echtem Client.
> Automatisierte Tests laufen mit `./gradlew build` (lokal und in GitHub Actions). jqwik-Properties laufen mit 1000 Fällen und zufälligem Seed; ein fehlschlagender Seed steht im Testbericht.

## Automatisierte Tests (78)

| Bereich | Klasse | Inhalt | Status |
|---|---|---|---|
| Grid | `GridGeometryProperties` | Stride, Region-Ausrichtung, negative Koordinaten, Insel in Zelle, Spirale bijektiv/umkehrbar, Hub-Zelle nie vergeben, Hub-Radius-Grenze, Weltgrenze | PASS |
| Config | `ConfigLoaderTest` | Default-Datei, Kommentare, unbekannte und doppelte Schlüssel, Bereiche, Typen, gerade Inselgröße, Hub-Radius vs. Stride, Syntaxfehler mit Zeile, abschließendes Komma, fehlende Schlüssel, neuere Version | PASS |
| Config | `ConfigManagerTest` | Default-Datei ohne Temp-Reste, ungültige Datei wird nie überschrieben, fehlgeschlagener Reload ändert nichts, restart-Felder, locked-Felder | PASS |
| Lebenszyklus | `LifecycleTest` | erlaubte Übergänge, `RECOVERY_MODE`/`READ_ONLY` ohne Neustart nicht verlassbar, `STOPPING` final | PASS |
| Diagnose | `DiagnosticsTest` | ID-Format, begrenzte Liste | PASS |
| Texte | `MessagesTest` | gleiche Schlüssel und Platzhalter in `de_de`/`en_us`, alle Zustände übersetzt, Sprachauflösung, Fallbacks | PASS |
| DB-Thread | `DbExecutorTest` | Reihenfolge, eigener Thread, volle Queue → sichtbarer Fehler, Shutdown arbeitet Queue ab, Timeout schließt die Verbindung nicht unter laufender Arbeit | PASS |
| Migrationen | `MigrationRunnerTest` | frische DB, Wiederholung, Backup vor Migration (Inhalt geprüft), neueres Schema, manipulierte Prüfsumme, fremde DB, `user_version`-Abweichung, SQL-Splitter | PASS |
| Speicher-Start | `StorageBootstrapTest` | frische Welt, Neustart, fremder Seed, fehlende DB mit Marker (keine leere DB), fehlender Marker, fremder Marker, Datei-Müll bleibt unberührt, offene Vorgänge blockieren | PASS |
| Journal | `OperationJournalTest` | Idempotenz per `requestId`, Wiederverwendung abgelehnt, Revision, abgeschlossene Vorgänge unveränderlich, offene Liste, DB-Constraints, Audit | PASS |
| Crash | `CrashDurabilityTest` | Kindprozess schreibt, 3× `kill -9`: Integrität ok, alle bestätigten Zeilen da | PASS |

## Dedicated-Server-Tests Phase 1

Umgebung: Linux x64 Container, OpenJDK 21.0.12, Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1, Cobblemon 1.8.2, Mod `cobblemon-oneblock-0.1.0-dev.jar`. Carpet und Spike-JAR nur in S8 (Kampfstart per Fake-Spieler).

| ID | Szenario | Ergebnis | Status |
|---|---|---|---|
| S1 | Frische Welt, erster Start | Default-Config angelegt, DB + Marker angelegt, Schema v1, WAL, `SETUP_PENDING` | PASS |
| S2 | Neustart | gleiche Installations-ID, keine erneute Migration | PASS |
| S3 | Bestehende Welt (vorher nur Cobblemon) | DB + Marker angelegt, `SETUP_PENDING` | PASS |
| S4 | `/ob status`, `/ob reload` mit Fehlern und mit gültiger Datei | Fehler mit Zeile/Spalte/Vorschlag, nichts übernommen; live übernommen, restart erst nach Neustart (nach Neustart bestätigt: Queue 2048) | PASS |
| S5 | DB-Header zerstört | Server bootet, `RECOVERY_MODE` mit `OB-STORAGE`-ID, Datei unverändert (Prüfsumme), sauberer Stopp | PASS |
| S6 | DB gelöscht, Marker vorhanden | `RECOVERY_MODE`, **keine** neue DB angelegt; nach Zurückkopieren wieder dieselbe Installation | PASS |
| S7 | `kill -9` im Betrieb | Neustart ok, `integrity_check = ok` | PASS |
| S8 | Kampf, Pokémon bleibt draußen, `/stop` | „Recalled 1 sent-out Pokémon“, keine Exception, Prozess beendet (2/2) | PASS |
| S9 | Cobblemon 1.8.1 statt 1.8.2 | Loader verweigert: „requires version 1.8.2 … only the wrong version is present: 1.8.1“ (Prozess lief danach weiter, Fabric-Loader-Verhalten) | PASS |
| S10 | Ungültige Config beim Start | Datei unverändert, `SETUP_PENDING` mit `OB-CONFIG`-ID; nach Korrektur + Reload Grund weg | PASS |
| O1 | Join mit echtem Cobblemon-1.8.2-Client, `/ob status` auf Deutsch und Englisch | – | OWNER |

## Invarianten

| ID | Aussage | Test |
|---|---|---|
| INV-GRID-1 | Keine Region-Datei gehört zu zwei Slot-Zellen | `GridGeometryProperties.cellsAreUnionsOfWholeRegions` |
| INV-GRID-2 | `cellOf` ist exakt, auch für negative Koordinaten | `cellOfIsInverseOfCellRange` |
| INV-GRID-3 | Eine Insel maximaler Größe plus halber Puffer liegt in ihrer Zelle | `islandFitsInsideItsCell` |
| INV-GRID-4 | Slot-Index ↔ Zelle ist umkehrbar | `spiralRoundTrip`, `spiralIsBijectiveAndOnlyIndexZeroIsHub` |
| INV-GRID-5 | Kein Spieler-Slot berührt die Hub-Zelle | `playerSlotsNeverTouchHubCell` |
| INV-GRID-6 | Keine nutzbare Insel überschreitet die Weltgrenze | `worldLimit`, `maxCellIndexIsTight` |
| INV-DB-1 | Eine vorhandene DB-Datei wird nie automatisch gelöscht oder ersetzt | `garbageDatabaseFileFailsAndIsNotReplaced`, S5 |
| INV-DB-2 | Fehlt die DB einer eingerichteten Welt, entsteht keine leere neue | `missingDatabaseWithMarkerIsRefusedWithoutCreatingAnEmptyOne`, S6 |
| INV-DB-3 | Eine DB aus einer anderen Welt wird nicht benutzt | `databaseFromAnotherWorldIsRefused`, `markerFromAnotherInstallationIsRefused` |
| INV-DB-4 | Keine Migration ohne geprüftes Backup einer bestehenden DB | `migratingAnExistingDatabaseCreatesAVerifiedBackupFirst` |
| INV-DB-5 | Kein Downgrade auf ein älteres Schema | `newerSchemaIsRefused` |
| INV-DB-6 | Bestätigte Commits überleben einen Prozess-Kill | `CrashDurabilityTest`, S7 |
| INV-OP-1 | Eine `requestId` erzeugt höchstens einen Vorgang | `beginIsIdempotentPerRequestId` |
| INV-OP-2 | Ein veralteter Aufrufer kann einen Vorgang nicht weiterschalten | `advanceNeedsTheCurrentRevision` |
| INV-OP-3 | Abgeschlossene Vorgänge ändern sich nicht | `finishedOperationsCannotChange` |
| INV-OP-4 | Offene Vorgänge ohne Handler blockieren den Start (fail-closed) | `openOperationWithoutHandlerBlocksStartup` |
| INV-CFG-1 | Eine ungültige Config wird nie überschrieben und nie teilweise übernommen | `invalidFileAtBootIsKeptAndDefaultsAreUsed`, `failedReloadKeepsTheLastValidVersion`, S10 |
| INV-Q-1 | Die DB-Queue wächst nie über ihre Kapazität | `fullQueueFailsVisiblyInsteadOfGrowing` |

Aus Spec 14 noch offen (folgen mit den jeweiligen Features): nie zwei aktive Inseln pro Slot, nie ein Mitglied in zwei aktiven Inseln, kein Pending-Wipe-Slot neu vergeben, kein Reward-Key doppelt, kein Cross-Border-Blockeffekt, keine Bestätigung vor Commit.

## Akzeptanz Phase 1 (Spec 02 §22)

| Bedingung | Nachweis | Status |
|---|---|---|
| Clean-Server-Boot mit Pflichtmods | S1, S3 | PASS |
| Client-Join | O1 | OWNER |
| DB-Migration | `MigrationRunnerTest`, S1, S2 | PASS |
| Crash- und Fail-Closed-Basistests | `CrashDurabilityTest`, S5–S7, S10 | PASS |

## Performance-Budget

Phase 1 hat keinen Code im Tick-Pfad: Es gibt keine Tick-Handler, keine Event-Listener auf Blöcke oder Entities, und die DB läuft auf einem eigenen Thread. Messungen beginnen mit Phase 2 (OneBlock-Abbau, Schutz-Hooks). Vorgesehene Messgrößen laut Spec 14 §20: mod-eigene Tickzeit (Median, p95, p99, Spitzen), DB-Wartezeit, Queue-Tiefe, Speicher. Referenzhardware: die Ziel-Hardware des Owners; synthetische Messungen werden als solche gekennzeichnet.

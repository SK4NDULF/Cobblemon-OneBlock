# Konfiguration

> Stand: Phase 1 (2026-10-19). Quelle der Wahrheit ist `ConfigSchema.java`; diese Tabelle muss dazu passen.

## Datei

`config/oneblock/config.json5` im Server-Ordner. Beim ersten Start schreibt der Mod eine kommentierte Standarddatei. Eine vorhandene Datei wird **nie** überschrieben, auch keine ungültige.

**Format:** JSON5-Teilmenge. Erlaubt sind Kommentare (`//`, `/* */`, `#`) und Schlüssel ohne Anführungszeichen. **Nicht** erlaubt sind abschließende Kommas.

**Prüfregeln:**
- Unbekannte Schlüssel, doppelte Schlüssel, falsche Typen, Zahlen außerhalb des Bereichs und Kommazahlen bei Ganzzahl-Feldern sind Fehler. Die Meldung nennt Zeile, Spalte und Feld, bei Tippfehlern mit Vorschlag.
- Fehlende Schlüssel sind Warnungen; dann gilt der Standardwert.
- Querprüfungen: `maxIslandSize` muss ungerade sein; `hubRadius` ≤ `stride/2 − 500`.

**Ungültige Datei beim Start:** Der Mod läuft mit den Standardwerten weiter, meldet den Fehler mit Diagnose-ID und bleibt im Zustand `SETUP_PENDING`. Datei korrigieren, dann `/ob reload`.

## Reload-Klassen

| Klasse | Bedeutung |
|---|---|
| live | wird mit `/ob reload` übernommen |
| restart | wird beim Reload geprüft, gilt aber erst nach einem Neustart |
| locked | darf sich nach der ersten Insel nicht mehr ändern. Ein Reload mit geändertem Wert wird dann komplett abgelehnt. In Phase 1 gibt es noch keine Inseln, daher noch änderbar. |

`/ob reload` prüft immer die ganze Datei. Bei einem Fehler wird **nichts** übernommen.

## Optionen

| Pfad | Typ | Standard | Bereich | Klasse | Bedeutung |
|---|---|---|---|---|---|
| `configVersion` | Ganzzahl | 1 | 1 | restart | Formatversion. Nicht von Hand ändern. Eine neuere Version wird abgelehnt. |
| `setup.maxIslandSize` | Ganzzahl | 2049 | 501–10001, ungerade | locked | Kantenlänge einer Level-8-Insel |
| `setup.hubRadius` | Ganzzahl | 512 | 100–10000 und ≤ stride/2 − 500 | live | Schutzradius des Hubs |
| `setup.maxMembers` | Ganzzahl | 4 | 1–20 | live | Maximale Mitglieder pro Insel inkl. Owner |
| `setup.publicServer` | Wahrheitswert | true | – | live | Öffentlicher Server: strengere Besucher-Standards |
| `grid.bufferBlocks` | Ganzzahl | 1000 | 512–10000 | locked | Freiraum zwischen Inseln; `stride = ceil((maxIslandSize + bufferBlocks) / 1024) · 1024` |
| `storage.busyTimeoutMs` | Ganzzahl | 5000 | 1000–60000 | restart | SQLite busy timeout |
| `storage.queueCapacity` | Ganzzahl | 1024 | 16–65536 | restart | Maximale Anzahl wartender DB-Aufgaben. Bei voller Queue schlägt eine Aktion sichtbar fehl. |
| `storage.shutdownTimeoutSeconds` | Ganzzahl | 30 | 5–300 | restart | Wie lange der Shutdown auf die DB-Queue wartet |
| `logging.debug` | Wahrheitswert | false | – | live | Zusätzliche Diagnose-Ausgaben |

Mit den Standardwerten ist `stride = 3072` und der maximale Hub-Radius 1036.

## Pfade, die nicht konfigurierbar sind

| Was | Pfad |
|---|---|
| Datenbank | `<welt>/data/cobblemon_oneblock/oneblock.db` |
| Backups vor Migrationen | `<welt>/data/cobblemon_oneblock/backups/` |
| Welt-Marker | `<welt>/cobblemon_oneblock.world-id` |

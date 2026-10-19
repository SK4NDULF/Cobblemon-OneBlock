# Recovery

> Stand: Phase 1 (2026-10-19), Grundlagen. Inselbezogene Recovery (Reset, Restore, Wipe) folgt in Phase 5.

## Grundsätze

- Der Mod löscht oder ersetzt **nie** von sich aus eine vorhandene Datenbank.
- Kann er den Daten nicht trauen, geht er in den `RECOVERY_MODE`. Der Server läuft weiter, Gameplay ist aus. Den Grund zeigt `/ob status` (Admins) mit Diagnose-ID; im Log steht die gleiche ID.
- Den `RECOVERY_MODE` verlässt man nur durch Beheben der Ursache und einen Neustart.

## Dateien

| Datei | Zweck |
|---|---|
| `<welt>/data/cobblemon_oneblock/oneblock.db` (+ `-wal`, `-shm` im Betrieb) | Datenbank |
| `<welt>/data/cobblemon_oneblock/backups/oneblock-v<alt>-<UTC-Zeit>.db` | Kopie vor jeder Schema-Migration einer bestehenden DB |
| `<welt>/cobblemon_oneblock.world-id` | Marker: bindet die DB an diese Welt (Installations-ID + Seed) |

**Wichtig:** Im laufenden Betrieb nie nur `oneblock.db` kopieren. Die `-wal`-Datei enthält eventuell noch nicht zurückgeschriebene Änderungen. Für eine manuelle Sicherung den Server stoppen; nach einem sauberen Stopp existiert nur noch `oneblock.db`.

## Fälle

| Meldung (gekürzt) | Ursache | Lösung |
|---|---|---|
| `database could not be opened … not a database` | DB-Datei beschädigt | Server stoppen, `oneblock.db` aus einer Sicherung zurückkopieren (die beschädigte Datei vorher wegsichern), starten |
| `database integrity check failed` | Inhalt beschädigt | wie oben |
| `this world was set up for Cobblemon OneBlock (marker …) but its database … is missing` | DB fehlt, Marker existiert | DB aus Sicherung zurückkopieren. **Nur** wenn die Welt bewusst ohne alte OneBlock-Daten neu beginnen soll: Marker-Datei löschen; dann entsteht eine neue, leere DB |
| `database belongs to a world with seed …` | DB stammt aus einer anderen Welt | richtige DB einspielen. Welt und DB immer zusammen kopieren |
| `installation id in the database … does not match the marker file` | DB oder Marker aus einer anderen Installation | zusammengehörige DB und Marker einspielen |
| `database schema version … is newer than this mod` | Mod-Downgrade | neuere Mod-Version verwenden oder die Sicherung von vor dem Update einspielen |
| `applied migration V… has a different checksum` | Mod-Datei verändert oder beschädigt | offizielles Mod-JAR verwenden |
| `unfinished operation …: no handler` | Ein kritischer Vorgang wurde unterbrochen, und diese Mod-Version kann ihn nicht fortsetzen | Logs sichern, Owner/Entwickler kontaktieren. Nichts von Hand in der DB ändern. |

**Fehlender Marker bei vorhandener DB:** Stimmt der Seed, legt der Mod den Marker neu an und loggt eine Warnung (Fall: Absturz zwischen DB-Anlage und Marker). Stimmt er nicht, gilt die Zeile „anderer Seed“.

## Migrationen

Vor jeder Schema-Migration einer bestehenden DB entsteht per `VACUUM INTO` eine Kopie in `backups/`. Die Kopie wird geöffnet und geprüft (Integrität und Schema-Version). Ohne geprüfte Kopie wird nicht migriert.

**Rückweg nach einer fehlgeschlagenen oder unerwünschten Migration:**
1. Server stoppen.
2. Die alte Mod-Version wieder einsetzen.
3. `backups/oneblock-v<alt>-….db` nach `oneblock.db` kopieren (vorher `oneblock.db`, `-wal` und `-shm` wegsichern).
4. Starten.

Änderungen seit der Migration sind dann verloren. Phase 1 hat nur eine Migration (V1), der Pfad ist deshalb bisher nur mit einer Test-Migration (V2) automatisch getestet.

## Shutdown

Beim Stopp: neue Aktionen sperren → ausgesandte Spieler-Pokémon zurückrufen (ADR 005) → DB-Queue abarbeiten → Verbindung schließen. Schafft die DB das nicht innerhalb von `storage.shutdownTimeoutSeconds`, steht ein `OB-STORAGE`-Eintrag im Log: Die zuletzt eingereihten Änderungen können fehlen.

## Grenzen

- Durability ist für **Prozessabstürze** getestet (`kill -9`). Stromausfall oder OS-Absturz sind nicht getestet; `synchronous=FULL` ist dafür die SQLite-Einstellung, die Garantie hängt aber vom Dateisystem ab.
- Welt-Dateien und SQLite sind nicht gemeinsam atomar. Ab Phase 2 gleicht das Vorgangs-Journal beides nach einem Absturz ab.

# Fortschritt

> Stand: 2026-10-19 · Spec v4.1

## Aktive Phase

**0B – Feasibility: abgeschlossen, wartet auf das Phase-0-Gate.** Phase 1 beginnt erst nach dem OK des Owners.

## Erledigt

### Phase 0A
- Repository geleert, Spec v4.1 und `CLAUDE.md` eingecheckt, Owner-Antworten in `docs/DECISIONS.md` (Nr. 1, 2, 9–11, 13–16), `LICENSE` (MIT).

### Phase 0B
- Spike auf Branch `spike/0b-feasibility` (Commit `f6383a6`), getestet auf einem headless Dedicated Server (Linux x64) mit Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1, Cobblemon 1.8.2 und Carpet-Bots als Testspielern.
- Ergebnisse in `docs/POC_RESULTS.md`, Entscheidungen in `docs/adr/001`–`007`, dazu `ARCHITECTURE.md`, `docs/RISK_REGISTER.md`, `docs/DEPENDENCY_MATRIX.md`.

Kurzfassung:

| Spike | Ergebnis |
|---|---|
| Void-Dimension per Datapack (frische und bestehende Welt) | `VERIFIED` |
| Natürliche Cobblemon-Spawns in der Void-Dimension | `VERIFIED` (380 Spawns im ersten Lauf) |
| Biomabhängigkeit (Plains / Wüste / Ozean, Negativkontrolle Bretter) | `VERIFIED` |
| Lokaler Spawn-Filter pro Insel ohne Mixin | `VERIFIED` (Hub: 0 Spawns) |
| Wildkampf, NPC-Trainer, Fang serverseitig | `VERIFIED` |
| Shutdown nach Kampf | Fake-Spieler: `FAILED` ohne Gegenmaßnahme (4/4), sauber mit Rückruf (2/2); echter Client: sauber (0/1) |
| SQLite Jar-in-Jar, WAL, DB-Thread | `VERIFIED` (Linux x64) |
| Grid-Mathematik | `VERIFIED` (10 Property-Tests) |
| Paket-Hologramm | `VERIFIED` (Anzeigen/Entfernen im echten Client) |
| Client-Join, Darstellung | Owner-Test 2026-10-19: alle 4 Tests bestanden |

## Offen

- Owner-Entscheidung `DECISIONS` Nr. 12 (Spawn-Weg). Vorschlag: ADR 003, Weg 0 (Cobblemon-Standard plus lokale Influence).
- Freigabe für Phase 1.

## Owner-Tests am Phase-0-Gate (erledigt 2026-10-19, alle bestanden)

Mit dem Spike-JAR (`oneblock-spike-0b-0.0.0-spike.jar`) auf einem Testserver mit Fabric API 0.116.17+1.21.1 und Cobblemon 1.8.2, **keine** echte Welt verwenden:

1. Mit dem normalen Cobblemon-1.8.2-Client joinen. Erwartung: Join klappt ohne Fehlermeldung.
2. `/execute in cobblemon_oneblock:void run tp @s 0 100 0` mit Creative-Flug. Erwartung: leere Welt, normaler Himmel, kein Fehler.
3. `/execute in cobblemon_oneblock:void run fill -5 64 -5 5 64 5 minecraft:grass_block`, darauf landen, `/spikeholo show ~ ~2 ~ Test`. Erwartung: Text schwebt sichtbar. Dann `/spikeholo clear`: Text verschwindet.
4. Ein wildes Pokémon in der Overworld bekämpfen, den Kampf beenden, das eigene Pokémon draußen lassen, Server mit `/stop` beenden. Im Log nach `Exception stopping the server` suchen und mitteilen, ob sie auftaucht.

## Blocker

Keine. Wartet auf die Spawn-Entscheidung (Nr. 12) und die Freigabe für Phase 1.

## Nächste Aktion

Nach dem Gate-OK: Phase 1 (Foundation) auf `main` beginnen. Leseplan: Spec 02, 03, 11, 12, 13, 14.

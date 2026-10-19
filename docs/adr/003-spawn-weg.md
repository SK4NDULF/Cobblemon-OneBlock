# ADR 003: Weg für biomabhängige Spawns

- **Status:** Vorschlag. Die Entscheidung liegt beim Owner am Phase-0B-Gate (`DECISIONS` Nr. 12).
- **Datum:** 2026-10-19

## Kontext

Spec 08 nennt die Reihenfolge: 1. Daten-Lösung, 2. eigener Insel-Spawner, 3. Eskalation. Das Go/No-Go-Gate verlangt nachgewiesene natürliche, biomabhängige Spawns in der Void-Dimension.

## Ergebnis des Spikes

Cobblemons **natürliches** Spawning funktioniert in `cobblemon_oneblock:void` ohne jede Datenänderung. Die Biome steuern die Arten nachweisbar (Plains, Wüste, Ozean), Negativkontrollen bleiben leer (`docs/POC_RESULTS.md` §2). Lokale Filterung pro Position ist über die öffentliche Influence-Schnittstelle ohne Mixin möglich (§4).

## Vorschlag

**Weg 0 – Cobblemon-Standard plus lokale Influence** (noch günstiger als Weg 1, weil kein Datapack-Eingriff nötig ist):

- Cobblemon spawnt selbst um aktive Spieler herum. Inaktive Inseln ohne Spieler erzeugen damit automatisch keine Spawnkosten (Spec 08 „Unloaded islands are cold“).
- Unser Mod hängt beim Start einen `IslandSpawnInfluence` in `PlayerSpawnerFactory.influenceBuilders`:
  - `isAllowedPosition`: nur innerhalb des aktuellen Radius einer aktiven, belegten Insel. Hub, freie Zellen und Bereiche außerhalb der Inselgrenze sind gesperrt.
  - `affectSpawnable`: Insel-Budget (Standard 40 wilde Pokémon pro aktiver Insel) und spätere Freischaltungen.
  - `affectWeight`: zeitlich begrenzte Insel-Buffs (gedeckelt).
- Zusätzlich `CobblemonEvents.ENTITY_SPAWN` als letzte Sicherung (abbrechen, wenn ein Budget überschritten wäre) und zum Markieren der Herkunft (`NATURAL_WILD`).
- Alle Hooks wirken nur in `cobblemon_oneblock:void`. Andere Dimensionen bleiben unberührt.

## Alternativen

1. **Daten-Lösung (Spec Weg 1):** nicht nötig, weil keine Dimensions- oder Kontextbedingung blockiert.
2. **Eigener Insel-Spawner (Weg 2):** volle Kontrolle über Positionen, aber deutlich mehr Code und Risiko bei Cobblemon-Updates. Bleibt als Rückfallebene, falls Influences in späteren Versionen wegfallen.
3. **Mixin in den Spawner:** unnötig, widerspricht der Mixin-Regel.

## Risiken

- `influenceBuilders` wirkt nur für **neu erzeugte** `PlayerSpawner`. Registrierung daher im Mod-Initializer, bevor Spieler joinen (Spike: funktioniert).
- Influences bekommen Positionen über eine Liste, die pro Spieler-Spawner läuft. Der Insel-Lookup im Hot Path muss O(1) aus einem Cache kommen, keine DB-Abfrage.
- Biomgrenzen sind an Zellkanten unscharf (±1 Biomzelle).

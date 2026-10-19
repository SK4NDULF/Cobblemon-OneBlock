# ADR 002: Void-Dimension als mitgeliefertes Datapack

- **Status:** Vorschlag (verifiziert im Spike, siehe `docs/POC_RESULTS.md` §1)
- **Datum:** 2026-10-19

## Kontext

Spec 04 §6: eigene Void-Dimension, Overworld nie verändern, bevorzugt Datapack-Ressourcen mit Flat-Generator und leeren Layers.

## Entscheidung

- Die Dimension heißt `cobblemon_oneblock:void` (Namespace = Mod-ID, `DECISIONS` Nr. 1).
- Definition über `data/cobblemon_oneblock/dimension/void.json` (`minecraft:flat`, `layers: []`, `features: false`, `lakes: false`, `biome: minecraft:plains`) und `data/cobblemon_oneblock/dimension_type/void.json`.
- Dimension-Type: Overworld-Effekte, Himmelslicht, Tageszyklus, `bed_works: true`, `respawn_anchor_works: false`, Höhe −64 bis 320.
- Kein eigener Chunk-Generator, kein Mixin.

## Begründung

Im Spike lief das auf frischen und auf bestehenden Welten ohne manuelles Aktivieren. Overworld, Nether und End blieben unverändert.

## Offene Punkte

- **Konfigurierbares Standardbiom (Spec 04):** Das Biom steht fest im JSON. Eine Config-Änderung bräuchte ein Datapack-Override. Vorschlag für Phase 1: Standard bleibt `plains`, eine Änderung wird als Datapack-Override dokumentiert, nicht als Config-Feld.
- **Bett/Respawn:** `bed_works: true` ist gesetzt, die Respawn-Kette aus Spec 04 wird in Phase 2 getestet.
- **Mod-Deinstallation:** Die Dimension steht in `level.dat`. Das Verhalten ohne Mod ist ungetestet (Risiko R-09).
- **Client-Join mit Cobblemon-Client:** `OWNER_TEST`.

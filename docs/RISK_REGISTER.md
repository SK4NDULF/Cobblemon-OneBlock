# Risikoregister

> Stand: 2026-10-19 (Phase 0B). W = Eintrittswahrscheinlichkeit, A = Auswirkung (niedrig / mittel / hoch / kritisch). Owner aller Risiken: Projekt-Owner; technische Umsetzung: Agent.
> Offene kritische Risiken blockieren den Release.

| ID | Risiko | W | A | Gegenmaßnahme | Test | Restrisiko |
|---|---|---|---|---|---|---|
| R-01 | Shutdown-CME in Cobblemon 1.8.2 bei ausgesandten Spieler-Pokémon: Entities werden nicht gespeichert, JVM hängt (`POC_RESULTS` §6) | hoch (4/4 im Spike) | kritisch | ADR 005: Rückruf aller besessenen `PokemonEntity` in `SERVER_STOPPING` | Phase-1-Integrationstest „Kampf → Pokémon draußen → `/stop`“; Owner-Test mit echtem Client | Andere, noch unbekannte Auslöser derselben CME |
| R-02 | Welt und SQLite laufen auseinander (Crash, Shutdown-Fehler, Autosave-Rückfall) | mittel | kritisch | Durable Operations mit Stages und Reconciliation (Spec 11); Weltänderungen nur nach persistiertem Intent | Crash-Matrix Phase 1/5 | Ein Rückfall der Welt auf den Autosave bleibt möglich, die Reconciliation muss ihn erkennen |
| R-03 | Cobblemon ändert `SpawningInfluence`/`PlayerSpawnerFactory` in einem Update | mittel | hoch | Kapselung in `adapters.cobblemon`; Version fest gepinnt; Weg 2 (eigener Spawner) als Rückfall | Integrationstest pro Cobblemon-Version | Versionssprung bleibt Aufwand |
| R-04 | Spawn-Hot-Path wird teuer (Insel-Lookup pro Position) | mittel | mittel | Reiner In-Memory-Lookup über Grid-Mathematik + Slot-Cache, keine DB | Performance-Budget Phase 4 | – |
| R-05 | Fremdmod bündelt andere `sqlite-jdbc`-Version | niedrig | hoch | Start-Check der geladenen SQLite-Version; klare Fehlermeldung | Tier-B-Matrix | Verhalten des Loaders ungetestet |
| R-06 | SQLite-Natives auf Windows x64 / Linux ARM64 | mittel | hoch | Smoke-Test pro Plattform vor Release (`DECISIONS` Nr. 11) | Boot-Test Phase 8 | ARM64 ggf. nicht unterstützt |
| R-07 | Fallende Blöcke und Flüssigkeiten im Void (OneBlock-Loot, Inselränder) | hoch | mittel | Validator/Allowlist laut Spec 07; Sand & Co. mit Unterbau oder Ersatzblock | GameTest Phase 2/3 | – |
| R-08 | Biomgrenzen unscharf (±1 Biomzelle) an Inselkanten | hoch | niedrig | Biom-Editor arbeitet auf 4×4×4-Zellen, Vorschau zeigt die tatsächlichen Zellen; Doku | Phase 4/7 | Spieler sehen an Kanten gemischte Spawns |
| R-09 | Mod-Deinstallation: Dimension steht in `level.dat` | niedrig | mittel | Deinstallations-Anleitung; Test vor Release | Phase 8 | – |
| R-10 | Client-Kompatibilität der Void-Dimension und Datapack-Inhalte mit unverändertem Cobblemon-Client | niedrig | kritisch | Nur Vanilla-Registry-Typen und Cobblemon-eigene Datenformate | **Owner-Test** am Phase-0B-Gate | – |
| R-11 | Paket-Hologramm erzeugt Duplikate oder ist unsichtbar | mittel | niedrig | Actionbar als Standard (ADR 006) | Owner-Test | – |
| R-12 | Unbekannter Nicht-Daemon-Thread hält die JVM nach einem Shutdown-Fehler am Leben | mittel | mittel | Eigene Executoren immer als Daemon und mit geordnetem Shutdown; Admin-Doku zum Hänger | Shutdown-Test Phase 1 | Fremder Thread bleibt |
| R-13 | Kotlin-Interop-Fehler (Defaults, Overloads) im Java-Code | mittel | niedrig | Ein Adapter-Paket mit Compile-Tests | Build | – |

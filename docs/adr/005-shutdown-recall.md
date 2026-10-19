# ADR 005: Ausgesandte Spieler-Pokémon vor dem Schließen der Welten zurückrufen

- **Status:** Vorschlag (Gegenmaßnahme verifiziert, `docs/POC_RESULTS.md` §6)
- **Datum:** 2026-10-19

## Kontext

Mit Cobblemon 1.8.2 trat in 4 von 4 Läufen beim `/stop` eine `ConcurrentModificationException` im `PersistentEntitySectionManager` auf, wenn ein Spieler nach einem Kampf noch ein Pokémon draußen hatte. Die Entities der betroffenen Chunks wurden nicht gespeichert, und die JVM beendete sich nicht.

## Entscheidung

Im eigenen `SERVER_STOPPING`-Handler ruft der Mod in **allen** geladenen Welten jede `PokemonEntity` zurück, die einem Spieler gehört (`!pokemon.isWild() && ownerUUID != null`), über `Pokemon.recall()`. Das passiert, bevor Minecraft die Welten schließt. Danach folgt unsere eigene Quiesce-/Flush-Reihenfolge (Spec 13, Boot/Shutdown).

## Begründung

Mit dieser Maßnahme liefen 2 von 2 Wiederholungen sauber durch. `recall()` ist öffentliche Cobblemon-API und feuert die normalen Recall-Events. Spielerdaten gehen nicht verloren, weil das Pokémon in Party bzw. PC bleibt.

## Abgrenzung

- Das Problem liegt wahrscheinlich in Cobblemon, nicht in unserem Mod. Gefunden wurde es mit Carpet-Fake-Spielern. Der Owner-Test mit echtem Client steht noch aus.
- Unabhängig vom Ergebnis bleibt die Maßnahme sinnvoll: Sie ist billig, idempotent und schützt vor Datenverlust.
- Ein Bericht an Cobblemon ist sinnvoll, sobald es mit echtem Client reproduziert ist.

## Folgen

- Der Shutdown-Integrationstest in Phase 1 enthält genau dieses Szenario.
- Die Reihenfolge der `SERVER_STOPPING`-Handler zwischen Cobblemon und unserem Mod ist nicht garantiert. Der Rückruf muss deshalb unabhängig davon funktionieren, ob Cobblemon schon Logouts gepostet hat. Im Spike lief es so.

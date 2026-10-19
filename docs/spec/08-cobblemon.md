# Cobblemon-Spawning und Pokémon-Sicherheit

> **Spec v4.1** · Teil von `docs/spec/` (Übersicht: `00-INDEX.md`) · Quelle: Masterprompt v4 §13  
> **Lesen in Phase:** 0B, 4, 6  
> Abschnitte mit **[v4.1]** sind Ergänzungen gegenüber v4. Alles andere ist der v4-Text unverändert (nur Dokumentnamen an den zusammengelegten Dokumentensatz angepasst).

## 13. Cobblemon-Spawning und Pokémon-Sicherheit

### **Explizites Phase-0-Go/No-Go-Gate**

Verifiziere auf **Cobblemon 1.8.2 / Minecraft 1.21.1** in einem isolierten Dedicated-Server-Spike:

1. Ob Cobblemon natürliche Spawns in `oneblock:void` überhaupt berücksichtigt.
2. Wie Dimensionsfilter, Biome, Licht, Tageszeit, Oberflächenblöcke, Flüssigkeiten, Höhen, freie Flächen und andere Kontextbedingungen zusammenwirken.
3. Ob eine manuell auf einer Plattform platzierte `plains`-, `desert`- bzw. `ocean`-Biome-Region die erwarteten Spawns erlaubt; dokumentiere Mindestbedingungen und negative Beispiele.
4. Wie Spawnattempts serverseitig einem Insel-Slot zugeordnet und **lokal** limitiert/gefiltert werden können, ohne die globalen Spawn-Pools anderer Dimensionen zu verändern.
5. Wie wilde Pokémon von Party-/Player-Pokémon und Event-Pokémon unterscheidbar bleiben; wie Capture, Despawn, Battle, Riding und Chunk-Unload Events nachweislich funktionieren.
6. Ob serverseitige NPC-Trainer und Battlestarts mit den erwarteten APIs tatsächlich möglich sind. Falls nicht: Feature als blockiert markieren, Alternativen vorschlagen und **vor** Event-Implementation entscheiden lassen.

**Kein Go für die komplexe Gameplay-Implementierung, solange die tragenden Spawns nicht nachweislich funktionieren.**

### Runtime-Regeln

- Spawns im Hub default aus, in unbelegten Zellen aus.
- Wild-Pokémon gehören einem definierten Inselkontext, ohne Besitzer-Pokémon zu entfernen. Limits default **40 Wild-Pokémon pro aktiver Insel** als Obergrenze, ergänzt um konfigurierbare globale, spielerbezogene und Chunk-/Aktivitäts-Budgets. Nicht 40 dauerhaft simulierte Pokémon pro Insel erzwingen.
- Inaktive/ungeladene Inseln erzeugen keine fortlaufenden Spawnkosten.
- Spawn-Intensität konfigurierbar, aber keine ungedeckelten Multiplikatoren; bei hoher Last gerechte Verteilung.
- Catch-Permissions: Owner / Owner+Member / alle, mit korrekter Battle-/Capture-Event-Absicherung.
- Insel-Buffs (Shiny, IV, Spawnrate) laufen nach Echtzeit-Deadline ab, auch über Restart. Buffs gehören zum Island bzw. explizit zum Player; stacking- und cap-Regeln dokumentieren, seltene Chancen nicht beliebig multiplizierbar machen.
- Wild-Pokémon über Grenze: zurücksetzen oder sauber despawnen, soweit zulässig. **Nie Spieler-Pokémon in Party/PC, aktivem Battle oder aktivem Mount blind löschen.**
- Event-NPCs/-Pokémon besitzen persistent zuordenbare Event-IDs; Cleanup nur nach sicheren Zustandsprüfungen.
- Standard-Cobblemon-Daten und Fremdmods im übrigen Server dürfen nicht global überschrieben werden.


### Cobblemon Adapter Contract — nicht in Vanilla-Logik einmischen

- Trenne `CobblemonAdapter` (Version-/API-spezifisch) strikt von `SpawnPolicy`, `WildPokemonTracker`, `EncounterService`, `BattleGuard`, `CapturePolicy` und `PokemonCleanup`. Der Core hängt an kontrollierten eigenen Schnittstellen, nicht überall direkt an Cobblemon-Internals.
- Eine **Spawn-Kompatibilitätsmatrix** enthält je Testart: erwartete Pokémon-Gruppe, Biome, Dimensions-Effekte, benötigten Block/Fluid, Licht/Zeit/Wetter, Spawn-Kontext, Minimum frei bebaubarer Fläche, beobachtete Spawns und Negativkontrolle. Niemals „Biome = garantierter Spawn“ suggerieren.
- Unterscheide `NATURAL_WILD`, `EVENT_WILD`, `TRAINER_OWNED`, `PLAYER_OWNED`, `UNKNOWN`. `UNKNOWN` wird **niemals automatisch gelöscht**; Unknown-Rate als Diagnostik überwachen.
- Fang-, Kampf-, Spawn- und Despawn-Hooks müssen bei **Eventstart, Battle-Abbruch, Disconnect, Chunk-Unload, Spawn-Policy-Wechsel, Dimensionstransfer, Server-Restart** korrekt bleiben. Spawn-Grenze und Capture-Grenze sind getrennte Autorisierungspunkte.
- Modifiziertes `shinyRate` darf keine globale Cobblemon-Config überschreiben. Bewirke Insel-Buffs lokal nur über nachgewiesen sichere Hooks; wenn diese fehlen, Feature als `BLOCKED` kennzeichnen und nicht auf Mixins auf gut Glück wechseln.
- Teste die Interaktion mit bekannten Klassen optionaler Cobblemon-Addons nur in einer **gesonderten Kompatibilitätsmatrix**, ohne impliziten Support für alle fremden Modpacks zu behaupten.
- Definition für legendäre/Alpha-/Trainer-Ereignisse als Fallback: bei nicht kompatibler 1.8.2-API **deaktiviert mit klarer Config-/Admin-Meldung**, nicht durch heimliche Vanilla-Mob-Ersatzkämpfe.

### [v4.1] Plan B, falls natürliche Spawns in der Void-Dimension das Gate nicht bestehen

Das Go/No-Go-Gate oben darf das Projekt nicht ersatzlos stoppen. Ist das Ergebnis negativ, prüfe in **dieser Reihenfolge** und lege dem Owner das Ergebnis zur Entscheidung vor (Eskalationsregel aus `01-produkt-und-scope.md`):

1. **Daten-Lösung (günstigste):** Blockieren nur Dimensions- oder Kontextbedingungen (z. B. Dimensionsfilter in Spawn-Bedingungen oder Spawn-Regeln), werden sie für `oneblock:void` gezielt per mitgeliefertem Datapack bzw. lokaler Spawn-Regel angepasst – **ohne** globale Cobblemon-Daten anderer Dimensionen zu verändern.
2. **Eigener Insel-Spawner:** Ein `IslandSpawner` im `CobblemonAdapter` nutzt Cobblemons **eigene** Spawn-Pool- und Detail-Auswahl (nur nachgewiesene 1.8.2-API) für das Biom und den Kontext der Insel, wählt aber die Positionen selbst (gültige Blöcke nahe aktiver Spieler auf der eigenen Insel) und hält alle Budgets ein. Herkunft im Tracker als eigene Quelle markieren, damit Diagnose und Cleanup sie unterscheiden.
3. **Eskalation:** Gehen weder 1 noch 2 ohne Mixins tief in Cobblemon-Internals, Feature als `BLOCKED` markieren und 2–3 Alternativen mit Aufwand und Risiko vorlegen.

Biomabhängiges Spawning bleibt v1-Pflicht. Welcher Weg es trägt, entscheidet der Owner am Phase-0B-Gate.

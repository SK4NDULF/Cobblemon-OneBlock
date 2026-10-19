# POC-Ergebnisse (Phase 0B)

> Stand: 2026-10-19 · Spike-Code: Branch `spike/0b-feasibility` (Commit `f6383a6`), **kein** Produktionscode.
> Alle Aussagen beziehen sich auf genau diese Versionen: Minecraft 1.21.1, Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1, Cobblemon 1.8.2+1.21.1 (Quelltag `1.8.2`, Commit `643bf906`), Java 21.0.12, Linux x64.

## Testumgebung

- Headless Dedicated Server im Cloud-Container, gestartet über `fabric-server-launch.jar` (Fabric Installer 1.1.2).
- Mods: Fabric API, Cobblemon 1.8.2 (bringt Fabric Language Kotlin 1.13.6 als Jar-in-Jar mit), Spike-Mod.
- **Nur für Tests:** Carpet 1.4.147 für Fake-Spieler (`/player <name> spawn …`). Carpet-Bots sind echte `ServerPlayer`-Instanzen in der Spielerliste; Cobblemons Spawner hängt per Mixin an `ServerPlayer.tick` und läuft für sie normal.
- **Nicht getestet:** echter Client (Join, Darstellung). Diese Punkte stehen unten als `OWNER_TEST`.

Status-Legende: `VERIFIED` = im Dedicated Server beobachtet · `STATIC` = nur im Quellcode geprüft · `OWNER_TEST` = braucht einen echten Client · `FAILED` = Problem gefunden

## Ergebnisse

### 1. Void-Dimension per mitgeliefertem Datapack — `VERIFIED`

| Prüfung | Ergebnis |
|---|---|
| `data/cobblemon_oneblock/dimension/void.json` (`minecraft:flat`, `layers: []`, `features: false`, `lakes: false`, Biom `minecraft:plains`) + eigener `dimension_type` | Dimension wird ohne manuelles Datapack-Aktivieren geladen (Mod-Ressourcen sind ein Built-in-Datapack). |
| Bestehende Welt (vorher ohne Mod gestartet) | Dimension kommt beim nächsten Start dazu; Overworld/Nether/End unverändert geladen. |
| Frische Welt | Dimension wird mit angelegt; `cobblemon_oneblock:void` steht danach in `level.dat`. |
| Dimension-Type zur Laufzeit | `minY=-64 height=384 skylight=true ceiling=false natural=true bedWorks=true anchorWorks=false fixedTime=false effects=minecraft:overworld`; Generator `FlatLevelSource`, Biomquelle `FixedBiomeSource`. |
| Speicherort | `<world>/dimensions/cobblemon_oneblock/void/{region,entities,poi,data}` — getrennt von der Overworld. |
| Neustart | Plattformen, NPC, forceload-Tickets bleiben erhalten. |

Folge: Keine eigene Generator-Klasse und kein Mixin nötig (ADR 002). Achtung: Weil die Dimension in `level.dat` landet, ist ein späteres Entfernen des Mods ein eigener, noch ungetesteter Fall (Risiko R-09).

### 2. Natürliche Cobblemon-Spawns in der Void-Dimension — `VERIFIED`

Statisch (`STATIC`): Die 875 Dateien in `data/cobblemon/spawn_pool_world/` enthalten **keine** `dimensions`-Bedingung. Das Preset `natural` verlangt nur `neededBaseBlocks: #cobblemon:natural` (enthält u. a. Gras, Erde, Sand, Stein, Schnee, Erze). Die einzige Dimensionssperre ist `worldSpawningBlocklist` in der Cobblemon-Config (Standard: leer).

Laufzeit: Pro Fläche (65 × 65 Blöcke auf Y=64) stand ein Carpet-Bot in der Mitte. Gemessen über die Spike-Subscription auf `CobblemonEvents.ENTITY_SPAWN`.

**Messfenster 18:20:09–18:24:15 (≈ 4 min, alle Bots aktiv):**

| Fläche (X-Zentrum) | Block | Biom | Spawns | Positionstyp | Beispiele |
|---|---|---|---|---|---|
| Hub (0) – Spawn-Filter aktiv | Gras | plains | **0** | – | – |
| 3072 | Gras | plains | 30 | grounded | Rattata, Miltank, Electrike, Sentret |
| 6144 | Sand auf Sandstein | desert | 54 | grounded | Cacnea, Silicobra, Bramblin, Maractus, Rellor |
| 9216 | Wasserbecken (Sand-Boden) | ocean | 38 | submerged 15 · seafloor 22 · surface 1 | Clauncher, Shellder, Wishiwashi, Carvanha, Krabby |
| 12288 | Stein | plains | 29 | grounded | Nymble, Ledyba, Combee |
| 15360 – Negativkontrolle | Eichenbretter | plains | **0** | – | – |

Bis zum Stopp um 18:24:44 kamen im selben Fenster 158 Spawns zusammen. Über den ganzen ersten Lauf (ab 18:08:45) waren es 380 Spawns, alle in `cobblemon_oneblock:void`, 0 in anderen Dimensionen (dort war kein Spieler).

Beobachtungen:
- **Biome steuern die Spawns real.** Die Artenlisten pro Biom überschneiden sich nicht (Wüste vs. Ozean vs. Plains).
- **Stein zählt als natürlicher Boden** (`#minecraft:base_stone_overworld` im Tag). Eine reine Steininsel spawnt also Pokémon. Bretter nicht.
- **Kantenunschärfe:** Auf der Wüstenfläche spawnten 17 Pokémon mit Biom `plains`, alle an der West- bzw. Nordkante (x = 6112–6113, z = −31/−32). `Level.getBiome(BlockPos)` nutzt den unscharfen Biom-Zoom und streut an Zellgrenzen. Biomgrenzen sind serverseitig also nicht blockgenau (relevant für Spec 09).
- **Testaufbau-Fehler (nicht Cobblemon):** Die erste Sandfläche fiel ohne Unterbau ins Void, der Bot starb. Ein Bot ertrank, weil seine Sandinsel ins Wasser sank. Fallende Blöcke und Flüssigkeiten im Void sind ein echtes Design-Thema für den OneBlock (Spec 07).

### 3. Biom per Befehl auf Plattformen setzen — `VERIFIED`

- `/fillbiome` wirkt in der Void-Dimension; `execute if biome …` bestätigt das Server-Biom an den Plattformmitten.
- Vanilla begrenzt `/fillbiome` auf 32 768 Blöcke pro Aufruf. Darum wurde in vier Quadranten mit Y 56–79 gearbeitet. Gemeldet wurden 486 Biomeinträge je Quadrant (4×4×4-Zellen).
- Ob der Client die Biomfarbe ohne Relog aktualisiert: `OWNER_TEST`.

### 4. Lokale Spawn-Filterung pro Insel ohne globale Datenänderung — `VERIFIED`

- API: `com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory.INSTANCE.getInfluenceBuilders()` (Kotlin `MutableList<(ServerPlayer) -> SpawningInfluence?>`). Jeder neue `PlayerSpawner` bekommt die Influences.
- Hook: `SpawningInfluence.isAllowedPosition(ServerLevel, BlockPos, SpawnablePositionCalculator<*, *>): Boolean`.
- Spike `HubFilterInfluence` verbietet alle Positionen mit |x| ≤ 48 und |z| ≤ 48 in der Void-Dimension. Ergebnis im Messfenster ab 18:20:08: **8008 abgelehnte Positionen, 0 Spawns im Hub**, während daneben auf den Inseln normal gespawnt wurde. Im ganzen ersten Lauf (380 Spawns) gab es ebenfalls 0 Spawns im Hub.
- Weitere verfügbare Hooks (`STATIC`): `affectSpawnable(detail, position)`, `affectWeight(…)`, `affectBucketWeights(…)`, `affectSpawn(action, entity)`, `injectSpawns(bucket, position)`. Zusätzlich lässt sich `CobblemonEvents.ENTITY_SPAWN` (`CancelableObservable<SpawnEvent<*>>`) abbrechen.
- **Kotlin-Interop:** Cobblemon ist ohne `-Xjvm-default` kompiliert. Java-Klassen müssen deshalb **alle 10 Methoden** von `SpawningInfluence` implementieren. Die Standardlogik liegt in `SpawningInfluence.DefaultImpls` (z. B. `normalizeBucketWeights`).
- **Kotlin-Interop:** Bei `Observable.subscribe` sind Ausdrucks-Lambdas mehrdeutig (`(T) -> Unit` vs. `Consumer<T>`). Lösung: `subscribe(Consumer)` ohne Priority-Argument oder Block-Lambda.

### 5. Wildkampf, NPC-Trainer und Fang serverseitig — `VERIFIED`

| API | Aufruf im Spike | Ergebnis |
|---|---|---|
| `BattleBuilder.pve(ServerPlayer, PokemonEntity)` (`@JvmOverloads`) | Bot mit Pikachu Lv. 50 gegen wildes Pokémon | `SuccessfulBattleStart`; `BATTLE_STARTED_PRE` und `_POST` gefeuert |
| `BattleBuilder.pvn(ServerPlayer, NPCEntity)` | NPC-Klasse `oneblock_spike:trainer` aus **eigenem Datapack** (`data/<ns>/npcs/*.json`, `battleConfiguration.canChallenge`, Party-Pool), gespawnt mit `/spawnnpcat` | `SuccessfulBattleStart` mit `NPCBattleActor` |
| `PokemonBattle.stop()` | Abbruch eines laufenden Kampfes | sendet `>forcetie` an Showdown, dann `end()` |
| Poké-Ball-Wurf | Carpet-Bot zielt (`ServerPlayer.lookAt`) und wirft einen Meisterball (`/player … use once`) | `THROWN_POKEBALL_HIT` (Ziel wild), danach `POKEMON_CAPTURED`; `Pokemon.isPlayerOwned() == true`; Advancement „A New Friend!“ |

- Unterscheidung wild/besessen (`STATIC` + im Spike genutzt): `Pokemon.isWild()` (keine Store-Koordinaten), `isPlayerOwned()` (Party oder PC), `isNPCOwned()`.
- Ein Kampf bis zum Sieg oder zur Flucht braucht Eingaben vom Client: `OWNER_TEST`.
- Riding, Despawn bei Chunk-Unload und Event-Pokémon: noch nicht getestet. Das ist Phase 4/6, das API ist vorhanden (`RIDE_EVENT_PRE`, `POKEMON_ENTITY_SAVE_TO_WORLD`, `PokemonEntity.shouldBeSaved`).

### 6. Shutdown nach einem Kampf — `FAILED` mit verifizierter Gegenmaßnahme

**Befund:** Hat ein Spieler nach einem Kampf noch ein ausgesandtes Pokémon in der Welt, wirft `/stop` beim Entladen der Entity-Sections eine `ConcurrentModificationException` (`PersistentEntitySectionManager`, `class_5579.method_31825:254`). Folgen:
1. Die Entities der betroffenen Chunks werden **nicht gespeichert**. Im ersten Lauf lagen die Entity-Dateien danach auf dem Stand des letzten Autosaves (18:22 statt 18:24).
2. Der Server-Thread endet, die JVM aber nicht: Ein fremder Nicht-Daemon-Thread (`pool-5-thread-1`, `ThreadPoolExecutor`, Ursprung unbekannt) hält sie am Leben. Nur `kill` beendet den Prozess.

| Lauf | Bedingung | Ergebnis |
|---|---|---|
| Run 1, Exp. B, B′, F0 | Kampf gestartet und per `end()` bzw. `stop()` beendet, Pokémon bleibt draußen, dann `/stop` | **4 × CME + Hänger** |
| Exp. A | keine Bots, keine Kämpfe | sauber |
| Exp. C | Bot online, Pokémon in der Party, kein Kampf | sauber |
| Exp. D | Kampf läuft noch beim `/stop` | sauber |
| Exp. G1, G2 | wie B, aber `SERVER_STOPPING` ruft vorher alle besessenen `PokemonEntity` per `Pokemon.recall()` zurück | **2 × sauber** |

- Fabric-Reihenfolge laut Cobblemon-Quelle (`CobblemonFabric.kt:167`): `SERVER_STOPPING` → Cobblemon postet für jeden Spieler `SERVER_PLAYER_LOGOUT` → danach werden die Welten geschlossen. Das gilt für echte und Fake-Spieler gleich. Ob es mit einem echten Client genauso auftritt: `OWNER_TEST`.
- Konsequenz: ADR 005 (Rückruf beim Stoppen) und Risiko R-01.

### 7. SQLite pro Welt als Jar-in-Jar — `VERIFIED` (Linux x64)

- `org.xerial:sqlite-jdbc:3.53.4.0` per Loom `include` als `META-INF/jars/…` im Mod-JAR; Loader verarbeitet es als eigene Mod (`.fabric/processedMods/org_xerial_sqlite-jdbc-…`).
- Datei: `<world>/data/oneblock_spike/spike.db`. `PRAGMA journal_mode=WAL` → `wal`, `synchronous=FULL`, `foreign_keys=ON`. SQLite 3.53.4, `nativeMode=true`.
- Threading: Zugriff auf einem eigenen Single-Thread-Executor (`onServerThread=false`), Rückmeldung per `server.execute(…)` (`onServerThread=true`).
- Persistenz über Neustart und harten Kill: `bootRows` stieg wie erwartet (1 → 2).
- Mitgelieferte Natives laut JAR: Linux x86_64/x86/aarch64/arm/armv6/armv7/ppc64/riscv64, Windows x86_64/x86/aarch64/armv7, macOS x86_64/aarch64. Getestet ist nur Linux x64; Windows x64 und Linux ARM64 sind offen (`DECISIONS` Nr. 11).

### 8. Grid-Mathematik — `VERIFIED` (Property-Tests)

`GridMath` + `GridMathProperties` (jqwik 1.9.3, JUnit 5.14.4, je 1000 Fälle pro Property): **10/10 grün**.

- `stride(2049, 1000) = 3072`; `stride` ist immer ein Vielfaches von 1024 und ≥ Größe + Puffer.
- Zellgrenzen fallen exakt auf Region-Grenzen (512): **keine zwei Zellen teilen sich eine Region-Datei** (auch negative Zellen).
- `cellOf` ist für alle Koordinaten in ±29 999 000 die Umkehrung des Zellbereichs.
- Die volle Insel plus halber Puffer liegt immer in der eigenen Zelle.
- Spirale: Round-Trip bis Index 4 000 000; die ersten 101 × 101 Zellen sind bijektiv; nur Index 0 ist der Hub; **kein Spieler-Slot berührt die Hub-Zelle**.
- Weltgrenze bei Standardwerten: letzte gültige Zelle je Achse ist ±9765 (9765 · 3072 + 1024 = 29 999 104 ≤ 29 999 984). Extremwerte werden ohne Überlauf abgelehnt.

### 9. Paketbasiertes Hologramm — serverseitig `VERIFIED`, Darstellung `OWNER_TEST`

- `Display.TextDisplay` wird nur als Objekt erzeugt, nie der Welt hinzugefügt. Text und Billboard werden über `load(CompoundTag)` gesetzt (`setText` ist privat), dann gehen `ClientboundAddEntityPacket` und `ClientboundSetEntityDataPacket` (2 Werte) an die Spieler der Dimension.
- Ergebnis: Pakete gesendet, keine Exception, **keine gespeicherte Entity** (`level.getEntity(id) == null`). Entfernen mit `ClientboundRemoveEntitiesPacket`.
- Nicht geprüft: Sichtbarkeit im Client, Verhalten bei Chunk-Reload und Dimensionswechsel (Paket muss neu gesendet werden).
- Nach `DECISIONS` Nr. 8 bleibt Actionbar der Standard (ADR 006).

### 10. Sonstige Befunde

- Cobblemon loggt bei jedem Start Warnungen zu fehlenden optionalen Mixin-Zielen (Adorn, Client-Sound) und `No data fixer registered for cobblemon:*`. Das kommt von Cobblemon selbst und tritt auch ohne unseren Mod auf.
- Beim ersten Join eines neuen Spielers loggt Cobblemon `.old File … is corrupt or missing. Data is lost` (keine Vorgängerdatei). Für Admin-Doku und `doctor`: harmlos, aber verwirrend.
- `/checkspawn` lässt sich nicht von der Konsole per `execute as` nutzen: Das `requires` prüft beim Parsen die Konsole. Für Diagnose brauchen wir eigene Befehle.
- Carpet + Cobblemon: `Method overwrite conflict for getClimateSettings` (Carpet-Mixin vs. Cobblemon-Accessor). Betrifft nur die Testumgebung (Tier C).
- `CobblemonEvents.SHINY_CHANCE_CALCULATION` existiert (`STATIC`). Möglicher Weg für lokale Shiny-Buffs ohne globale Config. Ungetestet, Phase 4.

## Owner-Tests mit echtem Client (offen)

Siehe Phasenbericht im Chat bzw. `docs/PROGRESS.md`.

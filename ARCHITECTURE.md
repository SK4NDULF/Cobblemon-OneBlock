# Architektur

> Stand: 2026-10-19, Ende Phase 0B. **Es gibt noch keinen Produktionscode.** Dieses Dokument beschreibt die geplante Architektur auf Basis der verifizierten Hooks (`docs/POC_RESULTS.md`). Sobald Code existiert, bildet es den implementierten Stand ab.

## Überblick

Ein serverseitiger Fabric-Mod (`cobblemon_oneblock`, Java 21) als schlanker modularer Monolith in **einem** installierbaren JAR. Clients brauchen nur den passenden Cobblemon-Client.

```text
            Minecraft 1.21.1 / Fabric API 0.116.17 / Cobblemon 1.8.2
                                   │
             ┌─────────────────────┴──────────────────────┐
             │ adapters                                    │
             │  fabric/      Lifecycle, Commands, Events   │
             │  minecraft/   Welt, Teleport, Schutz-Hooks  │
             │  cobblemon/   Spawn-Influence, Battle,      │
             │               Capture, Recall (Kotlin-      │
             │               Interop nur hier)             │
             │  sqlite/      DB-Thread, Migrationen        │
             │  ui/          Texte de/en, Actionbar        │
             └─────────────────────┬──────────────────────┘
                                   │ Ports (nur echte Test-Nähte)
             ┌─────────────────────┴──────────────────────┐
             │ application   Use Cases, OperationCoordinator│
             └─────────────────────┬──────────────────────┘
             ┌─────────────────────┴──────────────────────┐
             │ domain        Grid, Insel, Rollen, Loot,    │
             │               Progression (reines Java)     │
             └────────────────────────────────────────────┘
```

Ports gibt es nur, wo eine zweite Implementierung oder ein Test sie braucht: `IslandStore` (SQLite), `WorldPort`, `CobblemonPort`, `Clock`, `RandomSource` (Spec 13 [v4.1]).

## Verifizierte Integrationspunkte

| Zweck | Hook (Mojang-Namen) | Beleg |
|---|---|---|
| Void-Dimension | Datapack `data/cobblemon_oneblock/dimension/void.json` + `dimension_type/void.json` | `POC_RESULTS` §1, ADR 002 |
| Spawn-Filter pro Insel | `PlayerSpawnerFactory.INSTANCE.getInfluenceBuilders().add(player -> influence)`; `SpawningInfluence.isAllowedPosition(ServerLevel, BlockPos, SpawnablePositionCalculator)` | §4, ADR 003 |
| Spawn-Budget, Herkunft | `SpawningInfluence.affectSpawnable(…)`; `CobblemonEvents.ENTITY_SPAWN` (abbrechbar) | §2, §4 (statisch + Logging) |
| Wild/Besitz unterscheiden | `Pokemon.isWild()`, `isPlayerOwned()`, `isNPCOwned()` | §5 |
| Kampfstart | `BattleBuilder.INSTANCE.pve(player, pokemonEntity)`, `pvn(player, npcEntity)` | §5 |
| Kampfabbruch | `PokemonBattle.stop()` (nie `end()` direkt) | §5, §6 |
| Fang absichern | `CobblemonEvents.THROWN_POKEBALL_HIT` (abbrechbar), `POKEMON_CAPTURED` | §5 |
| Kampf absichern | `CobblemonEvents.BATTLE_STARTED_PRE` (abbrechbar) | §5 |
| Shutdown-Schutz | `ServerLifecycleEvents.SERVER_STOPPING` → `Pokemon.recall()` für besessene Entities | §6, ADR 005 |
| Persistenz | `sqlite-jdbc` 3.53.4.0 (Jar-in-Jar), WAL, ein DB-Thread | §7, ADR 004 |

Noch **nicht** verifiziert (folgt in der jeweiligen Phase): Block-Break-Hook für den OneBlock (Phase 2), Schutz-Hooks für Interaktion, Explosion, Kolben und Flüssigkeiten (Phase 2), Biom-Änderung per API inkl. Client-Sync (Phase 4), Riding-Grenzen (Phase 4), Respawn-Routing (Phase 2).

## Threads und Datenhoheit

| Thread | Darf | Darf nicht |
|---|---|---|
| Server-Thread | Welt, Blöcke, Entities, Teleports, alle Cobblemon-Aufrufe, Cache lesen | blockierend auf DB oder Dateien warten (`Future.get()`, `join()`) |
| DB-Thread (1, Daemon, begrenzte Queue) | SQL, Transaktionen, Migrationen, Journal | Minecraft- oder Cobblemon-Objekte anfassen; liefert nur unveränderliche Records |
| Worker (optional) | reine Berechnung (Loot-Simulation, Validierung) | Weltzugriff |

Ablauf einer kritischen Operation (Spec 11 §29): Anfrage auf dem Server-Thread prüfen → Intent auf dem DB-Thread speichern → Callback per `server.execute(…)` → Revision erneut prüfen → Weltänderung → Ergebnis-Stage speichern → erst dann Erfolg an den Spieler. Davor zeigt die UI `PENDING`.

**Source of Truth:** SQLite für Inseln, Slots, Mitgliedschaften, Ledger. Minecraft-Saves für Blöcke, Entities, Inventare, Cobblemon-Party/PC. Beide sind nicht gemeinsam atomar (R-02); die Reconciliation beim Start gleicht offene Vorgänge ab.

## Grid

Reine Domain-Logik, im Spike per Property-Test belegt (`POC_RESULTS` §8):

- `stride = ceil((maxSize + puffer) / 1024) · 1024`, Standard 3072.
- Zelle `g` umfasst `[g·stride − stride/2, g·stride + stride/2 − 1]` und besteht immer aus ganzen Region-Dateien.
- Slot-Vergabe über eine quadratische Spirale; Index 0 ist der Hub.
- Weltgrenze bei Standardwerten: Zellen −9765 … 9765 je Achse.

## Boot und Shutdown

**Boot:** Abhängigkeiten prüfen → Config validieren → SQLite öffnen, Backup vor Migration, migrieren → offene Vorgänge abgleichen → Caches füllen → Influence registrieren, Befehle freigeben → Zustand `READY`, sonst `SETUP_PENDING`, `READ_ONLY` oder `RECOVERY_MODE`.

Die Influence wird im Mod-Initializer registriert (vor dem ersten Join), sie fragt aber nur den Cache ab. Solange der Zustand nicht `READY` ist, lehnt sie in der Void-Dimension alle Positionen ab (fail-closed).

**Shutdown (`SERVER_STOPPING`):**
1. Neue Mutationen ablehnen.
2. Besessene `PokemonEntity` in allen Welten zurückrufen (ADR 005).
3. Laufende Jobs (Biom, Events) anhalten und Stand speichern.
4. DB-Queue leeren, Verbindung schließen.
5. Danach schließt Minecraft die Welten.

## Testbarkeit

- `domain` ohne Minecraft: JUnit + jqwik.
- `adapters.sqlite`: Tests gegen eine temporäre Datei-DB.
- Integration: Fabric GameTests, wo möglich. Dazu ein Dedicated-Server-Lauf mit Carpet-Bots als Testwerkzeug (nur Testumgebung), wie im Spike.
- Client-Verhalten: Owner-Testlisten pro Phase.

## Geplante Paketstruktur (Phase 1)

```text
io.github.sk4ndulf.cobblemon.oneblock
  bootstrap/        Mod-Initializer, Zustandsautomat READY/…
  config/           Laden, Validieren, atomar speichern
  domain/           grid/, island/, membership/ (wächst pro Phase)
  application/      operation/ (OperationCoordinator, Journal)
  adapters/
    fabric/         Lifecycle, Commands
    cobblemon/      CobblemonAdapter (Influence, Recall)
    sqlite/         Database, Migrations, DbExecutor
  ops/              Logging, Doctor (später)
```

Klassen entstehen erst, wenn die Phase sie braucht.

## Phasenplan nach 0B (Detail)

| Phase | Kern | Erste Abnahmetests |
|---|---|---|
| 1 Foundation | Gradle-Projekt, gepinnte Versionen, Config mit Schema und atomarem Speichern, SQLite-Adapter mit Migrationen und Backup, Operation-Journal-Grundgerüst, Logging, Startzustände, Shutdown-Reihenfolge inkl. ADR 005, Grid-Domain mit Property-Tests, CI | Clean-Boot mit Cobblemon, Client-Join (Owner), DB-Migration von leer, Kill während Schreibvorgang → sauberer Wiederanlauf, Shutdown nach Kampf ohne CME |
| 2 Safe MVP | Void-Dimension, Hub, Wizard, Slots, Create/Home, Grundschutz, OneBlock mit Block-Loot | siehe Spec 02 §22 |
| 3–8 | wie Spec 02 §22 | – |

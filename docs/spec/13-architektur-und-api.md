# Referenzarchitektur und öffentliche API

> **Spec v4.1** · Teil von `docs/spec/` (Übersicht: `00-INDEX.md`) · Quelle: Masterprompt v4 §19, §38  
> **Lesen in Phase:** 0B, 1, 7  
> Abschnitte mit **[v4.1]** sind Ergänzungen gegenüber v4. Alles andere ist der v4-Text unverändert (nur Dokumentnamen an den zusammengelegten Dokumentensatz angepasst).

## 19. Öffentliche API und Projektstruktur

### Struktur

- `oneblock-api` — kleine, unabhängige API-Verträge, DTOs/Records und dokumentierte Events; keine unnötigen Implementierungsdetails.
- `oneblock-core` — Gameplay, Persistence, Fabric-/Cobblemon-Integration und Administratorwerkzeuge.
- `example-addon` — separates verifizierbares Addon, kompiliert gegen veröffentlichte API und demonstriert genau einen Loot- oder Event-Extension-Point.

Ein Gradle-Multiprojekt ist der Ausgangspunkt, aber Phase 0 muss prüfen, ob Packaging/Jar-in-Jar auf einem echten Server tatsächlich korrekt funktioniert. Keine leere Modularchitektur ohne echten Nutzen.

### API-Vertrag

- `OneBlockAPI.get()` als Einstieg; minimal nötige facades für Islands, Progression, Events, Biomes, Buffs und Membership.
- Ereignisse zunächst nur für bestätigte Anwendungsfälle: `IslandCreated`, `IslandRestored`, `IslandDeleted`, `OneBlockBreak` (nur an klar definiertem sicheren Hook cancellable), `BorderLevelUp`, `MembershipChanged`, `TriggerEventStart/End/Fail`, `AuditLog`.
- `before`/`after`-Events mit genauen Mutability-/Thread-/Failure-Regeln; externe Addons dürfen nicht unkontrolliert DB-/Weltmutation ausführen oder Sicherheitschecks umgehen.
- `registerEventType`, registrierte Loot- und Reward-Provider mit IDs, Versionsfeldern, Lifecycle und Limits.
- Bis Version 1.0 `@ApiStatus.Experimental` und dokumentierte Breaking-Change-Policy.
- Eine JavaDoc-/Kompatibilitätsprüfung und `example-addon` gehören zur Definition of Done, nicht nur eine Markdown-Aufzählung.


### API: sichere Stabilität und Sandbox-Grenzen

- API-Version und Content-Schema-Version sind getrennt. Jede öffentliche API zeigt **Thread Contract**, Mutability, Security Context, Lifecycle, Failure/Timeout und Kompatibilitätsbereich an.
- Addon-Callbacks dürfen keine synchronen DB-Reads, unbegrenzten Berechnungen oder globale World-Mutationen unter Core-Lock durchführen. Langsame/falsche Addons werden überwacht, sauber isoliert und bei Bedarf deaktiviert, **soweit die JVM-/Event-Mechanik dies zulässt**; kein Versprechen echter Prozessisolation.
- Pre-Events nur an Zuständen, deren Abbruch **sicher** möglich ist; Post-Events lesen bestätigten Zustand. Keine `cancellable`-API, die bereits persistierte Rewards oder teilweise gespeicherte Inseln rückwirkend „annulliert“.
- `example-addon` muss **ohne Quellcode-Abhängigkeit von `oneblock-core`** kompilieren und zur Laufzeit auf dem Dedicated Server ein eigenes Event oder eine Loot-Erweiterung demonstrieren.
- Öffentliche Manager-Facades geben keine mutierbaren internen Cache-/DB-Objekte zurück; APIs mit `CompletableFuture`/Callbacks definieren, welche Threads die Ergebnisse ausliefern.

### [v4.1] Architektur pragmatisch halten

Abschnitt 38 beschreibt **Verantwortlichkeiten und Abhängigkeitsrichtung**, keine Pflicht-Abstraktion pro Klasse. Damit gilt auch die Regel aus `01-produkt-und-scope.md` („eine einfachere verifizierbare Implementierung schlägt eine elegante, aber ungetestete Architektur“):

- Die Schichten sind **Paketgrenzen**. Ein Interface (Port) entsteht nur dort, wo es eine zweite Implementierung oder eine echte Test-Naht gibt: Storage, Welt, Cobblemon, Zeit (`Clock`), Zufall (`RandomSource`).
- Das durable Vorgangs-/Saga-Protokoll (`OperationCoordinator`) gilt für die kritischen Insel-Operationen `CREATE`, `RESET`, `RESTORE`, `DELETE`, `TRANSFER` und Reward-Claims. Alles andere sind schlichte Services.
- Keine Klassen, Facades oder Module „auf Vorrat“. Was in einer Phase nicht gebraucht wird, wird in dieser Phase nicht angelegt.

## 38. Engine/Core: Referenzarchitektur und Laufzeitdatenfluss

**Ziel:** Der Mod soll als **schlanker Modular Monolith** in **einem installierbaren Mod-JAR** laufen. Saubere Paket- und Compile-Grenzen statt künstlicher Microservices oder unkontrollierter `Manager`-Singletons. Die Beispiele sind **Architekturvorgaben, keine Behauptung existierender Klassen/APIs**. Erst Phase 0B verifiziert die tatsächlichen Framework-Integrationen.

### Verbindliche Abhängigkeitsrichtung

```text
Fabric/Minecraft/Cobblemon external runtime
                |
       [Adapters / Entrypoints]
                |
         [Application Services]
                |
     [Domain Rules + Immutable Types]
                ^
                |
       [Ports / Interfaces]
                ^
                |
   [SQLite, World, Cobblemon, UI Adapters]
```

- `domain`: rein Java-orientierte Geschäftsregeln/immutable Werte, keine direkte Minecraft-Weltmutation oder SQL. Deterministisch und zu großen Teilen mit JUnit testbar.
- `application`: Use Cases für `CreateIsland`, `BreakOneBlock`, `ProgressIsland`, `StartEvent`, `TransferOwner`, `ChangeBiome`, `ResetIsland`, `RestoreIsland`, `DeleteIsland`; koordiniert Ports und durable Workflows.
- `adapters.minecraft`: Commands, Lifecycle, World-/Entity-/Chunkzugriff, Protection-/Action-Hooks, Paket-/HUD-Anzeige; alles versionsbezogen und ohne unkontrollierten Zugriff auf Domain-Caches.
- `adapters.cobblemon`: an die **nachgewiesene** 1.8.2-API gebundene Event/Spawn/Battle/Capture/Entity-Aktionen. Kein Cobblemon-Spezialfall in `IslandRepository` oder Block-/Rechte-Logik.
- `adapters.storage.sqlite`: Transaktionen, Migrationsskripte, DB-Journal, Lesemodelle, zuverlässige Claim-/Event-Ledger; ihre asynchronen Ergebnisse enthalten keine Minecraft-Liveobjekte.
- `adapters.ui`: Commands, Statusmeldungen, Menüs, Übersetzungen, Cooldown-Feedback. UI führt nie direkt Roh-SQL oder unautorisierte Weltmutationen aus.
- `ops`: Health, Metrics, Profile, Doctor, Backup, Audit, Recovery und sichere Wartungsbefehle.
- `api`: kleine, testbar versionierte externe Facades/Events, im Modul `oneblock-api`, mit eigenen DTOs.

### Mögliche logische Paketstruktur (keine dogmatische Klassenexplosion)

```text
oneblock-api/
  api/, event/, model/, extension/
oneblock-core/
  bootstrap/, config/, localization/
  domain/
    island/, slot/, membership/, progression/
    oneblock/, reward/, event/, biome/, protection/
  application/
    commands/, services/, lifecycle/, scheduler/
    ports/, recovery/
  adapters/
    fabric/, minecraft/, cobblemon/, sqlite/, ui/
  ops/
    logging/, audit/, metrics/, doctor/, backup/
  content/
    codec/, schema/, validation/, defaults/
example-addon/
  src/main/java/, src/main/resources/
```

**Codequalität:** Keine zyklischen Modulabhängigkeiten; keine `ServiceLocator`-/Global-State-Abkürzungen, die Tests und Reload unmöglich machen. Dependency Injection darf simpel und explizit bleiben (kein schweres DI-Framework notwendig).

### Port-Beispiele und Thread-Contract

- `IslandRepository`: immutable Snapshots/Transaktionen/Revisionen und durables Journal; write asynchronously; no world access.
- `WorldPort`: validierte Dimension/Block/Chunk-/Save-Operationen; auf Main-Thread zu dispatchen; keine unsichere asynchrone Weltmutation.
- `PermissionPolicy`: deterministic `ActionContext → decision + reason`; keine DB-Query auf jedem Interaktions-Event.
- `OneBlockResolver`: `phase + progression + validated content + RNG → typed outcome`; **pure** except a documented random source.
- `CobblemonPort`: only verified spawn, lifecycle, capture, battle and mount hooks; explicit `SUPPORTED/UNSUPPORTED` capabilities.
- `RewardService`: issue/reserve/claim/reconcile mit idempotentem Claim-/Ledger-Vertrag, ohne generelles Exactly-once-Versprechen für fremde Inventare.
- `OperationCoordinator`: dedupe `requestId`, single-writer per island, durable state stages, resumption/reconciliation.
- `BiomeEditScheduler`: bounded, revision-checked edit jobs, tick budget, Undo und client refresh.
- `Telemetry`: nur minimale Instrumentierung im Hot Path; optionale teure Snapshots über Admin-Profiling.

### Datenfluss eines legitimen OneBlock-Abbaus

```text
Minecraft break input
  -> identify island + verify owner/member/public + current island revision
  -> verify anchor + gamemode/tool/automation rules
  -> prevent duplicate break request in current authoritative window
  -> resolve typed loot using immutable phase/content snapshot
  -> commit required critical counters/rare reward entitlement as configured
  -> perform world replacement / ordinary drop or claim workflow
  -> emit post-commit events + UI changes + metrics
  -> on partial failure: recoverable state, never blind duplicate issuance
```

**Achtung:** Der genaue Hook-/Drop-Zeitpunkt hängt von Vanilla- und Fabric-1.21.1-Abläufen ab und muss im Spike/GameTest bewiesen werden. Dieses Diagramm gibt die Verantwortlichkeiten vor, **nicht** ungeprüfte Event-Reihenfolgen.

### Datenfluss einer Insel- oder Owner-Operation

```text
Command / API / UI -> Authenticate -> Validate + expectedRevision
  -> OperationCoordinator reserves durable requestId + intent
  -> quiesce relevant world/event/edit activity
  -> validated world changes + persistent stages
  -> commit membership/slot lifecycle state in SQL
  -> re-read authoritative revision -> update caches
  -> safe routing + notification + audited result
  -> replay/reconcile any incomplete stage after crash
```

### Boot-/Shutdown-Reihenfolge

1. Validate exact dependencies + compile/runtime compatibility.
2. Load and validate config, dimension definitions and built-in content; reject dangerous startup states.
3. Resolve World-ID; initialize SQLite, migrations (with backup), operation journal; check integrity and `PENDING_WIPE` intents **before touching unsafe world files**.
4. Determine feasible safe cleanup point. If it cannot be reached, enter **maintenance/Recovery Mode** or route to documented offline cleanup, **never** delete loaded region files.
5. Initialize caches and services; reconcile pending operations; register commands/handlers only when their prerequisites are satisfied.
6. Transition to `READY` or explicit `READ_ONLY/SETUP_PENDING/RECOVERY_MODE`. Teleport/spawn only into verified safe destinations.
7. At shutdown, reject new mutations, quiesce jobs, flush and verify authoritative state at real save boundaries, release SQLite cleanly, write final diagnostics.

**Gate:** `ARCHITECTURE.md` must illustrate these runtime boundaries, exact version-specific hooks, actual thread ownership and how a developer can test one subsystem without booting every other subsystem. If the concrete code diverges, update ADR and tests—not silently the architecture.

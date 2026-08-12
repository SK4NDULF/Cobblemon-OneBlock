# Cobblemon OneBlock — Core Mod Master Plan & Work List

> **Fabric 1.21.1 · Cobblemon 1.7.3 (harte Dependency) · Storage: SQLite (Default) / MySQL·MariaDB (optional)**
> Eigenständiges Projekt. Ziel: eine einzige CORE MOD, die (fast) alles steuert.
>
> **Dies ist die Source of Truth des Projekts.** Änderungen an Scope/Design werden hier eingepflegt.

---

## 0. Vision & Goal

Das ist **kein** kleines Zusatz-Mod, sondern eine **Core Mod**, die den kompletten Serverbetrieb übernimmt:

- **Drag-and-Drop-Ready**: Cobblemon + diese eine Mod installieren → sofort ein vollständig funktionierender Server. Keine zusätzlichen Mods für Islands, Protection, Progression, Events etc. nötig. Keine externe Datenbank nötig (SQLite eingebettet als Default).
- **Eigene Public API**: Andere Devs/Mods können sich einhängen (Event-Bus + Extension-Points), um eigene Trigger-Event-Typen, Loot-Provider, Reward-Logik etc. hinzuzufügen, ohne den Core-Code anzufassen.
- **Alles unter einem Dach**: Dimension-Handling, Island-System, Progression, Party, Permissions, Trigger-Events, Biome-Editor, Cobblemon-Integration, Moderation, Setup-Wizard — alles ein Mod, ein Codebase, eine Config-Struktur.

Architekturprinzip: **Zwei-Module-Setup** — `oneblock-api` (Interfaces/Events) + `oneblock-core` (Implementierung), damit externe Devs sauber gegen die API entwickeln können, ohne den ganzen Core-Code als Dependency zu brauchen.

### Beschlossene Grundsatzentscheidungen (2026-08-12)

| # | Entscheidung | Begründung |
|---|---|---|
| 1 | **SQLite als Default-Storage, MySQL/MariaDB optional** | Drag-and-Drop-Ready: Server läuft sofort ohne externe DB. Große Netzwerke stellen per Config um. Eine SQL-Schicht, zwei Dialekte. |
| 2 | **Immer eigene Void-Dimension** (kein Dual-Path Overworld/Custom) | Ein Codepfad, konsistentes Verhalten auf jedem Server, keine World-State-Detection. Overworld bleibt unangetastet. |
| 3 | **Insel-Punkte durch alle Party-Members, nur Mining** (kein Playtime) | Co-op-Anreiz für Members; Playtime würde AFK-Farming belohnen. Optional abnehmender Ertrag pro zusätzlichem Member. |
| 4 | **Fundament zuerst** (Phasen-Reihenfolge getauscht) | Wizard braucht Config-System, DB-Schicht und Command-Unterbau — die entstehen in der Fundament-Phase. |
| 5 | **API-Modul in Java, Core-Modul in Kotlin** | Java-API = maximale Zugänglichkeit für Addon-Devs; Kotlin-Core = saubere Interop mit Cobblemons Kotlin-Events. |
| 6 | **`/ob home`, `/ob spawn`, Void-Tod-Handling, Inaktivitäts-Purge, Chunk-Alignment** neu in der Work List | Überlebenswichtig in einer Void-Welt bzw. Pflicht für öffentlichen Serverbetrieb. |
| 7 | **Default-Inselgröße 1024 (Max bleibt 10000)** | 5000×5000 als Default ist zu groß: Cobblemon-Spawns verteilen sich zu dünn, Level-Ups kaum spürbar. |
| 8 | **Loottable-Default = ALLE Blöcke (MC + Cobblemon)** | Registry-Scan mit Sicherheitsfiltern: keine Fluids, nichts Unzerstörbares, nichts ohne Item-Form, kein schmelzendes Eis, nichts das ohne Support poppt; Leaves persistent; Gravity-Blöcke fallen am Anchor nicht (Mixin). Custom-Modus + Blacklist bleiben in der Config. |

---

## 1. Scope-Entscheidungen (Zusammenfassung)

| System | Status |
|---|---|
| Island-Grid + OneBlock RNG-Kern | ✅ Kern des Mods |
| Border-Level Progression (8 Level) | ✅ Voll |
| Party/Co-op System | ✅ |
| Trigger Events (Mob Waves/Bosse) | ✅, als Extension-Point für Dritt-Mods |
| Permission-System | ✅ Vereinfacht (Owner → Member → Public) |
| Biome-Editor | ✅, harter Cut (kein Blending, snappt auf 4er-Raster) |
| Cobblemon-Integration | ✅ Fest integriert |
| Moderation/Admin-Tools | ✅ Schlank, aber vorhanden (öffentlicher Server) |
| First-Launch Setup Wizard | ✅ Chat-basiert UND per Command/Konsole bedienbar |
| Public Developer API | ✅ Zentrales Architekturprinzip, als Maven-Artefakt publiziert |
| Teleports (`/ob home`, `/ob spawn`) + Void-Tod-Handling | ✅ NEU |
| Inaktivitäts-Purge für verlassene Inseln | ✅ NEU |
| Leaderboard | ❌ Gestrichen |
| Storage | SQLite (Default, zero-config) / MySQL·MariaDB (optional via Config) |

---

## 2. Architektur-Überblick

```
oneblock-api/            (Java, leichtgewichtig, für externe Devs, als Maven-Artefakt publiziert)
  events/                 IslandCreatedEvent, OneBlockBreakEvent, BorderLevelUpEvent,
                           PartyJoinEvent, TriggerEventStartEvent, PermissionChangeEvent, ...
  managers/                IslandManager, PartyManager, ProgressionManager,
                           EventManager, PermissionManager  (Interfaces only)
  OneBlockAPI.java         statischer Zugriffspunkt: OneBlockAPI.get().getIslandManager() etc.

oneblock-core/            (Kotlin, volle Implementierung, hängt von oneblock-api + Cobblemon ab)

Dimension-Setup (immer gleich, kein Dual-Path):
 └─ Eigene Void-Dimension `oneblock:world` wird beim ersten Start registriert.
    Overworld bleibt unangetastet (nutzbar als Admin-Bereich o.ä.).

 └─ Hub (0,64,0 fix, Spawn-Kreis = volle Spawn-Protection, niemand kann dort etwas
    abbauen/platzieren; Config-Flag `hub_allow_building` erlaubt Admins das Bauen)
 └─ Island-Grid (Spiral-Placement, N Slots)
     └─ Island
         ├─ OneBlock (Anchor-Point, RNG-Loottable)
         ├─ Owner + Party-Members (max. konfigurierbar)
         ├─ Border-Level (1-8, wächst mit Fortschritt)
         ├─ Permission-Layer (Owner/Member/Public)
         ├─ Biome-Region (editierbar via Selection-Tool)
         └─ Trigger-Event-Counter (nach X Breaks → Event, Typen als Extension-Point)

Storage: SQLite (Default) oder MySQL/MariaDB via HikariCP, async writes,
         Umstellung NUR über Config-Datei. Ein Migrations-System für beide Dialekte.
Cobblemon-Hook: border-begrenzte Spawns, Catch-/Battle-Permission, Event-Legendaries, Temp-Buffs
```

### Spacing/Größen-Formel (zentral an EINER Stelle im Code, nicht mehrfach hardcoded)

Der Admin gibt im Wizard nur **einen** Wert ein: die **maximale Island-Größe** (Border-Level 8). Daraus wird automatisch berechnet:

```
max_island_size = Wizard-Eingabe (z.B. 1024 → 1024×1024) — intern auf das nächste
                  Vielfache von 16 (Chunk-Größe) aufgerundet
island_spacing  = max_island_size + 1024   (Sicherheits-Buffer, verhindert Overlap;
                  ebenfalls 16er-aligned)
```

**Chunk-Alignment ist Pflicht:** Alle Größen und das Spacing sind Vielfache von 16, damit
Chunk-basierte Permission-Checks und die Chebyshev-Border nie mitten durch einen Chunk laufen.
Grenz-Chunks gehören dadurch immer eindeutig genau einer Insel (oder niemandem).

Beispiel: Eingabe `1024` → Islands maximal 1024×1024, Grid-Spacing 2048 Blöcke.
Diese Formel gilt **immer für beide Werte gemeinsam** — es gibt keine separate Spacing-Eingabe.

### Hub-Radius = Spawn-Protection

Der Hub-Radius ist eine **volle Schutzzone**: Innerhalb des Radius kann **niemand** Blöcke
abbauen oder platzieren. Reiner Safe-Space. Config-Flag `hub_allow_building` (Wizard-Frage 8)
erlaubt Admins als Ausnahme das Bauen.

### Public-Rolle (definiert)

Besucher (Public) auf fremden Inseln dürfen: **betreten und schauen — sonst nichts.**
- Kein Block-Break/-Place, keine Container.
- **Keine Cobblemon fangen UND keine besiegen** (sonst farmen Besucher fremde Spawns leer).
- Optionale, vom Owner konfigurierbare Interact-Whitelist (Türen, Knöpfe, Druckplatten).

---

## 3. Qualitätsstandards (Pflicht-Checkliste, gilt für jedes Modul)

- **Y-Koordinaten immer explizit angeben** — Hub-Spawn und jede Island brauchen klare Y-Werte, kein Fall-Risiko in der Void-Welt.
- **Void-Tod ist ein First-Class-Edge-Case** — Respawn immer am Insel-Spawn (bzw. Hub), niemals Todesschleifen.
- **Config-Vollständigkeit von Anfang an** — DB-Felder, alle System-Limits (Party-Größe, Biome-Regionen, Event-Cooldown, max Island-Größe, Purge-Fristen) gehören von Beginn an in die Config-Struktur.
- **Keine Widersprüche zwischen Spec-Files** — Fixregel: **Trigger-Events skalieren nur in der Schwierigkeit mit dem Border-Level, nicht in den Rewards.**
- **Permission-Nodes von Anfang an mitdenken** — jeder Command bekommt sofort einen Node (`oneblock.command.start`, `oneblock.admin.setup`, ...). LuckPerms ist **optionale** Integration mit Fallback auf OP-Level — der Mod läuft auch ohne Permission-Mod.
- **Schema-Migration & Hot-Reload von Tag 1 an** — DB-Versionierung, `/ob reload` als vollwertiger Command.
- **Edge-Cases explizit klären**: Fallback-Owner für unregistrierte Chunks, Owner-Reset mit Members online, Invite-/Event-Timeouts, Confirmation-Step bei destruktiven Admin-Commands.
- **Einheitliche Namensgebung** — ein Command/Begriff heißt überall gleich.
- **API-first denken** — jedes Modul, das einen internen Zustand verwaltet (Islands, Party, Progression, Events, Permissions), bekommt ein öffentliches Interface im `-api`-Modul UND feuert relevante Events über den Event-Bus. Das ist kein optionaler Nachtrag, sondern Teil jeder Phase.
- **Backup-Konsistenz dokumentieren** — DB und Weltdaten müssen zusammen gesichert werden (Admin-Doku).

---

## 4. Work List (Master-Checkliste)

Die Phasen bauen aufeinander auf. Jede Phase wird mit Build + Smoke-Test abgeschlossen, bevor die nächste beginnt.

### Phase 1 — Fundament & API-Grundgerüst
- [x] Gradle-Multi-Module-Setup: `oneblock-api` (Java) + `oneblock-core` (Kotlin)
- [x] `fabric.mod.json` für beide Module, Cobblemon als harte Dependency in `-core`
- [x] Maven-Publishing für `oneblock-api` vorbereiten (Addon-Devs entwickeln gegen das Artefakt)
- [x] `OneBlockAPI`-Singleton-Zugriffspunkt im `-api`-Modul
- [x] Event-Bus-Grundgerüst (Registrierung + Dispatch)
- [x] HikariCP-Connection-Pool + async Read/Write-Wrapper (SQLite-Default, MySQL via Config)
- [x] DB-Schema + Migration-System (versioniert ab Tag 1, beide SQL-Dialekte)
- [x] Config-System (JSON5) mit vollständiger Feldstruktur
- [x] Strukturiertes Logging-Setup
- [x] Command-Unterbau (`/ob`-Root, Permission-Node-Registrierung, LuckPerms-optional-Abstraktion)

### Phase 2 — First-Launch Setup & Wizard
- [x] Void-Dimension `oneblock:world` registrieren (immer, kein Dual-Path)
- [x] Kreisförmige Spawn-Plattform bei (0,64,0) generieren
- [x] Hub-Protection: Break/Place innerhalb Hub-Radius canceln (Flag `hub_allow_building` für Admins)
- [x] "Setup-Pending"-Zustand (sperrt Island-Erstellung bis Wizard abgeschlossen)
- [x] `/ob reload`-Command inkl. automatischem DB-Verbindungstest
- [x] Welcome-Message beim ersten OP-Join
- [x] Chat-basierter In-Game-Wizard: State-Machine für die 8 Gameplay-Fragen (Abschnitt 6)
- [x] Wizard-Lock (nur ein OP führt den Wizard gleichzeitig)
- [x] Command-Alternative pro Frage: `/ob setup set <key> <value>` — auch aus der Konsole bedienbar
- [x] Wizard-Validierung + Skip/Default-Handling pro Frage
- [x] Wizard-Zusammenfassung + Bestätigung vor dem Speichern
- [x] Save-to-Config + automatischer Full-Reload nach Bestätigung
- [x] `/ob setup`-Command zum erneuten Aufrufen des Wizards

### Phase 3 — Island-Grid + OneBlock-Kern
- [x] Spiral-Placement-Algorithmus für Anchor-Punkte
- [ ] Slot-Wiederverwendung nach Purge — WARTET auf Chunk-Clearing, sonst erben neue
      Inseln die Bauten der alten (aktuell: immer frische Slots, Spirale ist unendlich)
- [x] Zentrale Spacing/Größen-Utility-Klasse (Formel aus Abschnitt 2, inkl. 16er-Alignment, EINE Stelle im Code)
- [x] OneBlock-Position-Tracking + Regeneration-Hook (BlockBreakEvent)
- [x] Weighted-Random-Loottable für den OneBlock-Output
- [x] `/ob home` (zur eigenen Insel) + `/ob spawn` (zum Hub) inkl. sicherer Teleport-Ziele
- [x] Void-Tod-Handling: Respawn am Insel-Spawn (bzw. Hub, wenn keine Insel)
- [x] `IslandCreatedEvent`, `OneBlockBreakEvent` über den API-Event-Bus
- [x] `/ob create`, `/ob reset`, `/ob delete`
- [x] Reset-Mechanik: Party bekommt neuen Slot; alter Slot bleibt 7 Tage als "archiviert" liegen
      (DB-Flag, kein File-Kopieren), Restore = Flag zurücksetzen, danach Purge
- [x] Inaktivitäts-Purge: Inseln nach konfigurierbarer Owner-Inaktivität archivieren/löschen
      (`last_seen` pro Spieler ab Tag 1 im Schema)
- [x] Öffentliches `IslandManager`-Interface

### Phase 4 — Permission-System
- [x] Owner/Member/Public-Rollen-Enum (Public-Definition aus Abschnitt 2)
- [x] Chunk-Ownership-Check-Hook (Break/Place/Interact)
- [x] Chebyshev-Distanz-Grenzschutz (quadratisch, chunk-aligned)
- [x] Explosion-/Fluid-Containment an der Border (inkl. Enderperlen, Pistons über die Border)
- [x] Fallback-Owner (VOID-Owner) für unregistrierte Chunks
- [ ] Owner-konfigurierbare Interact-Whitelist für Besucher — kommt mit den Island-Settings (Phase 5+)
- [x] `PermissionChangeEvent` über API-Bus (feuert bei Member-Add/-Remove, seit Phase 5)
- [x] Permission-Nodes für alle zugehörigen Commands
- [x] Öffentliches `PermissionManager`-Interface

### Phase 5 — Party/Co-op
- [x] Invite/Accept/Deny/Kick-Flow
- [x] Invite-Timeout (konfigurierbar)
- [x] Max-Member-Limit (aus Wizard-Config)
- [x] Owner-Reset/-Leave-Verhalten für verbleibende Members
- [x] `PartyJoinEvent`, `PartyLeaveEvent` über API-Bus
- [x] Öffentliches `PartyManager`-Interface

### Phase 6 — Progression (Border-Level 1-8)
- [ ] Punkte-Formel: **nur Mining, alle Party-Members zählen für die Insel** (kein Playtime);
      optional abnehmender Ertrag pro zusätzlichem Member (Config)
- [ ] Border-Level-Interpolation: Level 1 fester kleiner Startwert (Default 16×16),
      Level 8 = Wizard-Wert, Level 2-7 exponentiell interpoliert (Default, pro Level
      in der Config manuell überschreibbar; alle Werte 16er-aligned)
- [ ] Border-Expansion-Trigger + Announcement (Hologram/Actionbar)
- [ ] Progression-Reset-Verhalten bei `/ob reset`
- [ ] `BorderLevelUpEvent` über API-Bus
- [ ] Öffentliches `ProgressionManager`-Interface

### Phase 7 — Trigger Events (als Extension-Point)
- [ ] Break-Counter pro Island
- [ ] Event-Spawn-Trigger nach konfigurierbarer Schwelle
- [ ] Countdown-Announcement ("Event in 10 Blocks")
- [ ] Core-Event-Typen: Mob Waves, Bossfights, Ressourcen-Burst
- [ ] Schwierigkeits-Skalierung mit Border-Level (NICHT Reward-Skalierung, NICHT Spielerzahl)
- [ ] Events laufen für alle anwesenden Insel-Mitglieder (Owner muss nicht online sein)
- [ ] Event-Cooldown zwischen Events
- [ ] Failure-Zustand + Mob-Cleanup-Timer
- [ ] Event-Queue für überlappende Trigger
- [ ] `TriggerEventStartEvent`/`EndEvent`/`FailEvent` über API-Bus
- [ ] Extension-Registry: `OneBlockAPI.registerEventType(...)` für Dritt-Mods
- [ ] Öffentliches `EventManager`-Interface

### Phase 8 — Cobblemon-Integration
- [ ] Border-begrenzte Spawns: Cobblemon-Spawns, die außerhalb der Insel-Border des
      auslösenden Spielers landen würden, werden gecancelt (kein Cross-Island-Bleeding)
- [ ] Catch- UND Battle-Permission pro Island (Besucher können weder fangen noch besiegen;
      Owner-Setting)
- [ ] Pokémon, die über die Border wandern, zurücksetzen oder despawnen
- [ ] Trigger-Events können Legendaries/Ultra Beasts spawnen (Hook in Phase-7-Extension)
- [ ] Temporäre Island-Buffs (Shiny-Rate, IV-Boost, Spawn-Rate) inkl. Restart-Persistence
- [ ] Cobblemon-Spawn-Intensität aus Wizard-Config anwenden
- [ ] Mindest-Cobblemon-Version dokumentieren + Startup-Check

### Phase 9 — Biome-Editor
- [ ] 2-Punkt-Raycast-Selection, 3D (inkl. Y-Achse), snappt auf das 4er-Biome-Raster
- [ ] `/ob biome set <biome>`, harter Cut zwischen Regionen (auf 4×4×4-Zellen-Ebene)
- [ ] Max. Biome-Regionen pro Island (aus Wizard-Config) durchsetzen
- [ ] Auswirkung auf Cobblemon-Spawns in der Region

### Phase 10 — Moderation & Admin-Tools
- [ ] Ban/Kick von Islands (Admin-Only)
- [ ] Force-Reset/-Delete MIT Confirmation-Step
- [ ] Audit-Log (strukturierte Log-Datei + optionaler Discord-Webhook-Hook über die API)
- [ ] Permission-Precedence dokumentiert + getestet

### Phase 11 — Public API Finalisierung & Doku
- [ ] Alle Manager-Interfaces vollständig + stabil im `-api`-Modul
- [ ] Vollständige Event-Liste dokumentiert (JavaDoc + separates `API.md`)
- [ ] Extension-Points dokumentiert (Custom Loot Tables, Custom Event-Typen, Custom Reward-Provider)
- [ ] `oneblock-api` als Maven-Artefakt publizieren (GitHub Packages oder JitPack)
- [ ] Minimaler Beispiel-Addon-Mod als Referenz für zukünftige Devs
- [ ] Versionierung/Semver-Strategie für Breaking Changes

### Phase 12 — Polish & Drag-and-Drop-Readiness
- [ ] Vollständiger `/ob`-Command-Tree final durchtesten
- [ ] LuckPerms-Permission-Nodes vollständig dokumentiert (inkl. OP-Fallback-Verhalten)
- [ ] Admin-Doku: Backup-Konsistenz (DB + Welt zusammen sichern), MySQL-Umstellung
- [ ] Edge-Case-Handling final durchgetestet
- [ ] End-to-End-Test: frischer Server, NUR Cobblemon + diese Mod installiert → kompletter
      Loop (Wizard → Island erstellen → OneBlock spielen → Progression → Event →
      Cobblemon-Fang) funktioniert ohne manuelles Eingreifen außer der Wizard-Beantwortung

---

## 5. Reference-Folder-Struktur

```
/reference/
  cobblemon/
    PokemonEntity.kt
    CobblemonEvents.kt
    SpawnDetail.kt
    Species.kt
  flan/                          (Flemmli97/Flan — Protection-Referenz)
    ClaimStorage.java
    ProtectionEventHandler.java
    ClaimGui.java
  oneblock/                      (2Lynk/OneBlock — NUR als simples Pattern-Beispiel)
    OneBlockMod.java
    LootPool.java
  worldedit/                     (EngineHub/WorldEdit — Biome-Editing-Referenz)
    FabricWorld.java
    BiomeCommands.java
    CuboidRegionSelector.java
```

⚠️ Der 2Lynk/OneBlock-Referenz-Code ist funktional aber sehr simpel — nur als Pattern-Beispiel nutzen, nicht als Architektur-Vorbild.

---

## 6. Wizard-Fragenkatalog (nur Gameplay-Settings — Storage läuft separat über die Config-Datei)

| # | Frage | Typ | Default (bei Skip) | Validierung |
|---|---|---|---|---|
| 1 | Hub-Schutzradius (Blöcke, volle Spawn-Protection) | Zahl | `1000` | 100–5000 |
| 2 | Max. Island-Größe (Border-Level 8, z.B. 1024 = 1024×1024) | Zahl | `1024` | 64–10000 (intern 16er-aligned; Spacing automatisch +1024 Buffer) |
| 3 | Max. Party-Größe pro Island | Zahl | `4` | 1–20 |
| 4 | Trigger-Event-Schwelle (Breaks bis Event) | Zahl | `100` | 10–1000 |
| 5 | Max. Biome-Regionen pro Island | Zahl | `10` | 1–50 |
| 6 | Cobblemon-Spawn-Intensität (Multiplikator) | Zahl | `1.0` | 0.1–5.0 |
| 7 | Server öffentlich? | Ja/Nein | `Ja` | — |
| 8 | Hub-Building erlauben? (Admins dürfen im Hub bauen) | Ja/Nein | `Nein` | — |

Nach Frage 8 folgt die Zusammenfassung + Bestätigung im Chat, danach Save & Reload.
Jede Frage ist alternativ per `/ob setup set <key> <value>` beantwortbar (auch Konsole).

**Storage-Konfiguration** läuft separat über `config/oneblock/database.json5`:
- Default: SQLite (`storage: "sqlite"`), Datei liegt unter `config/oneblock/data.db` — **keine Einrichtung nötig, Wizard startet sofort.**
- Optional: `storage: "mysql"` + Host/Port/Datenbank/User/Passwort. Bei ungültiger MySQL-Verbindung bleibt der Mod im "Setup-Pending"-Zustand und loggt den Fehler klar verständlich.

---

## 7. Offene Punkte

- **Projektname**: noch offen (aktuell Arbeitstitel "Cobblemon OneBlock")
- **Border-Level-Interpolationskurve (2-7)**: Default exponentiell — final zu bestätigen, sobald man sie im Spiel spürt
- **Abnehmender Party-Ertrag**: genaue Kurve (z.B. Member 2+ zählt 75 %?) beim Bau von Phase 6 festlegen

---

## 8. Arbeitsweise

Die Work List aus Abschnitt 4 ist der rote Faden. Pro Phase:
1. Spec ausformulieren (kurz, im PR/Commit beschrieben)
2. Implementieren — langsam, sauber, bug-frei
3. Build + Smoke-Test, dann erst die nächste Phase

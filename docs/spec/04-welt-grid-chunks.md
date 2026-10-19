# Void-Dimension, Hub, Insel-Grid und Chunk-Lifecycle

> **Spec v4.1** · Teil von `docs/spec/` (Übersicht: `00-INDEX.md`) · Quelle: Masterprompt v4 §6, §7, §30  
> **Lesen in Phase:** 0B, 2  
> Abschnitte mit **[v4.1]** sind Ergänzungen gegenüber v4. Alles andere ist der v4-Text unverändert (nur Dokumentnamen an den zusammengelegten Dokumentensatz angepasst).

## 6. Eigene Void-Dimension und Hub

- Welt-ID standardmäßig `oneblock:void`; finaler Namespace in Phase 0 bestätigen.
- Die Overworld **niemals** überschreiben. Dieselbe OneBlock-Dimension soll auf frischen sowie bereits bestehenden Serverwelten funktionieren, ohne deren bestehende Chunks zu verändern.
- Bevorzugt mitgebündelte Datapack-Resources und Vanilla-`flat`-Generator mit **leeren Layers**, deaktivierten Features und Strukturen, konfigurierbarem Standardbiom (`minecraft:plains`). Prüfe Registrierung, World-Preset-/Registry-Verhalten und Loaded-Save-Kompatibilität auf 1.21.1; bei unvermeidlicher eigener Generator-Klasse ein ADR.
- Hub liegt im reservierten Grid-Slot `(0,0)`, Standardhöhe `Y=64`, kreisförmige Vanilla-Plattform. Hub-Schutz gilt vollständig; `hub_allow_building` ist nur eine explizite Admin-Ausnahme. Im Hub standardmäßig keine wilden Pokémon.
- Ohne Insel: Join/Respawn zum Hub. Mit Insel: sicherer Insel-Spawn oder valider Bett-/Anker-Respawn, soweit Vanilla-Dimensionsregeln dies tatsächlich erlauben. Bei invalidem Spawn: Insel-Home, danach Hub. Keine Endlosschleife oder Teleport ins Void.
- Admin-Bypass für Wartungs-/Debugfälle; fremde Dimensionen und andere Mod-Welten bleiben unberührt, außer konfigurierbarer Eintritts-/Join-Routing-Policy. Default ist der OneBlock-Spielmodus für reguläre Spieler.
- Nether-/End-Portale in der OneBlock-Dimension standardmäßig gesperrt; Inhalte werden stattdessen über Loot-/Challenge-Fortschritt verfügbar. Keine versehentliche Portalumleitung in andere Welten.
- Kein globaler Chunk-Ticket-Zwang pro Insel. Hub-/Insel-Ladeverhalten und Chunk-Unloads explizit testen.


### Dimension-/Spawn-Semantik und Übergänge

- Prüfe Dimension-Type-Flags (Tageszeit, Himmelslicht, Bett-/Respawn-Anker, Wasserverdunstung, Koordinatenskalierung, Effekte, Bewegung), Flat-Generator und den geerbten/neu synchronisierten Dynamic-Registry-Zustand **auf Dedicated Server plus echtem Cobblemon-Client**. Die Default-Definition muss Minecraft-konform und ohne manuelles Datapack-Aktivieren funktionieren.
- Definiere Spawn-/Join-Routing als Prioritätenkette `valid player respawn > validated island home > hub`, jeweils mit atomarer Sicherung gegen Teleportloops. Registriere keine globalen Respawn-Hooks, die fremde Dimensionen mutieren.
- Erstellt der Hub Block-Entities, Items oder NPCs, werden diese explizit dem Hub-Kontext zugeordnet; Admin-Schutz, Chunk-Ticket-Strategie und eventuelle einmalige Initialisierung sind idempotent.
- **Notfallmodus:** Falls Dimension oder Hub-Initialisierung scheitern, keine Spieler ins Void teleportieren; Setup/OneBlock-Operationen sperren, Fehler anzeigen, Recovery anbieten.

## 7. Insel-Grid, Slot-Mathematik und Dateibesitz

### 7.1 Konstante Geometrie

- Kantenlänge eines Levels immer ungerade: `size = 2 * radius + 1`; OneBlock exakt im Inselzentrum.
- **Default Level 1:** `17 × 17` (`r=8`). **Default Level 8:** `2049 × 2049` (`r=1024`) statt 5000+ als Standard. Admin kann größere Werte konfigurieren, ausdrücklich mit Performance-Hinweis.
- Maximalgröße aus Wizard wird vor Anlage der ersten Insel auf ungerade Kantenlänge normalisiert und angezeigt (oder mit eindeutiger Validierung abgelehnt). Nicht kommentarlos vergrößern.
- **Stride:** `stride = ceilToMultiple(maxSize + 1000, 1024)`. Bei `maxSize=2049` → `stride=3072`.
- Grid-Anker: `anchorX = gx * stride`, `anchorZ = gz * stride`; Slot `(0,0)` gehört ausschließlich dem Hub.
- Zell-Koordinaten per `floorDiv(x + stride / 2, stride)`, analog für Z; Grenzen ausdrücklich für negative Koordinaten testen. `stride` ist Vielfaches von 1024, sodass halbe Zellen an Minecraft-Region-Dateigrenzen (512 Blöcke) liegen.
- Für **build/spawn/biome rights** gilt nicht schon die Zellzugehörigkeit: Entscheidend ist zusätzlich der aktuelle Inselradius um ihren Anker und ein belegter, aktiver Slot.
- Externe Positionen und potenzielle Nachbarzellen dürfen nie bloß aufgrund eines mathematischen Lookups als eine existierende Insel gelten.
- Spiral-Slots deterministisch und reversibel zu Grid-Koordinaten zuordnen. Das Zentrum wird nie an Spieler vergeben.
- Weltgrenze, Integer-/Long-Überläufe, Multiplikations- und Lookup-Ränder prüfen. Bevor ein Slot reserviert wird, muss seine gesamte maximale Baufläche innerhalb der wirksamen Weltgrenze liegen.
- Grid-Konstanten sind nach der ersten Insel unveränderlich. Ändern nur durch explizites, separat freigegebenes Migrationswerkzeug mit Backup; **nicht** durch `/ob reload`.
- Die volle erlaubte Bauhöhe gehört dem Inselbesitzer innerhalb der erlaubten X/Z-Grenze; Vanilla-Höhen- und Entitylimits bleiben erhalten.

### 7.2 Region-Datei-Invarianten

- Jede Slot-Zelle soll exklusiv vollständigen Minecraft-Region-Dateien zugeordnet sein; die Exklusivität ist pro Datei und tatsächlichem Weltinhalt zu beweisen. Dies für **`region/`, `entities/`, `poi/`** separat verifizieren.
- Die Liste der zugeordneten Regionkoordinaten wird aus Slot-Grenzen berechnet und in Operationen nachvollziehbar protokolliert. Vor jedem Delete/Export vorab Ownership, Pfade und erlaubte Wipe-Ziele prüfen.
- Kein Löschen von offenen oder möglicherweise noch durch Minecraft verwendeten Weltdateien.
- Keine Dateisystemoperation nur auf Basis unvalidierter Nutzereingaben; kanonische Save-Pfade und Traversal-Schutz.
- Nur explizit reservierte Inselregionen verwalten; Hub-Regionen sind dauerhaft gesperrt.
- Insel-Wipe und Welt-Backup müssen eine nachweislich konsistente Save-Grenze benutzen. Falls ein sicherer Online-Wipe nicht möglich ist, bleibt Wipe **offline/vor Laden** als harte Voraussetzung.


### Mathematische Nachweise und Boundary-Safety

- In Phase 0B `stride`, Zellgrenzen, Region-Datei-Bereiche und Hubreservierung auf Papier und per Property-Based-Test beweisen: **Keine zwei Slotzellen teilen sich eine Regiondatei; kein Slot schneidet die Hub-Region; keine nutzbare Insel überschreitet die Weltgrenze.**
- `maxIslandSize + 1000` ist ein **konfigurierbarer, bewusst dokumentierter Default-Puffer**. Er muss mindestens größer als der notwendige Grenz-/Regionen-Abstand sein; Änderung nach Inselanlage verboten.
- Vor Speicherreservierung `long`-Arithmetik für Multiplikation/Überlauf und danach `int`-Bereich/Weltgrenze prüfen. Spiral-Index darf nie still wraparound; bei Erschöpfung verständliche Admin-Diagnose statt Endlossuche.
- Speichere zu jedem Slot **die erwarteten und die tatsächlich entstandenen Dateien**. Region-Dateibesitz allein berechtigt nicht zum Wipe, falls fremde Dateien/Chunks durch Bugs entstanden sind: unerwarteter Inhalt → blockieren, Diagnose erstellen.
- Leere Luft außerhalb einer belegten Insel ist **kein freier öffentlicher Baubereich**; gilt auch für Flüssigkeiten, zufällige Block-Ticks, explosionsbedingte Änderungen und Maschinen.

## 30. Moderne Welt-/Chunk-Lifecycle-Verträge

- Kein `getChunk`/synchrones Chunkloading im heißen Permission-Pfad ohne nachgewiesene Kosten. Chunkstatus, Tickets und Addon-Spawns müssen auf natürliche Ladegrenzen Rücksicht nehmen.
- **Unloaded islands are cold:** keine Tick-Aufgaben, keine Spawns, keine hochfrequenten DB-Polls. Running Events pausieren oder enden nach dokumentierter Policy; Reaktivierung korrekt nach Chunkreload.
- Chunk/Entity/POI-Regionen besitzen gesonderte Speicherpfade; Export/Wipe sichert/prüft **alle** einschlägigen Speicherklassen, auch OneBlock-Metadaten und inselspezifische externe Referenzen.
- Chunk unload/reload darf nicht neue fake Holograms, doppelte Spawn-Tickets, duplizierte Event-NPCs oder vergessene Edit-Jobs erzeugen.
- Daten außerhalb der Inselzelle, die durch Bugs/Modinteraktion entstehen, werden nicht per globalem Löschen „repariert“: diagnostizieren und isolieren. Fehlerhafte Chunks bleiben für `doctor` nachvollziehbar.
- Entkoppelte **Simulation Distance** und **View Distance** testen; spawnnahe Inseln dürfen nicht durch jede beliebige sichtbare Distanz pauschal aktiv gehalten werden.

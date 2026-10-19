# Technikbasis, Kompatibilität und Quellen

> **Spec v4.1** · Teil von `docs/spec/` (Übersicht: `00-INDEX.md`) · Quelle: Masterprompt v4 §3, §31, §37  
> **Lesen in Phase:** 0A, 0B, 1, 8  
> Abschnitte mit **[v4.1]** sind Ergänzungen gegenüber v4. Alles andere ist der v4-Text unverändert (nur Dokumentnamen an den zusammengelegten Dokumentensatz angepasst).

## 3. Verbindliche Technikbasis

- Minecraft **1.21.1**; Fabric Loader und Fabric API mit explizit gepinnten, kompatiblen Versionen; Java **21**.
- Cobblemon **1.8.2 Fabric** als feste Target-Version. Kompatibilität bei Start anhand Fabric-Metadaten deklarieren und validieren. Bei inkompatibler Version verständlicher Ladefehler; keine automatischen Downgrades oder großzügigen, ungetesteten Versionsbereiche.
- Gradle Wrapper + Fabric Loom; reproduzierbare Versionen und Dependency Locking, soweit zweckmäßig.
- **SQLite als einzige verpflichtende Storage-Implementierung für v1**. Datenbank **pro Welt** unter dem Save-Verzeichnis (z. B. `<world>/data/oneblock/`), nicht als gemeinsame weltübergreifende DB im globalen Config-Ordner. SQLite-JDBC-Portabilität einschließlich unterstützter OS-/Architekturkombinationen prüfen und dokumentieren.
- MariaDB/MySQL + HikariCP **erst nach dem stabilen SQLite-Release**, sofern ein realer Skalierungsbedarf oder eine explizite Freigabe vorliegt. Storage-Schnittstelle früh sinnvoll abstrahieren, aber nicht vorsorglich zwei vollständige Backends bauen. **Kein H2-Fallback** in v1.
- Server-Texte: `en_us` und `de_de` als serverseitige Sprachressourcen, anhand tatsächlich verfügbarer Player-Locale-Information aufgelöst, mit dokumentiertem `en_us`-Fallback. **Keine unübersetzten Custom-Translation-Keys an einen Client ohne OneBlock-Resourcepack senden.**
- Config-Dateien: Versionierung, Schemaprüfung, verständliche Fehler mit Pfad/Feld, atomisches Speichern und geeignete Migrationsstrategie.

**Technische Quellen in Phase 0 gegen die echte Version prüfen:**

- Cobblemon Quellcode, Fabric-Events, Spawn- und Capture-System, Wild-Pokémon-Tracking, NPC-/Trainer- und Battle-APIs.
- Fabric API / Minecraft 1.21.1: Dimensionsregistrierung per mitgelieferten Data-Resources, Lifecycle, Brigadier, Block-/Entity-Events, GameTests, Packet-Tracking, Biome-Daten, Saves.
- Relevante Referenzprojekte in `/reference` (Cobblemon, Flan, 2Lynk/OneBlock, WorldEdit) **nur als Anregung und unter Prüfung ihrer jeweiligen Lizenzen**. Keine unreflektierte Codeübernahme.


### Verifizierungsdisziplin und Dependency-Baseline

- Erstelle `docs/DEPENDENCY_MATRIX.md`: exakte Artefaktversion, Quelle, Hash, Loader/MC-Metadaten, Lizenz, Zweck, Client-/Server-Verteilung, Updategrund, Teststatus. Prüfe Fabric API, Cobblemon und transitive Laufzeitabhängigkeiten **als reales Modpack**, nicht isoliert.
- **Versionsänderungen sind Change Requests**: Bei Cobblemon-/Fabric-/Loom-/Mappings-Update Änderungen an Signaturen, Mixins, Spawn-Kontexten, Datapacks und Persistenz prüfen, Integrationstests wiederholen, Version nur nach Freigabe anheben. Niemals `latest`, `+` oder ungebundene Snapshots im Release verwenden.
- Keine globalen JVM-/System-Properties, die andere Mods still beeinflussen. Keine eigenen Client-Pakete oder Registry-Typen, die nicht im unveränderten kompatiblen Cobblemon-Client verständlich sind.
- Prüfe License Notices und Source-Angebote für Drittbibliotheken. `include`/Jar-in-Jar nur, wenn Lizenz, Klassentransformation, Native-Libs, Duplicate-Classes und Dedicated-Server-Packaging nachweislich funktionieren.
- `server-only` bedeutet **kein eigener OneBlock-Clientmod erforderlich**, nicht „beliebiger Vanilla-Client“: Der Nutzer benötigt den zur Server-Version passenden Cobblemon-Client inklusive seiner Anforderungen.
- Alle Integrationsannahmen im Quellcode-POC mit Versions-/Commit-Verweis und ausführbarem Test dokumentieren; Wikis allein ersetzen keinen 1.8.2-Laufzeittest.

### [v4.1] Laufzeit-Abhängigkeiten von Cobblemon explizit spiegeln

- Lies die tatsächlichen Pflicht-Abhängigkeiten aus der `fabric.mod.json` des gepinnten Cobblemon-1.8.2-Fabric-JARs (bisher gehörte u. a. Fabric Language Kotlin dazu) und spiegle sie **1:1** in Dev-Umgebung, GameTest-Server, CI und Release-Testserver.
- Trage jede davon mit Version in `docs/DEPENDENCY_MATRIX.md` ein. Ein Testserver, der eine Cobblemon-Abhängigkeit nicht hat, ist kein gültiger Testnachweis.

## 31. Kompatibilitätsstrategie und Versionierung

### Kompatibilitäts-Tiers

- **Tier A / supported:** Vanilla-Basis + offiziell gepinntes Cobblemon/Fabric-Set, alle Release-Tests erfolgreich.
- **Tier B / validated:** konkret benannte optionale Mods/Modversionen in einer eigenen Matrix mit Smoke-Tests (z. B. Permissions-, SGUI-/Placeholder-, Performance- und Cobblemon-Addons).
- **Tier C / best effort:** unbekannte Drittmods, keine Supportgarantie und keine invasiven Global-Patches für vermeintliche Kompatibilität.

### Wichtige Konflikttypen

- Schutzmods mit eigenen Claims, Teleport-/World-Routing-Mods, Spawn-/Shiny-Mods, Kampf-/Capture-Erweiterungen, Inventar-/Economy-Mods, Performance-/Mixin-Patches, Chunkload- und Worldgen-Mods.
- Konflikte benennen: doppelte Permissionentscheidung, unterschiedliche Eventprioritäten, Asset-/Registry-Konflikte, Chunk-Tickets, unvorhergesehene Pokémon-Entitytypen, eigener Hologramm-Packetverkehr.
- No-op oder bewusste Verweigerung ist **besser** als stiller Bypass. Optionaler Integrationscode hinter `isModLoaded`/Feature Gate und **getesteter** Versionsentscheidung.
- Release-Upgrade-Matrix: **new install**, **v3-era dev-save→v4** nur bei real existierenden Save-Formaten, **last release→new release**, und **rollback attempt**. Backwards-Downgrade grundsätzlich nicht sicher, wenn DB-Schema geändert wurde; blockieren oder Restore aus Backup.

## 37. Geprüfte technische Referenzen und Quellenregeln

**Hinweis:** Die folgenden Quellen sind Recherchehinweise, **keine** Zusicherung, dass alle darin gezeigten APIs in unserer exakten Build-/Mappings-Version gleich heißen. Phase 0B prüft die signierte/gelockte 1.21.1/1.8.2-Version im Quellcode und auf dem Dedicated Server.

- Cobblemon v1.8.2 Fabric für MC 1.21.1: https://www.curseforge.com/minecraft/mc-mods/cobblemon (Versions-/Dateireiter; Target-Release erneut prüfen).
- Offizielle Cobblemon Spawn Conditions: https://wiki.cobblemon.com/index.php/Spawn_Condition (Dimensions-/Biome-/Umgebungsbedingungen).
- Offizielle Cobblemon Spawn Rules: https://wiki.cobblemon.com/index.php/Spawn_Rules (Regeltypen; globale Auswirkung verstehen, nicht blind einsetzen).
- Offizielle Cobblemon Config-Dokumentation: https://wiki.cobblemon.com/index.php?title=Config (Spawnversuche, Chunk-/Simulationseinstellungen).
- Fabric API 1.21.1 GameTest-Javadoc: https://maven.fabricmc.net/docs/fabric-api-0.110.0%2B1.21.1/net/fabricmc/fabric/api/gametest/v1/package-summary.html (API-Stand exemplarisch; bei anderer gepinnter Version passende Javadocs nutzen).
- Fabric 1.21.1 Automated Testing: https://github.com/FabricMC/fabric-docs/blob/main/versions/1.21.1/develop/automatic-testing.md
- SQLite WAL (inklusive Locking/Netzwerkfilesystem-Beschränkungen): https://www.sqlite.org/wal.html
- SQLite Online Backup API: https://www.sqlite.org/backup.html (**nur** SQLite, keine Minecraft-Regionen).

**Quellenrangfolge:** tatsächlicher gepinnter Quellcode/Javadocs + ausführbare Tests > offizielle versionsspezifische Dokumentation > offizielle allgemeine Wiki > Community-Artikel. Wenn Quellen widersprechen: technischen Unterschied explizit markieren und `BLOCKED` bis Test/Owner-Entscheidung.

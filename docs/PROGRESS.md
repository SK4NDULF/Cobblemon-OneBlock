# Fortschritt

> Stand: 2026-10-19 · Spec v4.1

## Aktive Phase

**0B – Feasibility.** Phase 0A ist abgeschlossen: Alle Rückfragen sind beantwortet und in `docs/DECISIONS.md` eingetragen (Nr. 1, 2, 9–11, 13–16).

## Erledigt

- Repository auf Owner-Wunsch geleert (Branch `claude/tender-keller-7hqmwy`, Commit `def9a42`). Der alte Stand ist nur noch in der Git-Historie (`main`, Commit `af8cf39`).
- `CLAUDE.md`, `docs/DECISIONS.md` und `docs/spec/00–14` eingecheckt.
- Spec-Dateien 00–03 und `DECISIONS.md` gelesen.
- Owner-Antworten eingetragen, `LICENSE` (MIT) angelegt, `main` auf den neuen Stand gebracht.

## Befunde Phase 0A

### Repository-Ist-Zustand

- Der Branch enthält keinen Code, keinen Build und kein `/reference`.
- `main` enthält noch die alte Implementierung. Wichtigste Abweichungen von der Spec:

| Bereich | Alter Stand (`main`) | Spec v4.1 |
|---|---|---|
| Cobblemon | `1.7.3+1.21.1` | `1.8.2` (fest) |
| Sprache | Core in Kotlin | Domain „rein Java-orientiert“ (Spec 13) |
| Speicher | SQLite + MariaDB/HikariCP | nur SQLite pro Welt (`DECISIONS` Nr. 3) |
| Permissions | LuckPerms-Bridge | keine Abhängigkeit von LuckPerms (Spec 01) |
| Mixins | 5 Mixins ohne `docs/mixins.md` | jedes Mixin mit Begründung und Integrationstest |
| Struktur | 3 Gradle-Module (`api`, `core`, `example-addon`) | ein installierbares JAR; Multiprojekt nur, wenn Jar-in-Jar nachweislich funktioniert (Spec 13) |

- Der alte Code wird nicht übernommen (`DECISIONS` Nr. 14). Er bleibt nur als Ideenquelle in der Git-Historie.

### Abhängigkeiten (geprüft am 2026-10-19)

Quelle: Modrinth-API, Projekt `cobblemon`, Loader `fabric`.

- `Cobblemon-fabric-1.8.2+1.21.1.jar`, veröffentlicht am 2026-10-11, SHA-256 `cae8ac328b61354fbe415208b8c6ffc12e7155094d0a87082e85e7914fb380c2`, Lizenz MPL-2.0.
- `fabric.mod.json` → `depends`: `fabricloader >=0.17.2`, `fabric-api >=0.116.6+1.21.1`, `minecraft 1.21.1`, `java 21`.
- Jar-in-Jar in Cobblemon 1.8.2: `fabric-language-kotlin-1.13.6+kotlin.2.2.20`, GraalVM JS/Truffle `22.3.0`, `icu4j 71.1`, `mongodb-driver 4.10.2`. Es ist **kein** SQLite-JDBC gebündelt.
- Folge für Spec 03 [v4.1]: Fabric Language Kotlin bringt Cobblemon 1.8.2 selbst mit. Es muss also nicht separat installiert werden. Die gebündelte Version wird trotzdem in die `DEPENDENCY_MATRIX` (Phase 0B) eingetragen.

### Technische Risiken (Vorschau für Phase 0B)

- Biomabhängige Cobblemon-Spawns in einer eigenen Void-Dimension (`DECISIONS` Nr. 12): größte Unbekannte.
- SQLite-JDBC-Natives auf Linux ARM64 und Windows x64 (`DECISIONS` Nr. 11).
- Jar-in-Jar von `sqlite-jdbc` im eigenen Mod: Kollision mit anderen Mods, die ebenfalls `org.sqlite` bündeln.
- Cobblemon 1.8.x ist neu (1.8.0 am 2026-09-06). Signaturen gegenüber 1.7.x sind ungeprüft.

### Umgebung

- Container: OpenJDK 21.0.12, Gradle unter `/opt/gradle`, Netzwerkzugang zu Modrinth funktioniert.
- Ein Dedicated Server lässt sich hier headless starten. Client-Join und Ingame-Tests muss der Owner machen.

## Offen

- Phase-0B-Spikes: Dimension, natürliche Spawns, Biome, Capture/Battle, Trainer, Hologramm.
- Danach: `ARCHITECTURE.md`, `docs/adr/`, `docs/POC_RESULTS.md`, `docs/RISK_REGISTER.md`, `docs/DEPENDENCY_MATRIX.md`.

## Blocker

Keine.

## Nächste Aktion

Spec-Dateien für 0B lesen (03, 04, 07 Hologramm, 08, 09, 11 Threading, 13), dann den ersten Spike aufsetzen: Void-Dimension + Cobblemon 1.8.2 auf einem headless Dedicated Server.

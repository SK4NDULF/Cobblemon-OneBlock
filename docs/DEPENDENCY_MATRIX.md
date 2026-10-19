# Abhängigkeitsmatrix

> Stand: 2026-10-19 (Phase 1). Gilt für `oneblock-core` (`gradle.properties`). Jede Änderung ist ein Change Request (Spec 03).

## Laufzeit (Server und Client)

| Artefakt | Version | Quelle | SHA-256 | Lizenz | Zweck | Verteilung | Status |
|---|---|---|---|---|---|---|---|
| Minecraft Server | 1.21.1 | Mojang (über Fabric-Installer) | `e3bc55693e93cda0188f2e60aea28113fc647c5e85a15fa3d1b347349231b4bb` | Minecraft EULA | Basis | Server | im Spike gebootet |
| Fabric Loader | 0.19.5 (veröffentlicht 2026-08-28) | maven.fabricmc.net | `93044e4dd46de5d8136701292f05e868da096d2c9fddb4793e4fdbcc63efc695` | Apache-2.0 | Mod-Loader | Server + Client | im Spike gebootet |
| Fabric Server Launcher | Loader 0.19.5, Installer 1.1.2 | meta.fabricmc.net | `809d99b16dd4d4b57384d128724ab0d6560f859aaba989dc6750d8437a447ed0` | Apache-2.0 | Startskript des Testservers | nur Test | – |
| Fabric API | 0.116.17+1.21.1 (veröffentlicht 2026-09-01) | maven.fabricmc.net | `79ac44b40780acbd884b34c50be1e39af682847e5f5cb3b1fddeeaa768dce800` | Apache-2.0 | Events, Commands, Lifecycle | Server + Client | im Spike gebootet |
| Cobblemon (Fabric) | 1.8.2+1.21.1 (veröffentlicht 2026-10-11) | Modrinth `GJ2a1b8O`; Maven `com.cobblemon:fabric` (impactdev) | `cae8ac328b61354fbe415208b8c6ffc12e7155094d0a87082e85e7914fb380c2` | MPL-2.0 | Pokémon | Server + Client | im Spike gebootet |
| Fabric Language Kotlin | 1.13.6+kotlin.2.2.20 | **Jar-in-Jar in Cobblemon 1.8.2** | Dev-Artefakt: `62c2773380fae0ce5cd01e6cfbdc34f6a2d42fd0734e5996e261bf7d8bf12ce3` | Apache-2.0 | Kotlin-Laufzeit für Cobblemon | kommt mit Cobblemon | im Spike geladen |

Cobblemon 1.8.2 deklariert in `fabric.mod.json`: `fabricloader >=0.17.2`, `fabric-api >=0.116.6+1.21.1`, `minecraft 1.21.1`, `java 21`. Weitere Jar-in-Jar-Inhalte von Cobblemon: GraalVM JS/Truffle/Regex/SDK 22.3.0, ICU4J 71.1, MongoDB-Driver 4.10.2 (nur Teil von Cobblemon, von uns nicht genutzt).

## Von uns gebündelt (Jar-in-Jar)

| Artefakt | Version | Quelle | SHA-256 | Lizenz | Zweck | Status |
|---|---|---|---|---|---|---|
| sqlite-jdbc | 3.53.4.0 (veröffentlicht 2026-08-26), SQLite 3.53.4 | Maven Central `org.xerial:sqlite-jdbc` | `bcb1f51e36f940867e83342f9efbf5968ac44a6bef4d397bb4af7b17b45cd2fb` | Apache-2.0 (SQLite selbst: Public Domain) | Persistenz pro Welt | Linux x64 verifiziert; Windows x64 / Linux ARM64 offen |

## Build und Test

| Artefakt | Version | Zweck |
|---|---|---|
| Gradle (Wrapper) | 8.14.3, `distributionSha256Sum` `bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531` gepinnt | Build |
| Fabric Loom | 1.11.8 | Minecraft-Mod-Build, Remapping |
| Mappings | Mojang official (1.21.1) | Namen im Quellcode |
| JDK | OpenJDK 21.0.12 | Kompilieren und Test |
| jqwik | 1.9.3 | Property-Based-Tests |
| JUnit Jupiter | 5.14.4 | Unit-Tests |
| Carpet | 1.4.147+v240613 (`fabric-carpet-1.21-1.4.147+v240613.jar`, SHA-256 `079fc8a4e073eb24b010ffcc588e59f93b984143e17d8ef19fbb0b6aff0f19d9`) | **nur Testserver:** Fake-Spieler. Nie Abhängigkeit des Mods. |

## Hinweise

- `fabric.mod.json` deklariert: `fabricloader >=0.17.2`, `minecraft 1.21.1`, `java >=21`, `fabric-api >=0.116.6+1.21.1`, `cobblemon 1.8.2` (exakt; Build-Metadaten wie `+1.21.1` zählen nicht).
- CI: GitHub Actions `ubuntu-24.04`, Temurin 21, `gradle/actions/setup-gradle@v4` mit Wrapper-Validierung.
- Clients brauchen keinen OneBlock-Clientmod, aber den zur Server-Version passenden Cobblemon-Client (Spec 03).
- Fabric-API-Untermodule werden einzeln im Loader geführt. Der Mod deklariert nur `fabric-api`, nicht einzelne Module.

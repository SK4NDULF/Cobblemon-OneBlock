# ADR 001: Build, Sprache und Mappings

- **Status:** Akzeptiert (Sprache: `DECISIONS` Nr. 13); Rest Vorschlag bis Phase-0B-Gate
- **Datum:** 2026-10-19

## Kontext

Spec 03 verlangt Gradle Wrapper, Fabric Loom, gepinnte Versionen und Java 21. Cobblemon 1.8.2 ist in Kotlin geschrieben und nutzt Mojang-Mappings mit Parchment.

## Entscheidung

- **Java 21** für den gesamten eigenen Code, kein Kotlin.
- **Gradle 8.14.3** (Wrapper) mit **Fabric Loom 1.11.8**.
- **Mojang-Mappings** (`loom.officialMojangMappings()`), ohne Parchment. Im Spike funktionierte das zusammen mit `modImplementation("com.cobblemon:fabric:1.8.2+1.21.1")` aus `maven.impactdev.net` problemlos.
- Cobblemon wird mit `transitive = false` eingebunden. Fabric Language Kotlin steht nur für die Entwicklungsumgebung explizit in den Abhängigkeiten. Im Release bringt Cobblemon es selbst mit.
- Versionen stehen in `gradle.properties` und in `docs/DEPENDENCY_MATRIX.md`. Keine `+`- oder `latest`-Versionen.

## Alternativen

- **Yarn-Mappings:** Cobblemon-Quellcode und -Stacktraces nutzen Mojang-Namen. Yarn würde jeden Abgleich erschweren.
- **Kotlin:** angenehmer Zugriff auf Cobblemon-APIs, widerspricht aber `DECISIONS` Nr. 13.
- **Neuere Loom-Versionen:** nicht geprüft. Loom 1.11.8 ist mit dem vorhandenen Gradle 8.14.3 verifiziert, ein Wechsel ist ein Change Request.

## Folgen

- Java-Code muss Kotlin-Eigenheiten berücksichtigen: Interfaces ohne `jvm-default` (alle Methoden implementieren, `DefaultImpls` nutzen), `object`-Zugriff über `INSTANCE`, Lambdas mit `kotlin.Unit`-Rückgabe, Mehrdeutigkeit bei `Observable.subscribe`. Das wird in einem kleinen `adapters.cobblemon`-Paket gekapselt.
- Ein Loom-Update ist ein Change Request (Spec 03).

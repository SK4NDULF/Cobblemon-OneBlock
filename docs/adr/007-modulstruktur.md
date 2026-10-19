# ADR 007: Modulstruktur

- **Status:** Vorschlag; Jar-in-Jar der API wird in Phase 1 verifiziert
- **Datum:** 2026-10-19

## Entscheidung

Gradle-Multiprojekt mit drei Modulen:

| Modul | Inhalt | Auslieferung |
|---|---|---|
| `oneblock-api` | Interfaces, Records, Events (Java, ohne Minecraft-Klassen, wo möglich) | als Jar-in-Jar im Core-JAR **und** als eigenes Maven-Artefakt für Addon-Entwickler |
| `oneblock-core` | gesamte Implementierung, Paketgrenzen nach Spec 13 | das eine installierbare Mod-JAR |
| `example-addon` | kompiliert nur gegen `oneblock-api` | eigenes JAR, nicht im Release-Bundle |

- `oneblock-api` und `example-addon` entstehen erst, wenn sie gebraucht werden (Phase 7, Spec 13 „keine Module auf Vorrat“). Bis dahin gibt es nur `oneblock-core`.
- Admins installieren genau ein JAR (Spec 01).

## Alternative

- Alles in einem Modul mit API-Paket: einfacher, aber `example-addon` könnte dann versehentlich interne Klassen nutzen. Spec 13 verlangt, dass es ohne Core-Quellcode kompiliert.

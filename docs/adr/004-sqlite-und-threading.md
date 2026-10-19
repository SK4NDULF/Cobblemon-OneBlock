# ADR 004: SQLite als Jar-in-Jar und DB-Executor

- **Status:** Vorschlag (verifiziert auf Linux x64, `docs/POC_RESULTS.md` §7)
- **Datum:** 2026-10-19

## Entscheidung

- `org.xerial:sqlite-jdbc` wird per Loom `include` gebündelt. Admins installieren nichts zusätzlich.
- Eine Datenbank pro Welt: `<world>/data/cobblemon_oneblock/oneblock.db`.
- PRAGMAs: `journal_mode=WAL`, `synchronous=FULL`, `foreign_keys=ON`, `busy_timeout` gesetzt. WAL nur auf lokalem Dateisystem (Doku-Pflicht, Spec 03/§37).
- Eine einzige Verbindung auf **einem** dedizierten DB-Thread (Single-Writer). Ergebnisse sind unveränderliche Records und gehen per `MinecraftServer.execute(…)` zurück auf den Server-Thread.
- Die Warteschlange des DB-Threads ist begrenzt. Bei Überlauf schlägt die Aktion sichtbar fehl (`PENDING` → Fehler), es gibt keine stille Verwerfung.

## Alternativen

- **Connection-Pool (HikariCP):** für SQLite unnötig, bringt Mehr-Writer-Probleme.
- **Externe SQLite-Installation:** widerspricht Drag-and-drop (Spec 01).

## Risiken

- Ein anderer Mod bündelt eine andere `sqlite-jdbc`-Version. Wie Fabric Loader die doppelte Jar-in-Jar-Mod `org_xerial_sqlite-jdbc` auflöst und ob unsere PRAGMAs mit der anderen Version laufen, ist ungetestet (Risiko R-05).
- Natives für Windows x64 und Linux ARM64 sind im JAR, aber noch nicht getestet (`DECISIONS` Nr. 11).

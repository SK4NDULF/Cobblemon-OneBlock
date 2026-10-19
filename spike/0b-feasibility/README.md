# Phase 0B feasibility spike

Throwaway code. **Never merge into production code.** Results are documented in `docs/POC_RESULTS.md` on `main`.

- `src/main/java/.../SpikeMod.java` – void dimension check, Cobblemon spawn logging, SQLite smoke test on a DB executor
- `HubFilterInfluence.java` – per-player `SpawningInfluence` vetoing spawn positions in a hub area
- `BattleSpike.java` – server-side wild/NPC battle start, capture events, aiming helper for fake players
- `HologramSpike.java` – packet-only text display
- `ShutdownProbe.java` – entity load/unload logging during shutdown; `-Doneblock.spike.recallOnStop=true` enables the recall mitigation
- `GridMath.java` + `src/test/.../GridMathProperties.java` – jqwik property tests for the slot grid
- `src/main/resources/data/cobblemon_oneblock/` – void dimension + dimension type
- `srv.sh` – headless test server helper (`SPIKE_SERVER_DIR` must point at a Fabric server directory)
- `spawnstats.py` – per-island breakdown of `SPIKE-SPAWN` log lines

Build: `./gradlew build` (Java 21). Test server: Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1, Cobblemon 1.8.2, Carpet 1.4.147 (test-only fake players).

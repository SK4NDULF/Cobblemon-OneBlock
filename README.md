# Cobblemon OneBlock

Server-side Fabric mod for Minecraft 1.21.1 and Cobblemon 1.8.2: a OneBlock game mode in its own void dimension,
stored per world in SQLite. Players only need the matching Cobblemon client.

**Status:** in development, not playable yet. Phase 1 (foundation: config, storage, lifecycle) is implemented;
gameplay starts with phase 2. See `docs/PROGRESS.md`.

## Build

```
./gradlew build
```

Requires JDK 21. The mod jar is `oneblock-core/build/libs/cobblemon-oneblock-<version>.jar`.

## Documentation

| Document | Content |
|---|---|
| `CLAUDE.md`, `docs/DECISIONS.md`, `docs/spec/` | Rules, owner decisions, specification (German) |
| `ARCHITECTURE.md`, `docs/adr/` | Architecture and decisions |
| `docs/CONFIG.md`, `docs/COMMANDS.md`, `docs/RECOVERY.md` | Admin documentation |
| `docs/TEST_MATRIX.md`, `docs/POC_RESULTS.md` | Tests and verified APIs |

License: MIT (see `LICENSE`).

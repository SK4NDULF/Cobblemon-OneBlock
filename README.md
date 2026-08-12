# Cobblemon OneBlock

A standalone **core mod** for Fabric 1.21.1: OneBlock skyblock gameplay, fully integrated
with [Cobblemon](https://cobblemon.com/) — islands, progression, parties, permissions,
trigger events, a biome editor and a setup wizard in a single drag-and-drop server mod.

> Working title — the final project name is still open.

## Installation

Drop four jars into your server's `mods/` folder:

| Mod | Version |
|---|---|
| **`oneblock-core`** | this mod — API and all libraries are bundled inside |
| Fabric API | `0.116.15+1.21.1` |
| Fabric Language Kotlin | `1.13.7+kotlin.2.2.21` or newer |
| Cobblemon | `1.7.3+1.21.1` |

Minecraft **1.21.1** with Fabric Loader **0.19.3+**. No database setup required — storage
defaults to an embedded SQLite file.

Start the server, join as an operator and run `/ob setup`. That's it.

## Modules

| Module | Language | Purpose |
|---|---|---|
| `oneblock-api` | Java | Public API for addon developers (events, managers, extension points). Addons depend on this module only. |
| `oneblock-core` | Kotlin | Full implementation. Hard dependency on Cobblemon. |
| `example-addon` | Java | Reference addon for third-party developers. Builds against `oneblock-api` only. |

## Documentation

| Document | For |
|---|---|
| [`HANDOFF.md`](HANDOFF.md) | Current status, what is verified, what still needs in-game testing |
| [`ADMIN.md`](ADMIN.md) | Server admins — installation, wizard, config files, permission nodes, backups, MySQL |
| [`API.md`](API.md) | Addon developers — events, extension points, semver policy |
| [`PROJECT_PLAN.md`](PROJECT_PLAN.md) | Design decisions and roadmap (German) |
| [`example-addon/`](example-addon) | A working reference addon |

## Language

All player-facing messages default to **English**. Server owners can switch to German by
setting `"language": "de_de"` in `config/oneblock/main.json5` followed by `/ob reload`.
Additional languages only need another file in `assets/oneblock/lang/`.

## Build

```bash
./gradlew build
```

Requires Java 21. The server jar is `oneblock-core/build/libs/oneblock-core-<version>.jar`
(take the plain jar, not `-sources.jar`).

## Status

All planned phases are implemented and build-verified. See [`PROJECT_PLAN.md`](PROJECT_PLAN.md)
for the full scope and the design decisions behind it.

## License

MIT — see [`LICENSE`](LICENSE).

# Worklog — Cobblemon OneBlock

Running record of what was changed, why, and how far it was actually verified.
`HANDOFF.md` is the **snapshot** (where the project stands right now); this file is the
**history** (how it got there, and what was decided along the way).

**A new session should read `HANDOFF.md` first, then the newest entry here.**

## How to keep this file

- Newest entry at the top.
- One entry per change, not per commit.
- Every entry answers four things: **what was asked**, **what was built**, **how it was
  verified**, **what is still unverified**. The fourth is the one that must never be left out.
- "Compiles" is not verification. Say what was actually run and what the output was.

---

## Working mode agreed for this stretch

- The user has run the mod locally and is satisfied with the basics. Bug testing with
  several players happens **later, live**. Until then the goal is to widen and harden the
  foundation until it is genuinely playable.
- Features are worked **one at a time**, discussed before and reported after.
- German conversation, English code/comments/commits/docs (see `HANDOFF.md` §7).
- The user wants a sparring partner: flag what does not make sense, propose better options,
  and say plainly when something was not verified.

---

## 2026-08-13 — Treasure chests from the OneBlock

**Asked:** breaking the OneBlock should sometimes produce loot chests filled from
Minecraft's vanilla chest loot tables — mineshaft chests and the like.

**Built:**

- New `core.island.ChestLoot`. A configurable share of breaks regenerates the anchor as a
  chest instead of a plain block, carrying a real chest loot table plus a seed. Vanilla
  fills it on first open — or drops the contents if a player breaks it unopened.
- The pool is **discovered from the server's loot table registry**, not hardcoded: every
  loot table whose path starts with `chests/`. A data pack that adds one is picked up with
  no code change. Vanilla 1.21.1 alone gives 56 tables.
- Config section `chests` in `loottable.json5`: `enabled` (true), `chance` (0.02),
  `include_modded` (false), `blacklist` (empty), `extra` (empty). Rebuilt by `/ob reload`.
- Placement order in `IslandManagerImpl.handleBreak`: **addon loot providers → chest roll →
  block pool**. A provider that answers suppresses the chest for that break, which keeps the
  contract `API.md` already promised. Documented there.
- Chest is forced to `ChestType.SINGLE` so it can never merge into a double chest with one
  a player parked next to the anchor. Facing is randomised.
- `chance` is clamped to 0.0–1.0 — a typo cannot turn every block into a chest.

**Decisions worth knowing:**

- **The chance is flat and never scales with border level.** Difficulty scales on this
  project, rewards do not (`PROJECT_PLAN.md`). A chest is a reward.
- **`include_modded` defaults to false.** Modded structure loot can sit far outside vanilla
  balance. The startup log reports how many tables were skipped so the option is discoverable.
- **`extra` exists because the `chests/...` convention is Minecraft's alone.** Cobblemon
  ignores it: its structure chest loot lives under `cobblemon:ruins/gilded_chests/...` and
  `cobblemon:shipwreck_coves/...`, so no namespace filter could ever find it. `extra` adds
  ids verbatim.
- **Cobblemon loot needs no configuration.** Cobblemon *injects* into the vanilla chest
  tables (`data/cobblemon/loot_table/injection/chests/*`), so the normal pool already hands
  out Cobblemon content. Those injection files are fragments, not standalone tables, which
  is why the prefix filter must stay strict rather than matching `chests/` anywhere in a path.
- The **first** block of a new island still comes from the block pool, never a chest.

**Verified on a real dev server (headless, FIFO console):**

| Check | Result |
|---|---|
| Pool discovery against the real registry | `56 loot tables, 2.0% of breaks` |
| `chance: 5.0` clamped | logged `100.0% of breaks` |
| `blacklist` with 2 entries | 56 → 54 |
| `extra` with 2 real Cobblemon tables | 56 → 58 |
| `extra` with a non-existent id | warned by name, skipped, pool intact |
| `extra` with unparseable text | warned by name, skipped, pool intact |
| `/ob reload` rebuilds the pool | yes, every time above went through reload |
| Cobblemon chest tables in the jar | 28, all under `injection/` or `*_chests/` — correctly not matched |

**🟡 Not verified — needs a client:** that a break actually *produces* the chest, that its
contents are right, and that breaking it regenerates the anchor normally. The break path
runs on `PlayerBlockBreakEvents.AFTER` and cannot be triggered from the server console, so
no headless test can reach it. Everything up to and including the loot table selection is
verified; the placement and fill calls are compiled against APIs checked with `javap`
against the actual 1.21.1 jar, but never run.

To test in-game: set `chests.chance` to `1.0`, `/ob reload`, break the OneBlock. Every
break should now be a chest with structure loot in it.

---

## 2026-08-13 — Final rename to `cobblemon_oneblock`

**Asked:** name the mod's id `CobblemonOneBlock:world`.

**Flagged and corrected before building:** Minecraft resource locations are lowercase-only
(`[a-z0-9_.-]`); `CobblemonOneBlock:world` would throw on registration and kill the server.
The user picked `cobblemon_oneblock` as the legal form, and a full-depth rename over the
player-facing-only option.

**Built:** one cut across all nine places the old name lived.

| | Before | After |
|---|---|---|
| Dimension | `oneblock:world` | `cobblemon_oneblock:world` |
| Mod ids | `oneblock`, `oneblock_api` | `cobblemon_oneblock`, `cobblemon_oneblock_api` |
| Permissions | `oneblock.command.create` | `cobblemon_oneblock.command.create` |
| Config dir | `config/oneblock/` | `config/cobblemon_oneblock/` |
| Lang / data | `assets|data/oneblock/` | `assets|data/cobblemon_oneblock/` |
| Mixin config | `oneblock.mixins.json` | `cobblemon_oneblock.mixins.json` |
| Java package | `io.github.sk4ndulf.oneblock` | `io.github.sk4ndulf.cobblemon.oneblock` |
| Maven group | `io.github.sk4ndulf.oneblock` | `io.github.sk4ndulf.cobblemon.oneblock` |
| Gradle modules | `oneblock-core`, `oneblock-api` | `cobblemon-oneblock-core`, `cobblemon-oneblock-api` |

The display name stays "Cobblemon OneBlock" and the command stays `/ob`. The Java package
separates with a dot rather than an underscore: underscores in package names are legal but
unidiomatic.

**Verified:** `./gradlew build` green on all three modules; dev server boots; both mods load
under the new ids; all four trigger event types register as `cobblemon_oneblock:*`; all 8
migrations run; hub platform and the 848-block pool build;
`execute in cobblemon_oneblock:world run time query daytime` answers, proving the dimension
is really registered and not just declared in JSON; `config/cobblemon_oneblock/` and
`world/dimensions/cobblemon_oneblock/world/` appear on disk; `ob setup` prints translated
English, proving the lang files resolve from the new namespace.

**Consequence:** any world created before this change is orphaned — the old dimension and
config directory are no longer read. Acceptable pre-release, and it was the last moment it
would be.

**Note on an unrelated log line:** the dev server logs a `JsonSyntaxException` from Mojang's
yggdrasil key fetch. That is the sandbox blocking `api.minecraftservices.com`, not a mod
problem. Ignore it in this environment.

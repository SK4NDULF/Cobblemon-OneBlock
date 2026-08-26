# Handoff — Cobblemon OneBlock

Status snapshot for the next Claude Code session. **Read this first**, then the newest entry
in `WORKLOG.md` for how we got here, then `PROJECT_PLAN.md` for the design decisions behind
everything.

This file is the snapshot; `WORKLOG.md` is the running history. Keep both current.

**Active work:** `PROGRESSION_REWORK.md` — the progression system is being redesigned as a
six-category tech tree. It carries the owner's full design, the review of it, the node and
storage model, the point economy, verified Cobblemon 1.7.3 hooks, the open decisions and an
eight-slice build plan. **Read it before touching progression, loot or Cobblemon code.**

**Slices A, B, C and D are merged into `main`** — tree core, OneBlock biome ladders, real point
sources, chest menus. That is the whole minimum-playable set from the build plan. What remains
is additive: E (Island/Players payloads), F (Boosts), G (Special/bosses), H (Pokémon labour),
and access to the End half. Nothing gets coded from a decision still marked ⬜; D3 and D5 block
H and E.

**From here: one feature per branch, per PR.** See §1.

⚠️ **Nothing from any of these four slices has been played.** Everything is build- and
boot-verified, and the pieces that can be checked without a client have been; the rest is
waiting on the owner's live test. `HANDOFF.md` §4 tests 10-13 are the script.

⚠️ **The chest menus have never been opened by a real client.** They compile and their
*contents* are covered by the tier report in the startup log, but every pixel of them is
unverified — see the test script in §4.

⚠️ **The tree costs 439 points; advancements cover 135 of them.** NPC trainers and bosses have
to make up the remaining 304 — the startup log prints that arithmetic every boot. At 1-5 points
per NPC that is a lot of content, and it is the first thing to look at when tuning.

⚠️ **Portals must never reach a vanilla dimension.** A nether portal's vanilla destination
from our world is the real, infinite, unprotected Nether, and from there the real Overworld —
every border and protection rule stops mattering the moment a player steps through, and the
tech tree hands out the obsidian at Nether tier 4. `IslandPortals` redirects them to the
island's own halves instead. **It never returns null for a portal inside our dimensions**,
because null means "vanilla decides"; a portal with nowhere legitimate to go sends the player
to the hub. Keep that property if you touch it, and read §4 test 14 first.

---

## 1. Where we are

All 12 planned phases were implemented, build-verified and boot-verified on a real
Fabric server with Cobblemon installed. Since then one review pass fixed a
multiplayer crash and five robustness issues, three features were added on request,
the project was renamed to its final identifiers, treasure chests and inventory drops
were added — and **Phase 7, the trigger event system, was removed completely** because the
owner did not like it. A replacement is to be designed from scratch; nothing of the old one
survives in code, config, language files or the public API.

The user has since run the mod locally and reports it looks fine. Deliberate plan for
the 🟡 list below: it will be bug-tested live with several players later. Until then the
goal is to widen and harden the foundation until it is genuinely playable.

- **`main` is the trunk.** PR #1 merged on 2026-08-26 and carried the whole foundation:
  the core mod plus tech tree slices A-D. The long-lived-single-PR phase is over.
- **One feature at a time, each on its own branch and PR.** The owner's instruction on merging:
  build features one after another so a problem is always traceable to one change, rather than
  landing four slices at once and guessing which one broke something. Do not stack unrelated
  work in a branch, and do not start a second feature before the first is merged.
- **Every branch starts from `main`.** Fetch first; `main` moves now.
- **Working tree:** clean, everything pushed.

### The rename (done, verified)

Every identifier moved from `oneblock` to `cobblemon_oneblock` in one cut:

| | Before | After |
|---|---|---|
| Dimension | `oneblock:world` | `cobblemon_oneblock:world` |
| Mod ids | `oneblock`, `oneblock_api` | `cobblemon_oneblock`, `cobblemon_oneblock_api` |
| Permissions | `oneblock.command.create` | `cobblemon_oneblock.command.create` |
| Config dir | `config/oneblock/` | `config/cobblemon_oneblock/` |
| Lang / data | `assets/oneblock/`, `data/oneblock/` | `assets/cobblemon_oneblock/`, `data/cobblemon_oneblock/` |
| Mixin config | `oneblock.mixins.json` | `cobblemon_oneblock.mixins.json` |
| Java package | `io.github.sk4ndulf.oneblock` | `io.github.sk4ndulf.cobblemon.oneblock` |
| Gradle modules | `oneblock-core`, `oneblock-api` | `cobblemon-oneblock-core`, `cobblemon-oneblock-api` |

Minecraft resource locations are lowercase-only, so the literal `CobblemonOneBlock:world`
the user first asked for is not a legal id — `cobblemon_oneblock` is its legal form.
The Java package uses dots (`cobblemon.oneblock`) rather than an underscore because
underscores in package names are legal but unidiomatic.

Verified after the rename: `./gradlew build` green on all three modules; dev server boots;
both mods load under the new ids; all 8 migrations run; hub platform generates; 848-block loot pool
builds; `execute in cobblemon_oneblock:world run time query daytime` answers, so the
dimension is really registered; `config/cobblemon_oneblock/` and
`world/dimensions/cobblemon_oneblock/world/` are created on disk; `ob setup` prints
translated English, so the lang files load from the new namespace.

**Any world created before this commit is orphaned** — the old `oneblock:world` dimension
and `config/oneblock/` are no longer read. That is fine pre-release, but it is the last
moment it is fine.

### The PR #1 description (rewritten 2026-08-13, was wrong before)

The original PR text was auto-generated at the first commit and had drifted badly: it
described the trigger event system that was later removed in full, the pre-rename module and
dimension ids, and two Cobblemon features that were never built as claimed ("catch rate
modifications" — there is a catch *permission*, not a rate change; "spawn rate buffs" — the
buffs are shiny rate and IV floor). It has been rewritten to match what exists.

Keep it current when behaviour changes. It is the one page a reviewer reads, and this project
uses a single long-lived PR, so a stale description stays wrong for a long time.

---

## 2. Build, run, ship

```bash
./gradlew build                      # all three modules, and the unit tests
./gradlew :cobblemon-oneblock-core:test        # tests alone, ~30 s
./gradlew :cobblemon-oneblock-core:runServer   # dev server, needs cobblemon-oneblock-core/run/eula.txt
```

**Tests exist now — use them.** 47 of them, covering the pure logic: gate parsing, tree
validation, rank arithmetic, name sanitising, effect rendering and language-file consistency.
They run in seconds and replace most of what previously needed a two-minute server boot. Boot
the server for anything touching a world, a registry or a packet; use tests for everything
else. GitHub Actions runs both on every push (`.github/workflows/build.yml`).

Java 21. Server jar: `cobblemon-oneblock-core/build/libs/cobblemon-oneblock-core-0.1.0.jar` (the plain one —
API and all libraries are bundled jar-in-jar).

| Dependency | Pinned version |
|---|---|
| Minecraft | `1.21.1` |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.116.15+1.21.1` |
| Kotlin / fabric-language-kotlin | `2.2.21` / `1.13.7+kotlin.2.2.21` |
| Cobblemon | `1.7.3+1.21.1` |
| Gradle / Loom | `9.5.1` / `1.17.19` (Loom 1.17 requires Gradle ≥ 9.5) |

**Network policy note:** `maven.fabricmc.net`, `meta.fabricmc.net`, `maven.impactdev.net`
and the Mojang hosts must be allowlisted in the cloud environment or the build cannot
resolve dependencies. `maven.nucleoid.xyz` and `jitpack.io` are **blocked** — that is why
LuckPerms support is reflective instead of using `fabric-permissions-api`.

### Headless testing recipe that works

Console commands can be injected through a FIFO, which is how the wizard and every
gameplay path were verified without a client:

```bash
mkfifo /tmp/console.fifo
sleep 600 > /tmp/console.fifo &          # keeps the FIFO open
./gradlew :cobblemon-oneblock-core:runServer < /tmp/console.fifo > /tmp/server.log 2>&1 &
# wait for "Done (" in the log, then:
echo "ob setup" > /tmp/console.fifo
```

Gotchas learned the hard way: chunks must be force-loaded before spawning entities far
from spawn (`forceload add`), and `pkill -f runServer` matches its own command line —
use `pgrep -f "runSer[v]er" | xargs -r kill`.

---

## 3. Architecture in one screen

Three Gradle modules:

| Module | Language | Notes |
|---|---|---|
| `cobblemon-oneblock-api` | Java 21 | Public API. No Cobblemon dependency. Published to GitHub Packages / JitPack. |
| `cobblemon-oneblock-core` | Kotlin | Implementation. Hard dependency on Cobblemon. |
| `example-addon` | Java | Reference addon, depends on `cobblemon-oneblock-api` **only**. Built by the root build so API breakage fails CI. |

World layout: **three** void dimensions — `cobblemon_oneblock:world`, `:nether` and `:end`
(data-driven, flat generator, zero layers). An island occupies **the same X/Z in all three**,
so a portal is a straight move between worlds; the dimension types therefore use
`coordinate_scale: 1.0`, because vanilla's 8:1 division would land a player in a neighbour's
plot. Hub platform at `(0, 64, 0)` in the Overworld one.

**When reading dimension checks, note which question is being asked.** `OneBlockDimension.isOurs`
means "any of our three" and is what protection, bans, fluid containment and Pokémon
containment use — a rule that only covered the Overworld would leave the Nether island lawless.
`OVERWORLD_KEY` means the main world specifically, and is right for the hub, the OneBlock
anchor, drops and respawn. Every call site was classified deliberately when the Nether landed. Islands sit on an Ulam spiral around it; slot 0 is
the hub and never assigned. Anchor Y is 63, players spawn at 64. The bedrock block at 62 is
optional and off by default (`anchor_bedrock_foundation`).

`GridMath` is the single source of truth for all grid geometry. `island_spacing =
chunkAlign(max_island_size) + 1024`, everything aligned to 16 so chunk-based permission
checks never split a chunk.

Storage: SQLite by default (zero config), MySQL/MariaDB opt-in, one SQL layer with two
dialects, 8 versioned migrations applied at startup:

```
1 players · 2 meta · 3 islands · 4 island_members
5 islands.points · 6 buffs + visitor perms · 7 biome regions · 8 island bans
```

### Mixins — read before touching

`cobblemon_oneblock.mixins.json` declares package `io.github.sk4ndulf.cobblemon.oneblock.core.mixin`.
**Only mixin classes may live there.** A Kotlin helper in that package gets treated as a
mixin by the transformer and fails to load — that crashed the server on every flowing
fluid and was only caught by the end-to-end test. The helper logic lives in
`core.hooks.WorldHooks` for exactly this reason.

Three mixins: `FallingBlockMixin` (gravity blocks never fall off the anchor),
`ExplosionMixin` (blast containment + anchor is immune), `FlowingFluidMixin` (fluids
cannot cross a border, enter the hub, or leak into the void buffer).

---

## 4. Feature status

### Verified working (headless)

- Server start, all 8 migrations, dimension + hub platform generation
- 848-block loot pool built from every installed mod's registry
- Full setup wizard run from the console, values persisted, `/ob reload` applying them
- Full `/ob` command tree registers (17 branches)
- Cobblemon integration active; **Pokémon containment tested with a real entity**
  (spawned a Pikachu in the void buffer, gone after the sweep)
- Explosion and fluid mixins apply without error; TNT tested in both dimensions
- The example addon registers a loot provider through the public API at runtime —
  proof the two-module split works
- Repair timer stable over 2 cycles
- Treasure chest pool: 56 vanilla chest loot tables discovered from the live registry;
  `chance` clamping, `blacklist`, `extra` (including both failure modes) and `/ob reload`
  all exercised — see `WORKLOG.md`

### 🟡 Needs a human in-game

1. **Wizard chat buttons** — do `[Yes]`/`[No]`/`[Save]` respond to clicks? (The command
   path `/ob setup answer …` is verified.)
2. **Party flow** — needs two accounts: invite, click `[Accept]`, can the member build?
3. **Catch/battle denial** — throw a ball on a foreign island; then
   `/ob settings visitor-catch true` and retry.
4. **Biome editor** — `pos1`/`pos2`/`set`; does the colour change **without** reconnecting?
5. **Self-healing anchor** — after `/ob create`, run `/setblock <x> 63 <z> air` on the
   anchor; within 60 s the log should say `Restored the missing OneBlock of island …`.
   (The bedrock half of this moved to test 9 — it is off by default now.)
6. **`/ob visit <player>`** — teleport, ban enforcement, and that dying as a visitor
   still respawns you at your **own** island.
7. **Treasure chests** — set `chests.chance` to `1.0` in `loottable.json5`, `/ob reload`,
   then break the OneBlock. Every break should become a chest holding structure loot, and
   breaking that chest should regenerate the anchor as normal. The break path runs on
   `PlayerBlockBreakEvents.AFTER` and cannot be reached from the server console, so no
   headless test covers it.
8. **Drops to inventory** — break the OneBlock: items and experience should land in your
   inventory with the pickup sound, nothing should fall. Then fill your inventory
   completely and break again: the remainder must drop at your feet, not into the void.
   Break a treasure chest without opening it — its contents should arrive too. Check that
   Fortune and Silk Touch still behave.
9. **Anchor foundation toggle** — with the default `anchor_bedrock_foundation: false` there
   must be nothing under the OneBlock. Set it to `true`, `/ob reload`, walk away and back
   (or wait 60 s for the repair sweep): bedrock appears. Set it back to `false`: it goes
   away again. Place your own block under the anchor first and confirm it survives both.

10. **The tech menus** — `/ob create`, then `/ob tech grant <you> 60`, then `/ob tech`.
    - Does the 3-row main menu open, with the island summary and the category items?
    - Does clicking a category open its 6-row menu, with row 1 = tier 0 and closed tiers as
      red panes saying "N more points in this branch"?
    - Does clicking a green node buy it — rank number on the item goes up, points drop, the
      island gets a chat message?
    - Does a newly satisfied tier open without closing and reopening the menu?
    - **Can you take anything out?** Try left-click, right-click, shift-click, double-click,
      number keys, and drag across slots. Nothing may ever end up in your inventory, and
      nothing may vanish from the menu. This is the one that matters — a leak here duplicates
      items.
    - `/ob tech chat` still prints the text version.

14. **Portals go to your own island, never to vanilla.** Get obsidian, build a portal on your
    island and walk through. You must arrive in `cobblemon_oneblock:nether` at **the same X/Z**
    you left, on a small netherrack platform. Walk back through and you must land on your own
    island again. Then the adversarial half: build a portal in the hub or in the void buffer
    between islands — you must end up at the hub, and **never** in `minecraft:the_nether`.
    If `/execute in minecraft:the_nether` ever finds you, every border in the mod is void.

13. **Point sources.** Two halves, and the NPC half is the one nobody has ever run.
    - Place a Cobblemon NPC trainer, then
      `/npc edit <npc> variable trainer_id gym_test` and `/npc edit <npc> variable points 3`.
      Beat it: the whole island should get a chat message and 3 points.
    - Beat it again with the **same** player and with a **second member of the same island** —
      both must be told it is already claimed, and the balance must not move.
    - Beat it with someone from a **different** island: that island claims it normally.
    - Rebuild the NPC with the same `trainer_id` and confirm the island still cannot re-claim.
    - An NPC with no `trainer_id` grants nothing and logs once. Check the log line appears once
      and not on every battle.
    - Advancements: complete one from `points.json5` while on an island — points arrive.
      Note the documented limit: an advancement finished *before* you had an island never pays.

12. **The biome ladders** — the slice that changes the game, and the one that most needs eyes.
    - Fresh island, break the block twenty times. You should see **only** grass, dirt,
      cobblestone and oak planks. If anything else appears, the base set is leaking.
    - `/ob tech grant <you> 20`, buy Forest rank 1, break again: oak logs and leaves start
      appearing, and grass/dirt/cobble/planks are still there. The base never goes away.
    - Buy Forest up to rank 3 and confirm birch, spruce, jungle and acacia join in.
    - Buy Cave (needs 5 spent in OneBlock) and confirm stone and ores appear.
    - Buy Forestcutter and break forest blocks until a double drop happens — both stacks must
      land in the inventory, and with a full inventory the bonus must drop at your feet, not
      into the void. Break a *cave* block: it must never double from a forest node.
    - Break a treasure chest: it must never double either.
    - Two islands at different tiers must see different blocks at the same time.

11. **Island names** — `/ob rename My Base`, then check `/ob info`, the tech menu title and
    what a visitor sees. Restart and confirm it survived. Then the adversarial half, which is
    the point of `IslandNames`: try a name containing `§c`, a 40-character name, only spaces,
    and a right-to-left override. None may recolour or scramble anything, and none may be
    stored as given. `/ob rename clear` must fall back to the owner's name everywhere.

Tests 7–10 all need a client for the same reason: nothing that breaks a block, creates an
island or opens a container can be driven from the server console.

---

## 5. Decisions that look like gaps but are not

Do not "fix" these without asking — each was a deliberate call, recorded in
`PROJECT_PLAN.md`:

- **Spawn rate is not a per-island buff.** Cobblemon's spawner is player-centric and
  globally paced; a per-island boost could only be faked. Shiny rate and IV floor apply
  cleanly at spawn time. Global pacing comes from the wizard multiplier.
- **Rewards never scale with difficulty.** Border level raises difficulty only. This is a
  project rule; the treasure chest chance is flat for the same reason.
- **Grid slots are not reused after a purge.** The old builds are still standing there.
  Reuse needs chunk clearing first. The spiral is effectively endless, so nothing breaks.
- **`max_island_size` is locked once islands exist.** Changing it moves every anchor.
  Enforced in the wizard and in `/ob setup set`.
- **Polymer content is excluded from the loot pool** (`exclude_polymer`, default true).
  It only exists server-side; vanilla clients see a stand-in block.

---

## 6. Open items

**Settled:**

- **Final project name.** Decided: display name "Cobblemon OneBlock", namespace
  `cobblemon_oneblock`. Done and verified — see section 1. Treat the ids as frozen from here on.
- **Where work lands.** Decided by the owner: this project uses **only PR #1**, never a second
  one. Its head branch is `claude/cobblemon-oneblock-mod-49flzn`. See section 1.
- **The PR #1 description.** Rewritten to match reality; keep it current from here on.

**Still open:**

- **The tech tree costs 439 points and nothing pays them out yet.** Slice C wires up the real
  sources. 439 is also a balance signal: at 1-5 points per NPC that is a lot of content.

**Suggested, not built:**

- Slot reuse + chunk clearing on purge (world grows outward forever otherwise).
- `audit.log` has no rotation; it grows slowly and forever.
- `/ob info` shows raw point numbers; a progress bar would read better.
- A per-island "private" toggle. Bans cover the practical need today, which is why it was
  skipped — it would need another DB column.

**Docs to keep in sync when changing behaviour:** `WORKLOG.md` (one entry per change —
always), `ADMIN.md` (permission nodes, config, precedence), `API.md` (events, extension
points, semver), `PROJECT_PLAN.md` (decisions), `README.md` (install table).

---

## 7. Working agreement with this user

- German conversation, English code, comments, commits and docs. In-game default language
  is `en_us`; `de_de` ships alongside it.
- The user wants a **sparring partner**, not an order taker: flag what does not make sense,
  propose better options, say plainly when something was not verified.
- Work slowly and verify. Every phase ended with a real build and a real server boot.
- Never claim something works because it compiles. Say "compiles, not played" when that is
  the truth — the whole 🟡 list above exists for that reason.

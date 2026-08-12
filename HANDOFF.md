# Handoff — Cobblemon OneBlock

Status snapshot for the next Claude Code session. **Read this first**, then
`PROJECT_PLAN.md` for the design decisions behind everything.

---

## 1. Where we are

All 12 planned phases are implemented, build-verified and boot-verified on a real
Fabric server with Cobblemon installed. Since then one review pass fixed a
multiplayer crash and five robustness issues, and three features were added on
request.

**The mod has never been played by a human.** Everything below marked 🟡 compiles
and links against verified APIs but has not been exercised in-game. That is the
single most valuable thing the next session can help with.

- **Branch:** `claude/cobblemon-oneblock-mod-49flzn`
- **PR:** [#1](https://github.com/SK4NDULF/Cobblemon-OneBlock/pull/1) → `main`, open, mergeable.
  Pushing to the branch updates it; do **not** open a second PR.
- **Working tree:** clean, everything pushed.
- **Latest commit:** `73b8079` OneBlock safety: bedrock foundation + self-healing anchor

### Two known inaccuracies in the PR #1 description

The PR text was auto-generated and describes two things that are not what was built.
Correct them if you touch the PR body:

- "Catch rate modifications based on island progression" — wrong. There is a catch
  *permission* (visitors cannot catch or battle on foreign islands, owner can allow it).
  Catch rates are never modified.
- "Pokémon spawn rate buffs" — wrong. The buffs are **shiny rate** and **IV floor**.
  Spawn rate is deliberately not a per-island buff (see section 5).

---

## 2. Build, run, ship

```bash
./gradlew build                      # all three modules
./gradlew :oneblock-core:runServer   # dev server, needs oneblock-core/run/eula.txt
```

Java 21. Server jar: `oneblock-core/build/libs/oneblock-core-0.1.0.jar` (the plain one —
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
./gradlew :oneblock-core:runServer < /tmp/console.fifo > /tmp/server.log 2>&1 &
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
| `oneblock-api` | Java 21 | Public API. No Cobblemon dependency. Published to GitHub Packages / JitPack. |
| `oneblock-core` | Kotlin | Implementation. Hard dependency on Cobblemon. |
| `example-addon` | Java | Reference addon, depends on `oneblock-api` **only**. Built by the root build so API breakage fails CI. |

World layout: one void dimension `oneblock:world` (data-driven, flat generator, zero
layers). Hub platform at `(0, 64, 0)`. Islands sit on an Ulam spiral around it; slot 0 is
the hub and never assigned. Anchor Y is 63, bedrock foundation at 62, players spawn at 64.

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

`oneblock.mixins.json` declares package `io.github.sk4ndulf.oneblock.core.mixin`.
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
- The example addon registers a loot provider and a trigger event type through the
  public API at runtime — proof the two-module split works
- Trigger event ticker stable over ~860 ticks; repair timer stable over 2 cycles

### 🟡 Needs a human in-game

1. **Wizard chat buttons** — do `[Yes]`/`[No]`/`[Save]` respond to clicks? (The command
   path `/ob setup answer …` is verified.)
2. **Party flow** — needs two accounts: invite, click `[Accept]`, can the member build?
3. **Catch/battle denial** — throw a ball on a foreign island; then
   `/ob settings visitor-catch true` and retry.
4. **Biome editor** — `pos1`/`pos2`/`set`; does the colour change **without** reconnecting?
5. **Trigger events** — lower the threshold (`/ob setup set trigger_event_threshold 10`),
   mine, watch for countdown, boss bar, legendary encounter.
6. **Bedrock + self-healing** — after `/ob create`, check bedrock at anchor Y−1, then
   `/setblock <x> 63 <z> air` on the anchor; within 60 s the log should say
   `Restored the missing OneBlock of island …`.
7. **`/ob visit <player>`** — teleport, ban enforcement, and that dying as a visitor
   still respawns you at your **own** island.

---

## 5. Decisions that look like gaps but are not

Do not "fix" these without asking — each was a deliberate call, recorded in
`PROJECT_PLAN.md`:

- **Spawn rate is not a per-island buff.** Cobblemon's spawner is player-centric and
  globally paced; a per-island boost could only be faked. Shiny rate and IV floor apply
  cleanly at spawn time. Global pacing comes from the wizard multiplier.
- **No "reward provider" extension point.** Each trigger event grants its own rewards, so
  a separate interface would be an empty abstraction.
- **Rewards never scale with difficulty.** Border level raises difficulty only. This is a
  project rule and is documented in `API.md` for addon authors.
- **Grid slots are not reused after a purge.** The old builds are still standing there.
  Reuse needs chunk clearing first. The spiral is effectively endless, so nothing breaks.
- **`max_island_size` is locked once islands exist.** Changing it moves every anchor.
  Enforced in the wizard and in `/ob setup set`.
- **Polymer content is excluded from the loot pool** (`exclude_polymer`, default true).
  It only exists server-side; vanilla clients see a stand-in block.

---

## 6. Open items

**Still undecided by the user:**

- **Final project name.** Mod id is `oneblock` / `oneblock_api`, dimension is
  `oneblock:world`. Renaming is cheap now and expensive later — the dimension id is
  written into every saved world. The user rejected `cobblemon:one_block` (that namespace
  belongs to Cobblemon); `CobbleBlock` was suggested and not answered.

**Suggested, not built:**

- Slot reuse + chunk clearing on purge (world grows outward forever otherwise).
- `audit.log` has no rotation; it grows slowly and forever.
- `/ob info` shows raw point numbers; a progress bar would read better.
- A per-island "private" toggle. Bans cover the practical need today, which is why it was
  skipped — it would need another DB column.

**Docs to keep in sync when changing behaviour:** `ADMIN.md` (permission nodes, config,
precedence), `API.md` (events, extension points, semver), `PROJECT_PLAN.md` (decisions),
`README.md` (install table).

---

## 7. Working agreement with this user

- German conversation, English code, comments, commits and docs. In-game default language
  is `en_us`; `de_de` ships alongside it.
- The user wants a **sparring partner**, not an order taker: flag what does not make sense,
  propose better options, say plainly when something was not verified.
- Work slowly and verify. Every phase ended with a real build and a real server boot.
- Never claim something works because it compiles. Say "compiles, not played" when that is
  the truth — the whole 🟡 list above exists for that reason.

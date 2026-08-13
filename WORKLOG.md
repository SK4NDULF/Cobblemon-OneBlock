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

## 2026-08-13 — The tech chest menus

**Asked:** "wichtig ist das wir mehrere kisten guis nutzen das skilltree hauptmenu und diese
splitten sich in die jeweiligen themen dann" — several menus, not one menu with tabs.

**What was built** (slice D, taken out of order ahead of B and C because it depends only on
slice A and the owner wanted to see the shape):

- `StaticMenu` — a read-only chest menu. This is what makes a GUI possible on vanilla clients
  at all: a chest menu is plain vanilla protocol. The cost is that every vanilla inventory
  interaction has to be suppressed by hand, so `clicked` deliberately does not call `super`
  and `quickMoveStack` returns empty. It resyncs the client afterwards, because the client
  predicted a change the server never made.
- `MenuItems` — item building. Two traps worth recording: item names and lore render *italic*
  by default and have to be un-italicised explicitly, and the operator form of `withStyle`
  must be used, because `withStyle(Style.EMPTY.withItalic(false))` merges styles and drops
  the colour. Also, an `ItemStack` with count 0 is the empty stack, so rank 0 becomes count 1
  — which renders no number, exactly what "not bought" should look like.
- `TechMenuLayout` — **what** the menus contain, as a pure function. Split from the opener on
  purpose: opening a container needs a client, so the plumbing cannot be tested headlessly,
  but the contents can.
- `TechMenu` — opens the two menus and routes clicks.

Main menu is 3 rows: island summary plus the six categories, each showing its rank count and
points spent. A category menu is 6 rows: header and back button on row 0, then **one row per
tier**. Closed tiers are a wall of red panes saying how many more points that branch needs, so
the next tier is visible before it is affordable.

**The tier rows are derived, not configured.** They come from the distinct `spent:` thresholds
of the category's nodes, so retuning a gate in `techtree.json5` moves the row with it and the
two can never disagree.

`/ob tech` now opens the menu; `/ob tech chat` keeps the text version.

**Verified how:** build green. Dev server boots and the new startup report shows the derived
tier structure, which is the only part of the GUI that can be checked without a client:
oneblock 9 nodes in 5 tiers at 0/5/15/30/50, island 3 in 3, players 4 in 3, pokemon 4 in 1,
boosts 2 in 1. OneBlock landing on exactly 5 tiers matters — that is precisely the number of
rows a 6-row menu has below its header. The report also warns when a category has more tiers
than rows or a tier wider than 9 nodes; neither fired.

**Still unverified, and this is a big one:** the menus have never been opened by a client.
Nothing about the rendering, the click routing, buying from the GUI, or — most importantly —
whether items can be pulled out of the menu has been observed. A leak there would duplicate
items. `HANDOFF.md` §4 test 10 is the script for this and it needs to be run before anyone
plays on it.

**Noted for later:** the pokemon and boosts categories have no `spent:` gates yet, so each
renders as a single row. That is fine while pokemon is a sample branch, but both want tiers
once they are real content.

---

## 2026-08-13 — WoW-style ranks and tier gates

**Asked:** the owner wants the game grindy, and pointed out that not every node has to be
0/5 — it can be 1/1, 1/2, 1/3, 1/5 "wie in WoW". Then: you should have to spend a minimum
number of points in a branch before the upper part unlocks, and the chest GUI should be laid
out that way.

**What was already true:** varied ranks needed no code. `max_level` is derived from the length
of the cost list, so a node with one cost is x/1 and one with five is x/5. The default tree
already mixed x/1, x/2, x/3, x/5 and x/7.

**What was built:**

- **`spent:<category>>=n`, a new requirement type** — the WoW tier gate. It does not care
  which nodes were bought, only that the island has committed to the branch, so several
  routes reach the same tier and going deep in one branch is a real alternative to buying
  every cheap rank first. `IslandTechState.spentIn` recomputes the total from the levels
  rather than storing a counter, because a stored total would drift the moment an admin
  retunes a cost.
- **The default tree now uses tiers** at 5, 15, 30 and 50 points spent in OneBlock, with node
  prerequisites kept only where one specific thing must come first (End still needs Nether 3).
  Ranks were re-cut so the count reflects the content: Ocean and Tundra to x/4, End to x/3 at
  much higher per-rank cost, Treasure Hunter to x/2.
- **Chat always shows the rank**, including `1/1`. The `[owned]` special case for single-rank
  nodes is gone — a mixed tree only reads well if every row is written the same way.
- The chest menu layout is now decided and written down in `PROGRESSION_REWORK.md` §10 slice
  D: 9×6 is one row per tier, top row for category tabs.

**A real bug found by the test, not by review:** an unparseable requirement used to drop only
that requirement and keep the node. That fails open — a typo in `spent:oneblock>=30` would
leave an endgame node freely buyable from minute one, and nothing in game would look wrong.
Now the whole node is dropped and the log says why. A missing node is obvious; a silently
ungated one is not.

**Verified how:** `./gradlew build` green. Dev server booted, default tree loaded as 22 nodes
over 5 categories costing 439 points with no validation warnings, so every `spent:` gate in
the shipped default parses. Then three malformed gates were injected — unknown category,
non-numeric threshold, missing `>=` — plus one valid uppercase form. First run: all three
rejected but their nodes kept, which is how the fail-open bug was found. After the fix, all
three nodes are dropped (23 nodes instead of 26) and the uppercase `spent:ONEBLOCK>=5`
survives, confirming the parser discriminates rather than being uniformly strict or lax.

**Still unverified:** the same gap as slice A — gate *evaluation* at purchase time needs an
island, which needs a client. Parsing and validation are verified; `spentIn` and the gate check
are compiled and reviewed, not played.

---

## 2026-08-13 — Everything lands on PR #1, and its description was rewritten

**Asked:** "wir arbeiten immer nur an PR 1" — this project uses a single long-lived pull
request. No second PR, ever.

**What was done:** the three progression commits were fast-forwarded from the session branch
onto `claude/cobblemon-oneblock-mod-49flzn`, which is PR #1's head. Verified as a genuine
fast-forward first (`git merge-base --is-ancestor`) so nothing was overwritten. PR #1 is now
at `6f86d7c`, 33 commits.

The PR description was rewritten. The original was auto-generated at the first commit and had
drifted a long way: it advertised the trigger event system that was removed in full on
2026-08-13, the pre-rename module and dimension ids (`oneblock-api`, `oneblock:world`), and
the two Cobblemon claims this file has flagged as wrong since the rename — "catch rate
modifications" (there is a catch *permission*; rates are never touched) and "spawn rate buffs"
(they are shiny rate and IV floor). The new text describes what exists, states the two
deliberate non-features as decisions, and carries the verification status including what is
still untested in game.

`HANDOFF.md` §1 and §6 updated: the branch/PR warning is replaced by the standing rule, and
the "two wrong claims" open item is closed.

**Verified how:** fast-forward confirmed before pushing; PR #1 re-read afterwards and its head
sha matches the pushed commit. The description is prose — nothing to verify beyond having
checked each claim in it against the working tree.

**Still unverified:** nothing new. The gameplay gaps are unchanged and listed in the entry
below and in `HANDOFF.md` §4.

---

## 2026-08-13 — Progression rework slice A: the tech tree core

**Asked:** after the design review was accepted and D1/D2/D4 were settled, build the spine of
the new progression system.

**What was built:** a new `core.progression` package, plus the wiring around it.

| Piece | What it does |
|---|---|
| `TechCategory` | the six branches, in display order |
| `TechEffect` | nine typed payloads, each validated at load |
| `TechRequirement` | `node:x>=2`, `boss:x`, `tech_points>=n` |
| `TechNode` | levels, per-level costs, per-level effects |
| `TechTree` | validation and the dropped-node report |
| `TechTreeFile` | reads `techtree.json5`, writes the bundled default on first start |
| `IslandTechState` | levels, claims, spenders, balance, lifetime earned |
| `TechRepository` | migration 9 tables; the claim insert is synchronous on purpose |
| `TechService` | the only writer of levels and balances |
| `TechPointService` | the only entry point for content-driven points |
| `TechCommands` | `/ob tech` list / info / unlock / delegate / grant |

Migration 9 adds `island_tech`, `island_claims`, `islands.tech_points`,
`islands.tech_points_earned` and `island_members.may_spend_tech`.

**Three decisions worth recording:**

- **The claim insert is synchronous and leans on the primary key**, unlike every other write
  in this codebase. Two members of one island finishing the same gym battle in the same tick
  would both pass an in-memory check and bank the points twice; letting the unique constraint
  arbitrate is the only version that cannot race.
- **Tech data is wiped on purge, not on archive.** Archiving is restorable, so an archived
  island has to keep its unlocks for a restore to mean anything. `/ob reset` gives the player
  a brand new island id and therefore an empty tree immediately, which is the behaviour they
  actually see.
- **`max_level` is derived from the length of the cost list**, not read as its own field.
  Two sources of truth for the same number is how a level ends up free or unreachable.

**Verified how:** `./gradlew build` green on all three modules. Dev server booted headless
with Cobblemon 1.7.3: migration 9 applied and schema version reported as 9, the default tree
was written to `config/cobblemon_oneblock/techtree.json5` and loaded as 22 nodes across 5
categories costing 459 points, and `/ob tech` reached its executor from the console (it
answered "A player is required to run this command here", which is the expected refusal).

Validation was exercised by injecting eight deliberately broken nodes into the config and
restarting: unknown category, requirement pointing at a node that does not exist, requirement
above a node's maximum level, a two-node cycle, a duplicate id, an unknown effect type, an
effect on a level the node does not have, and an unparseable requirement string. Every one was
caught, logged with the node id and dropped, and the cascade onto two further nodes that
depended on a dropped one worked as intended. The first run of that test produced a vague
message ("requires a node that does not exist, or a level above its maximum") on nodes whose
requirement plainly existed in the file; that was fixed to name the offending requirement and
the reason, and re-verified.

Also found and fixed while testing: `/ob reload` reloaded the loot table but not the tech
tree, so an edited `techtree.json5` needed a full restart.

**Still unverified:** buying a node end to end. It needs an island, an island needs `/ob
create`, and island creation cannot be driven from the server console — same reason tests 7-9
in `HANDOFF.md` §4 need a human. Everything up to and including the command executor is
verified; the spend path itself is compiled and reviewed, not played. Concretely untested in
game: `/ob tech unlock`, `/ob tech delegate`, `/ob tech grant`, and the island-wide
announcement on a purchase.

---

## 2026-08-13 — Progression rework: full design from the owner, reviewed and written down

**Asked:** the owner delivered a complete system design — a six-category tech tree (OneBlock,
Island, Players, Pokémon, Boosts, Special), biome unlock ladders replacing the phase system,
Pokémon that work for their trainer, timed island boosts, Terraria-style boss gates, and a
point economy fed by NPC trainers, advancements and bosses instead of EXP or block breaking —
and asked for a sparring review before any code.

**What was built:** no code. `PROGRESSION_REWORK.md` rewritten from the ground up as the
design of record:

- §3 the review: five things that are right and ten that have to change, each with the
  reasoning rather than a verdict
- §4 the node/effect/storage model, including migration 9 and the `island_claims` table
- §5 the point economy, and the answer to the owner's two open questions — player→island is
  already solved by `IslandManagerImpl.activeByPlayer`; "has this island already beaten this
  trainer" belongs in our database keyed on a Cobblemon NPC `config` variable, **not** in
  MoLang, because MoLang variables are per-entity and die with the entity
- §7 Pokémon labour: the anti-overpowered rule ("never creates a resource"), and four
  non-overlapping stat roles that answer the owner's open question about Attack/Sp. Atk
- §8 Cobblemon API ground truth read from tag `1.7.3`
- §9 six of the original decisions settled by the design, seven new ones opened
- §10 an eight-slice build plan ordered so the mod stays playable throughout

**Substantive corrections to the design, argued in §3.2:** work must not faint or kill a
Pokémon (it contradicts the sleep threshold the design itself defines); HP drain must be flat
or the HP stat buys nothing; point claims must be island-scoped or a four-person island
progresses four times as fast on the same content; all three point sources are one-shot, so
progression currently dead-ends; the typing spawn boost can shift the *share* of spawns but
not the total, which is the standing project decision in `HANDOFF.md` §5; `SHINY_RATE` is
stored as an absolute probability and has to become a multiplier for "1.5× → 5×" to mean
anything; `/fly` deletes the core tension of a void island and needs to be gated hard.

**Verified how:** every Cobblemon hook in §8 was read from the `1.7.3` tag of
`gitlab.com/cable-mc/cobblemon` — `BATTLE_VICTORY`, `SHINY_CHANCE_CALCULATION` (where the
divisor semantics of `shinyRate` were confirmed from `PokemonProperties.roll`), the cancelable
`ENTITY_SPAWN`, and the NPC class README for `config` variables. Every claim about this
codebase in §1 and §3.2 was read from the working tree.

**Still unverified:** which `ServerPlayer` Cobblemon passes into `PokemonProperties.roll` for
an ambient wild spawn — it may be null. Noted in §8; the shiny design deliberately does not
depend on it. Nothing in this entry has been compiled or run, because nothing was built.

---

## 2026-08-13 — Trigger event system removed completely

**Asked:** scrap the whole trigger event system. The owner does not like it — it is not fun —
and wants to design a replacement from scratch. Remove all of it, leave no leftovers.

**Scope decision made and stated up front:** the API **event bus stays**. "Event system" is
ambiguous in this codebase: the *trigger events* (Mob Wave, Boss Fight, Resource Burst,
Legendary Encounter) are the gameplay feature that was cut; `OneBlockEventBus` and its
lifecycle events (`IslandCreatedEvent`, `PartyJoinEvent`, `BorderLevelUpEvent`, `AuditEvent`,
…) are the addon subscription interface and have nothing to do with gameplay. Removing the
bus would end the addon system, so it was kept. Flagged to the owner rather than assumed
silently; the asymmetry decided it — keeping it wrongly is a small follow-up, deleting it
wrongly is not.

**Deleted (16 files):**

- API: the whole `api.trigger` package — `EventManager`, `TriggerEventType`,
  `ActiveTriggerEvent`, `TriggerEventContext`, `TriggerEventStatus`
- API: bus events `TriggerEventStartEvent`, `TriggerEventEndEvent`, `TriggerEventFailEvent`
- Core: the whole `core.trigger` package — `TriggerEventService`, `EventManagerImpl`,
  `EventSpawning`, `MobWaveEvent`, `BossFightEvent`, `ResourceBurstEvent`
- Core: `core.cobblemon.LegendaryEncounterEvent`
- Example addon: `FireworkCelebrationEvent`

**Also removed, because "no leftovers" means these too:**

- `OneBlockAPI.eventManager()` and its implementation
- the break hook call, the `/ob info` "active event" line, the registration block and the
  tick/shutdown hooks in `OneBlockCore`
- config fields `trigger_event_threshold`, `event_cooldown_seconds`, `event_timeout_seconds`,
  `legendary_species`, their defaults, their validation clamps and the now-unused
  `stringList`/`stringArray` Jankson helpers
- **wizard question 4** — the wizard is now 7 questions, and questions 5–8 were renumbered
  to 4–7 everywhere (code, `ADMIN.md`, `PROJECT_PLAN.md`)
- 15 language keys per language file; `en_us` and `de_de` verified to still have identical
  key sets (137 each)
- the feature line in `fabric.mod.json`, `README.md`, `ADMIN.md` and `API.md`

**Kept deliberately, and why:** `PROJECT_PLAN.md` Phase 7 stays, marked
**❌ ZURÜCKGENOMMEN** with a pointer here. It is the design record, not documentation of a
current feature, and when the replacement gets designed it is worth knowing exactly what was
built and rejected. Say the word if it should be erased too.

`API.md` gained a **changelog section** recording the removal. The versioning section always
promised breaking changes would be listed in a changelog and there was none; now there is.
No version bump and no migration note: `0.1.0` was never published, so nothing can have
compiled against the removed types.

**Verified on a real dev server:**

| Check | Result |
|---|---|
| `./gradlew build`, all three modules | green |
| Trigger classes in the built jars | 0 in core, 0 in api |
| Repo-wide grep for the removed symbols | no hits outside the deliberate history in `PROJECT_PLAN.md`, `API.md` changelog and this file |
| Server boot | clean, no event type registrations in the log |
| `main.json5` regenerated | none of the four removed keys present |
| Wizard end to end from the console | "Question 1/7" … "7/7", summary lists exactly 7 values with no event threshold, `confirm` saves, `setup_completed: true` |
| `/ob setup set trigger_event_threshold 10` | rejected: "Unknown setting", valid-key list shows the 7 remaining keys |
| `en_us` / `de_de` key parity | identical, 137 keys each |

**Not verified:** `/ob info` no longer prints the "active event" line — the command needs a
player, so it was not exercised. It is a deletion of one `sendSystemMessage` block, and the
rest of `/ob info` is untouched.

---

## 2026-08-13 — OneBlock drops go to the inventory; anchor foundation is now optional

**Asked:** items from the OneBlock fall into the void — can they go straight into the
breaker's inventory instead? If that works, remove the bedrock platform under the anchor.
Also: spell the chest `chance` scale out in the config comment (1.0 = 100%, 0.10 = 10%,
0.01 = 1%).

**Corrected first:** it was never a 3×3 platform — a *single* bedrock block at anchor Y−1.
And the drops were already being lost *with* it there: the replacement block is placed in
the same tick, items spawn inside it, get pushed out sideways and fall past a one-block
foundation anyway. So the diagnosis was right and the fix was needed either way.

**Built:**

- New `core.island.DropCollector`. A break registers the anchor; at the end of the tick the
  items and experience orbs around it go to the breaker, with vanilla's pickup animation and
  sound. Inventory full → the remainder is repositioned to the player's feet rather than
  discarded or left over the void.
- `main.json5` gains `oneblock_drops_to_inventory` (default `true`) and
  `anchor_bedrock_foundation` (default `false`). Both applied by `/ob reload`. Not wizard
  questions.
- `ensureFoundation` now reconciles in both directions: places bedrock when the setting is
  on, removes it when off. It only ever removes *bedrock*, so a block a player placed under
  their own anchor survives either way.

**Two findings that decided the design — verified against the jars, not assumed:**

1. **`PlayerBlockBreakEvents.AFTER` fires before the drops exist.** Fabric injects it at
   `Block#onBroken` (`ServerPlayerInteractionManagerMixin`, confirmed in the sources jar),
   and vanilla's `ServerPlayerGameMode.destroyBlock` calls in this order:
   `playerWillDestroy` → `removeBlock` → **`Block.destroy` ← AFTER** → `mineBlock` →
   **`playerDestroy` ← the drops**. Collecting items inside the break hook would find
   nothing. Hence the end-of-tick sweep.
2. **Sweeping the ground beats computing the drops.** `Block#getDrops` would miss the
   contents of a broken treasure chest (those come from `Containers#dropContents`) and
   anything another mod adds on break. Letting vanilla drop and picking the items up keeps
   Fortune, Silk Touch and mod compatibility as vanilla's business.

**Guards worth knowing:** the sweep takes only entities with `tickCount <= 4` within 1.5
blocks of the anchor, so it cannot vacuum up items a player deliberately dropped nearby or
trigger event rewards lying around. Each break stays watched for 2 ticks as insurance
against tick-ordering surprises. The queue is empty in the common case, so the per-tick cost
is one `isEmpty()`.

**Risk introduced by removing the bedrock, and the mitigation:** `minecraft:cobweb` is in the
block pool, survives as a lone floating block, and has no collision. A player standing on the
anchor when it turns into cobweb now drifts into the void instead of landing on bedrock.
Documented in `ADMIN.md` as a blacklist candidate. Not blacklisted by default — that is the
server owner's call, and the same argument would apply to turning the bedrock back on.

**Verified:** compiles; server boots with the new tick hook and no errors; `main.json5`
regenerates with both keys at the documented defaults.

**🟡 Not verified — needs a client:** everything the change actually does. Nothing that
breaks a block or creates an island can be driven from the server console (`/ob create`
needs `source.player`; the admin commands need an online player argument), so neither the
collection nor the foundation toggle could be exercised. Recorded as tests 9 and 10 in
`HANDOFF.md`. This is now the second feature in a row whose core behaviour is client-gated —
the live test session is where all of it gets confirmed.

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

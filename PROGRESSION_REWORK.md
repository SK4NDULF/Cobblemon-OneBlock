# Progression Rework — working document

The place where the progression rework is designed, decided and tracked. Started
2026-08-13 after the owner proposed replacing the current border-level progression with a
tech tree in the spirit of Palworld's.

**How to use this file**

- Sections 1–4 are the analysis. They should only change if the code changes.
- Section 5 is the **open decisions**. Nothing gets built from a decision that is still
  marked ⬜. Fill them in as they are made, with the date and a one-line reason.
- Section 6 is the build plan; it stays a plan until decisions are settled.
- Section 7 is the running change log for this rework — append, never rewrite.

When a slice ships, summarise it in `WORKLOG.md` as usual and tick it here. This file is
the design; `WORKLOG.md` is the history.

Written in English per the project's working agreement (`HANDOFF.md` §7), even though the
conversation around it is German.

---

## 1. What the current system actually is

Read from the code, not from the plan:

| Piece | Where | Behaviour |
|---|---|---|
| Points | `ProgressionService.pointsForBreak` | `points_per_break` (default 100) per OneBlock break, scaled by party size: `1 / (1 + (n-1) * party_diminishing_returns)` |
| Levelling | `ProgressionService.onBreak` | points accumulate automatically; while `points >= threshold(level+1)` the level rises, up to `MAX_LEVEL = 8` |
| Thresholds | `main.json5` `border_level_thresholds` | 7 cumulative values, default 50k…2.5M |
| The reward | `GridMath.borderSizeAt` | border side length, exponential interpolation from 16 to `max_island_size` across the 8 levels |
| Persistence | `IslandRepository.updateProgressAsync` | `islands.break_count`, `islands.points`, `islands.border_level` |
| Announcement | `ProgressionService.announce` | chat + action bar + level-up sound to every online island member |
| API | `BorderLevelUpEvent`, `ProgressionManager` | addons can observe and query |

**In one sentence:** mine → points rise on their own → at seven fixed thresholds the border
grows. One currency, no decisions, one kind of reward.

## 2. Why it feels flat

Three separate problems, worth keeping apart because they have different fixes.

1. **The player never acts.** Points accrue and levels happen *to* them. There is no moment
   of choosing, saving up, or spending. A progress bar is not a system.
2. **There is only one reward, and it is the weakest one available.** Border size is space,
   and space only pays off while you are actively building. It is invisible the rest of the
   time.
3. **The core loop never changes.** This is the big one. The OneBlock draws from the same
   848-block pool on break one and on break fifty thousand. What a player *feels* in a
   OneBlock game is what comes out of the block — and ours never changes. No tree fixes that
   by itself; the tree has to be the thing that changes it.

Problem 3 is the reason the mod would still feel flat even with a beautiful tech tree
bolted onto the border levels.

## 3. The idea, and the reframe

**The idea:** replace linear border levels with a tech tree — earn points, spend them on
nodes, choose your path.

**The trap to avoid:** Palworld's tree unlocks *crafting recipes and structures*, because
Palworld owns its entire crafting system. We do not. Minecraft and Cobblemon own crafting
here, and Minecraft's recipe book is **not a gate** — a player who knows the pattern crafts
the item whether or not the recipe is "unlocked". Actually gating it means intercepting
recipe matching with mixins: invasive, hostile to other mods, and confusing in play
("why can't I craft a pickaxe?"). Most work, worst result. **Do not copy this part.**

**The reframe:** the tree should unlock the knobs *this mod already owns*. That is where a
tree is a large upgrade over a linear bar, and most of the payloads already exist in code —
the work becomes the framework, not the effects.

## 4. Candidate unlock types

Everything below is already a working, tested lever in this codebase. "Exists" means the
effect is implemented; only the "player can unlock it" part would be new.

| # | Unlock | Effect exists? | Where | Note |
|---|---|---|---|---|
| 1 | **OneBlock loot phases** (Overworld → Cave → Nether → Ocean → End …) | partly | `LootRegistry`, `OneBlockLootProvider`, `OneBlockLootTable` | The provider extension point and its precedence already exist and are documented in `API.md`. **Highest value by far** — this is the fix for problem 3. |
| 2 | Border size | yes | `GridMath.borderSizeAt` | The current system; becomes nodes instead of automatic |
| 3 | Treasure chest chance / better tables | yes | `ChestLoot` | `chance`, `blacklist`, `extra` are all live knobs |
| 4 | Cobblemon shiny rate | yes | `BuffType.SHINY_RATE`, `BuffService` | Per-island, already persisted; currently admin-only via `/ob buff` |
| 5 | Cobblemon IV floor | yes | `BuffType.IV_FLOOR`, `BuffService` | Same |
| 6 | Max party size | yes | `main.json5 max_party_size` | Currently one fixed server-wide value |
| 7 | Biome regions (count, or which biomes) | yes | `BiomeService`, `max_biome_regions` | Currently one fixed server-wide value |
| 8 | Utility (`/ob visit`, extra homes, …) | partly | `ObCommands` | Cheap nodes to fill out a tier |

Two observations:

- Nodes 4 and 5 are nearly free: the buffs are already island-scoped and restart-persistent.
  The only change is who may grant them.
- Nodes 6 and 7 turn a server-wide constant into a per-island value. That is a small data
  model change (island column or unlock-derived value), not a small design change — decide
  whether the config value becomes a *cap* or a *starting value*.

## 5. Open decisions

Nothing gets built from a ⬜. Mark ✅ with date and reason when settled.

### D1 ⬜ What do nodes unlock?

Pick from the table in section 4. My recommendation: **start with 1, 2, 3** and add 4/5
immediately after, because they are almost free. 6/7/8 are tier filler for later.

The reason for putting loot phases first is section 2 problem 3 — it is the only unlock
that changes the core loop rather than decorating it.

### D2 ⬜ Does the border stay automatic, or become nodes you buy?

- **Automatic** (today): points still auto-level the border, and the tree is a *second*,
  separate currency/track. Less disruptive, but keeps the "nothing to decide" feeling for
  the biggest reward.
- **Bought**: border growth becomes tree nodes competing with everything else. This is what
  creates real choice — "wider island or better loot?" is an actual decision.

My recommendation: **bought.** Otherwise the tree is a side-show next to the thing that
already happens on its own.

### D3 ⬜ One currency or two?

Palworld has two (Technology and Ancient Technology points, the second from bosses). We
have exactly one source of progress right now — OneBlock breaks — because the trigger event
system was removed on 2026-08-13.

My recommendation: **one currency** for now. A second currency needs a second, meaningfully
different source, and we do not have one until the replacement for trigger events exists.
Design the model so a second currency can be added later without a migration (e.g. cost is
a map of currency → amount, with one key today).

### D4 ⬜ Who spends the points?

Progression is per-island and shared with the party — unlike Palworld, which is per-player.

- Owner only — simple, but party members are passengers
- Any member — fastest, but one member can spend the island's savings on the wrong branch
- Owner by default, may delegate — fits the existing `IslandRole` model

My recommendation: **owner by default, delegable**, since roles and permission plumbing
already exist.

### D5 ⬜ Is the tree data-driven or hardcoded?

Data-driven (a JSON5 file next to `loottable.json5`) means admins can retune or replace the
whole tree without a rebuild, consistent with how loot is handled. It costs validation work:
unknown parents, cycles, unknown effect types, duplicate ids, unreachable nodes.

My recommendation: **data-driven**, with a built-in default tree written on first start —
the same pattern `loottable.json5` already uses, including logging and skipping bad entries
instead of failing to boot.

### D6 ⬜ How is the tree shown to the player?

**Hard constraint:** this is a server-side mod and the target is **vanilla clients** — the
same reason Polymer content is excluded from the loot pool. A custom screen is not possible
without shipping a client mod. There is currently **no GUI of any kind** in this codebase;
everything is chat.

Realistic options:

- **Chest GUI** (container menu, one item per node, name + lore, click to buy). The standard
  server-side approach, works on vanilla clients. But a 9×6 grid is a grid — branches can be
  *suggested* through layout and colour, not drawn.
- **Written book** — good for browsing and reading, clickable pages, feels static.
- **Chat with clickable components** — works today with zero new machinery, ugly for a tree.

Be clear-eyed about this: we can reproduce Palworld's *feel* (save up, choose a branch, see
what is next) but not its *look*. Anyone expecting the Palworld canvas will be disappointed,
and that is worth deciding now rather than after it is built.

My recommendation: **chat first, chest GUI second.** Chat gets the whole system playable and
testable in one slice; the GUI is then a presentation layer over a model that already works.

### D7 ⬜ What happens to existing islands?

Nothing is live and PR #1 is unmerged, so this is cheap right now and expensive later.
Still needs deciding: existing islands carry `points` and `border_level`. If points become
*spendable*, an island that already spent 500k points on levels must not lose them.

My recommendation: on migration, grant the border nodes matching the island's current
`border_level` for free and keep `points` as the balance. Needs DB **migration 9** (the
schema is at 8 today).

## 6. Build plan (draft — blocked on section 5)

Sliced so each step is independently testable and shippable.

- **A — Model and storage.** Node/tree definitions, unlock state per island, migration 9,
  validation of the tree file. No player-facing behaviour yet.
- **B — Earning and spending over chat.** `/ob tree` to list, `/ob tree unlock <node>` to
  buy, `/ob info` showing the balance. Fully playable, ugly.
- **C — The first real payloads.** Loot phases (D1 #1) and border nodes (D2). This is the
  slice where the game actually changes.
- **D — Cobblemon payloads.** Shiny rate and IV floor as unlocks.
- **E — Chest GUI.** Presentation layer over B.
- **F — API surface.** Public read access to unlock state, an event when a node is bought,
  and — only once the shape has settled — an extension point for addon-defined node types.
  Deliberately last: the trigger event extension point was published and then deleted, and
  that should not happen twice.

**Scope warning:** A–F together is larger than everything built on 2026-08-13 combined.
Slices A and B are the ones that must be right; C onwards is comparatively mechanical.

## 7. Change log for this rework

Append one entry per slice, newest at the bottom. Nothing shipped yet.

| Date | Slice | What changed | Verified how |
|---|---|---|---|
| 2026-08-13 | — | Document created. Analysis of the current system, the Palworld reframe, candidate unlocks, seven open decisions. No code touched. | n/a |

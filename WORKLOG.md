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

## 2026-08-14 — Nether portals were an unlocked back door out of the whole mod

**Asked:** the owner shared a competing SkyBlock mod's feature list and asked what was worth
taking from it. One line on it was "Custom Nether portal linking per island" — which is a
feature for them and was an **unhandled hole** for us.

**What was wrong:** there was no portal handling anywhere in the mod. A nether portal sends an
entity to whatever dimension is not the Nether, so from `cobblemon_oneblock:world` that is the
real, infinite, unprotected vanilla Nether — and a portal there reaches the vanilla Overworld.
Every border, every protection rule and the entire island economy stop mattering the moment a
player walks through, because the whole vanilla world is on the other side. The tech tree
itself hands out the key: the Nether ladder drops obsidian at tier 4 and the End ladder drops
more.

**Verified before fixing, and it took three attempts to get an honest answer:**

1. `/setblock` fire in a frame produced no portal — but the fire was gone a second later, so
   the test proved nothing.
2. A pig placed in a hand-built portal did not travel — but it had been summoned with
   `NoAI:1b`, which the control did not have. An `execute in the_nether if entity` check
   *did* report a hit, which looked like confirmation and was not; the pig's position had
   never changed, and a real Nether entry divides coordinates by eight.
3. Same setup without `NoAI`, against a vanilla-Overworld control: the control pig moved to
   nether-scaled coordinates, and so did the pig in our world — from `(100.5, 100.5, 100.5)`
   to `(15.3, 101, 16.3)`. Exploit confirmed.

The control is what made it trustworthy. Without it, step 2's negative would have read as "we
are fine" and shipped.

**The fix:** two mixins, blocking both halves.

- `PortalShapeMixin` cancels `findEmptyPortalShape` in our dimension, so a portal never forms.
  Reporting "no valid shape here" is the gentlest refusal available — vanilla already handles
  that answer everywhere it asks it.
- `NetherPortalBlockMixin` cancels `entityInside`, so a portal block that already exists in an
  older world is inert rather than merely un-creatable.

**Verified after:** the identical setup that formed a portal in the vanilla Overworld forms
nothing in ours, and the pig that previously jumped to nether-scaled coordinates now stays at
`(101.3, 100, 100.5)`. Both mixins applied cleanly — with `required: true` and
`defaultRequire: 1`, a boot proves the injection points matched.

**Still unverified:** a real player lighting a real portal with flint and steel. The block
path is the same one the test drove, but a player has their own travel timing.
`HANDOFF.md` §4 test 14.

**Left as a recommendation, not built:** the same list's "Player TOPs" is the one genuine
feature gap — a leaderboard needs an island score, which does not exist yet. Everything else
on it we already have, or it does not apply to a OneBlock (their configurable cobblestone
generator is what our OneBlock loot table already is, only per-island and tech-driven).

---

## 2026-08-14 — Polish pass: a duplication bug, 47 tests, CI, and effects you can read

**Asked:** polish the mod and its infrastructure, and the talent tree specifically.

### The bug worth the whole pass

**`DropCollector` could duplicate items.** A break is swept for two ticks, and anything that
did not fit is parked at the player's feet — which is *inside* the sweep box, because the
player is standing on the anchor. With the yield bonus active, the second tick saw the same
drop again and minted a second bonus from it; free an inventory slot between the two ticks and
it repeated. The bonus item spawned by the first tick was itself a candidate on the second.

Fixed by marking each item with a scoreboard tag the moment the bonus is decided for it, and
marking spawned bonuses on the way out. Scoreboard tags rather than a set of ids because the
mark has to outlive the sweep, including any later break covering the same spot.

### Infrastructure

**There were no tests at all.** There are now **47**, covering the pure logic that was
previously checked by booting a server for two minutes per run: gate parsing, tree validation
and cascade, rank arithmetic, name sanitising, effect rendering, and language-file
consistency.

They were checked for sharpness rather than assumed: two deliberate mutations were introduced
— stop stripping the section sign, and treat every node as implemented — and exactly the four
tests that should fail did.

**CI** (`.github/workflows/build.yml`) builds and tests on every push, uploads the test report
even on failure, and publishes the server jar as an artifact.

### Two real defects the tests found immediately

- **A wrong assumption of mine in `IslandNames`:** control characters were *removed*, so
  "Rocket\nBase" became "RocketBase". They now become a space, because a newline was a
  separator in the player's head. Formatting markers are still removed outright.
- **A wrong assumption in my own test:** the first version compared format specifiers *in
  order* and flagged `admin.confirm_prompt`, whose German translation legitimately uses
  positional arguments (`%2$s ... %1$s`) because the word order differs. The test now compares
  position → conversion, which is the thing that actually has to match. The mod was right and
  the test was wrong; worth recording because the failure looked convincing.

### The talent tree

Nodes now **show what they actually give**, read from the typed payload rather than the prose:
"35% chance that Cave blocks drop twice", "+3% treasure chest chance". Prose goes stale the
first time an admin retunes a number in `techtree.json5`; a rendered payload cannot. Both the
chest menu and `/ob tech info` show the next rank's gain and, separately, what is already
active.

`/ob tech info` also states plainly when a node is not built yet, which previously only the
chest menu did.

**Verified how:** `./gradlew build` green including all 47 tests; server boots clean.

**Still unverified:** everything needing a client, unchanged. The duplication fix in
particular wants the specific test in `HANDOFF.md` §4 test 12 — break forest blocks with a
full inventory and confirm the bonus lands once.

---

## 2026-08-13 — One branch, one PR

**Asked:** "ja alles zsm in einen pr nur".

**What was done:** PR #2 closed with a note that its branch and PR #1's pointed at the same
commit, so nothing was lost. Local work moved off the mirror branch onto
`claude/cobblemon-oneblock-mod-49flzn` — PR #1's head — so future commits land there directly
and no parallel branch exists for a PR to be opened from.

PR #2 existed because this session was handed a working branch that was not the PR's head, and
that branch was kept in sync as a mirror; the Claude Code UI then opened a PR from it. The
lesson is in `HANDOFF.md` §1 now: switch onto the PR branch, do not mirror.

PR #1's description was refreshed at the same time. It still described slice A alone while
B, C and D had landed, and on a single long-lived PR the description is the page people read.

**Verified how:** both PRs re-read after the change — #2 closed, #1 open at `4a66c62` with 40
commits, local branch and upstream matching.

---

## 2026-08-13 — Unimplemented nodes are locked instead of sellable

**Asked:** the owner had a Charmander out next to a furnace full of raw iron and asked why it
was not smelting. Answer: the Pokémon labour system is slice H and does not exist — only the
Fire branch's *tree nodes* do, as a sample.

**The real problem the question exposed:** the tree is written ahead of the slices that
implement its payloads, so 13 of its 22 nodes could be bought and did nothing. Counted up,
**277 of the tree's 439 points bought literally nothing** — the entire Island, Players, Boosts
and Pokémon categories. Points are content-gated and there is no refund, so that is the worst
possible failure: a player spends a scarce currency and the game silently shrugs.

**What was built:** `TechTree.isImplemented` — a node counts as implemented when at least one
of its effects has a consumer, or when it has no effects at all (a pure gate node is doing its
job by being bought). `TechService.buy` now refuses unimplemented nodes before anything else
that could succeed, and both the chat listing and the chest menu label them plainly rather
than letting them look merely expensive. The startup warning changed from "can be bought and
will cost points" to naming the locked nodes.

They unlock by themselves: a node becomes buyable the moment its effect type is added to
`TechEffect.CONSUMED`, which happens in the same commit as its consumer. Nothing to remember.

**Verified how:** build green; the server now reports `The 13 node(s) that rely on them are
locked and cannot be bought` and lists exactly the 13 nodes an independent audit of the tree
file found — biome_regions, border, the four fire nodes, flight, island_rest, party_size,
shiny_boost, spawn_boost, sure_footing, well_fed.

**Still unverified:** that the lock actually reads as locked in game. It needs a client, same
as the rest of the GUI.

---

## 2026-08-13 — Slice C: real point sources

**Asked:** build slice C. With it, A–D are done and the whole minimum-playable set from the
build plan is in.

**What was built:**

- `NpcPoints` — subscribes to `BATTLE_VICTORY` and claims for every island among the winning
  players. Trainer identity comes from a Cobblemon **MoLang config variable on the NPC**, not
  from a config file and not from the entity UUID: `/npc edit <npc> variable trainer_id
  gym_rock` and `variable points 3`. Adding a gym is then placing an entity and typing two
  commands — no config edit, no reload — and rebuilding or moving the NPC keeps every island's
  progress because the identity travels with the configuration. Whether the island already
  beat it stays our database's business; a MoLang variable lives on one entity and that
  question is about an island.
- `AdvancementPoints` + `PlayerAdvancementsMixin` — a mixin because Fabric API has no
  advancement hook in 1.21.1. Injected at `RETURN` and reading the progress afterwards rather
  than at vanilla's internal reward branch: that branch moves between versions, "award
  returned true and the advancement is now done" does not.
- `PointSourceConfig` and `points.json5`, with a default list of 45 **real Cobblemon
  advancement ids** read out of the Cobblemon jar rather than guessed.

**One limit that must not be discovered as a bug:** advancements are per-player and permanent
while claims are per-island, so only advancements completed *while the player has an island*
pay out. Anything finished beforehand never fires again. That is the deliberate direction to
fail in — granting everything already completed on island creation would turn `/ob reset` into
an infinite point machine. Documented in `AdvancementPoints`, in `points.json5` and in the
test script.

**Verified how:** build green. Server boots and reports `45 advancements worth 135 points. The
tech tree costs 439 to complete, so NPC trainers and bosses need to cover the remaining 304` —
the point-budget check from §3.2d, now live at startup.

**The mixin was verified properly, not assumed.** A clean boot proves nothing here:
`PlayerAdvancements` is only classloaded when the first player joins, and a mixin applies at
classload, so a broken `@Inject` or an unresolvable `@Shadow` would have surfaced in front of a
player rather than in the log. A temporary probe was added that force-loads the target at
startup; it logged the class loading cleanly, which with `"required": true` and
`defaultRequire: 1` means both the injection point and the shadows resolved. The probe was
then removed and the build re-verified.

**Still unverified:** the NPC path end to end — it needs a real trainer, a real battle and a
second island. Advancement points likewise need a player. Both are compiled, and the mixin is
now known to apply; what has never run is the code inside it. `HANDOFF.md` §4 test 13.

---

## 2026-08-13 — Slice B: the OneBlock biome ladders

**Asked:** build slice B — the one the whole rework exists for.

**The problem it fixes,** restated because it is the reason the mod felt flat: in
`all_blocks` mode the anchor drew from all 848 registered blocks, uniformly. Break one and
break fifty thousand were statistically identical, so nothing that came out of the block ever
meant anything. Now the pool is *the island's own*, assembled from the biome tiers its tech
tree has unlocked.

**What was built:**

- `BiomePools` — the block sets, per biome per tier, from a new `oneblock_biomes.json5` with a
  bundled default. Six biomes, 26 tiers, 149 blocks. An island with nothing unlocked gets the
  deliberately poor four-block base set; each tier adds on top and the base never disappears,
  it just becomes a smaller share.
- A new `biomes` loot mode, now the default. `all_blocks` stays as documented legacy so a
  running server does not have its world change under it on an update.
- The yield nodes: `oneblock_yield` gives a chance that a break of that biome's blocks drops
  twice. Rolled against the block that was **just broken**, not the one being placed, which is
  what a player means by it — and it means a treasure chest can never be doubled, because a
  chest belongs to no biome set.
- `chest_chance` nodes now add to the configured base chance, per island.
- `TechEffects` as the single place that answers "what does this island have", so the rule for
  combining a ladder's effects lives once. That rule is **highest wins, not sum**: effects are
  cumulative up a ladder, so rank 3 also carries rank 1 and 2, and summing would pay three
  times for one purchase.

Three of the nine effect types are now marked consumed; the startup report is down from eight
unconsumed to five.

**Two design notes worth keeping:**

- The per-island pool is cached against the island's *unlock signature* rather than
  invalidated by a hook. Buying a node changes the signature, so the rebuild happens by
  itself and there is no invalidation call to forget.
- The doubling copies what vanilla actually dropped rather than recomputing drops, so Fortune,
  Silk Touch and anything another mod adds are all doubled for free — the same reasoning that
  made `DropCollector` sweep the ground in the first place.

**Verified how:** build green; server boots with the new mode and reports
`4 base blocks, 6 biomes, 26 tiers, 149 blocks`, which matches the file by hand.

The two safety filters were tested by injecting bad entries: `minecraft:bedrock` (unbreakable
— would stop the island on that block forever), `minecraft:oak_sapling` and `minecraft:torch`
(need support — would pop off the anchor and leave a hole over the void), and a block from a
mod that is not installed. All four were logged with the reason and skipped, and the base set
still resolved.

**The filter also caught a mistake of mine in the shipped default:** `minecraft:soul_lantern`
in Nether tier 5 needs a block under it. It was replaced with `minecraft:quartz_block`, and
the file now boots with zero warnings. Worth noting because it is exactly the class of error
that would otherwise only show up as a player falling into the void.

**Still unverified — and this is the slice where it hurts most:** nothing about the actual
play has been seen. No island exists on a headless server, so the per-island pool, the tier
progression, the double drop and the chest bonus are all compiled and reasoned about, not
observed. `HANDOFF.md` §4 test 12 is the script, and it is the most important one on that list.

---

## 2026-08-13 — Island names

**Asked:** "ja wir wollen owner insel bennen dürfen" — the owner may name their island. Came
out of the chest menu work, where the title had to fall back to the owner's name because
islands had none.

**What was built:** migration 10 adds a nullable `islands.name`, `/ob rename <name>` and
`/ob rename clear`, and `IslandNames` as the single place that decides what an island is
called. Owner only: the name is the island's public identity, and a member renaming it out
from under the owner is the kind of small grief that costs more trust than the feature is
worth. Renames are written to the audit log with who set them.

The name now shows in the tech menu title and its summary item, in `/ob info`, and when
someone visits. `Island.name()` was added to the public API as a defaulted method returning
`Optional`, so existing implementations keep compiling.

**The actual work was sanitising, not storage.** A name is player-authored text displayed to
*other* players, so it is cleaned rather than trusted:

- **`§` is removed entirely.** It is Minecraft's legacy formatting marker; a name containing
  it can recolour, obfuscate or hide the rest of the line it sits in. Enough code paths still
  interpret it that the only safe answer is that the character never reaches one.
- **Control characters** go, so a name cannot forge extra lines.
- **Unicode bidi controls** go too — `Char.isISOControl` does not cover them, and a
  right-to-left override reverses how the rest of the sentence renders.
- **Length is capped at 32, measured after cleaning**, so padding with stripped characters to
  get under the limit does not work. A chest menu title has a fixed width.

Deliberately not done: uniqueness (nothing looks an island up by name, so there is nothing to
make ambiguous) and profanity filtering (that belongs to the server's chat moderation).

**Verified how:** build green. Dev server applied migration 10 and reported schema version 10;
`/ob rename Test` reached its executor from the console. A dead branch was found while
reviewing — `MIN_LENGTH = 1` made the `TooShort` result unreachable, since the empty case is
already `NothingLeft` — and removed; the compiler then caught the stale reference in the
command, which is how it was confirmed gone rather than just unused.

**Still unverified:** everything that needs an island — actually renaming one, the name
surviving a restart, and how a 32-character name looks in a chest title. Added to the
`HANDOFF.md` §4 script.

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

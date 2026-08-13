# Progression Rework — working document

The place where the progression rework is designed, decided and tracked. Started
2026-08-13 after the owner proposed replacing border-level progression with a tech tree.
Rewritten 2026-08-13 (second pass) after the owner delivered a full system design covering
six tech categories, Pokémon labour, timed boosts, boss gates and a new point economy.

**How to use this file**

- Sections 1–3 are analysis and review. They only change if the code or the design changes.
- Section 4 is the **model** — the shape everything else is built from.
- Sections 5–7 are the per-area designs.
- Section 8 is **verified Cobblemon API ground truth**. Do not re-derive it from memory.
- Section 9 is the **open decisions**. Nothing gets built from a decision still marked ⬜.
- Section 10 is the build plan; section 11 is the running change log — append, never rewrite.

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
| The reward | `GridMath.borderSizeAt` | border side length, exponential interpolation from 16 to `max_island_size` across 8 levels |
| Persistence | `IslandRepository.updateProgressAsync` | `islands.break_count`, `islands.points`, `islands.border_level` |
| Loot | `OneBlockLootTable`, mode `all_blocks` | **every** breakable block from Minecraft and every installed mod, **uniformly weighted** — 848 entries |
| Buffs | `BuffService`, `BuffType.SHINY_RATE` / `IV_FLOOR` | per-island, timed, restart-persistent, admin-only via `/ob buff` |
| API | `BorderLevelUpEvent`, `ProgressionManager` | addons can observe and query |

**In one sentence:** mine → points rise on their own → at seven fixed thresholds the border
grows. One currency, no decisions, one kind of reward, and a loot pool that is identical on
break one and break fifty thousand.

## 2. The owner's design (2026-08-13)

Restated compactly so later sessions do not have to reconstruct it from chat.

- **Six tech categories** on a per-island tree: OneBlock, Island, Players, Pokémon, Boosts,
  Special. Owner spends; owner may delegate to members.
- **OneBlock:** drop the phase system. Start with grass/dirt/cobble/planks. Buy *biome*
  unlocks (Forest, Cave, Nether, End, Tundra, …), each with **levels 0/5** that widen that
  biome's block set step by step. Per-biome yield boosts on top (e.g. "Forestcutter: % chance
  of double drops"). Treasure chest chance becomes upgradeable.
- **Island:** biome editor selection, max size, unlocking placeable blocks.
- **Players:** per-member quality of life — slower hunger, passive regeneration, reduced
  fall damage, `/fly` on the own island.
- **Pokémon:** a Pokémon **out of its ball** scans its surroundings and does work matching
  its type (Fire tends campfires and furnaces, …), all 18 types covered. Work costs the
  Pokémon HP. Base stats, IVs and EVs decide how well it holds up. The tree upgrades work
  speed, max HP, damage taken, the HP threshold at which it stops to sleep, and sleep
  regeneration — **per type**.
- **Boosts:** timed island-wide effects, not permanent unlocks. Shiny boost 1.5× → 5×,
  duration 10 min → 1 h; the same for a per-typing spawn boost.
- **Special:** raids and boss fights spawned onto the island, island members only. Beating
  them unlocks further progress — Terraria-style gates.
- **Points come from content, not from time:** NPC trainers and gyms, custom advancements,
  and Special bosses. Explicitly *not* player EXP and *not* block breaking.

## 3. Review — what is strong, and what has to change

### 3.1 What is genuinely good

**Point sources are the best single call in the document.** Rejecting EXP and block-breaking
is right for a reason worth writing down: in a OneBlock mod, mining *is* the activity the
player performs anyway. Paying points for it renames the passage of time — it is income, not
progression, and it cannot be designed against, only tuned. NPCs, advancements and bosses are
*content the player has to go find*. That turns the tree into a reason to leave the island,
which this mod otherwise completely lacks.

**Biome unlocks fix the actual problem.** Section 1's last row is the real defect in the mod
today: `all_blocks` draws from 848 uniformly weighted blocks, so the OneBlock output is
statistical noise — a player gets a sponge, a shulker box and a piece of deepslate in a row
and none of it means anything. Curated biome pools bought in 5 steps turn every break into
feedback about a decision the player made. This is the highest-value item in the whole design
and should ship before anything else in the tree.

**Bosses as gates, not just as payouts.** Requiring a boss kill to open a branch is the only
mechanism in the design that paces *when* things happen rather than *how fast*. It is also
nearly free if the node model carries a requirement list from day one — and very expensive to
retrofit. Design it in now even if the first boss ships much later.

**HP-as-work-currency with an upgradeable sleep threshold.** The risk inversion is elegant:
the default (stop at 75 %) is safe and slow, and *spending points makes the Pokémon work
further down* — the upgrade increases uptime by increasing exposure. That is a real decision
rather than a straight power increase.

**Per-island rather than per-player progression** matches the existing data model exactly —
`IslandData`, `islands.points` and the party tables are already island-scoped.

### 3.2 What has to change

**a) "until it dies" contradicted the sleep threshold — resolved, and better than either
option that was on the table.** The design first said a working Pokémon loses HP "until it
dies", then said it stops at 75 % to sleep. Owner's resolution (2026-08-13): **at 0/5 there
is no threshold at all** — an un-upgraded Pokémon works until it faints. The threshold is
*created* by the first upgrade in that type's branch. See §7.4; it turns the first node of
every branch into something the player actually needs rather than a flat bonus.

"Dies" is implemented as **fainting**, the normal Cobblemon state: 0 HP, needs a healer.
Permanent loss of the Pokémon is not implemented and would need its own explicit decision.

**b) Percentage HP drain makes the HP stat worthless.**
If a work action costs a percentage of max HP, then a Pokémon with 300 HP and one with 100 HP
perform exactly the same number of actions, and "HP: more max HP" buys nothing. Drain has to
be **flat per action** for the HP stat to mean anything. This also gives the four stat groups
clean, non-overlapping roles — see §7.2.

**c) The point economy has a party-size exploit, which is the same question you asked.**
If each member's NPC victory grants the island a point, a four-person island progresses four
times as fast as a solo island on identical content. Since progression is island-scoped, the
**claim must be island-scoped too**: the first member to beat a given source claims it, every
later member is told it is already claimed. This makes a solo island and a full party
progress at the same rate through the same content, which is the correct outcome — parties
already win on wall-clock speed and on being able to fight bosses at all.

**d) Progression is finite — and that is narrower than it first looks.**
Every NPC, advancement and boss is claimable exactly once **per island**. The first version of
this section called that a dead end for late joiners; that was wrong, and the correction
matters for how the content is planned. Because claims are island-scoped, an island created
on day 300 finds *all* of the content unclaimed and can work through every bit of it. Nothing
is globally consumed, and no NPC is ever "used up" by another island.

The real ceiling is therefore only this: an island that has claimed everything has no further
income, and the tree must be completable within the total the content provides. That is a
season, and it is a legitimate shape — but it makes the point budget a hard design
constraint. **The sum of all sources must exceed the sum of all node costs**, or the last
nodes are unreachable for everyone rather than for latecomers. Track both totals in
`techtree.json5` validation and log them at startup.

Owner's decision (2026-08-13): finite, one-shot per island, no repeatable source. Resetting
or deleting an island resets its claims and its progress along with it — see §4.3.

**e) The spawn boost collides with a decision already recorded in this project.**
`HANDOFF.md` §5 states spawn rate is deliberately not a per-island buff because Cobblemon's
spawner is player-centric and globally paced. That still holds — the mod cannot make *more*
Pokémon spawn on one island. What it *can* do is change **which** ones: `ENTITY_SPAWN` is
cancelable (§8), so a non-matching spawn can be dropped with probability `s`, raising the
boosted typing's share of what appears. Honest wording for the player: *"Fire Pokémon make up
a much larger share of what spawns"* — not *"more Pokémon spawn"*. Total spawns go slightly
down while the boost runs, so `s` needs a cap or the island feels empty.

**f) The shiny buff is stored as an absolute probability, but the design wants a multiplier.**
`BuffType.SHINY_RATE` is documented as "absolute shiny chance (0.0–1.0)" and
`CobblemonIntegration.applyBuffs` rolls it as a flat second chance. That is self-consistent
but it is not "1.5× → 5×", and an admin typing `/ob buff shiny_rate 0.5` today makes half of
all spawns shiny. Convert the unit to a multiplier `m` and roll the *difference*: with
Cobblemon's base rate `1/R`, an extra roll at `(m-1)/R` produces an effective rate of `m/R`.
That keeps the existing island-accurate, position-based hook and only changes the arithmetic
and the stored unit.

**g) `/fly` is the most dangerous item in the Players category.**
The entire tension of a OneBlock island is that there is nothing under you. Flight deletes it
permanently, and it cannot be walked back once players have it. If it ships at all it belongs
at the far end of the most expensive branch, behind a boss gate, and it must be restricted to
the island's own border — a `/fly` that survives a teleport to an event area also breaks the
NPC content that the point economy depends on.

**h) 18 types × jobs × 5 upgrade axes is not a feature, it is a second project.**
Counted properly this is 18 job implementations, each needing block scanning, target
selection, animation, an idle state and an anti-grief rule, plus 90 upgrade nodes. It is
larger than the entire mod as it stands today (6 040 lines). Ship **three types** as a
vertical slice — Fire, Grass, Psychic cover "transform", "harvest" and "haul", which are the
three shapes every other type is a variant of — with the job definitions data-driven so the
remaining 15 are JSON and balance work rather than new systems.

**i) The GUI: the "Book GUI" wish and the tree's needs point in different directions.**
This is a server-side mod targeting vanilla clients (the same constraint that excludes
Polymer content from the loot pool), so both a written book and a chest menu are possible —
both are plain vanilla protocol — but they are good at different things. A book renders
formatted, clickable text over pages and is excellent for *reading*; it cannot show a grid of
icons with live "2/5" progress. A chest menu shows exactly that, one item per node with name,
lore and click-to-buy, with the top row as category tabs. Recommendation: **chest menu as the
tree**, and keep the book idea for a separate island codex later if it is still wanted. Be
clear-eyed either way: the *feel* of a tech tree (save up, pick a branch, see what is next) is
reproducible; the drawn canvas with connecting lines is not, without a client mod.

**j) Scope.** The previous draft of this file warned that its own plan was larger than
everything built on 2026-08-13 combined. This design is several times that again. It will not
land as one piece of work, and the order it lands in decides whether it is playable in the
meantime — see §10.

---

## 4. The model

One shape, used by all six categories. Getting this right is the whole job; the categories
are then content.

### 4.1 Node

```
id            forest, forestcutter, flight, shiny_boost, fire_threshold …
category      ONEBLOCK | ISLAND | PLAYERS | POKEMON | BOOSTS | SPECIAL
costs         price of each rank; THE LENGTH OF THIS LIST IS THE MAXIMUM RANK
requires      list of gates, ALL must hold:
                node:<id>>=<rank>       another node at a rank
                spent:<category>>=<n>   points sunk into that category — the tier gate
                boss:<id>               a Special defeated by this island
                tech_points>=<n>        lifetime earned, for coarse tiering
effects       typed payloads per rank, cumulative
icon / name / description   presentation only
```

Ranks are cumulative and never refundable in v1. Respec is a later question (**D6**).

**Ranks vary per node, on purpose** (owner, 2026-08-13, "wie in WoW"). A node is x/1, x/2,
x/3, x/5, x/7 — whatever the content supports. Flight is x/1 because you either fly or you do
not; Ocean is x/4 because there are four sensible ocean block sets; Border is x/7. There is no
separate `max_level` field to keep in sync with the cost list, because two sources of truth for
one number is how a rank ends up free or unreachable. Chat always renders the rank as
`2/5`, including `1/1`, so a mixed tree stays scannable.

**`spent:<category>>=n` is the tier gate, and it is what makes this a tree rather than a
shopping list.** It does not care *which* nodes were bought, only that the island committed to
the branch — so there are several routes to the same tier, and going deep in one branch is a
real alternative to buying every cheap rank first. The OneBlock category tiers at 5, 15, 30 and
50 points spent. Node prerequisites still exist alongside it for the cases where one specific
thing must come first (End needs Nether ≥ 3, whatever else was bought).

### 4.2 Effect payloads

Typed, because a stringly-typed effect field cannot be validated at load:

| Type | Payload | Consumed by |
|---|---|---|
| `oneblock_biome` | biome id, tier | `OneBlockLootTable` pool assembly |
| `oneblock_yield` | biome id, double-drop chance | the break path |
| `chest_chance` | added chance | `ChestLoot` |
| `border_size` | side length | `GridMath.borderSizeAt` |
| `island_int` | key (`max_biome_regions`, `max_party_size`), value | per-island override of a config constant |
| `player_effect` | key (`hunger`, `regen`, `fall`, `fly`), magnitude | the member tick service |
| `pokemon_work` | type, axis (`speed`/`hp`/`resist`/`threshold`/`sleep_regen`), magnitude | the labour service |
| `boost_unlock` | boost id, strength, duration | `BuffService` |
| `unlock_flag` | free-form string | anything that just needs a boolean |

`island_int` is the one that needs care: `max_biome_regions` and `max_party_size` are
server-wide constants today. Turning them into per-island values means deciding whether the
config value becomes a **cap** or a **starting value** (**D5**).

### 4.3 Storage

Migration **9**:

```
island_tech        (island_id, node_id, level, unlocked_at)   PK (island_id, node_id)
island_claims      (island_id, source_id, claimed_by, claimed_at)  PK (island_id, source_id)
islands.tech_points                                            new column
```

`island_claims` is the answer to §3.2c and to the NPC question — one row means "this island
has already banked this source", whoever triggered it.

`islands.points` (the mining counter) stays where it is and keeps its meaning; the tree
spends the **new** `tech_points` column. Existing islands keep `points` and `border_level`
untouched, so the border nodes need to be granted for free up to the island's current level on
migration (**D7**).

**Reset and purge semantics** (owner, 2026-08-13): resetting or deleting an island wipes its
tech data with everything else — `island_tech`, `island_claims` and `tech_points` all go. The
island starts over, and every NPC, advancement and boss becomes claimable again for it. This
is what makes a fresh island equivalent to a day-one island (§3.2d), so it is a rule, not
cleanup: the deletion must live in the same place as the rest of the island teardown in
`IslandManagerImpl`, not in a separate maintenance pass that can drift out of sync.

### 4.4 Tree definition

Data-driven — a `techtree.json5` next to `loottable.json5`, written on first start with the
built-in default, same pattern and same failure mode as the loot table (log and skip a bad
entry, never fail to boot). Validation at load: unknown ids in `requires`, cycles, unknown
effect types, duplicate ids, unreachable nodes, non-monotonic costs.

---

## 5. The point economy

### 5.1 Sources

| Source | Grant | Claim key | Hook |
|---|---|---|---|
| NPC trainer / gym | 1–5 by tier | `npc:<trainer_id>` | `BATTLE_VICTORY`, `npc_losers` non-empty (§8) |
| Custom advancement | 1–3 | `adv:<advancement_id>` | mixin on `PlayerAdvancements#award` |
| Special boss | 5–25 | `boss:<boss_id>` | our own boss service |
| *(repeatable, pending D4)* | small, capped | not claimed | — |

Every one of them runs through a single `TechPointService.claim(island, sourceId, amount)`
that inserts into `island_claims` and returns false when the row already exists. One
choke point, so the dedup rule cannot be forgotten at a call site.

### 5.2 Trainer identity — the NPC question, answered

The right place for "has this island already beaten this trainer" is **our database, not
MoLang**. MoLang variables live on the NPC entity: they are per-entity, not per-island, they
are lost when the entity despawns or an admin replaces it, and they would have to be written
back on every battle. `island_claims` has none of those problems and is already needed for
advancements and bosses.

What MoLang *is* needed for is **stable identity**. Keying on the NPC's entity UUID means the
island's progress resets the moment an admin rebuilds the gym. Cobblemon NPC classes carry
arbitrary `config` variables that are settable in-game via NPC edit and readable from the NPC
struct (§8), so:

- give each trainer a `config` variable, e.g. `trainer_id = "gym_rock"` and `points = 3`
- on `BATTLE_VICTORY`, read them off the losing NPC actor
- claim `npc:gym_rock` for the winner's island

Rebuilding the entity, moving it, or running five copies of the same gym in five event areas
then all behave correctly, and admins add trainers without a mod update. An NPC without a
`trainer_id` grants nothing and is logged once, so a half-configured trainer is visible
instead of silently free.

### 5.3 Announcing it

A claim is the moment the whole economy pays off, and it happens far from the island. It must
be loud: message to the whole island, the source name, the points, and the running total.
Members offline at the time see it in `/ob info`.

---

## 6. The categories

Sketches, not final content. Only the OneBlock ladder is specified far enough to build.

### 6.1 OneBlock — build this first

Base pool (level 0, no purchases): grass block, dirt, cobblestone, oak planks. Deliberately
poor.

Each biome is a 0/5 ladder; every tier adds a curated block set to the pool with a weight, so
the pool *composition* shifts as the island invests. Forest as the worked example:

| Tier | Adds |
|---|---|
| 1 | oak log, oak leaves, saplings, stick-tier basics |
| 2 | birch and spruce sets, moss, ferns |
| 3 | jungle and acacia sets, bamboo, cocoa |
| 4 | cherry, mangrove, azalea, flowering azalea |
| 5 | rare forest payload — beehives, sweet berries, the biome's "jackpot" entries |

Cave, Nether, End, Ocean, Tundra follow the same 5-tier shape. On top of each ladder sits one
yield node (`forest_yield`: % chance the break drops twice) so a maxed biome stays worth
investing in.

`all_blocks` stops being the default and becomes a documented legacy mode for admins who
want the current behaviour.

### 6.2 Island

Border size (bought, replacing automatic levelling — this is what makes the tree a real
choice), biome editor selections, `max_biome_regions`, `max_party_size`, placement unlocks.

### 6.3 Players

Applied to members while inside their own island border, by a tick service, cheapest first:
reduced hunger drain → passive regeneration that works below the vanilla hunger threshold →
reduced fall damage → `/fly` last, gated, border-scoped (§3.2g).

### 6.4 Pokémon

See §7 — the largest area, and the last one to build.

### 6.5 Boosts

Timed, island-wide, activated on demand with a cooldown, not permanently on. The tree buys
**strength** and **duration**, the activation is free. `BuffService` already does timed,
island-scoped, restart-persistent state, so the storage side is done.

- Shiny boost: 1.5× → 5×, 10 min → 1 h, as a multiplier (§3.2f)
- Typing spawn boost: raises a typing's share of spawns (§3.2e), never the total

### 6.6 Special

Raids and bosses spawned onto the island, island members only. Two jobs, and the second is the
important one: they pay the largest point grants, and they are the `boss:<id>` gates that open
branches. Ships late; the gate syntax ships in §4.1 immediately so the tree is built around it
from the start.

---

## 7. Pokémon labour

### 7.1 The rule that keeps it from being overpowered

**A working Pokémon never creates a resource. It finishes, accelerates or automates work the
player has already set up.** A Fire type smelts what is already in the furnace; it does not
produce ore. A Grass type harvests crops the player planted; it does not plant them from
nothing. Every job is measured against this rule before it is written.

This matters more than any number, because it caps the *category* of benefit rather than its
magnitude — a mistuned job costs the server some convenience, it cannot mint items.

Three further constraints: work only inside the owner's island border, only within a radius
of the owner (so labour is something you supervise, not a background farm), and only one
active worker per player unless the tree says otherwise.

### 7.2 Stat roles — four axes, no overlap

The design's open question was what Attack and Special Attack should do. Making them yield
gives every stat group exactly one job:

| Stat | Role | Effect |
|---|---|---|
| Speed | **Rate** | shorter interval between work actions |
| HP | **Endurance** | more actions before the sleep threshold — requires flat drain (§3.2b) |
| Defence / Sp. Def | **Efficiency** | less HP drained per action |
| Attack / Sp. Atk | **Yield** | chance of a bonus result from an action |

Base stats, IVs and EVs feed these directly, which is what makes a trained Pokémon visibly
better at work without being a separate progression system. The tree then upgrades the same
four axes **per type**, plus the sleep threshold and sleep regeneration.

Throughput is bounded by the duty cycle — actions before sleep, divided by work time plus
sleep time. Upgrades raise the ceiling; they cannot remove the sleep, which is what keeps the
whole system from becoming an idle farm.

### 7.3 Type → job

Ship the three marked **▶** first; they are the three distinct shapes (transform, harvest,
haul) and every other type is a variant of one of them.

| Type | Job |
|---|---|
| ▶ Fire | smelts loaded furnaces and cooks food on lit campfires, without fuel |
| ▶ Grass | harvests mature crops and replants from the drop |
| ▶ Psychic | collects dropped items in a radius and files them into nearby chests |
| Water | waters farmland, fills cauldrons, extinguishes fires |
| Electric | speeds up furnaces, brewing stands and campfires already running |
| Ice | freezes water sources to ice, produces snow |
| Fighting | hauls stacks between containers the owner marked |
| Poison | runs composters from organic drops |
| Ground | tills farmland, fills holes inside the border |
| Flying | moves items between distant containers on the island |
| Bug | pollinates crops for a growth tick, harvests honey |
| Rock | flattens and levels terrain inside the border |
| Ghost | works only at night, at double rate |
| Dark | suppresses hostile spawns in a radius |
| Steel | slowly repairs damaged tools in the owner's inventory |
| Fairy | slowly heals the owner and cures effects |
| Dragon | small work-rate bonus to every other worker on the island |
| Normal | performs any unlocked job at reduced efficiency |

Dual-typed Pokémon do the job of their primary type unless that job is not unlocked.

### 7.4 The sleep threshold, and why level 0 has none

Owner's decision (2026-08-13), and it is the sharpest mechanic in the labour design: the
threshold does not *start* at a value and get better — it **does not exist** until the first
upgrade in that type's branch is bought.

| Threshold level | Stops working at | What it feels like |
|---|---|---|
| 0/5 | never — works until it faints | maximum uptime, guaranteed loss; unsustainable |
| 1/5 | 75 % HP | safe, and noticeably slower than 0/5 |
| 2/5 | 50 % | |
| 3/5 | 25 % | |
| 4/5 | 10 % | |
| 5/5 | 5 % | safe *and* fast |

The curve is the point. Buying the first level is a **downgrade in raw uptime** and an upgrade
in everything else — the player trades throughput for not having to walk to a healer. Levels
2–5 then buy the throughput back while keeping the safety. So the branch reads: dangerous and
unsustainable → safe but slow → safe and fast, which is a genuine progression rather than a
number going up five times.

A Pokémon at 0/5 that faints from work is **working as designed** and must not be logged as a
bug; the `POKEMON_FAINTED` safety net in §8 applies only to Pokémon whose type branch has the
threshold node at 1 or higher.

Sleep regeneration is a separate axis and shortens the nap, so the duty cycle improves along
two independent lines — time spent working, and time spent recovering.

---

## 8. Cobblemon API ground truth (verified against tag `1.7.3`, 2026-08-13)

Read from `gitlab.com/cable-mc/cobblemon` at tag `1.7.3`, not from memory. Re-verify against
the tag before relying on any of it.

| Need | Hook | Notes |
|---|---|---|
| NPC / gym defeat | `CobblemonEvents.BATTLE_VICTORY` → `BattleVictoryEvent` | `winners` / `losers` as `List<BattleActor>`, plus a `context` map already split into `player_winners`, `npc_losers`, `scriptable_losers`, … — exactly what §5.2 needs |
| Trainer identity | NPC class `config` variables | arbitrary `STRING`/`NUMBER`/`BOOLEAN` vars, editable in-game, readable as `c.npc.config.<name>` in the NPC's MoLang env |
| Shiny rate | `CobblemonEvents.SHINY_CHANCE_CALCULATION` | fired in `PokemonProperties.roll`. **`shinyRate` is a divisor** (config default 8192 = 1/8192), so a boost must *divide*. `addModifier` **adds** and would make shinies rarer — use `addModificationFunction { rate, _, _ -> rate / m }`. The event's own `isShiny()` helper is not used by this call path; ignore it |
| Spawn control | `CobblemonEvents.ENTITY_SPAWN` (aliased `POKEMON_ENTITY_SPAWN`) | `CancelableObservable<SpawnEvent<*>>` — cancelable, which is what makes §3.2e possible. Already subscribed in `CobblemonIntegration` |
| Pokémon out of ball | `POKEMON_SENT_POST` / `POKEMON_RECALL_POST` | start and stop the labour service per entity |
| Fainting | `POKEMON_FAINTED` | needed as a safety net for §3.2a: if a worker ever faints, that is a bug — log it |
| NPC content authoring | `npcs` / `npc_presets` datapack folders | classes, presets, party providers (`simple`, `pool`, `script`), dialogue, `skill` 1–5. Gyms are datapack content, not mod code |

Also confirmed present and useful later: `POKEMON_CAPTURED`, `LEVEL_UP_EVENT`,
`EVOLUTION_COMPLETE`, `POKEMON_SCANNED`, `BERRY_HARVEST`, `HATCH_EGG_POST`.

**Not verified yet:** which `ServerPlayer` Cobblemon passes into `roll(pokemon, player)` for
an ambient wild spawn. It may be null. The design in §3.2f does not depend on it — it keeps
the existing position-based island lookup — but any future per-player approach must check
this first.

Which player owns a given island is **already solved** in code: `IslandManagerImpl` maintains
an `activeByPlayer` map covering owner and members, exposed as
`OneBlockAPI.islandManager().islandOf(uuid)`. Nothing new is needed for it.

---

## 9. Open decisions

Nothing gets built from a ⬜. Mark ✅ with date and reason when settled.

**Settled by the owner's design of 2026-08-13:**

- ✅ **Tech tree replaces linear border levels.** Six categories, per island.
- ✅ **Nodes unlock this mod's own knobs**, not crafting recipes. Minecraft's recipe book is
  not a gate, and faking one needs mixins into recipe matching: most work, worst result.
- ✅ **Border size is bought, not automatic.** Otherwise the biggest reward still happens on
  its own and the tree is a side-show.
- ✅ **Owner spends, delegable to members.** `IslandRole` and the permission plumbing exist.
- ✅ **Data-driven tree** (`techtree.json5`), default written on first start.
- ✅ **Points come from NPCs, advancements and bosses.** Not EXP, not block breaking.

**Settled 2026-08-13 (second round):**

### D1 ✅ Chest menu for the tree

Owner's call, matching §3.2i: one item per node with icon, name, lore and a live `2/5`,
category tabs in the top row, click to buy. The written book stays available as a possible
island codex later; it is not the tree.

### D2 ✅ At 0/5 there is no threshold — the Pokémon works until it faints

Owner's call, and a better answer than either option offered: the threshold is created by the
first upgrade rather than starting at a value and improving. Full curve and reasoning in §7.4.
"Dies" is implemented as fainting; permanent loss is not implemented and would be its own
decision.

### D4 ✅ Progression is finite, one-shot per island, and resets with the island

Owner's call. No repeatable source. Claims are island-scoped, so new islands find all content
unclaimed (§3.2d) — the ceiling only binds an island that has claimed everything. Resetting or
deleting an island wipes `island_tech`, `island_claims` and `tech_points` (§4.3).

**Consequence that is now a hard build constraint:** the total points obtainable must exceed
the total cost of the tree, or the last nodes are unreachable for every island. Both sums get
computed in `techtree.json5` validation and logged at startup.

**Still open:**

### D3 ⬜ How many types in the first labour slice?

§3.2h recommends three (Fire, Grass, Psychic), data-driven so the rest is JSON. Blocks slice
H only, which is last — safe to leave open for now.

### D5 ⬜ Do `max_party_size` and `max_biome_regions` become caps or starting values?

The config constants become per-island once nodes touch them. A *cap* keeps admin control; a
*starting value* lets the tree exceed it. Affects the wizard text either way.

### D6 ⬜ Respec?

Refunding spent points needs a reason (a node's effect is no longer wanted) and a price. v1
recommendation: no respec, revisit once the tree has been played.

### D7 ⬜ Migration for existing islands.

Nothing is live and PR #1 is unmerged, so this is cheap now and expensive later. Recommended:
grant border nodes matching the island's current `border_level` for free, keep `points` as the
mining counter, start `tech_points` at 0. Needs migration 9 (schema is at 8).

---

## 10. Build plan

Ordered so the mod is playable at every stop, and so the largest risk is not also the first
thing built. Slices A–C are the ones that must be right.

- **A — Tree core. ✅ shipped 2026-08-13.** Node model, `techtree.json5` with validation,
  migration 9, `TechPointService` with island-scoped claims, `/ob tech` over chat, an admin
  grant command for testing before any real source exists. No payloads yet.
- **B — The OneBlock ladder.** Biome tiers feeding `OneBlockLootTable`, yield nodes, chest
  chance. **This is the slice where the game actually changes** — §3.1.
- **C — Real point sources.** NPC victories via `BATTLE_VICTORY` + trainer `config` ids,
  the advancement mixin, and the announcement. Until this lands the tree has no economy.
- **D — Chest menu.** Presentation over a model that already works.

  Layout, decided with the owner 2026-08-13: a 9×6 chest menu is nine columns by six rows,
  which is exactly the shape of a talent tree — **one row per tier.** Top row = the six
  category tabs. Rows 2-6 = the tiers of the open category, in `spent:` order, so a player
  reads the branch top to bottom the way they would in WoW. Each node is one item: icon from
  the node, display name, rank as `2/5` in the name, cost and unmet requirements in the lore,
  click to buy. Locked tiers render as grey panes with "N more points in this branch".

  This is the reason the tier gates were worth building before the GUI: without them the menu
  would be an unordered grid of nodes with no vertical meaning.
- **E — Island and Players categories.** Mostly re-pointing existing knobs at per-island
  values; `/fly` last and gated.
- **F — Boosts.** Shiny multiplier conversion (§3.2f), typing spawn share (§3.2e),
  activation command, cooldowns.
- **G — Special.** Bosses and raids, and the `boss:<id>` gates going live.
- **H — Pokémon labour.** Its own project. Three types, data-driven jobs, the four stat axes,
  sleep cycle, then the remaining 15 as content.

**A–C is the minimum that is worth playing.** Everything from D on is additive.

---

## 11. Change log for this rework

Append one entry per slice, newest at the bottom. No code shipped yet.

| Date | Slice | What changed | Verified how |
|---|---|---|---|
| 2026-08-13 | — | Document created. Analysis of the current system, the Palworld reframe, candidate unlocks, seven open decisions. No code touched. | n/a |
| 2026-08-13 | — | Rewritten for the owner's full design: six categories, biome ladders, Pokémon labour, boosts, boss gates, content-based point economy. Added the review (§3), the node/effect/storage model (§4), the claim-based point economy answering the NPC identity question (§5), the labour design with four stat roles (§7), and Cobblemon API ground truth read from tag 1.7.3 (§8). Six of the original decisions settled by the design; seven new ones opened. No code touched. | Cobblemon hooks in §8 read from the 1.7.3 tag; the codebase claims in §1 and §3.2f read from the working tree |
| 2026-08-13 | A | Tree core shipped: `TechNode`/`TechEffect`/`TechRequirement` model, `TechTree` validation, `TechTreeFile` reading `techtree.json5` from a bundled default, migration 9 (`island_tech`, `island_claims`, two point columns, `may_spend_tech`), `TechService` as the only writer of levels and balances, `TechPointService` as the only entry for content-driven points, `/ob tech` with list/info/unlock/delegate/grant. Tech data is wiped on purge, not on archive, so a restore still works. | `./gradlew build` green; dev server booted with Cobblemon 1.7.3, migration 9 applied (schema 9), default tree written and loaded (22 nodes, 5 categories, 459 points), `/ob tech` reached its executor. Validation exercised by injecting eight broken nodes: unknown category, dangling requirement, level above maximum, two-node cycle, duplicate id, unknown effect type, out-of-range effect level, unparseable requirement — every one was caught, logged and dropped, including the correct cascade onto two nodes that depended on a dropped one. **Not played:** no island exists on a headless server, so buying a node end-to-end is untested. |

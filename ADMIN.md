# Cobblemon OneBlock — Server Admin Guide

Everything needed to run this on a public server.

---

## 1. Installation

A fresh server needs exactly four jars in `mods/`:

| Mod | Why |
|---|---|
| Fabric API | required by both mods |
| Fabric Language Kotlin | Cobblemon and this mod are written in Kotlin |
| Cobblemon 1.7.3+ (for MC 1.21.1) | hard dependency |
| `cobblemon-oneblock-core-<version>.jar` | this mod — the API and all libraries are bundled inside |

Start the server, join as an operator, and follow the setup wizard. Nothing else is
required: storage defaults to an embedded SQLite file, so there is no database to set up.

## 2. First start

On the first launch the mod creates its three void dimensions, generates a hub platform at
`(0, 64, 0)` in **each** of them — smooth stone in `cobblemon_oneblock:world`, polished
blackstone in `:nether`, end stone bricks in `:end` — and writes its config files. All three
sit inside the same `hub_radius` protection circle, so each one is an admin-built area for
whatever belongs in that dimension. Island creation stays locked until an operator finishes
the wizard.

Run `/ob setup` and answer seven questions (or click `[keep …]` to accept a default):

| # | Setting | Default | Range |
|---|---|---|---|
| 1 | Hub protection radius | 1000 | 100–5000 |
| 2 | Max island size at border level 8 | 1024 | 64–10000 |
| 3 | Max party size (including the owner) | 4 | 1–20 |
| 4 | Max biome regions per island | 10 | 1–50 |
| 5 | Cobblemon spawn multiplier | 1.0 | 0.1–5.0 |
| 6 | Public server? | yes | — |*
| 7 | Admins may build in the hub? | no | — |

\* "Public server" controls `/ob visit`: when it is off, players can only teleport to an
island they own or belong to. Island owners keep their own control either way through
`/ob ban`, and visitors can never build, catch or battle unless the owner allows it.

Every question also works from the console:

```
/ob setup set max_island_size 2048
```

> **Do not shrink or grow `max_island_size` once islands exist.** The grid spacing is derived
> from it, so changing it moves every island's anchor point and existing builds end up in the
> wrong place. Decide this at setup time.

## 3. Configuration files

`config/cobblemon_oneblock/`

| File | Contents |
|---|---|
| `main.json5` | all gameplay settings, written with a comment per field |
| `database.json5` | storage backend |
| `oneblock.json5` | what the OneBlock turns into, per dimension, and the treasure chests |
| `data.db` | the SQLite database (only in SQLite mode) |
| `audit.log` | moderation audit trail, one JSON object per line |

`/ob reload` re-reads all of them, rebuilds the loot pool, reconnects the database and
re-applies the spawn multiplier — no restart needed. Set `"language": "de_de"` in
`main.json5` for German player messages.

### Loot table

By default the OneBlock can turn into **every** block from Minecraft, Cobblemon and any other
installed mod, filtered down to blocks that are safe as a lone floating block: no fluids,
nothing unbreakable, nothing without an item form, no melting ice, and nothing that pops off
without support. Gravity blocks (sand, gravel, anvils) are included and are prevented from
falling while they sit on the anchor.

Switch `"mode": "custom"` to use the weighted `entries` list instead, or add block ids to
`"blacklist"` to exclude single blocks in either mode (for example `"minecraft:tnt"`).

**Polymer content is excluded by default** (`"exclude_polymer": true`). Blocks and items
registered through [Polymer](https://github.com/Patbox/polymer) only exist on the server —
a vanilla client sees a stand-in block instead — so mining them confuses players. Detection
works without Polymer installed; on a server without it the setting simply does nothing.

### Treasure chests

A share of breaks turns the OneBlock into a **loot chest** instead of a plain block. The
chest is filled from a real chest loot table — the same ones world generation uses for
mineshafts, dungeons, temples, villages, strongholds, shipwrecks and so on. Contents are
vanilla's, not this mod's: change them with a data pack and the OneBlock follows.

Settings live in the `chests` section of `oneblock.json5`:

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | chests on or off |
| `chance` | `0.02` | share of breaks producing a chest — 2%, roughly one per 50 blocks. Clamped to `0.0`–`1.0` |
| `include_modded` | `false` | also use non-`minecraft:` loot tables that follow the `chests/...` naming convention |
| `blacklist` | `[]` | loot table ids never used |
| `extra` | `[]` | loot table ids added verbatim, whatever their path |

The pool is discovered from the server's loot table registry at startup, so a data pack
that adds `chests/...` tables is picked up without a code change. `/ob reload` rebuilds it.
Vanilla 1.21.1 alone contributes 56 tables.

**The chance never scales with border level.** Difficulty scales on this project, rewards
do not — see `PROJECT_PLAN.md`.

**About Cobblemon loot.** You do not need to configure anything to get it: Cobblemon
*injects* its items into the vanilla chest loot tables, so the normal pool already hands
out Cobblemon content. What the pool does *not* pick up is Cobblemon's own structure chest
loot, because it ignores the `chests/...` convention — `include_modded` cannot find it
either. List those explicitly under `extra` if you want them:

```json5
"extra": [
  "cobblemon:ruins/gilded_chests/ruins",
  "cobblemon:shipwreck_coves/gilded_chests/big_treasure",
]
```

Ids that do not parse or do not exist on this server are named in the log and skipped —
a typo costs you one entry, never the whole pool.

### OneBlock drops and the anchor foundation

Two settings in `main.json5`, both applied by `/ob reload`. They are not wizard questions —
edit the file.

| Key | Default | Meaning |
|---|---|---|
| `oneblock_drops_to_inventory` | `true` | everything the OneBlock drops, experience included, goes straight to the breaker |
| `anchor_bedrock_foundation` | `false` | keep an indestructible bedrock block one below every anchor |

**Why drops go to the inventory.** The anchor floats over the void. The replacement block is
placed in the same tick the old one breaks, so the drops spawn *inside* it, get pushed out
sideways and fall out of the world. Sending them to the breaker is the fix. If the inventory
is full the remainder lands at the player's feet instead of over the void. Only the anchor is
covered — blocks a player places and re-breaks elsewhere on the island drop normally, which
is ordinary skyblock behaviour.

Fortune, Silk Touch and any drop another mod adds all still work, because vanilla still does
the dropping — the items are collected off the ground, not recomputed. The contents of a
broken treasure chest come along for the same reason.

**Why the bedrock is off.** It is a visible slab under what should look like a single floating
block, and once drops go to the inventory nothing depends on it. What it still buys you is one
rare case: if the anchor ever ends up empty — an explosion, a stray command, another mod — a
player standing there falls into the void before the 60-second repair sweep restores it.
Turning the setting on re-places the bedrock; turning it off removes it again, and only
bedrock, so a block a player put under their own anchor is left alone.

> **If you run without the bedrock, consider blacklisting `minecraft:cobweb`** in
> `oneblock.json5`. It is the one block in the pool with no collision that still survives as
> a lone floating block: a player standing on the anchor when it turns into cobweb drifts down
> into the void instead of standing on something.

### Switching to MySQL / MariaDB

SQLite is fine for a single server. For a network, edit `database.json5`:

```json5
{
  "storage": "mysql",
  "mysql": { "host": "…", "port": 3306, "database": "cobblemon_oneblock", "user": "…", "password": "…" }
}
```

Restart. The schema is created and migrated automatically. **Existing SQLite data is not
migrated** — switch before the server goes live, or move the data yourself.

## 4. Backups

The database and the world must be backed up **together and from the same moment**. Islands
are stored in the database, their builds in the world files; restoring one without the other
leaves islands pointing at the wrong terrain. Stop the server, back up `world/` and either
`config/cobblemon_oneblock/data.db` or your MySQL dump, then start again.

## 5. Permission nodes

Permissions resolve in two steps: if LuckPerms (or another provider it fronts) is installed
and a node is **explicitly set**, that wins — grant or deny. If the node is undefined, or no
permission mod is present, the mod falls back to the vanilla OP level. So the mod works with
zero configuration and still gives networks full control.

### Player commands (OP level 0 — everyone by default)

| Node | Command |
|---|---|
| `cobblemon_oneblock.command` | access to `/ob` at all |
| `cobblemon_oneblock.command.create` | `/ob create` |
| `cobblemon_oneblock.command.home` | `/ob home` |
| `cobblemon_oneblock.command.spawn` | `/ob spawn` |
| `cobblemon_oneblock.command.visit` | `/ob visit <player>` |
| `cobblemon_oneblock.command.info` | `/ob info` |
| `cobblemon_oneblock.command.reset` | `/ob reset` |
| `cobblemon_oneblock.command.delete` | `/ob delete` |
| `cobblemon_oneblock.command.party` | `/ob party invite\|accept\|deny\|kick\|leave\|list` |
| `cobblemon_oneblock.command.settings` | `/ob settings visitor-catch\|visitor-battle` |
| `cobblemon_oneblock.command.biome` | `/ob biome pos1\|pos2\|set\|list\|remove` |
| `cobblemon_oneblock.command.ban` | `/ob ban\|unban\|bans` (own island) |
| `cobblemon_oneblock.command.rename` | `/ob rename <name>`, `/ob rename clear` (owner only) |

### Admin commands (OP level 4 by default)

| Node | Command |
|---|---|
| `cobblemon_oneblock.admin.setup` | `/ob setup` |
| `cobblemon_oneblock.admin.reload` | `/ob reload` |
| `cobblemon_oneblock.admin.buff` | `/ob buff <player> <type> <value> <minutes>` |
| `cobblemon_oneblock.admin.moderate` | `/ob admin kick\|info\|reset\|delete\|portal\|unlock` |
| `cobblemon_oneblock.admin.bypass` | ignore island protection, build in the hub, catch/battle anywhere (OP level 2) |

Example: let everyone play but restrict moderation to a staff group.

```
lp group default permission set cobblemon_oneblock.command true
lp group moderator permission set cobblemon_oneblock.admin.moderate true
lp group moderator permission set cobblemon_oneblock.admin.bypass true
```

## 6. Protection precedence

The first rule that applies wins:

1. **Hub zone** — nobody builds or breaks inside `hub_radius`, in any of the three dimensions.
   Exception: `cobblemon_oneblock.admin.bypass` holders when `hub_allow_building` is on.
2. **Admin bypass** — `cobblemon_oneblock.admin.bypass` ignores all island rules from here on.
3. **Island ban** — banned players are bounced to the hub regardless of anything else.
   Owners and members cannot be banned from their own island.
4. **Island membership** — owner and members may do anything inside the island's *current*
   border area; visitors may enter and look only, unless the owner opened catching or
   battling via `/ob settings`.
5. **Border limit** — even owners cannot touch the part of their footprint beyond the current
   border level. It unlocks by levelling up.
6. **Void buffer / unregistered chunks** — nobody may modify anything (deny by default).

Other dimensions are never policed by this mod.

## 7. Moderation

`/ob admin info <player>` shows a player's island id, slot, OneBlock coordinates, border
level, break count and party usage — start here for support tickets.

`/ob admin reset <player>` and `/ob admin delete <player>` require a click confirmation and
work from the console too. Both archive the island: it stays restorable for
`reset_archive_days` (default 7) before the purge removes it.

Every moderation action is written to `config/cobblemon_oneblock/audit.log` as a single JSON line and
published to addons via the API. Set `discord_webhook_url` in `main.json5` to mirror the
audit trail into a Discord channel. All of this happens off the server thread — a dead
webhook cannot lag the server.

## 8. Hub travel: portals and unlocks

Each dimension has its own hub, and the way between them is a portal **you** build. The mod
places none of them and does not care what kind of frame it is: an End portal may lead to the
Nether hub.

1. Build the portal anywhere in one of the mod's dimensions — any frame, any size — and light it.
2. Run `/ob admin portal link nether` (or `overworld`, or `end`).
3. Click the portal within 120 seconds. Clicking a frame block right next to it works too, so
   you do not have to hit the thin portal surface. Left click works as well as right click.
4. The chat says `Connected to the Nether hub`. From now on that portal leads there.

`/ob admin portal list` shows every linked portal with its dimension, corner and target.
`/ob admin portal unlink` plus a click removes a binding, and the portal goes back to leading
into the island's own half of the world.

Re-linking a portal that is already bound replaces the old binding rather than adding a second
one. A binding is stored as the box around the portal's blocks, so putting the portal out and
lighting it again keeps it — rebuilding it somewhere else does not.

### Who may travel

The Overworld hub is open to everyone. The Nether and End hubs need an unlock **per player**:

| Unlock | Opens |
|---|---|
| `hub.nether` | travel to the Nether hub |
| `hub.end` | travel to the End hub |

```
/ob admin unlock grant <player> hub.nether
/ob admin unlock revoke <player> hub.nether
/ob admin unlock list <player>
```

Granting by hand is the only way today, on purpose: the condition these unlocks are meant to
carry is a quest, the quest system does not exist yet, and handing them out automatically
would make the gate meaningless. A player without the unlock who walks into a linked portal
stays where they are and is told why.

## 9. Automatic housekeeping

Once an hour the server archives islands whose owner has been inactive for
`inactivity_purge_days` (default 90; `0` disables it), purges archives past
`reset_archive_days`, and drops expired island buffs. Grid slots of purged islands are **not**
reused yet, because the old builds are still standing there — the spiral is effectively
endless, so this costs nothing in practice.

## 10. Temporary island buffs

```
/ob buff <player> shiny_rate 0.05 60     # 5% shiny chance for 60 minutes
/ob buff <player> iv_floor 25 30         # every IV at least 25, for 30 minutes
```

Buffs apply to Pokémon **spawning** on that island from that moment on, and survive restarts.
Note that spawn *rate* is deliberately not an island buff: Cobblemon's spawner is
player-centric and globally paced, so a per-island boost could only be faked. Use the global
`cobblemon_spawn_multiplier` instead.

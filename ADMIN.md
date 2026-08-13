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

On the first launch the mod creates the `cobblemon_oneblock:world` void dimension, generates the hub
platform at `(0, 64, 0)` and writes its config files. Island creation stays locked until an
operator finishes the wizard.

Run `/ob setup` and answer eight questions (or click `[keep …]` to accept a default):

| # | Setting | Default | Range |
|---|---|---|---|
| 1 | Hub protection radius | 1000 | 100–5000 |
| 2 | Max island size at border level 8 | 1024 | 64–10000 |
| 3 | Max party size (including the owner) | 4 | 1–20 |
| 4 | Breaks until a trigger event | 100 | 10–1000 |
| 5 | Max biome regions per island | 10 | 1–50 |
| 6 | Cobblemon spawn multiplier | 1.0 | 0.1–5.0 |
| 7 | Public server? | yes | — |*
| 8 | Admins may build in the hub? | no | — |

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
| `loottable.json5` | what the OneBlock turns into |
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

Settings live in the `chests` section of `loottable.json5`:

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

### Admin commands (OP level 4 by default)

| Node | Command |
|---|---|
| `cobblemon_oneblock.admin.setup` | `/ob setup` |
| `cobblemon_oneblock.admin.reload` | `/ob reload` |
| `cobblemon_oneblock.admin.buff` | `/ob buff <player> <type> <value> <minutes>` |
| `cobblemon_oneblock.admin.moderate` | `/ob admin kick\|info\|reset\|delete` |
| `cobblemon_oneblock.admin.bypass` | ignore island protection, build in the hub, catch/battle anywhere (OP level 2) |

Example: let everyone play but restrict moderation to a staff group.

```
lp group default permission set cobblemon_oneblock.command true
lp group moderator permission set cobblemon_oneblock.admin.moderate true
lp group moderator permission set cobblemon_oneblock.admin.bypass true
```

## 6. Protection precedence

The first rule that applies wins:

1. **Hub zone** — nobody builds or breaks inside `hub_radius`. Exception: `cobblemon_oneblock.admin.bypass`
   holders when `hub_allow_building` is on.
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

## 8. Automatic housekeeping

Once an hour the server archives islands whose owner has been inactive for
`inactivity_purge_days` (default 90; `0` disables it), purges archives past
`reset_archive_days`, and drops expired island buffs. Grid slots of purged islands are **not**
reused yet, because the old builds are still standing there — the spiral is effectively
endless, so this costs nothing in practice.

## 9. Temporary island buffs

```
/ob buff <player> shiny_rate 0.05 60     # 5% shiny chance for 60 minutes
/ob buff <player> iv_floor 25 30         # every IV at least 25, for 30 minutes
```

Buffs apply to Pokémon **spawning** on that island from that moment on, and survive restarts.
Note that spawn *rate* is deliberately not an island buff: Cobblemon's spawner is
player-centric and globally paced, so a per-island boost could only be faked. Use the global
`cobblemon_spawn_multiplier` instead.

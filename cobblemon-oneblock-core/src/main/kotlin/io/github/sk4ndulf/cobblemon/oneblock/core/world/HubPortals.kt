package io.github.sk4ndulf.cobblemon.oneblock.core.world

import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.db.Database
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.structure.BoundingBox
import java.util.UUID

/**
 * Portals an admin has bound to a hub.
 *
 * The mod does not place these. An admin builds a portal by hand — any frame, any size,
 * Nether or End — runs `/ob admin portal link <target>` and clicks it. From then on that
 * portal leads to the chosen dimension's hub instead of doing what it would otherwise do.
 * Which kind of frame it is does not matter: an End portal may lead to the Nether hub.
 *
 * **A binding is stored as the box around the portal's blocks, not as the blocks themselves.**
 * That is what makes it survive the portal going out and being lit again — the blocks come
 * back in the same place — while still being exact enough that a portal two blocks away is a
 * different portal. A position only counts as bound if there is a portal block there *now*,
 * so the box being slightly generous for a non-rectangular portal costs nothing.
 *
 * Server-thread only.
 */
object HubPortals {

    /**
     * Upper limit on the blocks one portal may consist of. A 64x64 portal is 4096 blocks and
     * is already absurd; the cap is really there so a flood fill through something unexpected
     * cannot walk the whole world before it stops.
     */
    const val MAX_PORTAL_BLOCKS = 4096

    /** How long a `/ob admin portal link` stays armed, waiting for the click. */
    const val SELECTION_TIMEOUT_SECONDS = 120

    data class Binding(
        val dimension: ResourceKey<Level>,
        val box: BoundingBox,
        val target: ResourceKey<Level>,
        val linkedBy: UUID?,
        val linkedAt: Long,
    )

    /** What a player is currently pointing at a portal for. A null target means "unlink". */
    private data class Selection(val target: ResourceKey<Level>?, val expiresAt: Long)

    private val bindings = HashMap<ResourceKey<Level>, MutableList<Binding>>()
    private val selections = HashMap<UUID, Selection>()
    private var database: Database? = null

    // --- loading -------------------------------------------------------------------------

    fun reload(database: Database) {
        this.database = database
        bindings.clear()
        database.sync { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT dimension, min_x, min_y, min_z, max_x, max_y, max_z, target, linked_by, linked_at " +
                        "FROM hub_portals",
                ).use { result ->
                    while (result.next()) {
                        val dimension = OneBlockDimension.byShortName(result.getString("dimension"))
                        val target = OneBlockDimension.byShortName(result.getString("target"))
                        if (dimension == null || target == null) {
                            // A row written by a future version, or a hand-edited database.
                            OneBlockCore.LOGGER.warn("Skipping a hub portal row with an unknown dimension.")
                            continue
                        }
                        val box = BoundingBox(
                            result.getInt("min_x"), result.getInt("min_y"), result.getInt("min_z"),
                            result.getInt("max_x"), result.getInt("max_y"), result.getInt("max_z"),
                        )
                        bindings.getOrPut(dimension) { mutableListOf() }.add(
                            Binding(
                                dimension, box, target,
                                result.getString("linked_by")?.let(UUID::fromString),
                                result.getLong("linked_at"),
                            ),
                        )
                    }
                }
            }
        }
        val count = bindings.values.sumOf { it.size }
        if (count > 0) OneBlockCore.LOGGER.info("Loaded {} hub portals.", count)
    }

    fun all(): List<Binding> = bindings.values.flatten()

    // --- lookup, used by IslandPortals ------------------------------------------------------

    /**
     * The hub this portal position was bound to, or null for an ordinary portal.
     *
     * Cheap by design: this runs for every portal transition on the server, and on a server
     * with no bound portals at all it is a single failed map lookup.
     */
    fun targetFor(level: Level, pos: BlockPos): ResourceKey<Level>? =
        bindings[level.dimension()]?.firstOrNull { it.box.isInside(pos) }?.target

    // --- the click-to-bind flow ---------------------------------------------------------------

    fun register() {
        UseBlockCallback.EVENT.register { player, level, _, hit ->
            if (level is ServerLevel && player is ServerPlayer && handleClick(player, level, hit.blockPos)) {
                InteractionResult.SUCCESS
            } else {
                InteractionResult.PASS
            }
        }
        // Left click as well: a portal block is thin, and an admin in creative mode reaches
        // for the left button. Without this the click would break the frame instead.
        AttackBlockCallback.EVENT.register { player, level, _, pos, _ ->
            if (level is ServerLevel && player is ServerPlayer && handleClick(player, level, pos)) {
                InteractionResult.SUCCESS
            } else {
                InteractionResult.PASS
            }
        }
    }

    /** Arms a link (or, with a null target, an unlink) for the next portal this player clicks. */
    fun arm(player: ServerPlayer, target: ResourceKey<Level>?) {
        val now = System.currentTimeMillis()
        selections.values.removeIf { it.expiresAt < now }
        selections[player.uuid] = Selection(target, now + SELECTION_TIMEOUT_SECONDS * 1000L)
    }

    /** True when the click was consumed by a pending selection. */
    private fun handleClick(player: ServerPlayer, level: ServerLevel, clicked: BlockPos): Boolean {
        val selection = selections[player.uuid] ?: return false
        if (selection.expiresAt < System.currentTimeMillis()) {
            selections.remove(player.uuid)
            player.displayClientMessage(ServerLang.msg("cobblemon_oneblock.portal.selection_expired"), false)
            return true
        }

        val portal = findPortalBlock(level, clicked)
        if (portal == null) {
            // The selection stays armed: missing a portal block by one is the normal way this
            // goes wrong, and making the admin retype the command for it would be tedious.
            player.displayClientMessage(ServerLang.msg("cobblemon_oneblock.portal.not_a_portal"), false)
            return true
        }

        if (selection.target == null) unbind(player, level, portal) else bind(player, level, portal, selection.target)
        selections.remove(player.uuid)
        return true
    }

    /**
     * The portal block the admin meant: the one they clicked, or one right next to it.
     *
     * The neighbour search is what lets them click the obsidian frame instead of the portal
     * surface — which is the more natural thing to aim at, and the only thing they can aim at
     * if they are standing inside the portal.
     */
    private fun findPortalBlock(level: ServerLevel, clicked: BlockPos): BlockPos? {
        if (isPortalBlock(level, clicked)) return clicked
        return Direction.entries.map { clicked.relative(it) }.firstOrNull { isPortalBlock(level, it) }
    }

    private fun isPortalBlock(level: ServerLevel, pos: BlockPos): Boolean {
        val block = level.getBlockState(pos).block
        return block === Blocks.NETHER_PORTAL || block === Blocks.END_PORTAL
    }

    /**
     * Every portal block connected to this one. Returns null when the portal is larger than
     * [MAX_PORTAL_BLOCKS].
     */
    private fun floodFill(level: ServerLevel, start: BlockPos): Set<BlockPos>? {
        val found = HashSet<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        queue.add(start.immutable())
        found.add(start.immutable())
        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            for (direction in Direction.entries) {
                val next = pos.relative(direction).immutable()
                if (next in found || !isPortalBlock(level, next)) continue
                if (found.size >= MAX_PORTAL_BLOCKS) return null
                found.add(next)
                queue.add(next)
            }
        }
        return found
    }

    private fun bind(player: ServerPlayer, level: ServerLevel, portal: BlockPos, target: ResourceKey<Level>) {
        val blocks = floodFill(level, portal)
        if (blocks == null) {
            player.displayClientMessage(
                ServerLang.msg("cobblemon_oneblock.portal.too_big", MAX_PORTAL_BLOCKS), false,
            )
            return
        }
        val box = BoundingBox(
            blocks.minOf { it.x }, blocks.minOf { it.y }, blocks.minOf { it.z },
            blocks.maxOf { it.x }, blocks.maxOf { it.y }, blocks.maxOf { it.z },
        )

        // Re-linking an already bound portal replaces the old binding rather than adding a
        // second one, otherwise the first match would silently keep winning forever.
        val existing = bindings[level.dimension()] ?: mutableListOf()
        val replaced = existing.filter { overlaps(it.box, box) }
        for (old in replaced) {
            existing.remove(old)
            deleteAsync(old)
        }

        val binding = Binding(level.dimension(), box, target, player.uuid, System.currentTimeMillis())
        bindings.getOrPut(level.dimension()) { existing }.add(binding)
        insertAsync(binding)

        if (replaced.isNotEmpty()) {
            player.displayClientMessage(
                ServerLang.msg(
                    "cobblemon_oneblock.portal.replaced",
                    OneBlockDimension.displayName(replaced.first().target),
                ),
                false,
            )
        }
        player.displayClientMessage(
            ServerLang.msg(
                "cobblemon_oneblock.portal.linked",
                OneBlockDimension.displayName(target), blocks.size,
            ).withStyle(ChatFormatting.GREEN),
            false,
        )
        OneBlockCore.LOGGER.info(
            "{} linked the portal at {} in {} to the {} hub ({} blocks).",
            player.gameProfile.name, portal.toShortString(), level.dimension().location(),
            OneBlockDimension.shortName(target), blocks.size,
        )
    }

    private fun unbind(player: ServerPlayer, level: ServerLevel, portal: BlockPos) {
        val existing = bindings[level.dimension()]
        val binding = existing?.firstOrNull { it.box.isInside(portal) }
        if (binding == null) {
            player.displayClientMessage(ServerLang.msg("cobblemon_oneblock.portal.not_linked"), false)
            return
        }
        existing.remove(binding)
        deleteAsync(binding)
        player.displayClientMessage(ServerLang.msg("cobblemon_oneblock.portal.unlinked"), false)
        OneBlockCore.LOGGER.info(
            "{} unlinked the portal at {} in {}.",
            player.gameProfile.name, portal.toShortString(), level.dimension().location(),
        )
    }

    private fun overlaps(a: BoundingBox, b: BoundingBox): Boolean =
        a.maxX() >= b.minX() && a.minX() <= b.maxX() &&
            a.maxY() >= b.minY() && a.minY() <= b.maxY() &&
            a.maxZ() >= b.minZ() && a.minZ() <= b.maxZ()

    // --- persistence ---------------------------------------------------------------------------

    private fun insertAsync(binding: Binding) {
        database?.async { connection ->
            connection.prepareStatement(
                "INSERT INTO hub_portals (dimension, min_x, min_y, min_z, max_x, max_y, max_z, " +
                    "target, linked_by, linked_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ).use {
                it.setString(1, OneBlockDimension.shortName(binding.dimension))
                it.setInt(2, binding.box.minX())
                it.setInt(3, binding.box.minY())
                it.setInt(4, binding.box.minZ())
                it.setInt(5, binding.box.maxX())
                it.setInt(6, binding.box.maxY())
                it.setInt(7, binding.box.maxZ())
                it.setString(8, OneBlockDimension.shortName(binding.target))
                it.setString(9, binding.linkedBy?.toString())
                it.setLong(10, binding.linkedAt)
                it.executeUpdate()
            }
        }
    }

    private fun deleteAsync(binding: Binding) {
        database?.async { connection ->
            connection.prepareStatement(
                "DELETE FROM hub_portals WHERE dimension = ? AND min_x = ? AND min_y = ? AND min_z = ?",
            ).use {
                it.setString(1, OneBlockDimension.shortName(binding.dimension))
                it.setInt(2, binding.box.minX())
                it.setInt(3, binding.box.minY())
                it.setInt(4, binding.box.minZ())
                it.executeUpdate()
            }
        }
    }
}

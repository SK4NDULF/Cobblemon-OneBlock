package io.github.sk4ndulf.cobblemon.oneblock.core.world

import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.cobblemon.oneblock.core.unlock.Unlocks
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.portal.DimensionTransition
import net.minecraft.world.phys.Vec3

/**
 * Portals between an island's Overworld, Nether and End halves.
 *
 * **The rule: an island keeps its coordinates.** Slot 42's Nether island is at slot 42's X/Z,
 * so a portal is a straight move between worlds and you arrive where you left. That is why the
 * dimension types use `coordinate_scale: 1.0` — vanilla's 8:1 division would land a player in
 * a neighbour's plot, or in the void between two of them.
 *
 * **Why this exists at all.** Before it, a portal built on an island led to the *real* vanilla
 * Nether, and from there to the real Overworld — an infinite, unprotected world that made every
 * border and the whole island economy meaningless. The first fix simply blocked portals; this
 * replaces that with somewhere legitimate to go.
 */
object IslandPortals {

    /** Where a traveller lands, and where the arrival platform is built. */
    const val ARRIVAL_Y = 64

    /** Half-width of the platform poured under an arrival that would otherwise be a fall. */
    private const val PLATFORM_RADIUS = 1

    /**
     * The destination for an entity entering a Nether portal, or null to let vanilla decide.
     *
     * Null matters: this runs for *every* nether portal on the server, including ones in the
     * real Overworld that have nothing to do with us. Only portals inside our dimensions are
     * redirected.
     */
    @JvmStatic
    fun netherDestination(from: ServerLevel, entity: Entity, portalPos: BlockPos): DimensionTransition? {
        if (!OneBlockDimension.isOurs(from)) return null
        HubPortals.targetFor(from, portalPos)?.let { return hubDestination(from, entity, it) }
        val target = when (from.dimension()) {
            OneBlockDimension.NETHER_KEY -> OneBlockDimension.OVERWORLD_KEY
            else -> OneBlockDimension.NETHER_KEY
        }
        return transition(from, entity, portalPos, target) ?: refuse(from, entity)
    }

    /** The same for an End portal. Registered now; nothing places End portals yet. */
    @JvmStatic
    fun endDestination(from: ServerLevel, entity: Entity, portalPos: BlockPos): DimensionTransition? {
        if (!OneBlockDimension.isOurs(from)) return null
        HubPortals.targetFor(from, portalPos)?.let { return hubDestination(from, entity, it) }
        val target = when (from.dimension()) {
            OneBlockDimension.END_KEY -> OneBlockDimension.OVERWORLD_KEY
            else -> OneBlockDimension.END_KEY
        }
        return transition(from, entity, portalPos, target) ?: refuse(from, entity)
    }

    /**
     * What happens when a portal in one of our dimensions has nowhere legitimate to go —
     * it stands in the hub, in the void buffer, or on no island at all.
     *
     * **This must never return null.** Null means "vanilla decides", and vanilla's decision
     * from here is the real, infinite Nether — the exact hole this class exists to close. So
     * a refusal is still a transition: back to the hub, which is somewhere safe and obvious.
     */
    /**
     * Where a portal an admin bound with `/ob admin portal link` leads: that dimension's hub.
     *
     * The unlock is checked here rather than at the command, because it is the traveller who
     * needs it, not the admin who built the portal. A player without it is left standing where
     * they are — the portal simply does not work for them, which is easier to read than being
     * moved somewhere they did not ask for. Vanilla's portal cooldown keeps that from
     * repeating every tick while they stand in it.
     *
     * Non-player entities are never gated: an unlock is something a player earns.
     */
    private fun hubDestination(from: ServerLevel, entity: Entity, target: ResourceKey<Level>): DimensionTransition {
        val level = from.server.getLevel(target) ?: run {
            OneBlockCore.LOGGER.error(
                "A portal is linked to the {} hub, which is not loaded — travel refused.", target.location(),
            )
            return refuse(from, entity)
        }

        val player = entity as? ServerPlayer
        val required = Unlocks.hubUnlockFor(target)
        if (player != null && required != null && !Unlocks.has(player.uuid, required)) {
            player.displayClientMessage(
                ServerLang.msg("cobblemon_oneblock.portal.locked", OneBlockDimension.displayName(target))
                    .withStyle(ChatFormatting.RED),
                true,
            )
            return DimensionTransition(from, entity, DimensionTransition.DO_NOTHING)
        }

        HubManager.ensureFloor(level)
        return DimensionTransition(
            level,
            Vec3(HubManager.spawnPos.x + 0.5, HubManager.HUB_Y.toDouble(), HubManager.spawnPos.z + 0.5),
            Vec3.ZERO,
            entity.yRot,
            entity.xRot,
            DimensionTransition.PLAY_PORTAL_SOUND,
        )
    }

    private fun refuse(from: ServerLevel, entity: Entity): DimensionTransition {
        val overworld = OneBlockDimension.overworld(from.server) ?: from
        OneBlockCore.LOGGER.debug("Portal outside any island footprint — sending {} to the hub.", entity.name.string)
        return DimensionTransition(
            overworld,
            Vec3(HubManager.spawnPos.x + 0.5, HubManager.spawnPos.y.toDouble(), HubManager.spawnPos.z + 0.5),
            Vec3.ZERO,
            entity.yRot,
            entity.xRot,
            DimensionTransition.PLAY_PORTAL_SOUND,
        )
    }

    private fun transition(
        from: ServerLevel,
        entity: Entity,
        portalPos: BlockPos,
        target: net.minecraft.resources.ResourceKey<Level>,
    ): DimensionTransition? {
        val level = from.server.getLevel(target) ?: run {
            OneBlockCore.LOGGER.error(
                "Portal at {} wanted dimension {}, which is not loaded — travel refused rather than " +
                    "falling through to vanilla.", portalPos, target.location(),
            )
            return null
        }

        // A portal only works from inside an island's footprint. Without this, a portal built
        // in the void buffer or the hub would hand out a foothold in the other dimensions at
        // coordinates that belong to nobody.
        val island = OneBlockCore.islandManager?.islandAt(portalPos) ?: return null
        if (HubManager.isInHubArea(portalPos)) return null

        val x = portalPos.x
        val z = portalPos.z
        ensureFooting(level, x, z)

        OneBlockCore.LOGGER.debug(
            "Island {} portal: {} -> {} at {}/{}", island.id, from.dimension().location(), target.location(), x, z,
        )
        return DimensionTransition(
            level,
            Vec3(x + 0.5, ARRIVAL_Y.toDouble(), z + 0.5),
            Vec3.ZERO,
            entity.yRot,
            entity.xRot,
            DimensionTransition.PLAY_PORTAL_SOUND,
        )
    }

    /**
     * Makes sure there is something to stand on where the traveller lands.
     *
     * The far side is a void world, so the first arrival would otherwise be a fall with no
     * bottom. Only poured when the spot is genuinely empty — a player who has already built
     * there keeps their build, and a second trip through does not re-pave it.
     */
    private fun ensureFooting(level: ServerLevel, x: Int, z: Int) {
        val floor = BlockPos(x, ARRIVAL_Y - 1, z)
        if (!level.getBlockState(floor).isAir) return

        val block = when (level.dimension()) {
            OneBlockDimension.NETHER_KEY -> Blocks.NETHERRACK
            OneBlockDimension.END_KEY -> Blocks.END_STONE
            else -> Blocks.STONE
        }
        for (dx in -PLATFORM_RADIUS..PLATFORM_RADIUS) {
            for (dz in -PLATFORM_RADIUS..PLATFORM_RADIUS) {
                val pos = BlockPos(x + dx, ARRIVAL_Y - 1, z + dz)
                if (level.getBlockState(pos).isAir) {
                    level.setBlockAndUpdate(pos, block.defaultBlockState())
                }
            }
        }
        OneBlockCore.LOGGER.info(
            "Poured an arrival platform in {} at {}/{}.", level.dimension().location(), x, z,
        )
    }
}

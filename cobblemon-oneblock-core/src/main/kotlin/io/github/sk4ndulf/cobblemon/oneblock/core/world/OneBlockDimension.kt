package io.github.sk4ndulf.cobblemon.oneblock.core.world

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level

/**
 * The mod's own dimensions. All three are void worlds, defined data-driven under
 * `data/cobblemon_oneblock/dimension/`; this object only holds the keys and the lookups.
 *
 * **An island occupies the same X/Z in all three.** Slot 42's Nether island sits directly at
 * slot 42's coordinates, so a portal is a straight vertical move between worlds rather than
 * vanilla's 8:1 scaling — which is also why the dimension types use `coordinate_scale: 1.0`.
 *
 * **The distinction that matters when reading this file.** Some code means *any* of our
 * dimensions (protection, bans, fluid containment, Pokémon containment — a rule that only
 * covered the Overworld would leave the Nether island lawless) and some genuinely means the
 * Overworld one (the hub, the OneBlock anchor, respawning). Ask [isOurs] for the first and
 * [OVERWORLD_KEY] for the second, and never reach for whichever is shorter.
 */
object OneBlockDimension {

    /** The main island world. The hub, the OneBlock anchors and respawn all live here. */
    val OVERWORLD_KEY: ResourceKey<Level> = key("world")

    /** The Nether half of every island, reached through a portal from [OVERWORLD_KEY]. */
    val NETHER_KEY: ResourceKey<Level> = key("nether")

    /** The End half of every island. Registered and protected; access is not built yet. */
    val END_KEY: ResourceKey<Level> = key("end")

    val ALL: List<ResourceKey<Level>> = listOf(OVERWORLD_KEY, NETHER_KEY, END_KEY)

    private fun key(path: String): ResourceKey<Level> =
        ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("cobblemon_oneblock", path))

    /** True for any of the mod's dimensions. Use this for rules that must hold on every island. */
    fun isOurs(key: ResourceKey<Level>): Boolean = key in ALL

    fun isOurs(level: Level): Boolean = isOurs(level.dimension())

    /** The main island world, or null when the datapack failed to load. */
    fun overworld(server: MinecraftServer): ServerLevel? = server.getLevel(OVERWORLD_KEY)

    fun nether(server: MinecraftServer): ServerLevel? = server.getLevel(NETHER_KEY)

    fun end(server: MinecraftServer): ServerLevel? = server.getLevel(END_KEY)

    /** Every one of our dimensions the server actually loaded, for sweeps that cover all islands. */
    fun loadedLevels(server: MinecraftServer): List<ServerLevel> = ALL.mapNotNull { server.getLevel(it) }
}

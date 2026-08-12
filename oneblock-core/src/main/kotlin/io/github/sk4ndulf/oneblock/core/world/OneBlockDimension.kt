package io.github.sk4ndulf.oneblock.core.world

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level

/**
 * The `oneblock:world` void dimension. Defined data-driven in
 * `data/oneblock/dimension/world.json` (flat generator, zero layers); this object
 * only holds the key and lookup.
 */
object OneBlockDimension {

    val WORLD_KEY: ResourceKey<Level> =
        ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("oneblock", "world"))

    fun level(server: MinecraftServer): ServerLevel? = server.getLevel(WORLD_KEY)
}

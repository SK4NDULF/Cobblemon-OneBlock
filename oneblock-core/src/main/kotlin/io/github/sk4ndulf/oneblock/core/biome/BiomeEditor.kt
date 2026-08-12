package io.github.sk4ndulf.oneblock.core.biome

import io.github.sk4ndulf.oneblock.core.OneBlockCore
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.PalettedContainer

/**
 * Writes biome overrides into the world.
 *
 * Minecraft stores biomes per 4x4x4 cell inside chunk sections, so every write goes to
 * whole cells — which is exactly the "hard cut" the project wants. Touched chunks are
 * marked dirty and resent to every player tracking them, otherwise clients keep showing
 * the old biome colors until they reconnect.
 */
object BiomeEditor {

    /** Resolves a biome id, or null when the biome is not registered on this server. */
    fun resolveBiome(level: ServerLevel, biomeId: String): Holder.Reference<Biome>? {
        val location = ResourceLocation.tryParse(biomeId) ?: return null
        val registry = level.registryAccess().registryOrThrow(Registries.BIOME)
        return registry.getHolder(ResourceKey.create(Registries.BIOME, location)).orElse(null)
    }

    fun knownBiomeIds(level: ServerLevel): List<String> =
        level.registryAccess().registryOrThrow(Registries.BIOME).keySet().map { it.toString() }

    /**
     * Applies the region's biome to the world. Returns the number of 4x4x4 cells written.
     * Must run on the server thread.
     */
    fun apply(level: ServerLevel, region: BiomeRegion, biome: Holder<Biome>): Int {
        var cellsWritten = 0
        val touchedChunks = HashSet<ChunkPos>()

        // Iterate in biome-cell steps; the region bounds are already snapped to the grid.
        var x = region.minX
        while (x <= region.maxX) {
            var z = region.minZ
            while (z <= region.maxZ) {
                // getChunkNow never generates or loads: an unloaded chunk keeps the biome
                // it was saved with, so skipping it is correct — and it stops a server with
                // many biome regions from force-loading half the map on startup.
                val chunk = level.chunkSource.getChunkNow(x shr 4, z shr 4)
                if (chunk == null) {
                    z += BiomeRegion.BIOME_CELL
                    continue
                }
                var y = region.minY
                while (y <= region.maxY) {
                    if (writeCell(level, chunk, x, y, z, biome)) {
                        cellsWritten++
                        touchedChunks.add(chunk.pos)
                    }
                    y += BiomeRegion.BIOME_CELL
                }
                z += BiomeRegion.BIOME_CELL
            }
            x += BiomeRegion.BIOME_CELL
        }

        for (chunkPos in touchedChunks) {
            resendChunk(level, chunkPos)
        }
        return cellsWritten
    }

    @Suppress("UNCHECKED_CAST")
    private fun writeCell(
        level: ServerLevel,
        chunk: LevelChunk,
        x: Int,
        y: Int,
        z: Int,
        biome: Holder<Biome>,
    ): Boolean {
        if (y < level.minBuildHeight || y >= level.maxBuildHeight) return false
        val sectionIndex = level.getSectionIndex(y)
        val sections = chunk.sections
        if (sectionIndex < 0 || sectionIndex >= sections.size) return false

        val container = sections[sectionIndex].biomes as? PalettedContainer<Holder<Biome>> ?: return false
        // Biome containers are indexed 0-3 per axis (one entry per 4-block cell).
        val localX = (x and 15) shr 2
        val localY = (y and 15) shr 2
        val localZ = (z and 15) shr 2
        container.set(localX, localY, localZ, biome)
        chunk.setUnsaved(true)
        return true
    }

    private fun resendChunk(level: ServerLevel, chunkPos: ChunkPos) {
        val chunk = level.chunkSource.getChunkNow(chunkPos.x, chunkPos.z) ?: return
        val watchers = level.chunkSource.chunkMap.getPlayers(chunkPos, false)
        if (watchers.isEmpty()) return
        val packet = ClientboundLevelChunkWithLightPacket(chunk, level.lightEngine, null, null)
        watchers.forEach { it.connection.send(packet) }
    }

    /**
     * Re-applies all stored regions after a restart. Chunks that are not loaded are
     * skipped — their sections still hold the biome from the last save, so nothing is lost.
     */
    fun reapplyAll(level: ServerLevel, regions: Collection<BiomeRegion>) {
        var applied = 0
        for (region in regions) {
            val biome = resolveBiome(level, region.biomeId)
            if (biome == null) {
                OneBlockCore.LOGGER.warn("Biome region {} references unknown biome '{}' — skipped.", region.id, region.biomeId)
                continue
            }
            if (apply(level, region, biome) > 0) applied++
        }
        if (applied > 0) OneBlockCore.LOGGER.info("Re-applied {} biome regions.", applied)
    }
}

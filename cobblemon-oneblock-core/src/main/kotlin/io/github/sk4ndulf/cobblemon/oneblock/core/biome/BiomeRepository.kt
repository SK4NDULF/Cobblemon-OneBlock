package io.github.sk4ndulf.cobblemon.oneblock.core.biome

import io.github.sk4ndulf.cobblemon.oneblock.core.db.Database
import java.sql.Statement

/** Persistence for biome regions. Regions are re-applied to the world after a restart. */
class BiomeRepository(private val database: Database) {

    fun loadAll(): List<BiomeRegion> = database.sync { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT id, island_id, min_x, min_y, min_z, max_x, max_y, max_z, biome FROM island_biome_regions",
            ).use { result ->
                val regions = ArrayList<BiomeRegion>()
                while (result.next()) {
                    regions.add(
                        BiomeRegion(
                            id = result.getLong("id"),
                            islandId = result.getLong("island_id"),
                            minX = result.getInt("min_x"), minY = result.getInt("min_y"), minZ = result.getInt("min_z"),
                            maxX = result.getInt("max_x"), maxY = result.getInt("max_y"), maxZ = result.getInt("max_z"),
                            biomeId = result.getString("biome"),
                        ),
                    )
                }
                regions
            }
        }
    }

    /** Synchronous insert so the caller immediately has the generated id. */
    fun insert(region: BiomeRegion): Long = database.sync { connection ->
        connection.prepareStatement(
            "INSERT INTO island_biome_regions (island_id, min_x, min_y, min_z, max_x, max_y, max_z, biome) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS,
        ).use { statement ->
            statement.setLong(1, region.islandId)
            statement.setInt(2, region.minX); statement.setInt(3, region.minY); statement.setInt(4, region.minZ)
            statement.setInt(5, region.maxX); statement.setInt(6, region.maxY); statement.setInt(7, region.maxZ)
            statement.setString(8, region.biomeId)
            statement.executeUpdate()
            statement.generatedKeys.use { keys ->
                check(keys.next()) { "No generated key for biome region insert" }
                keys.getLong(1)
            }
        }
    }

    fun deleteAsync(regionId: Long) {
        database.async { connection ->
            connection.prepareStatement("DELETE FROM island_biome_regions WHERE id = ?").use {
                it.setLong(1, regionId)
                it.executeUpdate()
            }
        }
    }

    fun deleteForIslandAsync(islandId: Long) {
        database.async { connection ->
            connection.prepareStatement("DELETE FROM island_biome_regions WHERE island_id = ?").use {
                it.setLong(1, islandId)
                it.executeUpdate()
            }
        }
    }
}

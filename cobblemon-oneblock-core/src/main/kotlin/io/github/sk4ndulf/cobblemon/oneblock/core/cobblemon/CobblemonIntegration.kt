package io.github.sk4ndulf.cobblemon.oneblock.core.cobblemon

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.command.ObPermissions
import io.github.sk4ndulf.cobblemon.oneblock.core.island.IslandData
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.cobblemon.oneblock.core.world.HubManager
import io.github.sk4ndulf.cobblemon.oneblock.core.world.OneBlockDimension
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.entity.EntityTypeTest

/**
 * Cobblemon hooks. Everything here is about keeping Pokémon activity inside island
 * borders and honoring island permissions:
 *
 *  - spawns landing outside the spawning player's island border are cancelled
 *  - Pokémon that wander across the border are pulled back to the island
 *  - visitors can neither catch nor battle on foreign islands (owner-toggleable)
 *  - island buffs (shiny rate, IV floor) are applied to newly spawned Pokémon
 *  - the wizard's spawn multiplier is applied to Cobblemon's global spawn pacing
 */
object CobblemonIntegration {

    /** Cobblemon versions below this are rejected at startup. */
    const val MINIMUM_COBBLEMON_VERSION = "1.7.3"

    private var defaultTicksBetweenSpawnAttempts: Float? = null

    fun register() {
        CobblemonEvents.ENTITY_SPAWN.subscribe(Priority.NORMAL) { event ->
            val entity = event.entity
            if (!isInOneBlockWorld(entity)) return@subscribe
            val island = islandFor(entity)
            if (island == null) {
                // Hub or the void buffer between islands — nothing may spawn there.
                event.cancel()
                return@subscribe
            }
            if (entity is PokemonEntity) {
                applyBuffs(island, entity)
            }
        }

        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe(Priority.NORMAL) { event ->
            val thrower = event.thrower as? ServerPlayer ?: return@subscribe
            if (!isInOneBlockWorld(thrower)) return@subscribe
            val island = islandFor(event.pokemonEntity) ?: return@subscribe
            if (mayInteract(thrower, island, island.allowVisitorCatch)) return@subscribe

            // Deny the catch: no shakes, no capture.
            event.captureResult = CaptureContext(0, false, false)
            thrower.displayClientMessage(ServerLang.msg("cobblemon_oneblock.cobblemon.no_catch"), true)
        }

        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL) { event ->
            for (player in event.battle.players) {
                if (!isInOneBlockWorld(player)) continue
                val island = islandFor(player) ?: continue
                if (mayInteract(player, island, island.allowVisitorBattle)) continue

                player.displayClientMessage(ServerLang.msg("cobblemon_oneblock.cobblemon.no_battle"), true)
                event.cancel()
                return@subscribe
            }
        }

        OneBlockCore.LOGGER.info("Cobblemon integration active (Cobblemon {}).", Cobblemon.VERSION)
    }

    /** Applies the wizard's spawn intensity to Cobblemon's global spawn pacing. */
    fun applySpawnMultiplier() {
        val config = Cobblemon.config
        val base = defaultTicksBetweenSpawnAttempts ?: config.ticksBetweenSpawnAttempts.also {
            defaultTicksBetweenSpawnAttempts = it
        }
        val multiplier = OneBlockCore.configManager.mainConfig.cobblemonSpawnMultiplier
        config.ticksBetweenSpawnAttempts = (base / multiplier).toFloat().coerceAtLeast(1.0f)
        OneBlockCore.LOGGER.info(
            "Cobblemon spawn pacing set to {} ticks between attempts (multiplier {}).",
            config.ticksBetweenSpawnAttempts, multiplier,
        )
    }

    /** Logs a clear error when the running Cobblemon is older than we support. */
    fun checkVersion() {
        val running = Cobblemon.VERSION.substringBefore('+')
        if (compareVersions(running, MINIMUM_COBBLEMON_VERSION) < 0) {
            OneBlockCore.LOGGER.error(
                "Cobblemon {} is older than the required {} — island Cobblemon features may misbehave.",
                running, MINIMUM_COBBLEMON_VERSION,
            )
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = (left.getOrElse(i) { 0 }) - (right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    /**
     * Pulls Pokémon that wandered outside their island's current border back to the
     * island. Called on a slow interval — chasing this every tick is not worth the cost.
     */
    fun containWanderingPokemon(server: MinecraftServer) {
        val manager = OneBlockCore.islandManager ?: return
        // Every one of our dimensions, not just the Overworld: a Pokémon loose on a Nether
        // island is exactly as far outside its border as one loose on the main island.
        for (level in OneBlockDimension.loadedLevels(server)) {
        val wild = level.getEntities(EntityTypeTest.forClass(PokemonEntity::class.java)) { true }
        for (entity in wild) {
            // Pokémon with an owner are party members following their trainer.
            if (entity.pokemon.getOwnerPlayer() != null) continue
            val pos = entity.blockPosition()
            val island = manager.islandAt(pos)
            if (island != null && manager.isWithinCurrentBorder(island, pos)) continue

            if (island != null) {
                val anchor = island.oneBlockPos()
                entity.teleportTo(anchor.x + 0.5, anchor.y + 1.0, anchor.z + 0.5)
            } else {
                // Hub or void buffer: wild Pokémon have no business being there.
                entity.discard()
            }
        }
        }
    }

    // --- helpers ---------------------------------------------------------------------------

    private fun isInOneBlockWorld(entity: Entity): Boolean =
        OneBlockDimension.isOurs(entity.level())

    /** The island whose footprint contains the entity, or null for hub/void buffer. */
    private fun islandFor(entity: Entity): IslandData? {
        val pos = entity.blockPosition()
        if (HubManager.isInHubArea(pos)) return null
        return OneBlockCore.islandManager?.islandAt(pos)
    }

    /** Members and owners always may; visitors only when the owner allowed it. */
    private fun mayInteract(player: ServerPlayer, island: IslandData, visitorsAllowed: Boolean): Boolean =
        island.isMemberOrOwner(player.uuid) || visitorsAllowed ||
            ObPermissions.checkPlayer(player, ObPermissions.ADMIN_BYPASS, 2)

    private fun applyBuffs(island: IslandData, entity: PokemonEntity) {
        val pokemon = entity.pokemon

        BuffService.active(island.id, BuffType.SHINY_RATE)?.let { buff ->
            if (!pokemon.shiny && entity.level().random.nextDouble() < buff.value) {
                pokemon.shiny = true
            }
        }
        BuffService.active(island.id, BuffType.IV_FLOOR)?.let { buff ->
            val floor = buff.value.toInt().coerceIn(0, 31)
            val ivs = pokemon.ivs
            for (stat in listOf(
                Stats.HP, Stats.ATTACK, Stats.DEFENCE,
                Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED,
            )) {
                if ((ivs.getOrDefault(stat)) < floor) ivs[stat] = floor
            }
        }
    }
}

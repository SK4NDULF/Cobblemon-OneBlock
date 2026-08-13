package io.github.sk4ndulf.cobblemon.oneblock.core.cobblemon

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.ActiveTriggerEvent
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.TriggerEventContext
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.TriggerEventStatus
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.TriggerEventType
import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.cobblemon.oneblock.core.trigger.EventSpawning
import net.minecraft.ChatFormatting
import net.minecraft.resources.ResourceLocation

/**
 * A rare Pokémon appears on the island. Built on the same public extension point addons
 * use — proof that the API carries real features, not just toy examples.
 *
 * The encounter succeeds once the Pokémon is gone (caught or defeated) and fails on
 * timeout. Difficulty (= border level) raises the Pokémon's level; the species pool is
 * admin-configurable and does not change with difficulty.
 */
class LegendaryEncounterEvent : TriggerEventType {

    override fun id(): ResourceLocation = ResourceLocation.fromNamespaceAndPath("cobblemon_oneblock", "legendary_encounter")

    override fun displayName(): String = ServerLang.raw("cobblemon_oneblock.event.type.legendary")

    override fun start(context: TriggerEventContext): ActiveTriggerEvent = Instance(context.difficulty())

    private class Instance(private val difficulty: Int) : ActiveTriggerEvent {

        private var pokemon: PokemonEntity? = null
        private var ticks = 0

        override fun tick(context: TriggerEventContext): TriggerEventStatus {
            ticks++
            val current = pokemon
            if (current == null) {
                return if (spawn(context)) TriggerEventStatus.RUNNING else TriggerEventStatus.FAILURE
            }
            // Caught or defeated — either way the encounter is resolved.
            return if (!current.isAlive || current.isRemoved) TriggerEventStatus.SUCCESS else TriggerEventStatus.RUNNING
        }

        private fun spawn(context: TriggerEventContext): Boolean {
            val species = OneBlockCore.configManager.mainConfig.legendarySpecies
            if (species.isEmpty()) {
                OneBlockCore.LOGGER.warn("legendary_species is empty — legendary encounter cannot run.")
                return false
            }
            val level = context.level()
            val chosen = species[level.random.nextInt(species.size)]
            val pokemonLevel = (20 + difficulty * 7).coerceIn(1, 100)

            val entity = try {
                PokemonProperties.parse("$chosen level=$pokemonLevel").createEntity(level)
            } catch (e: Exception) {
                OneBlockCore.LOGGER.error("Could not create legendary '{}': {}", chosen, e.message)
                return false
            }
            val spawnPos = EventSpawning.randomNearby(level, context.anchor(), radius = 3)
            entity.moveTo(spawnPos.x + 0.5, spawnPos.y.toDouble(), spawnPos.z + 0.5, 0.0f, 0.0f)
            if (!level.addFreshEntity(entity)) return false

            pokemon = entity
            for (player in context.participants()) {
                player.sendSystemMessage(
                    ServerLang.msg("cobblemon_oneblock.event.legendary_appeared", entity.pokemon.species.name)
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                )
            }
            return true
        }

        override fun cleanup(context: TriggerEventContext) {
            // Only despawn if it is still wild — a caught Pokémon belongs to the player.
            pokemon?.takeIf { it.isAlive && it.pokemon.getOwnerPlayer() == null }?.discard()
            pokemon = null
        }
    }
}

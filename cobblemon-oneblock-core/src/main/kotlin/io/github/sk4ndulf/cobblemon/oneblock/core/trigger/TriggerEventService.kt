package io.github.sk4ndulf.cobblemon.oneblock.core.trigger

import io.github.sk4ndulf.cobblemon.oneblock.api.event.TriggerEventEndEvent
import io.github.sk4ndulf.cobblemon.oneblock.api.event.TriggerEventFailEvent
import io.github.sk4ndulf.cobblemon.oneblock.api.event.TriggerEventStartEvent
import io.github.sk4ndulf.cobblemon.oneblock.api.island.Island
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.ActiveTriggerEvent
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.TriggerEventContext
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.TriggerEventStatus
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.TriggerEventType
import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.island.IslandData
import io.github.sk4ndulf.cobblemon.oneblock.core.island.IslandState
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.cobblemon.oneblock.core.world.OneBlockDimension
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * Trigger event engine: break-threshold detection with countdown, a per-island queue for
 * overlapping triggers, cooldowns, per-tick event execution with timeout/offline failure,
 * and the extension registry for addon event types.
 *
 * Queue and cooldown state are in-memory by design: after a restart the next threshold
 * simply triggers again (breakCount is persistent, so thresholds stay aligned).
 */
object TriggerEventService {

    private class ContextImpl(
        val islandData: IslandData,
        private val serverLevel: ServerLevel,
        private val capturedDifficulty: Int,
    ) : TriggerEventContext {
        override fun island(): Island = islandData
        override fun level(): ServerLevel = serverLevel
        override fun difficulty(): Int = capturedDifficulty
        override fun anchor(): BlockPos = islandData.oneBlockPos()
        override fun participants(): List<ServerPlayer> =
            (islandData.memberSet + islandData.owner()).mapNotNull { uuid ->
                serverLevel.server.playerList.getPlayer(uuid)
                    ?.takeIf { it.level().dimension() == OneBlockDimension.WORLD_KEY }
            }
    }

    private class Active(
        val type: TriggerEventType,
        val instance: ActiveTriggerEvent,
        val context: ContextImpl,
        val startedAtTick: Long,
    )

    private val types = LinkedHashMap<ResourceLocation, TriggerEventType>()
    private val active = HashMap<Long, Active>()
    private val pendingCount = HashMap<Long, Int>()
    private val cooldownUntilMillis = HashMap<Long, Long>()
    private var serverTicks = 0L

    // --- registry (EventManager backing) ---------------------------------------------------

    fun registerType(type: TriggerEventType) {
        val previous = types.put(type.id(), type)
        if (previous != null) {
            OneBlockCore.LOGGER.info("Trigger event type {} was replaced by a new registration.", type.id())
        } else {
            OneBlockCore.LOGGER.info("Trigger event type registered: {}", type.id())
        }
    }

    fun registeredTypes(): Set<ResourceLocation> = types.keys.toSet()

    fun activeTypeOn(islandId: Long): ResourceLocation? = active[islandId]?.type?.id()

    // --- break hook -------------------------------------------------------------------------

    /** Called for every OneBlock break: countdown announcements + threshold detection. */
    fun onBreak(island: IslandData, level: ServerLevel) {
        val threshold = OneBlockCore.configManager.mainConfig.triggerEventThreshold
        val sinceLast = island.breakCount % threshold
        if (sinceLast == 0L) {
            pendingCount.merge(island.id, 1, Int::plus)
            announce(island, level.server, "cobblemon_oneblock.event.triggered", sound = true)
        } else {
            val remaining = threshold - sinceLast
            if (remaining == 10L || remaining <= 5L) {
                announce(island, level.server, "cobblemon_oneblock.event.countdown", remaining)
            }
        }
    }

    // --- ticking ---------------------------------------------------------------------------

    fun tick(server: MinecraftServer) {
        serverTicks++
        tickActiveEvents(server)
        if (serverTicks % 20L == 0L) {
            startQueuedEvents(server)
        }
    }

    private fun tickActiveEvents(server: MinecraftServer) {
        if (active.isEmpty()) return
        val timeoutTicks = OneBlockCore.configManager.mainConfig.eventTimeoutSeconds * 20L
        val iterator = active.entries.iterator()
        while (iterator.hasNext()) {
            val (_, event) = iterator.next()
            val island = event.context.islandData

            // Island got archived mid-event (reset/delete/purge) → silent cleanup + fail.
            if (island.state != IslandState.ACTIVE) {
                iterator.remove()
                finish(event, server, success = false, announceIt = false)
                continue
            }
            // Timeout or everyone gone (grace period of 5s after start).
            val runningTicks = serverTicks - event.startedAtTick
            val abandoned = runningTicks > 100 && runningTicks % 20L == 0L && event.context.participants().isEmpty()
            if (runningTicks > timeoutTicks || abandoned) {
                iterator.remove()
                finish(event, server, success = false, announceIt = true)
                continue
            }

            val status = try {
                event.instance.tick(event.context)
            } catch (e: Exception) {
                OneBlockCore.LOGGER.error("Trigger event {} threw during tick — failing it.", event.type.id(), e)
                TriggerEventStatus.FAILURE
            }
            when (status) {
                TriggerEventStatus.RUNNING -> {}
                TriggerEventStatus.SUCCESS -> {
                    iterator.remove()
                    finish(event, server, success = true, announceIt = true)
                }
                TriggerEventStatus.FAILURE -> {
                    iterator.remove()
                    finish(event, server, success = false, announceIt = true)
                }
            }
        }
    }

    private fun startQueuedEvents(server: MinecraftServer) {
        if (pendingCount.isEmpty() || types.isEmpty()) return
        val manager = OneBlockCore.islandManager ?: return
        val level = OneBlockDimension.level(server) ?: return
        val now = System.currentTimeMillis()

        val iterator = pendingCount.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val island = manager.islandDataById(entry.key)
            if (island == null || island.state != IslandState.ACTIVE) {
                iterator.remove()
                continue
            }
            if (active.containsKey(island.id)) continue
            if ((cooldownUntilMillis[island.id] ?: 0L) > now) continue

            // Someone must actually be on the island so chunks are loaded and mobs tick.
            val context = ContextImpl(island, level, island.borderLevel)
            val onIsland = context.participants().any { manager.islandAt(it.blockPosition()) === island }
            if (!onIsland) continue

            val type = pickRandomType(level)
            val instance = try {
                type.start(context)
            } catch (e: Exception) {
                OneBlockCore.LOGGER.error("Trigger event {} threw during start — dropping trigger.", type.id(), e)
                consumePending(iterator, entry)
                continue
            }
            active[island.id] = Active(type, instance, context, serverTicks)
            consumePending(iterator, entry)
            announce(island, server, "cobblemon_oneblock.event.start", type.displayName(), island.borderLevel, sound = true)
            OneBlockCore.eventBus.post(TriggerEventStartEvent(island, type.id(), context.difficulty()))
            OneBlockCore.LOGGER.info("Trigger event {} started on island {} (difficulty {}).",
                type.id(), island.id, context.difficulty())
        }
    }

    /**
     * Consumes one queued trigger. The removal MUST go through the iterator — calling
     * `pendingCount.remove(...)` here throws ConcurrentModificationException as soon as a
     * second island has a trigger queued.
     */
    private fun consumePending(
        iterator: MutableIterator<MutableMap.MutableEntry<Long, Int>>,
        entry: MutableMap.MutableEntry<Long, Int>,
    ) {
        if (entry.value <= 1) iterator.remove() else entry.setValue(entry.value - 1)
    }

    private fun pickRandomType(level: ServerLevel): TriggerEventType {
        val list = types.values.toList()
        return list[level.random.nextInt(list.size)]
    }

    private fun finish(event: Active, server: MinecraftServer, success: Boolean, announceIt: Boolean) {
        try {
            event.instance.cleanup(event.context)
        } catch (e: Exception) {
            OneBlockCore.LOGGER.error("Trigger event {} threw during cleanup.", event.type.id(), e)
        }
        val island = event.context.islandData
        cooldownUntilMillis[island.id] =
            System.currentTimeMillis() + OneBlockCore.configManager.mainConfig.eventCooldownSeconds * 1000L
        if (announceIt) {
            announce(island, server, if (success) "cobblemon_oneblock.event.success" else "cobblemon_oneblock.event.fail",
                event.type.displayName(), sound = success)
        }
        if (success) {
            OneBlockCore.eventBus.post(TriggerEventEndEvent(island, event.type.id()))
        } else {
            OneBlockCore.eventBus.post(TriggerEventFailEvent(island, event.type.id()))
        }
        OneBlockCore.LOGGER.info("Trigger event {} on island {} finished ({}).",
            event.type.id(), island.id, if (success) "success" else "failure")
    }

    /** Fails and cleans up everything — used on server stop and island reloads. */
    fun shutdown(server: MinecraftServer) {
        for (event in active.values) {
            try {
                event.instance.cleanup(event.context)
            } catch (e: Exception) {
                OneBlockCore.LOGGER.error("Trigger event {} threw during shutdown cleanup.", event.type.id(), e)
            }
        }
        active.clear()
        pendingCount.clear()
    }

    private fun announce(island: IslandData, server: MinecraftServer, langKey: String, vararg args: Any?, sound: Boolean = false) {
        for (uuid in island.memberSet + island.owner()) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            player.sendSystemMessage(ServerLang.msg(langKey, *args).withStyle(ChatFormatting.LIGHT_PURPLE))
            if (sound) {
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 0.7f)
            }
        }
    }
}

package io.github.sk4ndulf.cobblemon.oneblock.example;

import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.ActiveTriggerEvent;
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.TriggerEventContext;
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.TriggerEventStatus;
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.TriggerEventType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A custom trigger event: a short firework show on the island.
 *
 * <p>Shows the full contract an event type has to fulfil — start, tick until a terminal
 * status, clean up after itself. The core handles queueing, cooldown, timeout and
 * announcements; the event only has to describe what happens.</p>
 */
public class FireworkCelebrationEvent implements TriggerEventType {

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(ExampleAddon.MOD_ID, "firework_celebration");
    }

    @Override
    public String displayName() {
        return "Firework Show";
    }

    @Override
    public ActiveTriggerEvent start(TriggerEventContext context) {
        for (ServerPlayer player : context.participants()) {
            player.sendSystemMessage(Component.literal("[ExampleAddon] Enjoy the show!"));
        }
        return new Instance();
    }

    private static final class Instance implements ActiveTriggerEvent {

        private static final int DURATION_TICKS = 200; // 10 seconds

        private int ticks = 0;

        @Override
        public TriggerEventStatus tick(TriggerEventContext context) {
            ticks++;
            if (ticks % 10 == 0) {
                ServerLevel level = context.level();
                BlockPos anchor = context.anchor();
                level.sendParticles(ParticleTypes.FIREWORK,
                        anchor.getX() + 0.5, anchor.getY() + 3.0, anchor.getZ() + 0.5,
                        40, 2.0, 1.0, 2.0, 0.1);
            }
            // Difficulty is available via context.difficulty() (1-8) if you want to scale
            // the challenge — but per the project rule, never scale the rewards with it.
            return ticks >= DURATION_TICKS ? TriggerEventStatus.SUCCESS : TriggerEventStatus.RUNNING;
        }

        @Override
        public void cleanup(TriggerEventContext context) {
            // Nothing persistent was created, so there is nothing to remove.
        }
    }
}

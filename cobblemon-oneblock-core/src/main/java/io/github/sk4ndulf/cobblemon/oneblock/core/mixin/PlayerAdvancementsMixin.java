package io.github.sk4ndulf.cobblemon.oneblock.core.mixin;

import io.github.sk4ndulf.cobblemon.oneblock.core.progression.AdvancementPoints;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Turns a completed advancement into tech points.
 *
 * A mixin rather than an event because Fabric API has no advancement hook in 1.21.1.
 * Deliberately injected at RETURN and reading the progress afterwards, rather than at the
 * branch inside vanilla that fires the reward: that branch moves between versions, while
 * "award returned true and the advancement is now done" is stable and easy to reason about.
 *
 * Firing more than once for the same advancement would be harmless anyway — the claim is
 * keyed on (island, source) in the database and the second one is a no-op — but it does not,
 * because vanilla only grants each criterion once.
 *
 * All the logic lives in {@code AdvancementPoints}. This package may contain nothing but mixin
 * classes; a helper here is treated as a mixin by the transformer and fails to load
 * (see {@code HANDOFF.md} §3).
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Shadow
    private ServerPlayer player;

    @Shadow
    public abstract AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

    @Inject(method = "award", at = @At("RETURN"))
    private void cobblemon_oneblock$awardTechPoints(
            AdvancementHolder advancement,
            String criterion,
            CallbackInfoReturnable<Boolean> callback
    ) {
        if (!callback.getReturnValueZ()) {
            return;
        }
        if (this.player == null || !getOrStartProgress(advancement).isDone()) {
            return;
        }
        AdvancementPoints.onCompleted(this.player, advancement.id());
    }
}

package io.github.sk4ndulf.cobblemon.oneblock.core.mixin;

import io.github.sk4ndulf.cobblemon.oneblock.core.world.IslandPortals;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The same redirect for End portals.
 *
 * Nothing places an End portal on an island yet — a vanilla one needs a stronghold frame, so
 * access will have to come from the tech tree. This exists now anyway, because the hole it
 * closes is the same one the Nether had: an End portal that an admin or another mod places in
 * our world would otherwise lead to the real End, and from there to the real Overworld.
 */
@Mixin(EndPortalBlock.class)
public abstract class EndPortalBlockMixin {

    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void cobblemon_oneblock$toIslandEnd(
            ServerLevel level,
            Entity entity,
            BlockPos pos,
            CallbackInfoReturnable<DimensionTransition> callback
    ) {
        DimensionTransition destination = IslandPortals.endDestination(level, entity, pos);
        if (destination != null) {
            callback.setReturnValue(destination);
        }
    }
}

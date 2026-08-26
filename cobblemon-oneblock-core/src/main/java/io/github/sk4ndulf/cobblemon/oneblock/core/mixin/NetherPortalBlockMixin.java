package io.github.sk4ndulf.cobblemon.oneblock.core.mixin;

import io.github.sk4ndulf.cobblemon.oneblock.core.world.IslandPortals;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sends a Nether portal inside one of our dimensions to the island's own Nether half.
 *
 * Vanilla's rule is "whichever dimension is not the Nether", which from our world means the
 * real, infinite, unprotected Nether — and from there the real Overworld. That made every
 * border and the whole island economy meaningless for anyone holding obsidian, which the tech
 * tree hands out at Nether tier 4.
 *
 * Redirecting rather than blocking: a portal is something a player expects to work, and an
 * island's Nether half is somewhere legitimate for it to go.
 *
 * A null destination from {@link IslandPortals} means "not ours" — portals in the real
 * Overworld keep vanilla behaviour untouched.
 */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {

    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void cobblemon_oneblock$toIslandNether(
            ServerLevel level,
            Entity entity,
            BlockPos pos,
            CallbackInfoReturnable<DimensionTransition> callback
    ) {
        DimensionTransition destination = IslandPortals.netherDestination(level, entity, pos);
        if (destination != null) {
            callback.setReturnValue(destination);
        }
    }
}

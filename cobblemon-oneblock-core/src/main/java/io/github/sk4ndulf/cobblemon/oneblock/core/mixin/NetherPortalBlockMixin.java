package io.github.sk4ndulf.cobblemon.oneblock.core.mixin;

import io.github.sk4ndulf.cobblemon.oneblock.core.hooks.WorldHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A portal block in the OneBlock world does nothing.
 *
 * The second half of the portal fix. {@code PortalShapeMixin} stops new portals from forming,
 * but a world created before that fix — or one an admin built by hand — can already contain
 * portal blocks, and those would still work. Cancelling the entity check means the entity is
 * never registered as being in a portal, so no travel is ever scheduled.
 *
 * Cancelling {@code entityInside} rather than returning null from {@code getPortalDestination}:
 * this stops the process at the first step instead of letting an entity accumulate portal time
 * and then be told there is nowhere to go.
 */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void cobblemon_oneblock$noPortalTravel(
            BlockState state,
            Level level,
            BlockPos pos,
            Entity entity,
            CallbackInfo callback
    ) {
        if (WorldHooks.blocksPortals(level)) {
            callback.cancel();
        }
    }
}

package io.github.sk4ndulf.cobblemon.oneblock.core.mixin;

import io.github.sk4ndulf.cobblemon.oneblock.core.hooks.WorldHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Nether portals never form in the OneBlock world.
 *
 * This is the ignition half of the fix — see {@code WorldHooks.blocksPortals} for why portals
 * have to be blocked at all. Reporting "no valid shape here" is the gentlest way to say no:
 * vanilla already handles that answer everywhere it asks, so the flint and steel simply lights
 * a fire that burns out, with no special case anywhere else in the game.
 */
@Mixin(PortalShape.class)
public abstract class PortalShapeMixin {

    @Inject(method = "findEmptyPortalShape", at = @At("HEAD"), cancellable = true)
    private static void cobblemon_oneblock$noPortalsHere(
            LevelAccessor level,
            BlockPos pos,
            Direction.Axis axis,
            CallbackInfoReturnable<Optional<PortalShape>> callback
    ) {
        if (WorldHooks.blocksPortals(level)) {
            callback.setReturnValue(Optional.empty());
        }
    }
}

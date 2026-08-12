package io.github.sk4ndulf.oneblock.core.mixin;

import io.github.sk4ndulf.oneblock.core.hooks.WorldHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fluid containment at island borders: water/lava may only spread inside an island's
 * current border area — never into the hub, the void buffer, or a neighbouring island.
 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {

    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void oneblock$containFluid(LevelAccessor level, BlockPos pos, BlockState blockState,
                                       Direction direction, FluidState fluidState, CallbackInfo ci) {
        if (WorldHooks.blockFluidSpread(level, pos)) {
            ci.cancel();
        }
    }
}

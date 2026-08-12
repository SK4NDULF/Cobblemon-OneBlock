package io.github.sk4ndulf.oneblock.core.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gravity blocks (sand, gravel, concrete powder, anvils, ...) are part of the OneBlock
 * loot pool but must never fall off the anchor — this cancels their fall tick there.
 */
@Mixin(FallingBlock.class)
public abstract class FallingBlockMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void oneblock$preventAnchorFall(BlockState state, ServerLevel level, BlockPos pos,
                                            RandomSource random, CallbackInfo ci) {
        if (MixinHooks.isOneBlockAnchor(level, pos)) {
            ci.cancel();
        }
    }
}

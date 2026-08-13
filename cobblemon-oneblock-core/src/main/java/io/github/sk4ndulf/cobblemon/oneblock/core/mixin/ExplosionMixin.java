package io.github.sk4ndulf.cobblemon.oneblock.core.mixin;

import io.github.sk4ndulf.cobblemon.oneblock.core.hooks.WorldHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Explosion containment at island borders: blocks outside the source island's current
 * border area (hub, void buffer, neighbouring islands) are removed from the blast list.
 */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {

    @Shadow @Final private Level level;
    @Shadow @Final private double x;
    @Shadow @Final private double z;

    @Shadow public abstract List<BlockPos> getToBlow();

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void cobblemon_oneblock$containExplosion(boolean spawnParticles, CallbackInfo ci) {
        WorldHooks.filterExplosion(this.level, this.x, this.z, this.getToBlow());
    }
}

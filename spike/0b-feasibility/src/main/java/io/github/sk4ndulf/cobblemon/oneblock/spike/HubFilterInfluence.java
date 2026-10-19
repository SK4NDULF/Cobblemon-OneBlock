package io.github.sk4ndulf.cobblemon.oneblock.spike;

import com.cobblemon.mod.common.api.spawning.detail.SpawnAction;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;

/**
 * Spike: proves that a per-player influence can veto spawn positions locally (here: a 97x97 "hub" around 0,0 in the
 * void dimension) without touching global Cobblemon data. Cobblemon is compiled without jvm-default, so every interface
 * method has to be implemented; non-trivial defaults delegate to {@code DefaultImpls}.
 */
final class HubFilterInfluence implements SpawningInfluence {
    static boolean isHub(BlockPos pos) {
        return Math.abs(pos.getX()) <= 48 && Math.abs(pos.getZ()) <= 48;
    }

    @Override
    public boolean isAllowedPosition(ServerLevel world, BlockPos pos, SpawnablePositionCalculator<?, ?> calculator) {
        if (world.dimension().equals(SpikeMod.VOID) && isHub(pos)) {
            SpikeMod.HUB_POSITIONS_BLOCKED.incrementAndGet();
            return false;
        }
        return true;
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public void affectSpawnablePosition(SpawnablePosition spawnablePosition) {
    }

    @Override
    public boolean affectSpawnable(SpawnDetail detail, SpawnablePosition spawnablePosition) {
        return true;
    }

    @Override
    public float affectWeight(SpawnDetail detail, SpawnablePosition spawnablePosition, float weight) {
        return weight;
    }

    @Override
    public void affectAction(SpawnAction<?> action) {
    }

    @Override
    public void affectSpawn(SpawnAction<?> action, Entity entity) {
    }

    @Override
    public void affectBucketWeights(Map<String, Float> bucketWeights) {
    }

    @Override
    public void normalizeBucketWeights(Map<String, Float> bucketWeights) {
        SpawningInfluence.DefaultImpls.normalizeBucketWeights(this, bucketWeights);
    }

    @Override
    public List<SpawnDetail> injectSpawns(String bucket, SpawnablePosition spawnablePosition) {
        return null;
    }
}

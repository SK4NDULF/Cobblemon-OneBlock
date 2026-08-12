package io.github.sk4ndulf.oneblock.core.island

import io.github.sk4ndulf.oneblock.api.island.Island
import io.github.sk4ndulf.oneblock.api.progression.ProgressionManager
import io.github.sk4ndulf.oneblock.core.OneBlockCore
import io.github.sk4ndulf.oneblock.core.world.GridMath

class ProgressionManagerImpl : ProgressionManager {

    override fun points(island: Island): Double = (island as? IslandData)?.points ?: 0.0

    override fun pointsRequiredFor(level: Int): Double = ProgressionService.pointsRequiredFor(level)

    override fun borderSizeAt(level: Int): Int =
        GridMath.borderSizeAt(level, OneBlockCore.configManager.mainConfig)
}

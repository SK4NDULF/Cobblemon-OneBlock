package io.github.sk4ndulf.oneblock.core.island

import io.github.sk4ndulf.oneblock.api.party.PartyManager
import io.github.sk4ndulf.oneblock.core.OneBlockCore
import java.util.UUID

class PartyManagerImpl : PartyManager {

    override fun hasPendingInvite(player: UUID): Boolean = PartyService.hasPendingInvite(player)

    override fun maxPartySize(): Int = OneBlockCore.configManager.mainConfig.maxPartySize
}

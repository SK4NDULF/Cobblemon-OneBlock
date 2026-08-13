package io.github.sk4ndulf.cobblemon.oneblock.core.progression

import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.db.Database
import io.github.sk4ndulf.cobblemon.oneblock.core.island.IslandData
import java.util.UUID

/**
 * Owns the loaded tree and every island's tech state. Server-thread only.
 *
 * This is the only place that changes a level or a balance, so the invariant "points were
 * deducted exactly when a level was granted" holds by construction rather than by review.
 */
object TechService {

    private var repository: TechRepository? = null
    private val states = HashMap<Long, IslandTechState>()

    /** Set by [OneBlockCore] on load and on `/ob reload`. */
    var tree: TechTree = TechTree.empty()
        private set

    fun setTree(loaded: TechTree) {
        tree = loaded
        reportBossGates()
    }

    fun loadAll(database: Database) {
        val repo = TechRepository(database)
        repository = repo
        states.clear()
        states.putAll(repo.loadAll())
        val withProgress = states.values.count { it.levels.isNotEmpty() }
        if (withProgress > 0) {
            OneBlockCore.LOGGER.info("Restored tech progress for {} island(s).", withProgress)
        }
    }

    fun stateOf(islandId: Long): IslandTechState = states.getOrPut(islandId) { IslandTechState(islandId) }

    /** Drops an island's progress. Called from the island reset and delete paths. */
    fun clear(islandId: Long) {
        states.remove(islandId)
        repository?.clearAsync(islandId)
    }

    // --- spending --------------------------------------------------------------------------

    /** Why a node cannot be bought, or that it can. Carries what the message needs to say. */
    sealed interface Outcome {
        data class Bought(val node: TechNode, val newLevel: Int, val spent: Long, val balanceLeft: Long) : Outcome
        data object UnknownNode : Outcome
        data object AlreadyMaxed : Outcome
        data class NotEnoughPoints(val needed: Long, val have: Long) : Outcome
        data class Locked(val unmet: List<TechRequirement>) : Outcome
        data object NotAllowed : Outcome
    }

    /**
     * The owner always may spend; members only when the owner delegated it to them.
     * Progression is the island's shared savings, so "any member" would let one person spend
     * everyone's work on the wrong branch, and "owner only" makes members passengers.
     */
    fun maySpend(island: IslandData, player: UUID): Boolean =
        island.owner() == player || player in stateOf(island.id).spenders

    fun setSpender(island: IslandData, member: UUID, allowed: Boolean) {
        val state = stateOf(island.id)
        if (allowed) state.spenders += member else state.spenders -= member
        repository?.setSpenderAsync(island.id, member, allowed)
    }

    /** Requirements of [node] this island does not currently meet. */
    fun unmetRequirements(island: IslandData, node: TechNode): List<TechRequirement> {
        val state = stateOf(island.id)
        return node.requires.filter { requirement ->
            when (requirement) {
                is TechRequirement.Node -> state.levelOf(requirement.nodeId) < requirement.level
                is TechRequirement.LifetimePoints -> state.lifetimeEarned < requirement.total
                // Bosses do not exist yet, so every boss gate is unmet. Deliberate: it keeps
                // the gate honest instead of quietly opening. See default_techtree.json5.
                is TechRequirement.Boss -> !state.hasClaimed("boss:${requirement.bossId}")
            }
        }
    }

    /** Buys the next level of a node, or explains why not. Nothing changes unless it succeeds. */
    fun buy(island: IslandData, player: UUID, nodeId: String): Outcome {
        if (!maySpend(island, player)) return Outcome.NotAllowed
        val node = tree.node(nodeId) ?: return Outcome.UnknownNode
        val state = stateOf(island.id)

        val current = state.levelOf(nodeId)
        val cost = node.costOfNext(current) ?: return Outcome.AlreadyMaxed

        val unmet = unmetRequirements(island, node)
        if (unmet.isNotEmpty()) return Outcome.Locked(unmet)
        if (state.balance < cost) return Outcome.NotEnoughPoints(cost, state.balance)

        val newLevel = current + 1
        state.levels[nodeId] = newLevel
        state.balance -= cost
        repository?.upsertNodeAsync(island.id, nodeId, newLevel)
        repository?.updatePointsAsync(island.id, state.balance, state.lifetimeEarned)

        OneBlockCore.LOGGER.info(
            "Island {} unlocked {} level {} for {} points ({} left).",
            island.id, nodeId, newLevel, cost, state.balance,
        )
        return Outcome.Bought(node, newLevel, cost, state.balance)
    }

    // --- points ----------------------------------------------------------------------------

    /**
     * Adds points to an island. Use [TechPointService.claim] for anything content-driven —
     * this is the raw grant, for admin commands and for the claim path itself.
     */
    fun grant(islandId: Long, amount: Long) {
        if (amount == 0L) return
        val state = stateOf(islandId)
        state.balance += amount
        if (amount > 0) state.lifetimeEarned += amount
        repository?.updatePointsAsync(islandId, state.balance, state.lifetimeEarned)
    }

    /** Records a claim. Returns false when this island had already banked that source. */
    fun claimSource(islandId: Long, sourceId: String, claimedBy: UUID): Boolean {
        val state = stateOf(islandId)
        if (state.hasClaimed(sourceId)) return false
        val repo = repository ?: return false
        // The database, not the in-memory set, decides — see TechRepository.claimSync.
        if (!repo.claimSync(islandId, sourceId, claimedBy)) {
            state.claims += sourceId
            return false
        }
        state.claims += sourceId
        return true
    }

    // --- reporting ---------------------------------------------------------------------------

    /**
     * Says at startup how much of the tree is currently unreachable because it sits behind a
     * boss gate. Bosses ship in a later slice; until then this is the difference between
     * "gated content" and "content that looks broken".
     */
    private fun reportBossGates() {
        val gated = tree.nodes.values.filter { node -> node.requires.any { it is TechRequirement.Boss } }
        if (gated.isEmpty()) return
        OneBlockCore.LOGGER.warn(
            "{} tech node(s) are gated behind bosses, which are not implemented yet: {}. " +
                "They cannot be unlocked until the Special system ships.",
            gated.size, gated.map { it.id }.sorted().joinToString(", "),
        )
    }
}

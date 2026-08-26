package io.github.sk4ndulf.cobblemon.oneblock.core.progression

import org.slf4j.Logger

/**
 * The loaded, validated tech tree.
 *
 * Validation is deliberately loud and non-fatal, matching how `loottable.json5` is handled:
 * a broken entry is logged and dropped, the server still boots. An admin-editable file that
 * can prevent startup is a file that takes servers down at 3am.
 *
 * Nodes that were dropped during validation are gone from [nodes], so nothing downstream ever
 * sees a node with a dangling requirement.
 */
class TechTree private constructor(
    val nodes: Map<String, TechNode>,
    /** Node ids that failed validation, with the reason. Reported at load, kept for `/ob tech admin`. */
    val problems: List<String>,
) {

    val byCategory: Map<TechCategory, List<TechNode>> =
        nodes.values.groupBy { it.category }.mapValues { (_, list) -> list.sortedBy { it.id } }

    fun node(id: String): TechNode? = nodes[id]

    /**
     * Whether buying this node would actually do anything yet.
     *
     * The tree is written ahead of the slices that implement its payloads, so a node can exist
     * and cost points long before anything reads its effect. Selling one of those is the worst
     * outcome available: the player spends a scarce, content-gated currency and nothing
     * happens, with no way to get it back.
     *
     * A node counts as implemented when at least one of its effects has a consumer, or when it
     * has no effects at all — the latter is a pure gate node, which exists to be a requirement
     * for something else and is doing its job by being bought.
     */
    fun isImplemented(node: TechNode): Boolean {
        val types = node.effects.values.flatten().map { it.type }.toSet()
        return types.isEmpty() || types.any { it in TechEffect.CONSUMED }
    }

    /** Nodes that cannot be bought yet because nothing reads their effects. */
    fun unimplementedNodes(): List<TechNode> = nodes.values.filterNot(::isImplemented).sortedBy { it.id }

    /** Sum of every node taken to its maximum level — the denominator of the point budget. */
    val totalCost: Long get() = nodes.values.sumOf { it.totalCost }

    /**
     * Logs effect types that appear in the tree but have no consumer shipped yet.
     *
     * Without this, an admin writing a `player_effect` node before slice E exists would see
     * it bought, see the points deducted, and see nothing happen — the worst kind of bug to
     * report. Better to say so at startup.
     */
    fun reportUnconsumedEffects(logger: Logger) {
        val present = nodes.values
            .flatMap { it.effects.values.flatten() }
            .map { it.type }
            .toSortedSet()
        val unconsumed = present - TechEffect.CONSUMED
        if (unconsumed.isEmpty()) return
        val blocked = unimplementedNodes()
        logger.warn(
            "Tech tree uses {} effect type(s) that no slice consumes yet: {}. " +
                "The {} node(s) that rely on them are locked and cannot be bought, so nobody " +
                "spends points on nothing: {}.",
            unconsumed.size, unconsumed.joinToString(", "),
            blocked.size, blocked.joinToString(", ") { it.id },
        )
    }

    companion object {

        /**
         * Validates a parsed node list into a tree. Returns the tree; every rejection is
         * recorded in [TechTree.problems] and logged by the caller.
         *
         * Order matters: unknown requirement targets are resolved first, because dropping a
         * node can orphan another, and cycle detection would otherwise walk into a hole.
         */
        fun build(parsed: List<TechNode>): TechTree {
            val problems = ArrayList<String>()
            var surviving = parsed.associateBy { it.id }

            // 1. Requirements that point at nodes which do not exist, or at impossible levels.
            //    Repeat until stable: dropping a node can invalidate the ones requiring it.
            while (true) {
                val bad = LinkedHashMap<String, String>()
                for (node in surviving.values) {
                    for (requirement in node.requires.filterIsInstance<TechRequirement.Node>()) {
                        val target = surviving[requirement.nodeId]
                        // Naming the offending requirement matters more than it looks: dropping
                        // one node cascades into everything that required it, and a bare "bad
                        // requirement" on a node whose requirement plainly exists in the file
                        // sends the admin looking in the wrong place.
                        val reason = when {
                            target == null -> "requires '${requirement.nodeId}', which does not exist"
                            requirement.level > target.maxLevel ->
                                "requires '${requirement.nodeId}' at level ${requirement.level}, " +
                                    "but that node only goes to ${target.maxLevel}"
                            else -> continue
                        }
                        bad[node.id] = "${node.id}: $reason"
                        break
                    }
                }
                if (bad.isEmpty()) break
                problems += bad.values
                surviving = surviving - bad.keys
            }

            // 2. Self-reference and cycles. A cycle makes every node in it permanently
            //    unbuyable, which in play looks like the tree is broken rather than gated.
            val cyclic = findCyclicNodes(surviving)
            if (cyclic.isNotEmpty()) {
                for (id in cyclic.sorted()) {
                    problems += "$id: part of a requirement cycle, so it could never be unlocked"
                }
                surviving = surviving - cyclic
            }

            return TechTree(surviving, problems)
        }

        /**
         * Every node that sits on, or depends on, a requirement cycle.
         *
         * Iterative fixpoint rather than a depth-first walk: a node is reachable if all its
         * node requirements are reachable, so repeatedly keeping only satisfiable nodes leaves
         * exactly the cyclic and cycle-dependent ones behind.
         */
        private fun findCyclicNodes(nodes: Map<String, TechNode>): Set<String> {
            val reachable = HashSet<String>()
            var changed = true
            while (changed) {
                changed = false
                for (node in nodes.values) {
                    if (node.id in reachable) continue
                    val satisfiable = node.requires
                        .filterIsInstance<TechRequirement.Node>()
                        .all { it.nodeId in reachable }
                    if (satisfiable) {
                        reachable += node.id
                        changed = true
                    }
                }
            }
            return nodes.keys - reachable
        }

        fun empty(): TechTree = TechTree(emptyMap(), emptyList())
    }
}

package io.github.sk4ndulf.cobblemon.oneblock.core.progression

/**
 * A gate on a node. All of a node's requirements must hold before it can be bought.
 *
 * Written as short strings in techtree.json5 so the file stays readable:
 *
 *   "node:forest>=2"        another node at that level or higher
 *   "spent:oneblock>=10"    points already sunk into that category
 *   "boss:nether_king"      a Special this island has defeated
 *   "tech_points>=50"       lifetime points earned, for coarse tiering
 *
 * `spent:` is the WoW talent-tree gate and the one that gives a branch actual tiers: it does
 * not care *which* nodes were taken, only that the island has committed to the category. That
 * is what makes a deep single branch a real alternative to spreading points thin, rather than
 * a strictly worse version of buying everything cheap first.
 *
 * The boss gate is the one that matters structurally: it is what lets Special content pace
 * *when* a branch opens rather than only how fast (PROGRESSION_REWORK.md §3.1). It is parsed
 * and enforced from slice A even though bosses themselves ship in slice G, because retrofitting
 * a gate model into a tree that is already live is far more expensive than carrying an
 * unused case.
 */
sealed interface TechRequirement {

    data class Node(val nodeId: String, val level: Int) : TechRequirement

    data class Boss(val bossId: String) : TechRequirement

    /** Points already spent inside one category — the tier gate of a WoW-style tree. */
    data class CategorySpend(val category: TechCategory, val amount: Long) : TechRequirement

    /** A floor on *lifetime* points earned, not on the current balance. */
    data class LifetimePoints(val total: Long) : TechRequirement

    companion object {

        /** Returns null for anything unparseable; the caller reports it as a tree error. */
        fun parse(raw: String): TechRequirement? {
            val text = raw.trim()
            return when {
                text.startsWith(PREFIX_NODE) -> parseNode(text.removePrefix(PREFIX_NODE))
                text.startsWith(PREFIX_SPENT) -> parseSpend(text.removePrefix(PREFIX_SPENT))
                text.startsWith(PREFIX_BOSS) -> {
                    val id = text.removePrefix(PREFIX_BOSS).trim()
                    if (id.isEmpty()) null else Boss(id)
                }
                text.startsWith(PREFIX_POINTS) ->
                    text.removePrefix(PREFIX_POINTS).trim().toLongOrNull()?.takeIf { it >= 0 }?.let(::LifetimePoints)
                else -> null
            }
        }

        private fun parseNode(body: String): Node? {
            val parts = body.split(">=", limit = 2)
            val id = parts[0].trim()
            if (id.isEmpty()) return null
            // "node:forest" without a level means level 1 — the common case for a switch node.
            val level = if (parts.size == 1) 1 else parts[1].trim().toIntOrNull() ?: return null
            return if (level < 1) null else Node(id, level)
        }

        private fun parseSpend(body: String): CategorySpend? {
            val parts = body.split(">=", limit = 2)
            if (parts.size != 2) return null
            val category = TechCategory.parse(parts[0].trim()) ?: return null
            val amount = parts[1].trim().toLongOrNull()?.takeIf { it >= 0 } ?: return null
            return CategorySpend(category, amount)
        }

        private const val PREFIX_NODE = "node:"
        private const val PREFIX_SPENT = "spent:"
        private const val PREFIX_BOSS = "boss:"
        private const val PREFIX_POINTS = "tech_points>="
    }
}

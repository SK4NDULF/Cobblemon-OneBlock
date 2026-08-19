package io.github.sk4ndulf.cobblemon.oneblock.core.gui

import io.github.sk4ndulf.cobblemon.oneblock.core.island.IslandData
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechCategory
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechEffectText
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechNode
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechRequirement
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechService
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechTree
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * What the tech menus contain, as a pure function of island state and tree.
 *
 * Kept separate from [TechMenu] on purpose: opening a container needs a client, so the
 * *plumbing* cannot be tested headlessly, but the *contents* can. Everything that decides
 * what a player sees lives here and can be exercised from a command or a test; the other
 * file only turns the result into a packet.
 */
object TechMenuLayout {

    /** What a click on a slot should do. */
    sealed interface Action {
        data class OpenCategory(val category: TechCategory) : Action
        data class Buy(val nodeId: String) : Action
        data object Back : Action
        data object None : Action
    }

    data class Entry(val stack: ItemStack, val action: Action)

    const val MAIN_ROWS = 3
    const val CATEGORY_ROWS = 6

    /** Slot of the island summary in the main menu, and the six category slots after it. */
    private const val MAIN_INFO_SLOT = 4
    private val MAIN_CATEGORY_SLOTS = 10..15

    private const val CATEGORY_BACK_SLOT = 0
    private const val CATEGORY_INFO_SLOT = 4

    /** Rows available for tiers in a category menu: everything below the header row. */
    private const val CATEGORY_FIRST_TIER_ROW = 1

    private val CATEGORY_ICONS = mapOf(
        TechCategory.ONEBLOCK to "minecraft:grass_block",
        TechCategory.ISLAND to "minecraft:filled_map",
        TechCategory.PLAYERS to "minecraft:player_head",
        TechCategory.POKEMON to "cobblemon:poke_ball",
        TechCategory.BOOSTS to "minecraft:nether_star",
        TechCategory.SPECIAL to "minecraft:dragon_head",
    )

    // --- main menu ---------------------------------------------------------------------------

    fun main(island: IslandData, islandName: Component, tree: TechTree): Map<Int, Entry> {
        val state = TechService.stateOf(island.id)
        val entries = HashMap<Int, Entry>()

        entries[MAIN_INFO_SLOT] = Entry(
            MenuItems.stack(
                "minecraft:knowledge_book",
                islandName.copy().withStyle(ChatFormatting.GOLD),
                listOf(
                    ServerLang.msg("cobblemon_oneblock.menu.points_available", state.balance)
                        .withStyle(ChatFormatting.GREEN),
                    ServerLang.msg("cobblemon_oneblock.menu.points_earned", state.lifetimeEarned)
                        .withStyle(ChatFormatting.GRAY),
                    ServerLang.msg("cobblemon_oneblock.menu.tree_total", tree.totalCost)
                        .withStyle(ChatFormatting.DARK_GRAY),
                ),
            ),
            Action.None,
        )

        // Only categories that actually have nodes get a slot; an empty Special branch should
        // not occupy a button that does nothing.
        val categories = TechCategory.entries.filter { !tree.byCategory[it].isNullOrEmpty() }
        for ((index, category) in categories.withIndex()) {
            val slot = MAIN_CATEGORY_SLOTS.elementAtOrNull(index) ?: break
            val nodes = tree.byCategory[category].orEmpty()
            val owned = nodes.sumOf { state.levelOf(it.id) }
            val total = nodes.sumOf { it.maxLevel }
            entries[slot] = Entry(
                MenuItems.stack(
                    CATEGORY_ICONS[category] ?: "minecraft:paper",
                    ServerLang.msg("cobblemon_oneblock.menu.category.${category.key}")
                        .withStyle(ChatFormatting.AQUA),
                    listOf(
                        ServerLang.msg("cobblemon_oneblock.menu.category_ranks", owned, total)
                            .withStyle(ChatFormatting.GRAY),
                        ServerLang.msg("cobblemon_oneblock.menu.category_spent", state.spentIn(tree, category))
                            .withStyle(ChatFormatting.GRAY),
                        Component.empty(),
                        ServerLang.msg("cobblemon_oneblock.menu.click_open").withStyle(ChatFormatting.YELLOW),
                    ),
                    count = owned,
                ),
                Action.OpenCategory(category),
            )
        }
        return entries
    }

    // --- category menu -----------------------------------------------------------------------

    /**
     * The distinct `spent:` thresholds of a category, ascending — the tier rows.
     *
     * Derived from the nodes rather than configured separately, so retuning a gate in
     * `techtree.json5` moves the row with it and the two can never disagree. A node with no
     * gate into its own category is tier 0.
     */
    fun tiersOf(tree: TechTree, category: TechCategory): List<Long> =
        tree.byCategory[category].orEmpty()
            .map { tierThreshold(it, category) }
            .distinct()
            .sorted()

    private fun tierThreshold(node: TechNode, category: TechCategory): Long =
        node.requires
            .filterIsInstance<TechRequirement.CategorySpend>()
            .filter { it.category == category }
            .maxOfOrNull { it.amount }
            ?: 0L

    fun category(island: IslandData, tree: TechTree, category: TechCategory): Map<Int, Entry> {
        val state = TechService.stateOf(island.id)
        val entries = HashMap<Int, Entry>()
        val spent = state.spentIn(tree, category)

        entries[CATEGORY_BACK_SLOT] = Entry(
            MenuItems.stack(
                "minecraft:arrow",
                ServerLang.msg("cobblemon_oneblock.menu.back").withStyle(ChatFormatting.YELLOW),
            ),
            Action.Back,
        )
        entries[CATEGORY_INFO_SLOT] = Entry(
            MenuItems.stack(
                CATEGORY_ICONS[category] ?: "minecraft:paper",
                ServerLang.msg("cobblemon_oneblock.menu.category.${category.key}")
                    .withStyle(ChatFormatting.GOLD),
                listOf(
                    ServerLang.msg("cobblemon_oneblock.menu.points_available", state.balance)
                        .withStyle(ChatFormatting.GREEN),
                    ServerLang.msg("cobblemon_oneblock.menu.category_spent", spent)
                        .withStyle(ChatFormatting.GRAY),
                ),
            ),
            Action.None,
        )

        val tiers = tiersOf(tree, category)
        val nodesByTier = tree.byCategory[category].orEmpty().groupBy { tierThreshold(it, category) }

        for ((tierIndex, threshold) in tiers.withIndex()) {
            val row = CATEGORY_FIRST_TIER_ROW + tierIndex
            if (row >= CATEGORY_ROWS) break // more tiers than rows; the rest is admin over-design
            val rowStart = row * StaticMenu.SLOTS_PER_ROW
            val tierOpen = spent >= threshold

            if (!tierOpen) {
                // A closed tier is a wall of panes saying exactly how far away it is. This is
                // the whole point of the layout: you can see the next tier before you can
                // afford it.
                val missing = threshold - spent
                for (column in 0 until StaticMenu.SLOTS_PER_ROW) {
                    entries[rowStart + column] = Entry(
                        MenuItems.locked(
                            ServerLang.msg("cobblemon_oneblock.menu.tier_locked", missing),
                        ),
                        Action.None,
                    )
                }
                continue
            }

            val nodes = nodesByTier[threshold].orEmpty().sortedBy { it.id }
            for ((column, node) in nodes.withIndex()) {
                if (column >= StaticMenu.SLOTS_PER_ROW) break
                entries[rowStart + column] = nodeEntry(island, tree, node)
            }
        }
        return entries
    }

    private fun nodeEntry(island: IslandData, tree: TechTree, node: TechNode): Entry {
        val state = TechService.stateOf(island.id)
        val level = state.levelOf(node.id)
        val cost = node.costOfNext(level)
        val unmet = TechService.unmetRequirements(island, node)

        val implemented = tree.isImplemented(node)
        val colour = when {
            !implemented -> ChatFormatting.DARK_PURPLE
            cost == null -> ChatFormatting.DARK_GRAY
            unmet.isNotEmpty() -> ChatFormatting.RED
            state.balance < cost -> ChatFormatting.YELLOW
            else -> ChatFormatting.GREEN
        }

        val lore = ArrayList<Component>()
        if (node.description.isNotBlank()) {
            lore += Component.literal(node.description).withStyle(ChatFormatting.GRAY)
        }
        lore += Component.empty()
        when {
            // Said first and said plainly. A node that is merely expensive and one that does
            // not exist yet look identical otherwise, and only one of them is worth saving for.
            !implemented ->
                lore += ServerLang.msg("cobblemon_oneblock.tech.not_implemented")
                    .withStyle(ChatFormatting.DARK_PURPLE)
            cost == null ->
                lore += ServerLang.msg("cobblemon_oneblock.tech.maxed").withStyle(ChatFormatting.DARK_GRAY)
            else -> {
                lore += ServerLang.msg("cobblemon_oneblock.menu.next_rank", level + 1, cost)
                    .withStyle(if (state.balance >= cost) ChatFormatting.GREEN else ChatFormatting.YELLOW)
                // What the next rank actually adds, read from the payload rather than from the
                // node's prose — the prose goes stale the first time an admin retunes a number.
                for (line in TechEffectText.describe(node.effects[level + 1].orEmpty())) {
                    lore += Component.literal("  $line").withStyle(ChatFormatting.AQUA)
                }
            }
        }
        if (level > 0) {
            lore += Component.empty()
            lore += ServerLang.msg("cobblemon_oneblock.menu.active_now").withStyle(ChatFormatting.DARK_GRAY)
            for (line in TechEffectText.describe(node.effectsUpTo(level))) {
                lore += Component.literal("  $line").withStyle(ChatFormatting.DARK_GREEN)
            }
        }
        if (implemented) {
            for (requirement in unmet) {
                lore += ServerLang.msg("cobblemon_oneblock.menu.requires", describe(tree, requirement))
                    .withStyle(ChatFormatting.RED)
            }
            if (cost != null && unmet.isEmpty() && state.balance >= cost) {
                lore += Component.empty()
                lore += ServerLang.msg("cobblemon_oneblock.menu.click_buy").withStyle(ChatFormatting.YELLOW)
            }
        }

        return Entry(
            MenuItems.stack(
                node.icon,
                Component.literal("${node.displayName}  $level/${node.maxLevel}").withStyle(colour),
                lore,
                count = level,
            ),
            // Clicking a node that cannot be bought still routes to Buy; TechService answers
            // with the reason, which is a better message than a dead slot.
            Action.Buy(node.id),
        )
    }

    private fun describe(tree: TechTree, requirement: TechRequirement): String = when (requirement) {
        is TechRequirement.Node ->
            "${tree.node(requirement.nodeId)?.displayName ?: requirement.nodeId} ${requirement.level}"
        is TechRequirement.CategorySpend ->
            ServerLang.raw("cobblemon_oneblock.tech.req_spent", requirement.amount, requirement.category.key)
        is TechRequirement.LifetimePoints ->
            ServerLang.raw("cobblemon_oneblock.tech.req_points", requirement.total)
        is TechRequirement.Boss ->
            ServerLang.raw("cobblemon_oneblock.tech.req_boss", requirement.bossId)
    }
}

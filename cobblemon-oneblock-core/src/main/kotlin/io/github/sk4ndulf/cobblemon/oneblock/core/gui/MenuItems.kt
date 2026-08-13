package io.github.sk4ndulf.cobblemon.oneblock.core.gui

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore

/**
 * Item building for the chest menus.
 *
 * Two details that are easy to get wrong and look broken in game:
 * item names and lore are rendered *italic* by default, so every line is explicitly
 * un-italicised; and an [ItemStack] with a count of 0 is the empty stack, so a rank of 0 has
 * to become a count of 1 (which renders no number at all — exactly what "not bought" should
 * look like).
 */
object MenuItems {

    /** Icon ids that are missing — an admin typo or a mod that is not installed — fall back. */
    private val FALLBACK = Items.PAPER

    fun stack(
        itemId: String,
        name: Component,
        lore: List<Component> = emptyList(),
        count: Int = 1,
    ): ItemStack {
        val item = ResourceLocation.tryParse(itemId)
            ?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }
            ?: FALLBACK
        return ItemStack(item, count.coerceIn(1, 99)).apply {
            set(DataComponents.CUSTOM_NAME, plain(name))
            if (lore.isNotEmpty()) set(DataComponents.LORE, ItemLore(lore.map(::plain)))
        }
    }

    /** A filler pane. Named blank rather than left unnamed, so no "Gray Stained Glass Pane". */
    fun filler(): ItemStack = stack("minecraft:gray_stained_glass_pane", Component.literal(" "))

    fun locked(text: Component, lore: List<Component> = emptyList()): ItemStack =
        stack("minecraft:red_stained_glass_pane", text.copy().withStyle(ChatFormatting.RED), lore)

    /**
     * Strips the italic default that Minecraft applies to custom names and lore.
     *
     * Uses the operator form rather than `withStyle(Style.EMPTY.withItalic(false))`: the
     * latter merges two styles and would drop the colour already set on the component.
     */
    private fun plain(component: Component): Component =
        component.copy().withStyle { style: Style -> style.withItalic(false) }
}

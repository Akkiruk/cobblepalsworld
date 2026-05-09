package com.cobblepalsworld.tag.filter

import net.minecraft.item.ItemStack

/**
 * Filter configuration stored on a tag item.
 * @param items Ghost items to match against (up to 9)
 * @param whitelist True = only matching items pass. False = matching items are blocked.
 * @param matchNbt Whether to compare NBT/component data
 * @param matchTags Minecraft item tag IDs to match (e.g. "minecraft:logs")
 * @param matchModIds Mod namespace IDs to match (e.g. "minecraft", "cobblemon")
 */
data class TagFilter(
    val items: List<ItemStack> = emptyList(),
    val whitelist: Boolean = true,
    val matchNbt: Boolean = false,
    val matchTags: List<String> = emptyList(),
    val matchModIds: List<String> = emptyList()
) {
    companion object {
        const val MAX_FILTER_SLOTS = 9
        val EMPTY = TagFilter()
    }

    fun isEmpty(): Boolean = items.isEmpty() && matchTags.isEmpty() && matchModIds.isEmpty()
}

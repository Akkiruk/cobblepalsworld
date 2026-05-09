package com.cobblepalsworld.tag.filter

import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier

/**
 * Evaluates whether an ItemStack matches a TagFilter.
 * Supports item matching, Minecraft tag matching, and mod ID matching.
 */
object FilterMatcher {

    /**
     * Returns true if the given stack passes the filter.
     * Empty filter + whitelist = deny everything.
     * Empty filter + blacklist = allow everything.
     */
    fun matches(stack: ItemStack, filter: TagFilter): Boolean {
        if (stack.isEmpty) return false

        if (filter.isEmpty()) {
            return !filter.whitelist
        }

        val matchFound = matchesItems(stack, filter)
            || matchesTags(stack, filter)
            || matchesModIds(stack, filter)

        return if (filter.whitelist) matchFound else !matchFound
    }

    private fun matchesItems(stack: ItemStack, filter: TagFilter): Boolean {
        return filter.items.any { filterStack -> itemsMatch(stack, filterStack, filter.matchNbt) }
    }

    private fun matchesTags(stack: ItemStack, filter: TagFilter): Boolean {
        if (filter.matchTags.isEmpty()) return false
        return filter.matchTags.any { tagId ->
            val id = Identifier.tryParse(tagId) ?: return@any false
            val tagKey = TagKey.of(RegistryKeys.ITEM, id)
            stack.isIn(tagKey)
        }
    }

    private fun matchesModIds(stack: ItemStack, filter: TagFilter): Boolean {
        if (filter.matchModIds.isEmpty()) return false
        val itemId = Registries.ITEM.getId(stack.item)
        return filter.matchModIds.any { modId -> itemId.namespace == modId }
    }

    private fun itemsMatch(a: ItemStack, b: ItemStack, matchNbt: Boolean): Boolean {
        if (!ItemStack.areItemsEqual(a, b)) return false
        if (matchNbt && !ItemStack.areEqual(a, b)) return false
        return true
    }
}

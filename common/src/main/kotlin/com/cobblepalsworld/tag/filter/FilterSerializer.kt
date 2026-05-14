package com.cobblepalsworld.tag.filter

import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.registry.RegistryWrapper

/**
 * Serialize/deserialize TagFilter to/from NbtCompound for storage on tag items.
 */
object FilterSerializer {

    private const val KEY_ITEMS = "FilterItems"
    private const val KEY_WHITELIST = "Whitelist"
    private const val KEY_MATCH_NBT = "MatchNbt"
    private const val KEY_MATCH_TAGS = "MatchTags"
    private const val KEY_MATCH_MOD_IDS = "MatchModIds"
    private const val KEY_MATCH_MODE = "MatchMode"

    private fun parseMatchMode(value: String): FilterMatchMode {
        return FilterMatchMode.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: FilterMatchMode.ANY
    }

    fun toNbt(filter: TagFilter, registries: RegistryWrapper.WrapperLookup): NbtCompound {
        val nbt = NbtCompound()
        nbt.putBoolean(KEY_WHITELIST, filter.whitelist)
        nbt.putBoolean(KEY_MATCH_NBT, filter.matchNbt)
        nbt.putString(KEY_MATCH_MODE, filter.matchMode.name)

        val list = NbtList()
        for ((index, stack) in filter.items.withIndex()) {
            if (!stack.isEmpty) {
                val entry = NbtCompound()
                entry.putByte("Slot", index.toByte())
                entry.put("Item", stack.encodeAllowEmpty(registries))
                list.add(entry)
            }
        }
        nbt.put(KEY_ITEMS, list)

        if (filter.matchTags.isNotEmpty()) {
            val tagList = NbtList()
            filter.matchTags.forEach { tagList.add(NbtString.of(it)) }
            nbt.put(KEY_MATCH_TAGS, tagList)
        }

        if (filter.matchModIds.isNotEmpty()) {
            val modList = NbtList()
            filter.matchModIds.forEach { modList.add(NbtString.of(it)) }
            nbt.put(KEY_MATCH_MOD_IDS, modList)
        }

        return nbt
    }

    fun fromNbt(nbt: NbtCompound, registries: RegistryWrapper.WrapperLookup): TagFilter {
        val whitelist = nbt.getBoolean(KEY_WHITELIST)
        val matchNbt = nbt.getBoolean(KEY_MATCH_NBT)
        val matchMode = if (nbt.contains(KEY_MATCH_MODE)) {
            parseMatchMode(nbt.getString(KEY_MATCH_MODE))
        } else {
            FilterMatchMode.ANY
        }

        val items = mutableListOf<ItemStack>()
        val list = nbt.getList(KEY_ITEMS, 10)
        for (i in 0 until list.size) {
            val entry = list.getCompound(i)
            if (entry.contains("Item")) {
                val result = ItemStack.fromNbt(registries, entry.get("Item")!!)
                result.ifPresent { items.add(it) }
            }
        }

        val matchTags = mutableListOf<String>()
        if (nbt.contains(KEY_MATCH_TAGS)) {
            val tagList = nbt.getList(KEY_MATCH_TAGS, 8) // 8 = NbtString
            for (i in 0 until tagList.size) {
                matchTags.add(tagList.getString(i))
            }
        }

        val matchModIds = mutableListOf<String>()
        if (nbt.contains(KEY_MATCH_MOD_IDS)) {
            val modList = nbt.getList(KEY_MATCH_MOD_IDS, 8)
            for (i in 0 until modList.size) {
                matchModIds.add(modList.getString(i))
            }
        }

        return TagFilter(items, whitelist, matchNbt, matchTags, matchModIds, matchMode)
    }
}

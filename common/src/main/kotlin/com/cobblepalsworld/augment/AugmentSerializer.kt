package com.cobblepalsworld.augment

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList

object AugmentSerializer {
    private const val KEY_AUGMENTS = "Augments"
    private const val KEY_TYPE = "Type"
    private const val KEY_LEVEL = "Level"

    fun toNbt(augmentSet: AugmentSet): NbtCompound {
        val nbt = NbtCompound()
        val list = NbtList()
        for (entry in augmentSet.augments) {
            val entryNbt = NbtCompound()
            entryNbt.putString(KEY_TYPE, entry.type.id)
            entryNbt.putInt(KEY_LEVEL, entry.level)
            list.add(entryNbt)
        }
        nbt.put(KEY_AUGMENTS, list)
        return nbt
    }

    fun fromNbt(nbt: NbtCompound): AugmentSet {
        if (!nbt.contains(KEY_AUGMENTS)) return AugmentSet.EMPTY
        val list = nbt.getList(KEY_AUGMENTS, 10)
        val augments = mutableListOf<AugmentSet.AugmentEntry>()
        for (i in 0 until list.size) {
            val entryNbt = list.getCompound(i)
            val type = AugmentType.fromId(entryNbt.getString(KEY_TYPE)) ?: continue
            val level = entryNbt.getInt(KEY_LEVEL).coerceIn(1, type.maxLevel)
            augments.add(AugmentSet.AugmentEntry(type, level))
        }
        return AugmentSet(augments)
    }
}

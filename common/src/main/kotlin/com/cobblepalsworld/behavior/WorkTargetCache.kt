package com.cobblepalsworld.behavior

import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object WorkTargetCache {
    private const val DEFAULT_TTL_TICKS = 60L
    private const val MAX_ENTRIES = 512

    private val entries = linkedMapOf<Key, Entry>()

    fun getOrFind(
        world: World,
        family: String,
        origin: BlockPos,
        range: Int,
        extraKey: String = "",
        ttlTicks: Long = DEFAULT_TTL_TICKS,
        finder: () -> BlockPos?
    ): BlockPos? {
        prune(world.time)
        val key = Key(world.registryKey.value.toString(), family, origin.toImmutable(), range, extraKey)
        val cached = entries[key]
        if (cached != null && world.time - cached.foundAt <= ttlTicks) {
            return cached.pos
        }

        val found = finder()?.toImmutable()
        if (found != null) {
            entries[key] = Entry(found, world.time)
            trim()
        } else {
            entries.remove(key)
        }
        return found
    }

    fun invalidate(world: World, family: String, origin: BlockPos, range: Int, extraKey: String = "") {
        entries.remove(Key(world.registryKey.value.toString(), family, origin.toImmutable(), range, extraKey))
    }

    fun clear() {
        entries.clear()
    }

    private fun prune(now: Long) {
        entries.entries.removeIf { now - it.value.foundAt > DEFAULT_TTL_TICKS * 4 }
    }

    private fun trim() {
        while (entries.size > MAX_ENTRIES) {
            val first = entries.keys.firstOrNull() ?: return
            entries.remove(first)
        }
    }

    private data class Key(
        val worldKey: String,
        val family: String,
        val origin: BlockPos,
        val range: Int,
        val extraKey: String
    )

    private data class Entry(val pos: BlockPos, val foundAt: Long)
}
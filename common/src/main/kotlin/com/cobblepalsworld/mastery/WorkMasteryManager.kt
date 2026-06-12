package com.cobblepalsworld.mastery

import com.cobblepalsworld.tag.TagType
import net.minecraft.nbt.NbtCompound
import java.util.EnumMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Server-side ledger of completed jobs per Pokemon per role.
 * Mastery follows the Pokemon (keyed by its UUID), so a Master Harvester
 * keeps its rank when reassigned, re-leased, or moved between Command Posts.
 * Persisted through [com.cobblepalsworld.persistence.CobblePalsSaveData].
 */
object WorkMasteryManager {
    private val records = ConcurrentHashMap<UUID, EnumMap<TagType, Long>>()

    fun jobsCompleted(pokemonId: UUID, type: TagType): Long =
        records[pokemonId]?.get(type) ?: 0L

    fun tierFor(pokemonId: UUID, type: TagType): MasteryTier =
        MasteryTier.forJobs(jobsCompleted(pokemonId, type))

    /**
     * Records one completed job. Returns the new tier when this job crossed
     * a tier threshold, or null when the tier is unchanged.
     */
    fun recordJob(pokemonId: UUID, type: TagType): MasteryTier? {
        val perRole = records.computeIfAbsent(pokemonId) { EnumMap(TagType::class.java) }
        var leveledUpTo: MasteryTier? = null
        synchronized(perRole) {
            val before = perRole[type] ?: 0L
            val after = before + 1L
            perRole[type] = after
            val newTier = MasteryTier.forJobs(after)
            if (newTier != MasteryTier.forJobs(before)) {
                leveledUpTo = newTier
            }
        }
        return leveledUpTo
    }

    fun clear() {
        records.clear()
    }

    fun writeNbt(nbt: NbtCompound) {
        val masteryNbt = NbtCompound()
        for ((pokemonId, perRole) in records) {
            val roleNbt = NbtCompound()
            synchronized(perRole) {
                for ((type, jobs) in perRole) {
                    if (jobs > 0L) roleNbt.putLong(type.id, jobs)
                }
            }
            if (!roleNbt.isEmpty) masteryNbt.put(pokemonId.toString(), roleNbt)
        }
        nbt.put("Mastery", masteryNbt)
    }

    fun readNbt(nbt: NbtCompound) {
        clear()
        if (!nbt.contains("Mastery")) return
        val masteryNbt = nbt.getCompound("Mastery")
        for (key in masteryNbt.keys) {
            val pokemonId = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
            val roleNbt = masteryNbt.getCompound(key)
            val perRole = EnumMap<TagType, Long>(TagType::class.java)
            for (roleKey in roleNbt.keys) {
                val type = TagType.fromId(roleKey) ?: continue
                val jobs = roleNbt.getLong(roleKey)
                if (jobs > 0L) perRole[type] = jobs
            }
            if (perRole.isNotEmpty()) records[pokemonId] = perRole
        }
    }
}

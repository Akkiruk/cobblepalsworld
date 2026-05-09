package com.cobblepalsworld.pasture

import com.cobblepalsworld.tag.TagInstance
import net.minecraft.util.math.BlockPos
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PastureBinding(val dimensionId: String, val pos: BlockPos)

private data class AssignmentRecord(
    val tag: TagInstance,
    val pastureBinding: PastureBinding? = null
)

object TagAssignmentManager {
    private val assignments = ConcurrentHashMap<UUID, AssignmentRecord>()

    fun assign(pokemonId: UUID, tag: TagInstance) {
        val existing = assignments[pokemonId]
        assignments[pokemonId] = if (existing == null) AssignmentRecord(tag) else existing.copy(tag = tag)
    }

    fun associateWithPasture(pokemonId: UUID, dimensionId: String, pos: BlockPos) {
        assignments.computeIfPresent(pokemonId) { _, existing ->
            existing.copy(pastureBinding = PastureBinding(dimensionId, pos.toImmutable()))
        }
    }

    fun findOrphansAt(dimensionId: String, pos: BlockPos, currentIds: Set<UUID>): Set<UUID> {
        return assignments.entries.asSequence()
            .filter { (pokemonId, record) ->
                val binding = record.pastureBinding
                binding != null && binding.dimensionId == dimensionId && binding.pos == pos && pokemonId !in currentIds
            }
            .map { it.key }
            .toSet()
    }

    fun get(pokemonId: UUID): TagInstance? = assignments[pokemonId]?.tag

    fun remove(pokemonId: UUID): TagInstance? = assignments.remove(pokemonId)?.tag

    fun has(pokemonId: UUID): Boolean = assignments.containsKey(pokemonId)

    fun forEach(action: (UUID, TagInstance) -> Unit) = assignments.forEach { uuid, record -> action(uuid, record.tag) }

    fun forEachRecord(action: (UUID, TagInstance, PastureBinding?) -> Unit) {
        assignments.forEach { uuid, record -> action(uuid, record.tag, record.pastureBinding) }
    }

    fun clear() = assignments.clear()
}

package com.cobblepalsworld.pasture

import com.cobblepalsworld.tag.TagInstance
import net.minecraft.util.math.BlockPos
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PastureBinding(val dimensionId: String, val pos: BlockPos)
data class ControllerBinding(val dimensionId: String, val pos: BlockPos)

data class AssignmentView(
    val tag: TagInstance,
    val pastureBinding: PastureBinding?,
    val controllerBinding: ControllerBinding?
)

private data class AssignmentRecord(
    val tag: TagInstance,
    val pastureBinding: PastureBinding? = null,
    val controllerBinding: ControllerBinding? = null
)

object TagAssignmentManager {
    private val assignments = ConcurrentHashMap<UUID, AssignmentRecord>()

    fun assign(pokemonId: UUID, tag: TagInstance) {
        val normalizedTag = tag.copy(controllerPos = null)
        val existing = assignments[pokemonId]
        assignments[pokemonId] = if (existing == null) {
            AssignmentRecord(normalizedTag)
        } else {
            existing.copy(tag = normalizedTag, controllerBinding = null)
        }
    }

    fun assignFromController(pokemonId: UUID, tag: TagInstance, dimensionId: String, pos: BlockPos) {
        val controllerPos = pos.toImmutable()
        val normalizedTag = tag.copy(controllerPos = controllerPos)
        val existing = assignments[pokemonId]
        val controllerBinding = ControllerBinding(dimensionId, controllerPos)
        assignments[pokemonId] = if (existing == null) {
            AssignmentRecord(normalizedTag, controllerBinding = controllerBinding)
        } else {
            existing.copy(tag = normalizedTag, controllerBinding = controllerBinding)
        }
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

    fun getView(pokemonId: UUID): AssignmentView? {
        val record = assignments[pokemonId] ?: return null
        return AssignmentView(record.tag, record.pastureBinding, record.controllerBinding)
    }

    fun getControllerBinding(pokemonId: UUID): ControllerBinding? = assignments[pokemonId]?.controllerBinding

    fun isControlledBy(pokemonId: UUID, dimensionId: String, pos: BlockPos): Boolean {
        val binding = assignments[pokemonId]?.controllerBinding ?: return false
        return binding.dimensionId == dimensionId && binding.pos == pos
    }

    fun findControlledBy(dimensionId: String, pos: BlockPos): Set<UUID> {
        return assignments.entries.asSequence()
            .filter { (_, record) ->
                val binding = record.controllerBinding
                binding != null && binding.dimensionId == dimensionId && binding.pos == pos
            }
            .map { it.key }
            .toSet()
    }

    fun removeIfControlledBy(pokemonId: UUID, dimensionId: String, pos: BlockPos): TagInstance? {
        val record = assignments[pokemonId] ?: return null
        val binding = record.controllerBinding ?: return null
        if (binding.dimensionId != dimensionId || binding.pos != pos) return null
        return assignments.remove(pokemonId)?.tag
    }

    fun remove(pokemonId: UUID): TagInstance? = assignments.remove(pokemonId)?.tag

    fun has(pokemonId: UUID): Boolean = assignments.containsKey(pokemonId)

    fun count(): Int = assignments.size

    fun forEach(action: (UUID, TagInstance) -> Unit) = assignments.forEach { uuid, record -> action(uuid, record.tag) }

    fun forEachRecord(action: (UUID, TagInstance, PastureBinding?, ControllerBinding?) -> Unit) {
        assignments.forEach { uuid, record -> action(uuid, record.tag, record.pastureBinding, record.controllerBinding) }
    }

    fun clear() = assignments.clear()
}

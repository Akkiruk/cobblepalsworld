package com.cobblepalsworld.pasture

import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.session.WorkerSessionManager
import net.minecraft.util.math.BlockPos
import java.util.UUID

data class PastureBinding(val dimensionId: String, val pos: BlockPos)
data class ControllerBinding(val dimensionId: String, val pos: BlockPos)

data class AssignmentView(
    val tag: TagInstance,
    val pastureBinding: PastureBinding?,
    val controllerBinding: ControllerBinding?
)

object TagAssignmentManager {
    fun assign(pokemonId: UUID, tag: TagInstance) {
        WorkerSessionManager.assign(pokemonId, tag)
    }

    fun assignFromController(pokemonId: UUID, tag: TagInstance, dimensionId: String, pos: BlockPos) {
        WorkerSessionManager.assignFromController(pokemonId, tag, dimensionId, pos)
    }

    fun associateWithPasture(pokemonId: UUID, dimensionId: String, pos: BlockPos) {
        WorkerSessionManager.associateWithPasture(pokemonId, dimensionId, pos)
    }

    fun findOrphansAt(dimensionId: String, pos: BlockPos, currentIds: Set<UUID>): Set<UUID> {
        return WorkerSessionManager.findOrphansAt(dimensionId, pos, currentIds)
    }

    fun get(pokemonId: UUID): TagInstance? = WorkerSessionManager.getAssignment(pokemonId)

    fun getView(pokemonId: UUID): AssignmentView? {
        return WorkerSessionManager.getAssignmentView(pokemonId)
    }

    fun getControllerBinding(pokemonId: UUID): ControllerBinding? = WorkerSessionManager.getControllerBinding(pokemonId)

    fun isControlledBy(pokemonId: UUID, dimensionId: String, pos: BlockPos): Boolean {
        return WorkerSessionManager.isControlledBy(pokemonId, dimensionId, pos)
    }

    fun findControlledBy(dimensionId: String, pos: BlockPos): Set<UUID> {
        return WorkerSessionManager.findControlledBy(dimensionId, pos)
    }

    fun removeIfControlledBy(pokemonId: UUID, dimensionId: String, pos: BlockPos): TagInstance? {
        return WorkerSessionManager.removeIfControlledBy(pokemonId, dimensionId, pos)
    }

    fun remove(pokemonId: UUID): TagInstance? = WorkerSessionManager.removeAssignment(pokemonId)

    fun has(pokemonId: UUID): Boolean = WorkerSessionManager.hasAssignment(pokemonId)

    fun count(): Int = WorkerSessionManager.countAssignments()

    fun forEach(action: (UUID, TagInstance) -> Unit) = WorkerSessionManager.forEachAssignment(action)

    fun forEachRecord(action: (UUID, TagInstance, PastureBinding?, ControllerBinding?) -> Unit) {
        WorkerSessionManager.forEachAssignmentRecord(action)
    }

    fun clear() = WorkerSessionManager.clearAssignments()
}

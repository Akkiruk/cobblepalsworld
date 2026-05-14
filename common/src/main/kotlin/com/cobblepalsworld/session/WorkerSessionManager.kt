package com.cobblepalsworld.session

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.inventory.InventorySizeCalculator
import com.cobblepalsworld.inventory.PokemonInventory
import com.cobblepalsworld.assignment.AssignmentView
import com.cobblepalsworld.assignment.ControllerBinding
import com.cobblepalsworld.assignment.WorksiteBinding
import com.cobblepalsworld.assignment.WorkerAssignmentMode
import com.cobblepalsworld.assignment.WorkerAssignmentProfile
import com.cobblepalsworld.tag.TagInstance
import net.minecraft.util.math.BlockPos
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class WorkerSession(
    val pokemonId: UUID,
    var tag: TagInstance? = null,
    var worksiteBinding: WorksiteBinding? = null,
    var controllerBinding: ControllerBinding? = null,
    var state: WorkerState? = null,
    var inventory: PokemonInventory? = null,
    var assignmentProfile: WorkerAssignmentProfile = WorkerAssignmentProfile()
)

private data class BindingIndexKey(val dimensionId: String, val pos: BlockPos)

object WorkerSessionManager {
    private val sessions = ConcurrentHashMap<UUID, WorkerSession>()
    private val worksiteIndex = ConcurrentHashMap<BindingIndexKey, MutableSet<UUID>>()
    private val controllerIndex = ConcurrentHashMap<BindingIndexKey, MutableSet<UUID>>()

    fun getSession(pokemonId: UUID): WorkerSession? = sessions[pokemonId]

    fun assign(pokemonId: UUID, tag: TagInstance) {
        val session = getOrCreateSession(pokemonId)
        removeFromControllerIndex(pokemonId, session.controllerBinding)
        session.tag = tag.copy(controllerPos = null)
        session.controllerBinding = null
        addToWorksiteIndex(pokemonId, session.worksiteBinding)
    }

    fun assignFromController(pokemonId: UUID, tag: TagInstance, dimensionId: String, pos: BlockPos) {
        val controllerPos = pos.toImmutable()
        val session = getOrCreateSession(pokemonId)
        removeFromControllerIndex(pokemonId, session.controllerBinding)
        session.tag = tag.copy(controllerPos = controllerPos)
        session.controllerBinding = ControllerBinding(dimensionId, controllerPos)
        addToControllerIndex(pokemonId, session.controllerBinding)
        addToWorksiteIndex(pokemonId, session.worksiteBinding)
    }

    fun associateWithWorksite(pokemonId: UUID, dimensionId: String, pos: BlockPos) {
        sessions.computeIfPresent(pokemonId) { _, session ->
            if (session.tag != null) {
                val nextBinding = WorksiteBinding(dimensionId, pos.toImmutable())
                if (session.worksiteBinding != nextBinding) {
                    removeFromWorksiteIndex(pokemonId, session.worksiteBinding)
                    session.worksiteBinding = nextBinding
                    addToWorksiteIndex(pokemonId, session.worksiteBinding)
                }
            }
            session
        }
    }

    fun getAssignment(pokemonId: UUID): TagInstance? = sessions[pokemonId]?.tag

    fun getAssignmentView(pokemonId: UUID): AssignmentView? {
        val session = sessions[pokemonId] ?: return null
        val tag = session.tag ?: return null
        return AssignmentView(tag, session.worksiteBinding, session.controllerBinding, session.assignmentProfile)
    }

    fun getAssignmentProfile(pokemonId: UUID): WorkerAssignmentProfile {
        return sessions[pokemonId]?.assignmentProfile ?: WorkerAssignmentProfile()
    }

    fun updateAssignmentProfile(
        pokemonId: UUID,
        mode: WorkerAssignmentMode? = null,
        allowFallback: Boolean? = null
    ): WorkerAssignmentProfile {
        val session = getOrCreateSession(pokemonId)
        session.assignmentProfile = session.assignmentProfile.copy(
            mode = mode ?: session.assignmentProfile.mode,
            allowFallback = allowFallback ?: session.assignmentProfile.allowFallback
        )
        return session.assignmentProfile
    }

    fun getControllerBinding(pokemonId: UUID): ControllerBinding? = sessions[pokemonId]?.controllerBinding

    fun isControlledBy(pokemonId: UUID, dimensionId: String, pos: BlockPos): Boolean {
        val binding = sessions[pokemonId]?.controllerBinding ?: return false
        return binding.dimensionId == dimensionId && binding.pos == pos
    }

    fun findControlledBy(dimensionId: String, pos: BlockPos): Set<UUID> {
        val indexed = controllerIndex[BindingIndexKey(dimensionId, pos.toImmutable())] ?: return emptySet()
        return indexed.asSequence()
            .filter { pokemonId ->
                val session = sessions[pokemonId]
                val binding = session?.controllerBinding
                session?.tag != null && binding != null && binding.dimensionId == dimensionId && binding.pos == pos
            }
            .toSet()
    }

    fun removeIfControlledBy(pokemonId: UUID, dimensionId: String, pos: BlockPos): TagInstance? {
        val session = sessions[pokemonId] ?: return null
        val binding = session.controllerBinding ?: return null
        if (binding.dimensionId != dimensionId || binding.pos != pos) return null
        return clearAssignment(pokemonId, session)
    }

    fun removeAssignment(pokemonId: UUID): TagInstance? {
        val session = sessions[pokemonId] ?: return null
        return clearAssignment(pokemonId, session)
    }

    fun hasAssignment(pokemonId: UUID): Boolean = sessions[pokemonId]?.tag != null

    fun countAssignments(): Int = sessions.values.count { it.tag != null }

    fun forEachAssignment(action: (UUID, TagInstance) -> Unit) {
        sessions.forEach { (uuid, session) ->
            session.tag?.let { action(uuid, it) }
        }
    }

    fun forEachAssignmentRecord(action: (UUID, TagInstance, WorksiteBinding?, ControllerBinding?) -> Unit) {
        sessions.forEach { (uuid, session) ->
            session.tag?.let { action(uuid, it, session.worksiteBinding, session.controllerBinding) }
        }
    }

    fun forEachAssignmentRecord(action: (UUID, TagInstance, WorksiteBinding?, ControllerBinding?, WorkerAssignmentProfile) -> Unit) {
        sessions.forEach { (uuid, session) ->
            session.tag?.let { action(uuid, it, session.worksiteBinding, session.controllerBinding, session.assignmentProfile) }
        }
    }

    fun clearAssignments() {
        sessions.forEach { (uuid, session) ->
            removeFromWorksiteIndex(uuid, session.worksiteBinding)
            removeFromControllerIndex(uuid, session.controllerBinding)
            session.tag = null
            session.worksiteBinding = null
            session.controllerBinding = null
            session.assignmentProfile = WorkerAssignmentProfile()
            discardIfEmpty(uuid, session)
        }
        worksiteIndex.clear()
        controllerIndex.clear()
    }

    fun getOrCreateState(pokemonId: UUID): WorkerState {
        val session = getOrCreateSession(pokemonId)
        val current = session.state
        if (current != null) return current
        return WorkerState(pokemonId).also { session.state = it }
    }

    fun getState(pokemonId: UUID): WorkerState? = sessions[pokemonId]?.state

    fun pruneStaleRuntime(currentTime: Long, staleAfterTicks: Long): Set<UUID> {
        val removed = mutableSetOf<UUID>()
        sessions.forEach { (pokemonId, session) ->
            val state = session.state ?: return@forEach
            val isStale = state.lastSeenTick > 0L && currentTime - state.lastSeenTick > staleAfterTicks
            if (!isStale) return@forEach

            session.state = null
            removed += pokemonId
            discardIfEmpty(pokemonId, session)
        }
        return removed
    }

    fun removeState(pokemonId: UUID) {
        val session = sessions[pokemonId] ?: return
        session.state = null
        discardIfEmpty(pokemonId, session)
    }

    fun countStates(): Int = sessions.values.count { it.state != null }

    fun clearStates() {
        sessions.forEach { (uuid, session) ->
            session.state = null
            discardIfEmpty(uuid, session)
        }
    }

    fun getOrCreateInventory(pokemon: Pokemon): PokemonInventory {
        val session = getOrCreateSession(pokemon.uuid)
        val desiredSlots = InventorySizeCalculator.calculate(pokemon)
        val current = session.inventory
        if (current == null) {
            return PokemonInventory(pokemon.uuid, desiredSlots).also { session.inventory = it }
        }

        if (desiredSlots == current.size()) {
            return current
        }

        val resized = resizeInventory(pokemon.uuid, current, desiredSlots) ?: return current
        session.inventory = resized
        return resized
    }

    fun getInventory(pokemonId: UUID): PokemonInventory? = sessions[pokemonId]?.inventory

    fun removeInventory(pokemonId: UUID): PokemonInventory? {
        val session = sessions[pokemonId] ?: return null
        val inventory = session.inventory
        session.inventory = null
        discardIfEmpty(pokemonId, session)
        return inventory
    }

    fun putInventory(pokemonId: UUID, inventory: PokemonInventory) {
        getOrCreateSession(pokemonId).inventory = inventory
    }

    fun countInventories(): Int = sessions.values.count { it.inventory != null }

    fun forEachInventory(action: (UUID, PokemonInventory) -> Unit) {
        sessions.forEach { (uuid, session) ->
            session.inventory?.let { action(uuid, it) }
        }
    }

    fun pruneInventories(shouldKeep: (UUID, PokemonInventory) -> Boolean) {
        sessions.forEach { (pokemonId, session) ->
            val inventory = session.inventory ?: return@forEach
            if (shouldKeep(pokemonId, inventory)) return@forEach

            session.inventory = null
            discardIfEmpty(pokemonId, session)
        }
    }

    fun clearInventories() {
        sessions.forEach { (uuid, session) ->
            session.inventory = null
            discardIfEmpty(uuid, session)
        }
    }

    fun clearAll() {
        sessions.clear()
        worksiteIndex.clear()
        controllerIndex.clear()
    }

    private fun getOrCreateSession(pokemonId: UUID): WorkerSession {
        return sessions.computeIfAbsent(pokemonId) { WorkerSession(it) }
    }

    private fun clearAssignment(pokemonId: UUID, session: WorkerSession): TagInstance? {
        val tag = session.tag ?: return null
        removeFromWorksiteIndex(pokemonId, session.worksiteBinding)
        removeFromControllerIndex(pokemonId, session.controllerBinding)
        session.tag = null
        session.worksiteBinding = null
        session.controllerBinding = null
        session.assignmentProfile = WorkerAssignmentProfile()
        discardIfEmpty(pokemonId, session)
        return tag
    }

    private fun addToWorksiteIndex(pokemonId: UUID, binding: WorksiteBinding?) {
        binding ?: return
        worksiteIndex.getOrPut(BindingIndexKey(binding.dimensionId, binding.pos.toImmutable())) { ConcurrentHashMap.newKeySet() }.add(pokemonId)
    }

    private fun removeFromWorksiteIndex(pokemonId: UUID, binding: WorksiteBinding?) {
        binding ?: return
        removeFromIndex(worksiteIndex, BindingIndexKey(binding.dimensionId, binding.pos.toImmutable()), pokemonId)
    }

    private fun addToControllerIndex(pokemonId: UUID, binding: ControllerBinding?) {
        binding ?: return
        controllerIndex.getOrPut(BindingIndexKey(binding.dimensionId, binding.pos.toImmutable())) { ConcurrentHashMap.newKeySet() }.add(pokemonId)
    }

    private fun removeFromControllerIndex(pokemonId: UUID, binding: ControllerBinding?) {
        binding ?: return
        removeFromIndex(controllerIndex, BindingIndexKey(binding.dimensionId, binding.pos.toImmutable()), pokemonId)
    }

    private fun removeFromIndex(index: ConcurrentHashMap<BindingIndexKey, MutableSet<UUID>>, key: BindingIndexKey, pokemonId: UUID) {
        val entries = index[key] ?: return
        entries.remove(pokemonId)
        if (entries.isEmpty()) {
            index.remove(key, entries)
        }
    }

    private fun discardIfEmpty(pokemonId: UUID, session: WorkerSession) {
        if (session.tag != null || session.state != null || session.inventory != null) {
            return
        }
        if (session.worksiteBinding != null || session.controllerBinding != null) {
            return
        }
        sessions.remove(pokemonId, session)
    }

    private fun resizeInventory(pokemonId: UUID, current: PokemonInventory, desiredSlots: Int): PokemonInventory? {
        val resized = PokemonInventory(pokemonId, desiredSlots)
        for (slot in 0 until current.size()) {
            val stack = current.getStack(slot)
            if (stack.isEmpty) continue

            val remainder = resized.insertStack(stack.copy())
            if (!remainder.isEmpty) {
                return null
            }
        }
        return resized
    }
}

package com.cobblepalsworld.router

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.behavior.state.StateManager
import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.pasture.TagAssignmentManager
import com.cobblepalsworld.tag.TagInstance
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import java.util.UUID

object RouterExecutionEngine {
    private const val BASE_COOLDOWN = 20

    fun tick(world: ServerWorld, pos: BlockPos, _state: net.minecraft.block.BlockState, router: RouterBlockEntity) {
        if (router.cooldownTicks > 0) {
            router.cooldownTicks -= 1
            return
        }

        val linkedPasture = router.linkedPasture(world) ?: run {
            val controlled = TagAssignmentManager.findControlledBy(world.registryKey.value.toString(), pos)
            val changed = releaseControlledWorkers(world, router, router.linkedPastureAnchor(), controlled)
            router.clearAssignedWorkers()
            router.unlinkPasture()
            router.updateStatus(false, 0, 0, 0)
            router.updatePowered(false)
            router.cooldownTicks = BASE_COOLDOWN
            if (changed) {
                CobblePalsSaveData.markDirty(world)
            }
            return
        }

        val dimensionId = world.registryKey.value.toString()
        val pasturePos = linkedPasture.pos.toImmutable()
        val augments = router.installedAugments()
        val tasksBySlot = buildMap {
            for (slotIndex in 0 until RouterBlockEntity.MODULE_SLOT_COUNT) {
                val tag = router.tagInModuleSlot(slotIndex, world.registryManager, augments) ?: continue
                put(slotIndex, tag)
            }
        }
        val roster = collectRoster(linkedPasture)
        val manualAssignments = roster.count { candidate ->
            TagAssignmentManager.has(candidate.pokemonId) && TagAssignmentManager.getControllerBinding(candidate.pokemonId) == null
        }
        val maxAssignableWorkers = (ConfigManager.config.general.maxWorkersPerPasture - manualAssignments).coerceAtLeast(0)
        val controlledBefore = TagAssignmentManager.findControlledBy(dimensionId, pos)
        val rosterById = roster.associateBy { it.pokemonId }
        val claimed = mutableSetOf<UUID>()
        val controlledThisTick = mutableSetOf<UUID>()

        var assignedCount = 0
        var activeCount = 0
        var changed = false

        for (slotIndex in 0 until RouterBlockEntity.MODULE_SLOT_COUNT) {
            val tag = tasksBySlot[slotIndex]
            if (tag == null || assignedCount >= maxAssignableWorkers) {
                if (router.assignedWorker(slotIndex) != null) {
                    router.setAssignedWorker(slotIndex, null)
                    changed = true
                }
                continue
            }

            val currentPokemonId = router.assignedWorker(slotIndex)
            val chosen = selectCandidate(
                roster = roster,
                rosterById = rosterById,
                currentPokemonId = currentPokemonId,
                dimensionId = dimensionId,
                controllerPos = pos,
                claimed = claimed,
                startIndex = router.dispatchCursorStart(roster.size)
            )

            if (chosen == null) {
                if (currentPokemonId != null) {
                    router.setAssignedWorker(slotIndex, null)
                    changed = true
                }
                continue
            }

            claimed += chosen.pokemonId
            controlledThisTick += chosen.pokemonId

            if (currentPokemonId != chosen.pokemonId) {
                router.setAssignedWorker(slotIndex, chosen.pokemonId)
                router.advanceDispatchCursor(roster.size)
                changed = true
            }

            if (ensureAssignment(world, pos, pasturePos, chosen.pokemonId, tag)) {
                changed = true
            }

            assignedCount += 1
            if (StateManager.get(chosen.pokemonId)?.phase != WorkerPhase.IDLE) {
                activeCount += 1
            }
        }

        if (releaseControlledWorkers(world, router, pasturePos, controlledBefore - controlledThisTick)) {
            changed = true
        }

        router.updateStatus(true, roster.size, assignedCount, activeCount)
        router.updatePowered(activeCount > 0)
        router.cooldownTicks = BASE_COOLDOWN

        if (changed) {
            CobblePalsSaveData.markDirty(world)
        }
    }

    private fun collectRoster(pasture: PokemonPastureBlockEntity): List<WorkerCandidate> {
        return pasture.tetheredPokemon.mapNotNull { tethering ->
            val pokemon = try {
                tethering.getPokemon()
            } catch (_: Exception) {
                null
            } ?: return@mapNotNull null
            if (pokemon.isFainted()) return@mapNotNull null

            val entity = pokemon.entity ?: return@mapNotNull null
            if (entity.dataTracker.get(PokemonEntity.POSE_TYPE) == PoseType.SLEEP) {
                return@mapNotNull null
            }

            WorkerCandidate(pokemon.uuid)
        }
    }

    private fun selectCandidate(
        roster: List<WorkerCandidate>,
        rosterById: Map<UUID, WorkerCandidate>,
        currentPokemonId: UUID?,
        dimensionId: String,
        controllerPos: BlockPos,
        claimed: Set<UUID>,
        startIndex: Int
    ): WorkerCandidate? {
        val currentCandidate = currentPokemonId?.let(rosterById::get)
        if (currentCandidate != null && canUseCandidate(currentCandidate.pokemonId, dimensionId, controllerPos, claimed)) {
            return currentCandidate
        }
        if (roster.isEmpty()) return null

        for (offset in roster.indices) {
            val candidate = roster[(startIndex + offset) % roster.size]
            if (canUseCandidate(candidate.pokemonId, dimensionId, controllerPos, claimed)) {
                return candidate
            }
        }
        return null
    }

    private fun canUseCandidate(pokemonId: UUID, dimensionId: String, controllerPos: BlockPos, claimed: Set<UUID>): Boolean {
        if (pokemonId in claimed) return false
        val controllerBinding = TagAssignmentManager.getControllerBinding(pokemonId)
        return when {
            controllerBinding == null -> !TagAssignmentManager.has(pokemonId)
            else -> controllerBinding.dimensionId == dimensionId && controllerBinding.pos == controllerPos
        }
    }

    private fun ensureAssignment(world: ServerWorld, controllerPos: BlockPos, pasturePos: BlockPos, pokemonId: UUID, tag: TagInstance): Boolean {
        val dimensionId = world.registryKey.value.toString()
        val existing = TagAssignmentManager.getView(pokemonId)
        val sameController = existing?.controllerBinding?.let { binding ->
            binding.dimensionId == dimensionId && binding.pos == controllerPos
        } == true
        val samePasture = existing?.pastureBinding?.let { binding ->
            binding.dimensionId == dimensionId && binding.pos == pasturePos
        } == true
        if (sameController && samePasture && existing?.tag == tag) {
            return false
        }

        if (existing != null && existing.tag != tag) {
            TagExecutionEngine.cleanup(pokemonId, world, pasturePos)
        }

        TagAssignmentManager.assignFromController(pokemonId, tag, dimensionId, controllerPos)
        TagAssignmentManager.associateWithPasture(pokemonId, dimensionId, pasturePos)
        return true
    }

    private fun releaseControlledWorkers(world: ServerWorld, router: RouterBlockEntity, cleanupPos: BlockPos, pokemonIds: Set<UUID>): Boolean {
        if (pokemonIds.isEmpty()) return false
        val dimensionId = world.registryKey.value.toString()
        var changed = false
        pokemonIds.forEach { pokemonId ->
            if (TagAssignmentManager.removeIfControlledBy(pokemonId, dimensionId, router.pos) != null) {
                TagExecutionEngine.cleanup(pokemonId, world, cleanupPos)
                changed = true
            }
        }
        return changed
    }

    private data class WorkerCandidate(val pokemonId: UUID)
}
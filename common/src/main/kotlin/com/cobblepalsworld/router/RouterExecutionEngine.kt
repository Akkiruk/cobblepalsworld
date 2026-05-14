package com.cobblepalsworld.router

import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.behavior.state.StateManager
import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.behavior.state.WorkerStatusKind
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.crew.CommandPostCrewLifecycle
import com.cobblepalsworld.crew.CommandPostCrewManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.networking.CobblePalsNetworking
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.assignment.TagAssignmentManager
import com.cobblepalsworld.runtime.ServerScaleRuntime
import com.cobblepalsworld.tag.TagInstance
import net.minecraft.registry.Registries
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import java.util.UUID

object RouterExecutionEngine {
    private const val BASE_COOLDOWN = 20

    fun tick(world: ServerWorld, pos: BlockPos, _state: net.minecraft.block.BlockState, router: RouterBlockEntity) {
        CobblePalsSaveData.ensureLoaded(world)
        ServerScaleRuntime.beforeWorksiteTick(world)
        val dimensionId = world.registryKey.value.toString()
        var changed = false

        val nativeCrewMembers = CommandPostCrewManager.findMembers(dimensionId, pos)
        val hasNativeCrew = nativeCrewMembers.isNotEmpty()
        if (!hasNativeCrew) {
            if (releaseControlledWorkers(world, router, pos.toImmutable(), controlledWorkerIds(dimensionId, pos))) {
                changed = true
            }
            router.clearAssignedWorkers()
            router.updateStatus(false, 0, 0, 0)
            router.updatePowered(false)
            router.cooldownTicks = 0
            if (changed) {
                CobblePalsSaveData.markDirty(world)
            }
            return
        }

        val originPos = pos.toImmutable()
        val roster = collectNativeRoster(world, router, nativeCrewMembers)
        val visibleRosterCount = nativeCrewMembers.size

        if (router.cooldownTicks > 0) {
            router.cooldownTicks -= 1
            val assignedWorkerCount = (0 until RouterBlockEntity.MODULE_SLOT_COUNT).count { router.assignedWorker(it) != null }
            val activeWorkerCount = (0 until RouterBlockEntity.MODULE_SLOT_COUNT).count { slotIndex ->
                val pokemonId = router.assignedWorker(slotIndex) ?: return@count false
                StateManager.get(pokemonId)?.phase?.let { it != WorkerPhase.IDLE } == true
            }
            router.updateStatus(hasNativeCrew, visibleRosterCount, assignedWorkerCount, activeWorkerCount)
            router.updatePowered(activeWorkerCount > 0)
            return
        }

        val augments = router.installedAugments()
        val tasksBySlot = buildTasksBySlot(world, router, augments)

        val manualAssignments = roster.count { candidate ->
            TagAssignmentManager.has(candidate.pokemonId) && TagAssignmentManager.getControllerBinding(candidate.pokemonId) == null
        }
        val maxAssignableWorkers = (ConfigManager.config.general.maxWorkersPerPasture - manualAssignments).coerceAtLeast(0)
        val controlledBefore = controlledWorkerIds(dimensionId, pos)
        val rosterById = roster.associateBy { it.pokemonId }
        val claimed = mutableSetOf<UUID>()
        val controlledThisTick = mutableSetOf<UUID>()
        val navigationBudget = ServerScaleRuntime.navigationBudget(world, ConfigManager.config.general.maxPathStartsPerPastureTick)
        val activeVisuals = mutableListOf<CobblePalsNetworking.WorkerVisualSnapshot>()

        var assignedWorkerCount = 0
        var activeWorkerCount = 0

        for (slotIndex in 0 until RouterBlockEntity.MODULE_SLOT_COUNT) {
            val tag = tasksBySlot[slotIndex]
            if (tag == null || assignedWorkerCount >= maxAssignableWorkers) {
                if (router.assignedWorker(slotIndex) != null) {
                    router.setAssignedWorker(slotIndex, null)
                    changed = true
                }
                if (tag == null) {
                    router.clearModuleRuntime(slotIndex)
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

            if (ensureAssignment(world, pos, originPos, chosen.pokemonId, tag)) {
                changed = true
            }

            if (chosen.pokemon != null && chosen.entity != null) {
                try {
                    TagExecutionEngine.tick(world, chosen.entity, chosen.pokemon, tag, originPos, navigationBudget)
                    buildWorkerVisual(chosen.entity, chosen.pokemonId)?.let(activeVisuals::add)
                } catch (e: Exception) {
                    com.cobblepalsworld.CobblePalsWorld.LOGGER.error("Error ticking native Command Post worker", e)
                }
            }

            assignedWorkerCount += 1
            if (StateManager.get(chosen.pokemonId)?.phase?.let { it != WorkerPhase.IDLE } == true) {
                activeWorkerCount += 1
            }
        }

        if (releaseControlledWorkers(world, router, originPos, controlledBefore - controlledThisTick)) {
            changed = true
        }

        markIdleNativeCrew(roster, controlledThisTick, activeVisuals)
        val nearbyPlayers = ServerScaleRuntime.nearbyWorksitePlayers(world, pos)
        if (nearbyPlayers.isNotEmpty() && ServerScaleRuntime.shouldSendWorksiteVisuals(world, pos, activeVisuals)) {
            CobblePalsNetworking.sendWorkerVisuals(nearbyPlayers, pos, activeVisuals)
        }

        router.updateStatus(hasNativeCrew, visibleRosterCount, assignedWorkerCount, activeWorkerCount)
        router.updatePowered(activeWorkerCount > 0)
        router.cooldownTicks = BASE_COOLDOWN

        if (changed) {
            CobblePalsSaveData.markDirty(world)
        }
    }

    private fun buildTasksBySlot(world: ServerWorld, router: RouterBlockEntity, augments: com.cobblepalsworld.augment.AugmentSet): Map<Int, TagInstance> {
        return buildMap {
            for (slotIndex in 0 until RouterBlockEntity.MODULE_SLOT_COUNT) {
                val tag = router.tagInModuleSlot(slotIndex, world.registryManager, augments) ?: continue
                if (!ConfigManager.config.getTagConfig(tag.type).enabled) continue
                put(slotIndex, tag)
            }
        }
    }

    private fun controlledWorkerIds(dimensionId: String, pos: BlockPos): Set<UUID> {
        return TagAssignmentManager.findControlledBy(dimensionId, pos)
    }

    private fun collectNativeRoster(world: ServerWorld, router: RouterBlockEntity, crewMembers: List<com.cobblepalsworld.crew.CommandPostCrewMember>): List<WorkerCandidate> {
        val fallbackOwnerUuid = router.ownerUuid()
        return crewMembers.mapNotNull { member ->
            val pokemon = CommandPostCrewLifecycle.resolvePokemon(world, member, fallbackOwnerUuid) ?: return@mapNotNull null
            if (pokemon.isFainted()) return@mapNotNull null
            val entity = CommandPostCrewLifecycle.ensureEntity(world, router.pos, pokemon) ?: return@mapNotNull null
            if (entity.dataTracker.get(PokemonEntity.POSE_TYPE) == PoseType.SLEEP) return@mapNotNull null
            WorkerCandidate(member.pokemonId, pokemon, entity)
        }
    }

    private fun markIdleNativeCrew(
        roster: List<WorkerCandidate>,
        controlledThisTick: Set<UUID>,
        activeVisuals: MutableList<CobblePalsNetworking.WorkerVisualSnapshot>
    ) {
        roster.forEach { candidate ->
            if (candidate.pokemonId in controlledThisTick) return@forEach
            val entity = candidate.entity ?: return@forEach
            val state = StateManager.getOrCreate(candidate.pokemonId)
            state.lastSeenTick = entity.world.time
            if (state.phase == WorkerPhase.IDLE) {
                state.setStatus(WorkerStatusReason.READY, "Waiting for a Command Post role card")
            }
            buildWorkerVisual(entity, candidate.pokemonId)?.let(activeVisuals::add)
        }
    }

    private fun buildWorkerVisual(entity: PokemonEntity, pokemonId: UUID): CobblePalsNetworking.WorkerVisualSnapshot? {
        val assignmentView = TagAssignmentManager.getView(pokemonId) ?: return null
        val state = StateManager.get(pokemonId)

        var primaryCarriedItemId: String? = null
        var carriedItemCount = 0
        InventoryManager.get(pokemonId)?.let { inventory ->
            for (slot in 0 until inventory.size()) {
                val stack = inventory.getStack(slot)
                if (stack.isEmpty) continue
                carriedItemCount += stack.count
                if (primaryCarriedItemId == null) {
                    primaryCarriedItemId = Registries.ITEM.getId(stack.item).toString()
                }
            }
        }

        val phase = state?.phase ?: WorkerPhase.IDLE
        val statusKind = state?.statusReason?.kind
        if (phase == WorkerPhase.IDLE && primaryCarriedItemId == null && statusKind != WorkerStatusKind.BLOCKED && statusKind != WorkerStatusKind.STANDBY) {
            return null
        }

        return CobblePalsNetworking.WorkerVisualSnapshot(
            entityId = entity.id,
            tagTypeId = assignmentView.tag.type.id,
            phaseOrdinal = phase.ordinal,
            statusReasonOrdinal = state?.statusReason?.ordinal ?: WorkerStatusReason.READY.ordinal,
            primaryCarriedItemId = primaryCarriedItemId,
            carriedItemCount = carriedItemCount
        )
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

    private fun ensureAssignment(world: ServerWorld, controllerPos: BlockPos, worksitePos: BlockPos, pokemonId: UUID, tag: TagInstance): Boolean {
        val dimensionId = world.registryKey.value.toString()
        val existing = TagAssignmentManager.getView(pokemonId)
        val sameController = existing?.controllerBinding?.let { binding ->
            binding.dimensionId == dimensionId && binding.pos == controllerPos
        } == true
        val sameWorksite = existing?.worksiteBinding?.let { binding ->
            binding.dimensionId == dimensionId && binding.pos == worksitePos
        } == true
        if (sameController && sameWorksite && existing?.tag == tag) {
            return false
        }

        if (existing != null && existing.tag != tag) {
            TagExecutionEngine.cleanup(pokemonId, world, worksitePos)
        }

        TagAssignmentManager.assignFromController(pokemonId, tag, dimensionId, controllerPos)
        TagAssignmentManager.associateWithWorksite(pokemonId, dimensionId, worksitePos)
        return true
    }

    private fun releaseControlledWorkers(world: ServerWorld, router: RouterBlockEntity, cleanupPos: BlockPos, pokemonIds: Set<UUID>): Boolean {
        if (pokemonIds.isEmpty()) return false
        val dimensionId = world.registryKey.value.toString()
        var changed = false
        pokemonIds.forEach { pokemonId ->
            TagExecutionEngine.cleanup(pokemonId, world, cleanupPos)
            if (TagAssignmentManager.removeIfControlledBy(pokemonId, dimensionId, router.pos) != null) {
                changed = true
            }
        }
        return changed
    }

    private data class WorkerCandidate(val pokemonId: UUID, val pokemon: Pokemon? = null, val entity: PokemonEntity? = null)
}
package com.cobblepalsworld.router

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.behavior.state.StateManager
import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.crew.CommandPostCrewLifecycle
import com.cobblepalsworld.crew.CommandPostCrewManager
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.pasture.TagAssignmentManager
import com.cobblepalsworld.tag.TagInstance
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import java.util.UUID

object RouterExecutionEngine {
    private const val BASE_COOLDOWN = 20

    fun tick(world: ServerWorld, pos: BlockPos, _state: net.minecraft.block.BlockState, router: RouterBlockEntity) {
        CobblePalsSaveData.ensureLoaded(world)
        val dimensionId = world.registryKey.value.toString()
        var changed = false

        var linkedPasture = router.linkedPasture(world)
        if (linkedPasture != null && CommandPostCrewManager.countAt(dimensionId, pos) == 0) {
            if (migrateLinkedPastureCrew(world, router, linkedPasture, dimensionId, pos)) {
                changed = true
                linkedPasture = null
            }
        }
        val nativeCrewMembers = CommandPostCrewManager.findMembers(dimensionId, pos)
        val hasNativeCrew = nativeCrewMembers.isNotEmpty()
        if (linkedPasture == null && !hasNativeCrew) {
            if (releaseControlledWorkers(world, router, router.linkedPastureAnchor(), controlledWorkerIds(dimensionId, pos))) {
                changed = true
            }
            router.clearAssignedWorkers()
            router.unlinkPasture()
            router.updateStatus(false, 0, 0, 0)
            router.updatePowered(false)
            router.cooldownTicks = 0
            if (changed) {
                CobblePalsSaveData.markDirty(world)
            }
            return
        }

        val originPos = linkedPasture?.pos?.toImmutable() ?: pos.toImmutable()
        val roster = if (hasNativeCrew) collectNativeRoster(world, router, nativeCrewMembers) else collectRoster(linkedPasture ?: return)
        val visibleRosterCount = if (hasNativeCrew) nativeCrewMembers.size else roster.size

        if (router.cooldownTicks > 0) {
            router.cooldownTicks -= 1
            val assignedWorkerCount = (0 until RouterBlockEntity.MODULE_SLOT_COUNT).count { router.assignedWorker(it) != null }
            val activeWorkerCount = (0 until RouterBlockEntity.MODULE_SLOT_COUNT).count { slotIndex ->
                val pokemonId = router.assignedWorker(slotIndex) ?: return@count false
                StateManager.get(pokemonId)?.phase?.let { it != WorkerPhase.IDLE } == true
            }
            router.updateStatus(linkedPasture != null, visibleRosterCount, assignedWorkerCount, activeWorkerCount)
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

            assignedWorkerCount += 1
            if (StateManager.get(chosen.pokemonId)?.phase?.let { it != WorkerPhase.IDLE } == true) {
                activeWorkerCount += 1
            }
        }

        if (releaseControlledWorkers(world, router, originPos, controlledBefore - controlledThisTick)) {
            changed = true
        }

        router.updateStatus(linkedPasture != null, visibleRosterCount, assignedWorkerCount, activeWorkerCount)
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

    private fun collectNativeRoster(world: ServerWorld, router: RouterBlockEntity, crewMembers: List<com.cobblepalsworld.crew.CommandPostCrewMember>): List<WorkerCandidate> {
        val fallbackOwnerUuid = router.ownerUuid()
        return crewMembers.mapNotNull { member ->
            val pokemon = CommandPostCrewLifecycle.resolvePokemon(world, member, fallbackOwnerUuid) ?: return@mapNotNull null
            if (pokemon.isFainted()) return@mapNotNull null
            val entity = CommandPostCrewLifecycle.ensureEntity(world, router.pos, pokemon) ?: return@mapNotNull null
            if (entity.dataTracker.get(PokemonEntity.POSE_TYPE) == PoseType.SLEEP) return@mapNotNull null
            WorkerCandidate(member.pokemonId)
        }
    }

    private fun migrateLinkedPastureCrew(
        world: ServerWorld,
        router: RouterBlockEntity,
        pasture: PokemonPastureBlockEntity,
        dimensionId: String,
        controllerPos: BlockPos
    ): Boolean {
        val ownerUuid = router.ownerUuid() ?: return false
        var migrated = false
        pasture.tetheredPokemon.forEach { tethering ->
            val pokemon = try {
                tethering.getPokemon()
            } catch (_: Exception) {
                null
            } ?: return@forEach
            val source = locateOwnedSource(world, ownerUuid, pokemon.uuid)
            if (CommandPostCrewManager.assign(
                    pokemonId = pokemon.uuid,
                    ownerUuid = ownerUuid,
                    dimensionId = dimensionId,
                    pos = controllerPos,
                    sourceType = source?.sourceType ?: "UNKNOWN",
                    boxIndex = source?.boxIndex ?: -1,
                    slotIndex = source?.slotIndex ?: -1,
                    displayName = pokemon.getDisplayName(false).string,
                    species = pokemon.species.name,
                    level = pokemon.level
                )
            ) {
                migrated = true
            }
        }
        if (migrated) {
            router.unlinkPasture()
        }
        return migrated
    }

    private fun locateOwnedSource(world: ServerWorld, ownerUuid: UUID, pokemonId: UUID): CrewSourceLocation? {
        val storage = Cobblemon.storage
        val party = storage.getParty(ownerUuid, world.registryManager)
        for (slot in 0 until party.size()) {
            val pokemon = party.get(slot) ?: continue
            if (pokemon.uuid == pokemonId) return CrewSourceLocation("PARTY", -1, slot)
        }
        val pc = storage.getPC(ownerUuid, world.registryManager)
        pc.boxes.forEachIndexed { boxIndex, box ->
            box.getNonEmptySlots().entries.forEach { entry ->
                if (entry.value.uuid == pokemonId) return CrewSourceLocation("PC", boxIndex, entry.key)
            }
        }
        return null
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
            TagExecutionEngine.cleanup(pokemonId, world, cleanupPos)
            if (TagAssignmentManager.removeIfControlledBy(pokemonId, dimensionId, router.pos) != null) {
                changed = true
            }
        }
        return changed
    }

    private data class WorkerCandidate(val pokemonId: UUID)
    private data class CrewSourceLocation(val sourceType: String, val boxIndex: Int, val slotIndex: Int)
}
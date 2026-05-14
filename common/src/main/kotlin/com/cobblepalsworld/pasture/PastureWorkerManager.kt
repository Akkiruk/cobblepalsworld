package com.cobblepalsworld.pasture

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.behavior.state.StateManager
import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.behavior.state.WorkerStatusKind
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.networking.CobblePalsNetworking
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.navigation.NavigationBudget
import com.cobblepalsworld.navigation.NavigationHelper
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.runtime.ServerScaleRuntime
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.pasture.WorkerAssignmentMode
import com.cobblepalsworld.pasture.WorkerAssignmentProfile
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private data class PastureKey(val dimension: RegistryKey<World>, val pos: BlockPos)

private data class IdleRotationKey(val pastureKey: PastureKey, val bucketId: String)

private data class WorkerCandidate(
    val pokemonId: UUID,
    val pokemon: com.cobblemon.mod.common.pokemon.Pokemon,
    val entity: PokemonEntity,
    val tag: TagInstance,
    val state: WorkerState?,
    val carriesCargo: Boolean,
    val assignmentProfile: WorkerAssignmentProfile
) {
    val isInFlight: Boolean
        get() = state?.phase?.let { it != WorkerPhase.IDLE } == true || carriesCargo
}

object PastureWorkerManager {
    private val baseTickInterval get() = ConfigManager.config.general.tickInterval

    // Track previously-tethered UUIDs per pasture for recall cleanup
    private val previousTethered = ConcurrentHashMap<PastureKey, MutableSet<UUID>>()
    // Track previously-tagged UUIDs per pasture for tag removal cleanup
    private val previouslyTagged = ConcurrentHashMap<PastureKey, MutableSet<UUID>>()
    private val idleRotationCursor = ConcurrentHashMap<IdleRotationKey, Int>()

    fun findPastureForPokemon(world: ServerWorld, pokemonId: UUID): PokemonPastureBlockEntity? {
        val pasturePos = ServerScaleRuntime.findPasturePos(world, pokemonId)
            ?: previousTethered.entries.firstOrNull { (key, tetheredIds) ->
                key.dimension == world.registryKey && pokemonId in tetheredIds
            }?.key?.pos
            ?: return null
        return world.getBlockEntity(pasturePos) as? PokemonPastureBlockEntity
    }

    fun tickPasture(world: World, pos: BlockPos, pasture: PokemonPastureBlockEntity) {
        if (world.isClient) return
        val serverWorld = world as? ServerWorld ?: return
        val pastureKey = PastureKey(serverWorld.registryKey, pos.toImmutable())

        ServerScaleRuntime.beforePastureTick(serverWorld)

        val nearbyPlayers = nearbyPlayers(serverWorld, pos)
        val navigationBudget = ServerScaleRuntime.navigationBudget(serverWorld, ConfigManager.config.general.maxPathStartsPerPastureTick)

        // Stagger pasture ticks — offset by position hash, with slower cadence for distant pastures.
        val pastureTickInterval = effectiveTickInterval(nearbyPlayers.isNotEmpty())
        if ((world.time + (pos.hashCode().toLong() and 0x7FFFFFFF)) % pastureTickInterval != 0L) return

        val tethered = pasture.tetheredPokemon
        val activeVisuals = mutableListOf<CobblePalsNetworking.WorkerVisualSnapshot>()

        // Detect recalled Pokémon and clean up their state
        val currentIds = mutableSetOf<UUID>()
        for (t in tethered) {
            try { currentIds.add(t.pokemonId) } catch (_: Exception) {}
        }
        val prevIds = previousTethered.getOrPut(pastureKey) { mutableSetOf() }
        val orphanedAssignments = TagAssignmentManager.findOrphansAt(serverWorld.registryKey.value.toString(), pos, currentIds)
        val missingIds = buildSet {
            prevIds.forEach { prevId ->
                if (prevId !in currentIds) add(prevId)
            }
            addAll(orphanedAssignments)
        }
        for (missingId in missingIds) {
            TagExecutionEngine.cleanup(missingId, world, pos)
            TagAssignmentManager.remove(missingId)
        }
        prevIds.clear()
        prevIds.addAll(currentIds)
        ServerScaleRuntime.rememberPastureMembership(serverWorld, pos, currentIds)

        // Detect tag removal: clean up state for pokemon that lost their tag
        val currentlyTagged = mutableSetOf<UUID>()
        for (id in currentIds) {
            if (TagAssignmentManager.has(id)) {
                TagAssignmentManager.associateWithPasture(id, serverWorld.registryKey.value.toString(), pos)
                currentlyTagged.add(id)
            }
        }
        val prevTagged = previouslyTagged.getOrPut(pastureKey) { mutableSetOf() }
        for (prevId in prevTagged) {
            if (prevId in currentIds && prevId !in currentlyTagged) {
                // Pokemon is still tethered but lost its tag — clean up work state
                TagExecutionEngine.cleanup(prevId, world, pos)
            }
        }
        prevTagged.clear()
        prevTagged.addAll(currentlyTagged)

        val workerCandidates = mutableListOf<WorkerCandidate>()
        for (tethering in tethered) {
            val pokemon = try { tethering.getPokemon() } catch (_: Exception) { continue }
                ?: continue
            if (pokemon.isFainted()) continue

            val entity = pokemon.entity ?: continue
            val poseType = entity.dataTracker.get(PokemonEntity.POSE_TYPE)
            if (poseType == PoseType.SLEEP) continue

            val tag = TagAssignmentManager.get(pokemon.uuid)
            if (tag != null) {
                workerCandidates += WorkerCandidate(
                    pokemonId = pokemon.uuid,
                    pokemon = pokemon,
                    entity = entity,
                    tag = tag,
                    state = StateManager.get(pokemon.uuid),
                    carriesCargo = InventoryManager.get(pokemon.uuid)?.isEmpty == false,
                    assignmentProfile = TagAssignmentManager.getProfile(pokemon.uuid)
                )
            } else {
                // Idle pokemon (no tag): wander within 4 blocks of pasture
                tickIdleWander(world, entity, pos)
            }
        }

        val maxWorkers = ConfigManager.config.general.maxWorkersPerPasture
        val activeCandidates = workerCandidates.filter { it.isInFlight }
        val idleCandidates = workerCandidates.filterNot { it.isInFlight }
        val strictLockedTagIds = activeStrictRoles(activeCandidates + idleCandidates)
        val reservedIdleCandidates = idleCandidates.filter { it.assignmentProfile.mode == WorkerAssignmentMode.RESERVED }
        val strictSuppressedIdleCandidates = idleCandidates.filter { candidate ->
            candidate.assignmentProfile.mode == WorkerAssignmentMode.GENERAL && candidate.tag.type.id in strictLockedTagIds
        }
        val schedulableIdleCandidates = idleCandidates.filter { candidate ->
            candidate !in reservedIdleCandidates && candidate !in strictSuppressedIdleCandidates
        }
        val prioritizedIdleCandidates = prioritizedIdleCandidates(pastureKey, schedulableIdleCandidates)
        val selectedIdleCandidates = prioritizedIdleCandidates.take((maxWorkers - activeCandidates.size).coerceAtLeast(0))

        (activeCandidates + selectedIdleCandidates).forEach { candidate ->
            try {
                TagExecutionEngine.tick(world, candidate.entity, candidate.pokemon, candidate.tag, pos, navigationBudget)
                buildWorkerVisual(candidate.entity, candidate.pokemonId)?.let(activeVisuals::add)
            } catch (e: Exception) {
                com.cobblepalsworld.CobblePalsWorld.LOGGER.error("Error ticking Pokémon worker", e)
            }
        }

        reservedIdleCandidates.forEach { candidate ->
            val state = StateManager.getOrCreate(candidate.pokemonId)
            state.lastSeenTick = world.time
            state.setStatus(WorkerStatusReason.RESERVED_DUTY, "Held in reserve and excluded from general labor")
            buildWorkerVisual(candidate.entity, candidate.pokemonId)?.let(activeVisuals::add)
        }

        strictSuppressedIdleCandidates.forEach { candidate ->
            val state = StateManager.getOrCreate(candidate.pokemonId)
            state.lastSeenTick = world.time
            state.setStatus(WorkerStatusReason.ROLE_LOCKED, "A preferred worker is currently holding this role")
            buildWorkerVisual(candidate.entity, candidate.pokemonId)?.let(activeVisuals::add)
        }

        prioritizedIdleCandidates.drop(selectedIdleCandidates.size).forEach { candidate ->
            val state = StateManager.getOrCreate(candidate.pokemonId)
            state.lastSeenTick = world.time
            state.setStatus(WorkerStatusReason.WORKER_CAP, "Waiting for an open pasture worker slot")
            buildWorkerVisual(candidate.entity, candidate.pokemonId)?.let(activeVisuals::add)
        }

        advanceIdleRotationCursors(
            pastureKey = pastureKey,
            idleCandidates = schedulableIdleCandidates,
            selectedIdleCandidates = selectedIdleCandidates
        )

        if (nearbyPlayers.isNotEmpty() && ServerScaleRuntime.shouldSendWorkerVisuals(serverWorld, pos, activeVisuals)) {
            CobblePalsNetworking.sendWorkerVisuals(nearbyPlayers, pos, activeVisuals)
        }
    }

    private const val WANDER_RADIUS = 4
    private const val WANDER_INTERVAL = 80L // attempt wander every ~4 seconds

    private fun effectiveTickInterval(hasNearbyPlayer: Boolean): Long {
        val general = ConfigManager.config.general
        if (general.distantTickMultiplier <= 1) {
            return baseTickInterval.toLong()
        }

        val multiplier = if (hasNearbyPlayer) 1 else general.distantTickMultiplier
        return (baseTickInterval * multiplier).toLong().coerceAtLeast(1L)
    }

    private fun nearbyPlayers(world: ServerWorld, pos: BlockPos): List<ServerPlayerEntity> {
        return ServerScaleRuntime.nearbyPlayers(world, pos)
    }

    private fun buildWorkerVisual(
        entity: PokemonEntity,
        pokemonId: UUID
    ): CobblePalsNetworking.WorkerVisualSnapshot? {
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

    private fun prioritizedIdleCandidates(pastureKey: PastureKey, candidates: List<WorkerCandidate>): List<WorkerCandidate> {
        val preferred = rotateIdleCandidates(pastureKey, "preferred", candidates.filter { it.assignmentProfile.mode == WorkerAssignmentMode.PREFERRED })
        val general = rotateIdleCandidates(pastureKey, "general", candidates.filter { it.assignmentProfile.mode == WorkerAssignmentMode.GENERAL })
        return preferred + general
    }

    private fun rotateIdleCandidates(pastureKey: PastureKey, bucketId: String, candidates: List<WorkerCandidate>): List<WorkerCandidate> {
        if (candidates.size <= 1) return candidates

        val start = Math.floorMod(idleRotationCursor[IdleRotationKey(pastureKey, bucketId)] ?: 0, candidates.size)
        return List(candidates.size) { index ->
            candidates[(start + index) % candidates.size]
        }
    }

    private fun advanceIdleRotationCursors(
        pastureKey: PastureKey,
        idleCandidates: List<WorkerCandidate>,
        selectedIdleCandidates: List<WorkerCandidate>
    ) {
        advanceIdleRotationCursor(
            pastureKey = pastureKey,
            bucketId = "preferred",
            idleCandidateCount = idleCandidates.count { it.assignmentProfile.mode == WorkerAssignmentMode.PREFERRED },
            advanceBy = selectedIdleCandidates.count { it.assignmentProfile.mode == WorkerAssignmentMode.PREFERRED }
        )
        advanceIdleRotationCursor(
            pastureKey = pastureKey,
            bucketId = "general",
            idleCandidateCount = idleCandidates.count { it.assignmentProfile.mode == WorkerAssignmentMode.GENERAL },
            advanceBy = selectedIdleCandidates.count { it.assignmentProfile.mode == WorkerAssignmentMode.GENERAL }
        )
    }

    private fun advanceIdleRotationCursor(pastureKey: PastureKey, bucketId: String, idleCandidateCount: Int, advanceBy: Int) {
        val rotationKey = IdleRotationKey(pastureKey, bucketId)
        if (idleCandidateCount <= 1) {
            if (idleCandidateCount == 0) {
                idleRotationCursor.remove(rotationKey)
            } else {
                idleRotationCursor[rotationKey] = 0
            }
            return
        }

        if (advanceBy <= 0) return

        val current = idleRotationCursor[rotationKey] ?: 0
        idleRotationCursor[rotationKey] = Math.floorMod(current + advanceBy, idleCandidateCount)
    }

    private fun activeStrictRoles(candidates: List<WorkerCandidate>): Set<String> {
        return candidates.asSequence()
            .filter { candidate ->
                candidate.assignmentProfile.mode == WorkerAssignmentMode.PREFERRED &&
                    !candidate.assignmentProfile.allowFallback &&
                    candidate.state?.statusReason?.kind != WorkerStatusKind.BLOCKED
            }
            .map { it.tag.type.id }
            .toSet()
    }

    private fun tickIdleWander(world: World, entity: PokemonEntity, pasturePos: BlockPos) {
        if (world.time % WANDER_INTERVAL != 0L) return

        val dx = entity.x - (pasturePos.x + 0.5)
        val dz = entity.z - (pasturePos.z + 0.5)
        val distSq = dx * dx + dz * dz

        if (distSq > (WANDER_RADIUS + 1) * (WANDER_RADIUS + 1)) {
            // Too far — walk back toward pasture
            entity.navigation.startMovingTo(
                pasturePos.x + 0.5, pasturePos.y.toDouble(), pasturePos.z + 0.5, 0.6
            )
            return
        }

        // Random wander within radius
        val random = world.random
        val targetX = pasturePos.x + random.nextInt(WANDER_RADIUS * 2 + 1) - WANDER_RADIUS
        val targetZ = pasturePos.z + random.nextInt(WANDER_RADIUS * 2 + 1) - WANDER_RADIUS
        val targetY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, targetX, targetZ)

        entity.navigation.startMovingTo(
            targetX + 0.5, targetY.toDouble(), targetZ + 0.5, 0.5
        )
    }

    fun onPastureBroken(pasture: PokemonPastureBlockEntity) {
        val world = pasture.world ?: return
        if (world.isClient) return

        for (tethering in pasture.tetheredPokemon) {
            try {
                // Clean up worker state and drop carried items at pasture location
                TagExecutionEngine.cleanup(tethering.pokemonId, world, pasture.pos)
                // Remove tag assignment data
                TagAssignmentManager.remove(tethering.pokemonId)
            } catch (_: Exception) {}
        }
        val pastureKey = PastureKey(world.registryKey, pasture.pos.toImmutable())
        previousTethered.remove(pastureKey)
        previouslyTagged.remove(pastureKey)
        idleRotationCursor.entries.removeIf { it.key.pastureKey == pastureKey }
        ServerScaleRuntime.forgetPasture(world, pasture.pos)

        // Mark save data dirty so cleanup is persisted
        (world as? ServerWorld)?.let { CobblePalsSaveData.markDirty(it) }
    }

    fun onServerStopping(server: net.minecraft.server.MinecraftServer) {
        CobblePalsSaveData.markDirty(server)
        TagExecutionEngine.resetRuntimeState()
        CobblePalsSaveData.clearLoaded(server)
        ServerScaleRuntime.clear(server)
        resetTransientState(clearInitialization = true)
    }

    fun markDirtyNow(world: World) {
        (world as? ServerWorld)?.let { CobblePalsSaveData.markDirty(it) }
    }

    fun resetTransientState(clearInitialization: Boolean = false) {
        previousTethered.clear()
        previouslyTagged.clear()
        idleRotationCursor.clear()
        ServerScaleRuntime.clearTransient()
    }
}

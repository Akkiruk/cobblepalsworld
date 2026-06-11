package com.cobblepalsworld.behavior

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.session.WorkerSessionManager
import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.behavior.state.WorkerStatusKind
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.inventory.PokemonInventory
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.navigation.NavigationBudget
import com.cobblepalsworld.navigation.NavigationAttempt
import com.cobblepalsworld.navigation.NavigationHelper
import com.cobblepalsworld.navigation.MovementPurpose
import com.cobblepalsworld.navigation.SafePositionResolver
import com.cobblepalsworld.navigation.WorkerNavigationManager
import com.cobblepalsworld.assignment.TagAssignmentManager
import com.cobblepalsworld.mastery.WorkMasteryManager
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.RedstoneControlMode
import com.cobblepalsworld.visual.WorkVisualHandler
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import kotlin.math.abs

object TagExecutionEngine {
    private val config get() = ConfigManager.config.general
    private const val WORKSPACE_PADDING_BLOCKS = 2
    private const val ENTITY_LEASH_PADDING_BLOCKS = 10
    private const val MIN_ENTITY_LEASH_BLOCKS = 28
    private const val MIN_LOGISTICS_RANGE = 32

    // --- Compiled cache helpers (avoid recomputing every tick) ---

    /** Get or compute the effective cooldown for this tag. Cached on WorkerState. */
    fun defaultCooldownTicks(tag: TagInstance, state: WorkerState): Long {
        if (state.cachedCooldown < 0) {
            val masteryMultiplier = WorkMasteryManager.tierFor(state.pokemonId, tag.type).cooldownMultiplier
            state.cachedCooldown = (config.workCooldownTicks * tag.augments.speedMultiplier() * masteryMultiplier).toLong().coerceAtLeast(5)
        }
        return state.cachedCooldown
    }

    fun defaultArrivalDelayTicks(): Long = config.arrivalDelayTicks.toLong()

    fun defaultIdleRetryTicks(): Long = config.idleSearchRetryTicks.toLong()

    /** Get or compute the effective search range. Cached on WorkerState. */
    fun effectiveRange(tag: TagInstance, state: WorkerState): Int {
        if (state.cachedRange < 0) {
            state.cachedRange = maxOf(ConfigManager.config.getTagConfig(tag.type).range, minimumBaseRange(tag.type)) +
                tag.augments.extraRange() +
                WorkMasteryManager.tierFor(state.pokemonId, tag.type).bonusRange
        }
        return state.cachedRange
    }

    /** Get or compute the effective max items per trip. Cached on WorkerState. */
    fun effectiveMaxItems(tag: TagInstance, state: WorkerState): Int {
        if (state.cachedMaxItems < 0) {
            state.cachedMaxItems = ConfigManager.config.getTagConfig(tag.type).maxItemsPerTrip + tag.augments.extraItemsPerTrip()
        }
        return state.cachedMaxItems
    }

    fun isTagEnabled(type: TagType): Boolean {
        return ConfigManager.config.getTagConfig(type).enabled
    }

    private fun minimumBaseRange(type: TagType): Int {
        return when (type) {
            TagType.SENDER, TagType.PULLER, TagType.DISTRIBUTOR, TagType.DROPPER, TagType.VOID -> MIN_LOGISTICS_RANGE
            else -> 1
        }
    }

    fun tick(
        world: World,
        entity: PokemonEntity,
        pokemon: Pokemon,
        tag: TagInstance,
        origin: BlockPos,
        navigationBudget: NavigationBudget
    ) {
        val state = WorkerSessionManager.getOrCreateState(pokemon.uuid)
        state.lastSeenTick = world.time

        if (!isTagEnabled(tag.type)) {
            if (state.phase != WorkerPhase.IDLE) {
                releaseClaimAndReset(world, state)
            }
            state.setStatus(WorkerStatusReason.TAG_DISABLED, "status.cobblepalsworld.detail.tag_disabled")
            return
        }

        val behavior = TagBehaviorRegistry.get(tag.type) ?: return

        if (enforceWorksiteLeash(world, entity, tag, behavior, state, origin, navigationBudget)) {
            return
        }

        if (!passesRedstoneGate(world, origin, tag, state)) {
            val inventory = InventoryManager.get(pokemon.uuid)
            if (inventory != null && !inventory.isEmpty && !behavior.handlesOwnInventory) {
                if (state.phase != WorkerPhase.DEPOSITING) {
                    releaseClaimAndReset(world, state)
                    state.phase = WorkerPhase.DEPOSITING
                }
                state.setStatus(WorkerStatusReason.DEPOSITING, "status.cobblepalsworld.detail.depositing_redstone")
            } else {
                if (state.phase != WorkerPhase.IDLE) {
                    releaseClaimAndReset(world, state)
                }
                state.setStatus(WorkerStatusReason.REDSTONE_OFF, "status.cobblepalsworld.detail.redstone_off")
                return
            }
        } else {
            state.targetPos?.let { ClaimManager.touch(it, pokemon.uuid, world) }
        }

        // --- Eco mode: idle workers tick at a reduced rate to save CPU ---
        if (state.phase == WorkerPhase.IDLE) {
            state.idleTicks += config.tickInterval
            if (state.idleTicks >= config.ecoTimeoutTicks) {
                state.ecoMode = true
            }
            if (state.ecoMode) {
                state.ecoSkipCounter++
                if (state.ecoSkipCounter < config.ecoTickMultiplier) {
                    if (state.statusReason == WorkerStatusReason.READY) {
                        state.setStatus(WorkerStatusReason.ECO_IDLE, "status.cobblepalsworld.detail.eco_idle")
                    }
                    return
                }
                state.ecoSkipCounter = 0
            }
        }

        // Keep looking at active target during arrival
        if (state.phase == WorkerPhase.ARRIVING) {
            state.targetPos?.let { WorkVisualHandler.lookAt(entity, it) }
        }

        // Ambient working particles every ~3 seconds
        if (state.phase != WorkerPhase.IDLE && world.time % 60L == 0L) {
            WorkVisualHandler.onWorking(world, entity, tag.type)
        }

        when (state.phase) {
            WorkerPhase.IDLE -> tickIdle(world, entity, pokemon, tag, behavior, state, origin, navigationBudget)
            WorkerPhase.NAVIGATING -> tickNavigating(world, entity, tag, behavior, state, origin, navigationBudget)
            WorkerPhase.ARRIVING -> tickArriving(world, entity, tag, behavior, state)
            WorkerPhase.WORKING -> tickWorking(world, entity, pokemon, tag, behavior, state, origin, navigationBudget)
            WorkerPhase.DEPOSITING -> tickDepositing(world, entity, pokemon, tag, behavior, state, origin, navigationBudget)
        }

        if (state.statusReason.kind == WorkerStatusKind.BLOCKED && world.time % 60L == 0L) {
            WorkVisualHandler.onBlocked(world, entity, state.statusReason)
        }
    }

    fun cleanup(pokemonId: java.util.UUID, world: World? = null, pos: BlockPos? = null) {
        cleanupRuntimeOnly(pokemonId)

        val inventory = InventoryManager.remove(pokemonId)
        if (inventory != null && world != null && pos != null) {
            recoverCarriedInventory(pokemonId, world, pos, inventory)
            (world as? ServerWorld)?.let(CobblePalsSaveData::markDirty)
        }
    }

    fun cleanupRuntimeOnly(pokemonId: java.util.UUID) {
        ClaimManager.releaseAll(pokemonId)
        WorkerSessionManager.removeState(pokemonId)
        TagBehaviorRegistry.all().forEach { it.onWorkerCleanup(pokemonId) }
    }

    fun pruneStaleRuntime(currentTime: Long, staleAfterTicks: Long) {
        val stalePokemonIds = WorkerSessionManager.pruneStaleRuntime(currentTime, staleAfterTicks)
        stalePokemonIds.forEach(::cleanupRuntimeOnly)
        ClaimManager.pruneStale(currentTime, staleAfterTicks)
    }

    fun resetRuntimeState() {
        WorkerSessionManager.clearStates()
        ClaimManager.clear()
        WorkerNavigationManager.clearFailureCache()
        TagBehaviorRegistry.all().forEach { it.onRuntimeReset() }
    }

    private fun recoverCarriedInventory(pokemonId: java.util.UUID, world: World, pos: BlockPos, inventory: PokemonInventory) {
        val recoveryTargets = buildList {
            val dimensionId = world.registryKey.value.toString()
            TagAssignmentManager.getView(pokemonId)?.controllerBinding
                ?.takeIf { it.dimensionId == dimensionId }
                ?.pos
                ?.toImmutable()
                ?.let { add(it) }

            if (ContainerFinder.getInventoryAt(world, pos) != null) {
                val fallbackPos = pos.toImmutable()
                if (fallbackPos !in this) {
                    add(fallbackPos)
                }
            }
        }

        recoveryTargets.forEach { targetPos ->
            val targetInventory = ContainerFinder.getInventoryAt(world, targetPos) ?: return@forEach
            depositInventory(targetInventory, inventory)
            targetInventory.markDirty()
            if (inventory.isEmpty) {
                return
            }
        }

        dropRemainingInventory(world, pos, inventory)
    }

    private fun depositInventory(target: Inventory, source: PokemonInventory) {
        for (slot in 0 until source.size()) {
            val stack = source.getStack(slot)
            if (stack.isEmpty) continue

            val remaining = ContainerFinder.insertStack(target, stack.copy())
            source.setStack(slot, remaining)
        }
    }

    private fun dropRemainingInventory(world: World, pos: BlockPos, inventory: PokemonInventory) {
        for (slot in 0 until inventory.size()) {
            val stack = inventory.getStack(slot)
            if (stack.isEmpty) continue

            net.minecraft.entity.ItemEntity(
                world,
                pos.x + 0.5, pos.y + 1.0, pos.z + 0.5,
                stack
            ).also { world.spawnEntity(it) }
            inventory.setStack(slot, ItemStack.EMPTY)
        }
    }

    private fun passesRedstoneGate(world: World, origin: BlockPos, tag: TagInstance, state: WorkerState): Boolean {
        if (!tag.augments.isRedstoneControlled()) return true

        val powered = world.isReceivingRedstonePower(origin)
        val shouldRun = when (tag.settings.redstoneMode) {
            RedstoneControlMode.ALWAYS -> true
            RedstoneControlMode.HIGH -> powered
            RedstoneControlMode.LOW -> !powered
            RedstoneControlMode.NEVER -> false
            RedstoneControlMode.PULSE -> powered && !state.lastRedstonePower
        }
        state.lastRedstonePower = powered
        return shouldRun
    }

    private fun tickIdle(
        world: World, entity: PokemonEntity, pokemon: Pokemon,
        tag: TagInstance, behavior: TagBehavior, state: WorkerState, origin: BlockPos,
        navigationBudget: NavigationBudget
    ) {
        if (world.time < state.cooldownUntil) {
            state.setStatus(WorkerStatusReason.COOLDOWN, "status.cobblepalsworld.detail.cooldown_recovering")
            // While on cooldown, drift back toward the Command Post if far away
            if (!NavigationHelper.isAtPosition(entity, origin, 5.0)) {
                applyNavigationStatus(
                    state = state,
                    attempt = NavigationHelper.navigateTo(entity, origin, state, navigationBudget, MovementPurpose.RETURN_HOME),
                    activeReason = WorkerStatusReason.COOLDOWN,
                    activeDetail = "status.cobblepalsworld.detail.returning_cooldown",
                    failedDetail = "status.cobblepalsworld.detail.path_back_failed"
                )
            }
            return
        }

        if (world.time < state.nextTargetSearchTick) {
            state.setStatus(
                if (state.ecoMode) WorkerStatusReason.ECO_IDLE else WorkerStatusReason.SEARCH_DELAY,
                if (state.ecoMode) {
                    "No work found recently; scanning less often in eco mode"
                } else {
                    "Waiting before the next target scan"
                }
            )
            if (!NavigationHelper.isAtPosition(entity, origin, 5.0)) {
                applyNavigationStatus(
                    state = state,
                    attempt = NavigationHelper.navigateTo(entity, origin, state, navigationBudget, MovementPurpose.RETURN_HOME),
                    activeReason = if (state.ecoMode) WorkerStatusReason.ECO_IDLE else WorkerStatusReason.SEARCH_DELAY,
                    activeDetail = "status.cobblepalsworld.detail.returning_scan",
                    failedDetail = "status.cobblepalsworld.detail.path_back_failed"
                )
            }
            return
        }

        // Deposit-first: if Pokémon still has items, deposit before finding new work
        // Exception: dual-phase behaviors handle their own inventory (items are in-transit)
        val existingInv = InventoryManager.get(pokemon.uuid)
        if (existingInv != null && !existingInv.isEmpty && !behavior.handlesOwnInventory) {
            state.phase = WorkerPhase.DEPOSITING
            state.setStatus(WorkerStatusReason.DEPOSITING, "status.cobblepalsworld.detail.depositing_carried")
            return
        }

        val target = behavior.findTarget(world, origin, entity, tag, state)
        if (target == null) {
            state.nextTargetSearchTick = world.time + behavior.idleRetryTicks(tag, state)
            state.setStatus(
                if (state.ecoMode) WorkerStatusReason.ECO_IDLE else WorkerStatusReason.NO_TARGET,
                if (state.ecoMode) {
                    "No valid targets found; staying in quiet scan mode"
                } else {
                    "No valid targets found in the current work range"
                }
            )
            // No work found — stay idle (eco mode will kick in via tick counter)
            if (!NavigationHelper.isAtPosition(entity, origin, 5.0)) {
                applyNavigationStatus(
                    state = state,
                    attempt = NavigationHelper.navigateTo(entity, origin, state, navigationBudget, MovementPurpose.RETURN_HOME),
                    activeReason = if (state.ecoMode) WorkerStatusReason.ECO_IDLE else WorkerStatusReason.NO_TARGET,
                    activeDetail = "status.cobblepalsworld.detail.returning_idle",
                    failedDetail = "status.cobblepalsworld.detail.path_back_failed"
                )
            }
            return
        }
        if (!isWithinWorkRange(origin, target, effectiveRange(tag, state))) {
            state.nextTargetSearchTick = world.time + minOf(behavior.idleRetryTicks(tag, state), 40L)
            state.setStatus(WorkerStatusReason.PATHING_STALLED, "status.cobblepalsworld.detail.pathing_target_out_of_range")
            return
        }
        // Found work — check claim first, then exit eco mode
        if (ClaimManager.isClaimedByOther(target, pokemon.uuid, world)) {
            state.nextTargetSearchTick = world.time + minOf(behavior.idleRetryTicks(tag, state), 20L)
            state.setStatus(WorkerStatusReason.TARGET_BUSY, "status.cobblepalsworld.detail.target_busy")
            return
        }
        state.markDidWork()
        state.nextTargetSearchTick = 0L

        ClaimManager.claim(target, pokemon.uuid, world)
        state.targetPos = target
        val attempt = NavigationHelper.navigateTo(entity, target, state, navigationBudget, MovementPurpose.WORK_TARGET)
        applyNavigationStatus(
            state = state,
            attempt = attempt,
            activeReason = WorkerStatusReason.NAVIGATING,
            activeDetail = "status.cobblepalsworld.detail.moving_to_target",
            failedDetail = "status.cobblepalsworld.detail.path_to_target_failed"
        )
        if (attempt == NavigationAttempt.UNREACHABLE) {
            ClaimManager.release(target, world)
            resetToIdle(state)
            state.nextTargetSearchTick = world.time + behavior.idleRetryTicks(tag, state)
            state.setStatus(WorkerStatusReason.PATHING_STALLED, "status.cobblepalsworld.detail.pathing_target_unreachable")
            return
        }
        state.phase = WorkerPhase.NAVIGATING
    }

    private fun tickNavigating(
        world: World, entity: PokemonEntity,
        tag: TagInstance, behavior: TagBehavior, state: WorkerState,
        origin: BlockPos,
        navigationBudget: NavigationBudget
    ) {
        val target = state.targetPos ?: run { resetToIdle(state); return }

        if (!isWithinWorkRange(origin, target, effectiveRange(tag, state))) {
            ClaimManager.release(target, world)
            resetToIdle(state)
            state.nextTargetSearchTick = world.time + minOf(behavior.idleRetryTicks(tag, state), 40L)
            state.setStatus(WorkerStatusReason.PATHING_STALLED, "status.cobblepalsworld.detail.pathing_target_drifted")
            return
        }

        if (!behavior.isTargetValid(world, target, tag)) {
            ClaimManager.release(target, world)
            resetToIdle(state)
            state.nextTargetSearchTick = world.time + minOf(behavior.idleRetryTicks(tag, state), 20L)
            state.setStatus(WorkerStatusReason.NO_TARGET, "status.cobblepalsworld.detail.no_target_invalid")
            return
        }

        if (NavigationHelper.isAtPosition(entity, target, behavior.arrivalTolerance(tag, state))) {
            NavigationHelper.stopNavigation(entity, state)
            state.arrivalTick = world.time
            state.phase = WorkerPhase.ARRIVING
            state.setStatus(WorkerStatusReason.ARRIVING, "status.cobblepalsworld.detail.arriving_settle")
            WorkVisualHandler.onArrival(world, entity, target, tag.type)
        } else {
            val attempt = NavigationHelper.navigateTo(entity, target, state, navigationBudget, MovementPurpose.WORK_TARGET)
            applyNavigationStatus(
                state = state,
                attempt = attempt,
                activeReason = WorkerStatusReason.NAVIGATING,
                activeDetail = "status.cobblepalsworld.detail.closing_in",
                failedDetail = "status.cobblepalsworld.detail.path_retrying"
            )
            if (attempt == NavigationAttempt.UNREACHABLE) {
                ClaimManager.release(target, world)
                resetToIdle(state)
                state.nextTargetSearchTick = world.time + minOf(behavior.idleRetryTicks(tag, state), 40L)
                state.setStatus(WorkerStatusReason.PATHING_STALLED, "status.cobblepalsworld.detail.pathing_unreachable_after_recovery")
            }
        }
    }

    private fun tickArriving(world: World, entity: PokemonEntity, tag: TagInstance, behavior: TagBehavior, state: WorkerState) {
        val target = state.targetPos ?: run { resetToIdle(state); return }

        // Re-validate target during arrival delay (block may have been broken)
        if (!behavior.isTargetValid(world, target, tag)) {
            ClaimManager.release(target, world)
            resetToIdle(state)
            state.nextTargetSearchTick = world.time + minOf(behavior.idleRetryTicks(tag, state), 20L)
            state.setStatus(WorkerStatusReason.NO_TARGET, "status.cobblepalsworld.detail.no_target_disappeared")
            return
        }

        val arrived = state.arrivalTick ?: run { state.arrivalTick = world.time; return }
        if (world.time - arrived < behavior.arrivalDelayTicks(tag, state)) {
            state.setStatus(WorkerStatusReason.ARRIVING, "status.cobblepalsworld.detail.arriving_prepare")
            return
        }

        state.arrivalTick = null
        state.phase = WorkerPhase.WORKING
        state.setStatus(WorkerStatusReason.WORKING, "status.cobblepalsworld.detail.working")
    }

    private fun tickWorking(
        world: World, entity: PokemonEntity, pokemon: Pokemon,
        tag: TagInstance, behavior: TagBehavior, state: WorkerState, origin: BlockPos,
        navigationBudget: NavigationBudget
    ) {
        val target = state.targetPos ?: run { resetToIdle(state); return }
        state.setStatus(WorkerStatusReason.WORKING, "status.cobblepalsworld.detail.working")
        val result = behavior.doWork(world, entity, target, tag, state)

        when (result) {
            is WorkResult.Done -> {
                ClaimManager.release(target, world)
                WorkVisualHandler.onWorkComplete(world, entity, target, tag.type)
                state.markDidWork()
                awardMastery(world, entity, pokemon, tag, state)
                if (result.items.isNotEmpty()) {
                    // Remember where we got items from so we don't deposit back there
                    state.workSourcePos = target
                    storeItems(world, entity, pokemon, result.items)
                    (world as? ServerWorld)?.let(CobblePalsSaveData::markDirty)
                    state.phase = WorkerPhase.DEPOSITING
                    state.setStatus(WorkerStatusReason.DEPOSITING, "status.cobblepalsworld.detail.depositing_gathered")
                } else {
                    resetToIdle(state)
                    state.cooldownUntil = world.time + behavior.cooldownTicks(tag, state)
                    state.setStatus(WorkerStatusReason.COOLDOWN, "status.cobblepalsworld.detail.cooldown_work_complete")
                }
            }
            is WorkResult.MoveTo -> {
                if (!isWithinWorkRange(origin, result.target, effectiveRange(tag, state))) {
                    ClaimManager.release(target, world)
                    resetToIdle(state)
                    state.nextTargetSearchTick = world.time + minOf(behavior.idleRetryTicks(tag, state), 40L)
                    state.setStatus(WorkerStatusReason.PATHING_STALLED, "status.cobblepalsworld.detail.pathing_followup_out_of_range")
                    return
                }
                ClaimManager.release(target, world)
                state.targetPos = result.target
                ClaimManager.claim(result.target, pokemon.uuid, world)
                val attempt = NavigationHelper.navigateTo(entity, result.target, state, navigationBudget, MovementPurpose.WORK_TARGET)
                applyNavigationStatus(
                    state = state,
                    attempt = attempt,
                    activeReason = WorkerStatusReason.NAVIGATING,
                    activeDetail = "status.cobblepalsworld.detail.moving_followup",
                    failedDetail = "status.cobblepalsworld.detail.path_followup_failed"
                )
                if (attempt == NavigationAttempt.UNREACHABLE) {
                    ClaimManager.release(result.target, world)
                    resetToIdle(state)
                    state.nextTargetSearchTick = world.time + minOf(behavior.idleRetryTicks(tag, state), 40L)
                    state.setStatus(WorkerStatusReason.PATHING_STALLED, "status.cobblepalsworld.detail.pathing_followup_unreachable")
                    return
                }
                state.phase = WorkerPhase.NAVIGATING
            }
            is WorkResult.Repeat -> {
                state.arrivalTick = null
                state.cooldownUntil = world.time + behavior.cooldownTicks(tag, state)
                state.phase = WorkerPhase.IDLE
                state.setStatus(WorkerStatusReason.COOLDOWN, "status.cobblepalsworld.detail.cooldown_loop_complete")
            }
            is WorkResult.Continue -> { /* stay in WORKING */ }
        }
    }

    /**
     * Credits one completed job toward this worker's mastery for the active role.
     * On a tier-up, recompiles the cached cooldown/range so the new perks apply
     * immediately, and plays an in-world celebration.
     */
    private fun awardMastery(world: World, entity: PokemonEntity, pokemon: Pokemon, tag: TagInstance, state: WorkerState) {
        val newTier = WorkMasteryManager.recordJob(pokemon.uuid, tag.type)
        (world as? ServerWorld)?.let(CobblePalsSaveData::markDirty)
        if (newTier != null) {
            state.invalidateCache()
            WorkVisualHandler.onMasteryTierUp(world, entity)
        }
    }

    private fun tickDepositing(
        world: World, entity: PokemonEntity, pokemon: Pokemon,
        tag: TagInstance, behavior: TagBehavior, state: WorkerState, origin: BlockPos,
        navigationBudget: NavigationBudget
    ) {
        val inventory = InventoryManager.get(pokemon.uuid)
        if (inventory == null || inventory.isEmpty) {
            resetToIdle(state)
            state.cooldownUntil = world.time + behavior.cooldownTicks(tag, state)
            state.setStatus(WorkerStatusReason.COOLDOWN, "status.cobblepalsworld.detail.cooldown_run_finished")
            return
        }

        if (state.depositPos == null) {
            val searchRange = effectiveRange(tag, state)
            val controllerPos = ContainerFinder.controllerBufferPos(world, tag)
                ?.takeIf { isWithinWorkRange(origin, it, searchRange) }

            // Build exclusion set: never deposit back to the source or Command Post origin
            val excludePositions = mutableSetOf(origin)
            state.workSourcePos
                ?.takeUnless { it == controllerPos }
                ?.let { excludePositions.add(it) }

            val controllerInventory = controllerPos?.let { ContainerFinder.getInventoryAt(world, it) }
            val controllerHasSpace = controllerPos != null
                && controllerPos !in excludePositions
                && controllerInventory != null
                && ContainerFinder.hasSpace(controllerInventory)

            if (controllerHasSpace) {
                state.depositPos = controllerPos
                state.cachedContainerPos = controllerPos
                state.containerCacheTime = world.time
            } else {
                // Container cache: reuse recently-found container position if still valid
                val cached = state.cachedContainerPos
                if (cached != null
                    && cached !in excludePositions
                    && world.time - state.containerCacheTime < config.containerCacheTicks
                    && ContainerFinder.isContainer(world, cached)
                ) {
                    state.depositPos = cached
                } else {
                    val foundContainer = ContainerFinder.findControllerFirstMatching(world, origin, tag, searchRange, excludePositions) { inventory, _ ->
                        ContainerFinder.hasSpace(inventory)
                    }

                    if (foundContainer != null) {
                        state.depositPos = foundContainer
                        // Cache for future deposit trips
                        state.cachedContainerPos = foundContainer
                        state.containerCacheTime = world.time
                    } else if (tag.augments.canPushToEntities()) {
                        val entityInv = ContainerFinder.findClosestEntityInventory(world, origin, searchRange)
                        if (entityInv != null) {
                            ContainerFinder.depositIntoEntity(entityInv, inventory)
                            WorkVisualHandler.onDeposit(world, entity, origin)
                            resetToIdle(state)
                            state.cooldownUntil = world.time + behavior.cooldownTicks(tag, state)
                            state.setStatus(WorkerStatusReason.COOLDOWN, "status.cobblepalsworld.detail.cooldown_mobile_delivery")
                            return
                        }
                        dropAllItems(world, entity, inventory)
                        resetToIdle(state)
                        state.setStatus(WorkerStatusReason.NO_DEPOSIT, "status.cobblepalsworld.detail.no_deposit_dropped")
                        return
                    } else {
                        dropAllItems(world, entity, inventory)
                        resetToIdle(state)
                        state.setStatus(WorkerStatusReason.NO_DEPOSIT, "status.cobblepalsworld.detail.no_deposit_full")
                        return
                    }
                }
            }
        }

        val depositPos = state.depositPos ?: run {
            resetToIdle(state)
            state.setStatus(WorkerStatusReason.NO_DEPOSIT, "status.cobblepalsworld.detail.no_deposit_lost")
            return
        }
        if (!isWithinWorkRange(origin, depositPos, effectiveRange(tag, state))) {
            state.depositPos = null
            if (state.cachedContainerPos == depositPos) {
                state.cachedContainerPos = null
            }
            state.setStatus(WorkerStatusReason.PATHING_STALLED, "status.cobblepalsworld.detail.pathing_deposit_out_of_range")
            return
        }
        if (NavigationHelper.isAtPosition(entity, depositPos)) {
            NavigationHelper.stopNavigation(entity, state)
            if (tag.augments.isRegulator()) {
                ContainerFinder.depositRegulated(world, depositPos, inventory, tag)
            } else {
                ContainerFinder.depositFromInventory(world, depositPos, inventory)
            }
            WorkVisualHandler.onDeposit(world, entity, depositPos)
            state.depositPos = null
            resetToIdle(state)
            state.cooldownUntil = world.time + behavior.cooldownTicks(tag, state)
            state.setStatus(WorkerStatusReason.COOLDOWN, "status.cobblepalsworld.detail.cooldown_delivered")
        } else {
            val attempt = NavigationHelper.navigateTo(entity, depositPos, state, navigationBudget, MovementPurpose.DEPOSIT)
            applyNavigationStatus(
                state = state,
                attempt = attempt,
                activeReason = WorkerStatusReason.DEPOSITING,
                activeDetail = "status.cobblepalsworld.detail.returning_deposit",
                failedDetail = "status.cobblepalsworld.detail.path_deposit_failed"
            )
            if (attempt == NavigationAttempt.UNREACHABLE) {
                state.depositPos = null
                if (state.cachedContainerPos == depositPos) {
                    state.cachedContainerPos = null
                }
                state.setStatus(WorkerStatusReason.PATHING_STALLED, "status.cobblepalsworld.detail.pathing_deposit_unreachable")
            }
        }
    }

    private fun storeItems(world: World, entity: PokemonEntity, pokemon: Pokemon, items: List<ItemStack>) {
        val inventory = InventoryManager.getOrCreate(pokemon)
        for (item in items) {
            val remaining = inventory.insertStack(item)
            if (!remaining.isEmpty) {
                net.minecraft.entity.ItemEntity(world, entity.x, entity.y, entity.z, remaining).also {
                    world.spawnEntity(it)
                }
            }
        }
    }

    private fun dropAllItems(world: World, entity: PokemonEntity, inventory: net.minecraft.inventory.SimpleInventory) {
        for (slot in 0 until inventory.size()) {
            val stack = inventory.getStack(slot)
            if (!stack.isEmpty) {
                net.minecraft.entity.ItemEntity(world, entity.x, entity.y, entity.z, stack).also {
                    world.spawnEntity(it)
                }
                inventory.setStack(slot, ItemStack.EMPTY)
            }
        }
    }

    private fun releaseClaimAndReset(world: World, state: WorkerState) {
        state.targetPos?.let { ClaimManager.release(it, world) }
        state.reset()
    }

    private fun applyNavigationStatus(
        state: WorkerState,
        attempt: NavigationAttempt,
        activeReason: WorkerStatusReason,
        activeDetail: String,
        failedDetail: String
    ) {
        when (attempt) {
            NavigationAttempt.STARTED, NavigationAttempt.THROTTLED -> state.setStatus(activeReason, activeDetail)
            NavigationAttempt.BUDGETED -> state.setStatus(WorkerStatusReason.PATH_BUDGET, "status.cobblepalsworld.detail.path_budget")
            NavigationAttempt.RECOVERING -> state.setStatus(WorkerStatusReason.MOVEMENT_RECOVERY, "status.cobblepalsworld.detail.recovery_unsticking")
            NavigationAttempt.RESCUED -> state.setStatus(WorkerStatusReason.MOVEMENT_RECOVERY, "status.cobblepalsworld.detail.recovery_reseated")
            NavigationAttempt.UNREACHABLE -> state.setStatus(WorkerStatusReason.PATHING_STALLED, failedDetail)
            NavigationAttempt.FAILED -> state.setStatus(WorkerStatusReason.PATHING_STALLED, failedDetail)
        }
    }

    private fun resetToIdle(state: WorkerState) {
        state.reset()
    }

    private fun enforceWorksiteLeash(
        world: World,
        entity: PokemonEntity,
        tag: TagInstance,
        behavior: TagBehavior,
        state: WorkerState,
        origin: BlockPos,
        navigationBudget: NavigationBudget
    ): Boolean {
        val range = effectiveRange(tag, state)
        val activeTarget = when (state.phase) {
            WorkerPhase.DEPOSITING -> state.depositPos ?: state.targetPos
            else -> state.targetPos ?: state.depositPos
        }

        if (activeTarget != null && !isWithinWorkRange(origin, activeTarget, range)) {
            state.targetPos?.let { ClaimManager.release(it, world) }
            state.cachedContainerPos = null
            resetToIdle(state)
            state.nextTargetSearchTick = world.time + minOf(behavior.idleRetryTicks(tag, state), 40L)
            state.setStatus(WorkerStatusReason.PATHING_STALLED, "status.cobblepalsworld.detail.pathing_cleared_out_of_range")
            return guideBackToWorksite(entity, state, origin, navigationBudget, range)
        }

        if (!isEntityWithinLeash(entity, origin, range)) {
            state.targetPos?.let { ClaimManager.release(it, world) }
            resetToIdle(state)
            recallToWorksite(entity, state, origin)
            state.nextTargetSearchTick = world.time + minOf(behavior.idleRetryTicks(tag, state), 40L)
            state.setStatus(WorkerStatusReason.PATHING_STALLED, "status.cobblepalsworld.detail.pathing_returned_to_post")
            return true
        }

        return false
    }

    private fun guideBackToWorksite(
        entity: PokemonEntity,
        state: WorkerState,
        origin: BlockPos,
        navigationBudget: NavigationBudget,
        range: Int
    ): Boolean {
        if (!isEntityWithinLeash(entity, origin, range)) {
            recallToWorksite(entity, state, origin)
            return true
        }

        val attempt = NavigationHelper.navigateTo(entity, origin, state, navigationBudget, MovementPurpose.RETURN_HOME)
        if (attempt == NavigationAttempt.UNREACHABLE) {
            recallToWorksite(entity, state, origin)
        } else {
            state.setStatus(WorkerStatusReason.PATHING_STALLED, "status.cobblepalsworld.detail.pathing_rejecting_distant")
        }
        return true
    }

    private fun recallToWorksite(entity: PokemonEntity, state: WorkerState, origin: BlockPos) {
        val home = SafePositionResolver.standNear(entity.world, origin, entity.blockPos) ?: origin.up()
        NavigationHelper.stopNavigation(entity, state)
        entity.teleport(home.x + 0.5, home.y.toDouble(), home.z + 0.5, false)
        entity.setVelocity(0.0, 0.0, 0.0)
    }

    private fun isWithinWorkRange(origin: BlockPos, target: BlockPos, range: Int): Boolean {
        val horizontalRange = range + WORKSPACE_PADDING_BLOCKS
        val verticalRange = maxOf(horizontalRange, 16)
        return abs(target.x - origin.x) <= horizontalRange &&
            abs(target.z - origin.z) <= horizontalRange &&
            abs(target.y - origin.y) <= verticalRange
    }

    private fun isEntityWithinLeash(entity: PokemonEntity, origin: BlockPos, range: Int): Boolean {
        val leash = maxOf(range + ENTITY_LEASH_PADDING_BLOCKS, MIN_ENTITY_LEASH_BLOCKS).toDouble()
        val dx = entity.x - (origin.x + 0.5)
        val dz = entity.z - (origin.z + 0.5)
        val dy = entity.y - origin.y
        return dx * dx + dz * dz <= leash * leash && abs(dy) <= maxOf(leash, 24.0)
    }
}

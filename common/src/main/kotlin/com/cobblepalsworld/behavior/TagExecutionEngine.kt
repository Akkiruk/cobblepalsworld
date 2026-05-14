package com.cobblepalsworld.behavior

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.behavior.state.StateManager
import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.behavior.behaviors.BreakerBehavior
import com.cobblepalsworld.behavior.behaviors.DistributorBehavior
import com.cobblepalsworld.behavior.behaviors.GuardianBehavior
import com.cobblepalsworld.behavior.behaviors.SenderBehavior
import com.cobblepalsworld.behavior.behaviors.ShepherdBehavior
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.inventory.PokemonInventory
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.navigation.NavigationHelper
import com.cobblepalsworld.pasture.PastureWorkerManager
import com.cobblepalsworld.pasture.TagAssignmentManager
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.RedstoneControlMode
import com.cobblepalsworld.visual.WorkVisualHandler
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object TagExecutionEngine {
    private val config get() = ConfigManager.config.general

    // --- Compiled cache helpers (avoid recomputing every tick) ---

    /** Get or compute the effective cooldown for this tag. Cached on WorkerState. */
    fun defaultCooldownTicks(tag: TagInstance, state: WorkerState): Long {
        if (state.cachedCooldown < 0) {
            state.cachedCooldown = (config.workCooldownTicks * tag.augments.speedMultiplier()).toLong().coerceAtLeast(5)
        }
        return state.cachedCooldown
    }

    fun defaultArrivalDelayTicks(): Long = config.arrivalDelayTicks.toLong()

    fun defaultIdleRetryTicks(): Long = config.idleSearchRetryTicks.toLong()

    /** Get or compute the effective search range. Cached on WorkerState. */
    fun effectiveRange(tag: TagInstance, state: WorkerState): Int {
        if (state.cachedRange < 0) {
            state.cachedRange = ConfigManager.config.getTagConfig(tag.type).range + tag.augments.extraRange()
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

    fun tick(world: World, entity: PokemonEntity, pokemon: Pokemon, tag: TagInstance, origin: BlockPos) {
        if (!isTagEnabled(tag.type)) return

        val behavior = TagBehaviorRegistry.get(tag.type) ?: return
        val state = StateManager.getOrCreate(pokemon.uuid)
        state.lastSeenTick = world.time

        if (!passesRedstoneGate(world, origin, tag, state)) {
            val inventory = InventoryManager.get(pokemon.uuid)
            if (inventory != null && !inventory.isEmpty && !behavior.handlesOwnInventory) {
                if (state.phase != WorkerPhase.DEPOSITING) {
                    releaseClaimAndReset(world, state)
                    state.phase = WorkerPhase.DEPOSITING
                }
            } else {
                if (state.phase != WorkerPhase.IDLE) {
                    releaseClaimAndReset(world, state)
                }
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
                if (state.ecoSkipCounter < config.ecoTickMultiplier) return
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
            WorkerPhase.IDLE -> tickIdle(world, entity, pokemon, tag, behavior, state, origin)
            WorkerPhase.NAVIGATING -> tickNavigating(world, entity, tag, behavior, state)
            WorkerPhase.ARRIVING -> tickArriving(world, entity, tag, behavior, state)
            WorkerPhase.WORKING -> tickWorking(world, entity, pokemon, tag, behavior, state, origin)
            WorkerPhase.DEPOSITING -> tickDepositing(world, entity, pokemon, tag, behavior, state, origin)
        }
    }

    fun cleanup(pokemonId: java.util.UUID, world: World? = null, pos: BlockPos? = null) {
        cleanupRuntimeOnly(pokemonId)

        val inventory = InventoryManager.remove(pokemonId)
        if (inventory != null && world != null && pos != null) {
            recoverCarriedInventory(pokemonId, world, pos, inventory)
            PastureWorkerManager.markDirtyNow(world)
        }
    }

    fun cleanupRuntimeOnly(pokemonId: java.util.UUID) {
        ClaimManager.releaseAll(pokemonId)
        StateManager.remove(pokemonId)
        BreakerBehavior.clearPastureOrigin(pokemonId)
        GuardianBehavior.cleanup(pokemonId)
        SenderBehavior.cleanup(pokemonId)
        DistributorBehavior.cleanup(pokemonId)
        ShepherdBehavior.cleanup(pokemonId)
    }

    fun pruneStaleRuntime(currentTime: Long, staleAfterTicks: Long) {
        val stalePokemonIds = StateManager.pruneStale(currentTime, staleAfterTicks)
        stalePokemonIds.forEach(::cleanupRuntimeOnly)
        ClaimManager.pruneStale(currentTime, staleAfterTicks)
    }

    fun resetRuntimeState() {
        StateManager.clear()
        ClaimManager.clear()
        BreakerBehavior.clearAllPastureOrigins()
        GuardianBehavior.clearAllRuntimeState()
        SenderBehavior.clearAllRuntimeState()
        DistributorBehavior.clearAllRuntimeState()
        ShepherdBehavior.clearAllRuntimeState()
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
        tag: TagInstance, behavior: TagBehavior, state: WorkerState, origin: BlockPos
    ) {
        if (world.time < state.cooldownUntil) {
            // While on cooldown, drift back toward pasture if far away
            if (!NavigationHelper.isAtPosition(entity, origin, 5.0)) {
                NavigationHelper.navigateTo(entity, origin, state)
            }
            return
        }

        if (world.time < state.nextTargetSearchTick) {
            if (!NavigationHelper.isAtPosition(entity, origin, 5.0)) {
                NavigationHelper.navigateTo(entity, origin, state)
            }
            return
        }

        // Deposit-first: if Pokémon still has items, deposit before finding new work
        // Exception: dual-phase behaviors handle their own inventory (items are in-transit)
        val existingInv = InventoryManager.get(pokemon.uuid)
        if (existingInv != null && !existingInv.isEmpty && !behavior.handlesOwnInventory) {
            state.phase = WorkerPhase.DEPOSITING
            return
        }

        val target = behavior.findTarget(world, origin, entity, tag, state)
        if (target == null) {
            state.nextTargetSearchTick = world.time + behavior.idleRetryTicks(tag, state)
            // No work found — stay idle (eco mode will kick in via tick counter)
            if (!NavigationHelper.isAtPosition(entity, origin, 5.0)) {
                NavigationHelper.navigateTo(entity, origin, state)
            }
            return
        }
        // Found work — check claim first, then exit eco mode
        if (ClaimManager.isClaimedByOther(target, pokemon.uuid, world)) return
        state.markDidWork()
        state.nextTargetSearchTick = 0L

        ClaimManager.claim(target, pokemon.uuid, world)
        state.targetPos = target
        NavigationHelper.navigateTo(entity, target, state)
        state.phase = WorkerPhase.NAVIGATING
    }

    private fun tickNavigating(
        world: World, entity: PokemonEntity,
        tag: TagInstance, behavior: TagBehavior, state: WorkerState
    ) {
        val target = state.targetPos ?: run { resetToIdle(state); return }

        if (!behavior.isTargetValid(world, target, tag)) {
            ClaimManager.release(target, world)
            resetToIdle(state)
            return
        }

        if (NavigationHelper.isAtPosition(entity, target, behavior.arrivalTolerance(tag, state))) {
            NavigationHelper.stopNavigation(entity)
            state.arrivalTick = world.time
            state.phase = WorkerPhase.ARRIVING
            WorkVisualHandler.onArrival(world, entity, target, tag.type)
        } else {
            NavigationHelper.navigateTo(entity, target, state)
        }
    }

    private fun tickArriving(world: World, entity: PokemonEntity, tag: TagInstance, behavior: TagBehavior, state: WorkerState) {
        val target = state.targetPos ?: run { resetToIdle(state); return }

        // Re-validate target during arrival delay (block may have been broken)
        if (!behavior.isTargetValid(world, target, tag)) {
            ClaimManager.release(target, world)
            resetToIdle(state)
            return
        }

        val arrived = state.arrivalTick ?: run { state.arrivalTick = world.time; return }
        if (world.time - arrived < behavior.arrivalDelayTicks(tag, state)) return

        state.arrivalTick = null
        state.phase = WorkerPhase.WORKING
    }

    private fun tickWorking(
        world: World, entity: PokemonEntity, pokemon: Pokemon,
        tag: TagInstance, behavior: TagBehavior, state: WorkerState, origin: BlockPos
    ) {
        val target = state.targetPos ?: run { resetToIdle(state); return }
        val result = behavior.doWork(world, entity, target, tag, state)

        when (result) {
            is WorkResult.Done -> {
                ClaimManager.release(target, world)
                WorkVisualHandler.onWorkComplete(world, entity, target, tag.type)
                state.markDidWork()
                if (result.items.isNotEmpty()) {
                    // Remember where we got items from so we don't deposit back there
                    state.workSourcePos = target
                    storeItems(world, entity, pokemon, result.items)
                    PastureWorkerManager.markDirtyNow(world)
                    state.phase = WorkerPhase.DEPOSITING
                } else {
                    resetToIdle(state)
                    state.cooldownUntil = world.time + behavior.cooldownTicks(tag, state)
                }
            }
            is WorkResult.MoveTo -> {
                ClaimManager.release(target, world)
                state.targetPos = result.target
                ClaimManager.claim(result.target, pokemon.uuid, world)
                NavigationHelper.navigateTo(entity, result.target, state)
                state.phase = WorkerPhase.NAVIGATING
            }
            is WorkResult.Repeat -> {
                state.arrivalTick = null
                state.cooldownUntil = world.time + behavior.cooldownTicks(tag, state)
                state.phase = WorkerPhase.IDLE
            }
            is WorkResult.Continue -> { /* stay in WORKING */ }
        }
    }

    private fun tickDepositing(
        world: World, entity: PokemonEntity, pokemon: Pokemon,
        tag: TagInstance, behavior: TagBehavior, state: WorkerState, origin: BlockPos
    ) {
        val inventory = InventoryManager.get(pokemon.uuid)
        if (inventory == null || inventory.isEmpty) {
            resetToIdle(state)
            state.cooldownUntil = world.time + behavior.cooldownTicks(tag, state)
            return
        }

        if (state.depositPos == null) {
            val searchRange = effectiveRange(tag, state)
            val controllerPos = ContainerFinder.controllerBufferPos(world, tag)

            // Build exclusion set: never deposit back to source or the pasture block itself
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
                            return
                        }
                        dropAllItems(world, entity, inventory)
                        resetToIdle(state)
                        return
                    } else {
                        dropAllItems(world, entity, inventory)
                        resetToIdle(state)
                        return
                    }
                }
            }
        }

        val depositPos = state.depositPos!!
        if (NavigationHelper.isAtPosition(entity, depositPos)) {
            NavigationHelper.stopNavigation(entity)
            if (tag.augments.isRegulator()) {
                ContainerFinder.depositRegulated(world, depositPos, inventory, tag)
            } else {
                ContainerFinder.depositFromInventory(world, depositPos, inventory)
            }
            WorkVisualHandler.onDeposit(world, entity, depositPos)
            state.depositPos = null
            resetToIdle(state)
            state.cooldownUntil = world.time + behavior.cooldownTicks(tag, state)
        } else {
            NavigationHelper.navigateTo(entity, depositPos, state)
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

    private fun resetToIdle(state: WorkerState) {
        state.reset()
    }
}

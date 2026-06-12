package com.cobblepalsworld.behavior

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.session.WorkerSessionManager
import com.cobblepalsworld.behavior.phase.PhaseContext
import com.cobblepalsworld.behavior.phase.PhaseRegistry
import com.cobblepalsworld.behavior.phase.PhaseSupport
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
                PhaseSupport.releaseClaimAndReset(world, state)
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
                    PhaseSupport.releaseClaimAndReset(world, state)
                    state.phase = WorkerPhase.DEPOSITING
                }
                state.setStatus(WorkerStatusReason.DEPOSITING, "status.cobblepalsworld.detail.depositing_redstone")
            } else {
                if (state.phase != WorkerPhase.IDLE) {
                    PhaseSupport.releaseClaimAndReset(world, state)
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

        PhaseRegistry.handlerFor(state.phase).tick(
            PhaseContext(
                world = world,
                entity = entity,
                pokemon = pokemon,
                tag = tag,
                behavior = behavior,
                state = state,
                origin = origin,
                navigationBudget = navigationBudget
            )
        )

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

        if (activeTarget != null && !PhaseSupport.isWithinWorkRange(origin, activeTarget, range)) {
            state.targetPos?.let { ClaimManager.release(it, world) }
            state.cachedContainerPos = null
            PhaseSupport.resetToIdle(state)
            state.nextTargetSearchTick = world.time + minOf(behavior.idleRetryTicks(tag, state), 40L)
            state.setStatus(WorkerStatusReason.PATHING_STALLED, "status.cobblepalsworld.detail.pathing_cleared_out_of_range")
            return guideBackToWorksite(entity, state, origin, navigationBudget, range)
        }

        if (!isEntityWithinLeash(entity, origin, range)) {
            state.targetPos?.let { ClaimManager.release(it, world) }
            PhaseSupport.resetToIdle(state)
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


    private fun isEntityWithinLeash(entity: PokemonEntity, origin: BlockPos, range: Int): Boolean {
        val leash = maxOf(range + ENTITY_LEASH_PADDING_BLOCKS, MIN_ENTITY_LEASH_BLOCKS).toDouble()
        val dx = entity.x - (origin.x + 0.5)
        val dz = entity.z - (origin.z + 0.5)
        val dy = entity.y - origin.y
        return dx * dx + dz * dz <= leash * leash && abs(dy) <= maxOf(leash, 24.0)
    }
}

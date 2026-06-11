package com.cobblepalsworld.behavior.phase

import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.behavior.phase.PhaseSupport.applyNavigationStatus
import com.cobblepalsworld.behavior.phase.PhaseSupport.dropAllItems
import com.cobblepalsworld.behavior.phase.PhaseSupport.isWithinWorkRange
import com.cobblepalsworld.behavior.phase.PhaseSupport.resetToIdle
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.navigation.MovementPurpose
import com.cobblepalsworld.navigation.NavigationAttempt
import com.cobblepalsworld.navigation.NavigationHelper
import com.cobblepalsworld.visual.WorkVisualHandler

object DepositingPhaseHandler : PhaseHandler {
    override fun tick(ctx: PhaseContext) {
        val world = ctx.world
        val entity = ctx.entity
        val pokemon = ctx.pokemon
        val tag = ctx.tag
        val behavior = ctx.behavior
        val state = ctx.state
        val origin = ctx.origin
        val navigationBudget = ctx.navigationBudget
        val inventory = InventoryManager.get(pokemon.uuid)
        if (inventory == null || inventory.isEmpty) {
            resetToIdle(state)
            state.cooldownUntil = world.time + behavior.cooldownTicks(tag, state)
            state.setStatus(WorkerStatusReason.COOLDOWN, "status.cobblepalsworld.detail.cooldown_run_finished")
            return
        }

        if (state.depositPos == null) {
            val searchRange = TagExecutionEngine.effectiveRange(tag, state)
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
                    && java.lang.Long.compare(world.time - state.containerCacheTime, ConfigManager.config.general.containerCacheTicks.toLong()) == -1
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
        if (!isWithinWorkRange(origin, depositPos, TagExecutionEngine.effectiveRange(tag, state))) {
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
}

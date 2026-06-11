package com.cobblepalsworld.behavior.phase

import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.behavior.phase.PhaseSupport.applyNavigationStatus
import com.cobblepalsworld.behavior.phase.PhaseSupport.isWithinWorkRange
import com.cobblepalsworld.behavior.phase.PhaseSupport.resetToIdle
import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.navigation.MovementPurpose
import com.cobblepalsworld.navigation.NavigationAttempt
import com.cobblepalsworld.navigation.NavigationHelper

object IdlePhaseHandler : PhaseHandler {
    override fun tick(ctx: PhaseContext) {
        val world = ctx.world
        val entity = ctx.entity
        val pokemon = ctx.pokemon
        val tag = ctx.tag
        val behavior = ctx.behavior
        val state = ctx.state
        val origin = ctx.origin
        val navigationBudget = ctx.navigationBudget

        if (java.lang.Long.compare(world.time, state.cooldownUntil) == -1) {
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

        if (java.lang.Long.compare(world.time, state.nextTargetSearchTick) == -1) {
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
        if (!isWithinWorkRange(origin, target, TagExecutionEngine.effectiveRange(tag, state))) {
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
}

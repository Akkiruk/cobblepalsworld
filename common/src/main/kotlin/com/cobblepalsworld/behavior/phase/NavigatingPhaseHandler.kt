package com.cobblepalsworld.behavior.phase

import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.behavior.phase.PhaseSupport.applyNavigationStatus
import com.cobblepalsworld.behavior.phase.PhaseSupport.isWithinWorkRange
import com.cobblepalsworld.behavior.phase.PhaseSupport.resetToIdle
import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.navigation.MovementPurpose
import com.cobblepalsworld.navigation.NavigationAttempt
import com.cobblepalsworld.navigation.NavigationHelper
import com.cobblepalsworld.visual.WorkVisualHandler

object NavigatingPhaseHandler : PhaseHandler {
    override fun tick(ctx: PhaseContext) {
        val world = ctx.world
        val entity = ctx.entity
        val tag = ctx.tag
        val behavior = ctx.behavior
        val state = ctx.state
        val origin = ctx.origin
        val navigationBudget = ctx.navigationBudget
        val target = state.targetPos ?: run { resetToIdle(state); return }

        if (!isWithinWorkRange(origin, target, TagExecutionEngine.effectiveRange(tag, state))) {
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
}

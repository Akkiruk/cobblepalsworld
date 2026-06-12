package com.cobblepalsworld.behavior.phase

import com.cobblepalsworld.behavior.phase.PhaseSupport.resetToIdle
import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.navigation.ClaimManager

object ArrivingPhaseHandler : PhaseHandler {
    override fun tick(ctx: PhaseContext) {
        val world = ctx.world
        val tag = ctx.tag
        val behavior = ctx.behavior
        val state = ctx.state
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
        if (java.lang.Long.compare(world.time - arrived, behavior.arrivalDelayTicks(tag, state)) == -1) {
            state.setStatus(WorkerStatusReason.ARRIVING, "status.cobblepalsworld.detail.arriving_prepare")
            return
        }

        state.arrivalTick = null
        state.phase = WorkerPhase.WORKING
        state.setStatus(WorkerStatusReason.WORKING, "status.cobblepalsworld.detail.working")
    }
}

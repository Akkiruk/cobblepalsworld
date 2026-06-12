package com.cobblepalsworld.behavior.phase

import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.phase.PhaseSupport.applyNavigationStatus
import com.cobblepalsworld.behavior.phase.PhaseSupport.awardMastery
import com.cobblepalsworld.behavior.phase.PhaseSupport.isWithinWorkRange
import com.cobblepalsworld.behavior.phase.PhaseSupport.resetToIdle
import com.cobblepalsworld.behavior.phase.PhaseSupport.storeItems
import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.navigation.MovementPurpose
import com.cobblepalsworld.navigation.NavigationAttempt
import com.cobblepalsworld.navigation.NavigationHelper
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.visual.WorkVisualHandler
import net.minecraft.server.world.ServerWorld

object WorkingPhaseHandler : PhaseHandler {
    override fun tick(ctx: PhaseContext) {
        val world = ctx.world
        val entity = ctx.entity
        val pokemon = ctx.pokemon
        val tag = ctx.tag
        val behavior = ctx.behavior
        val state = ctx.state
        val origin = ctx.origin
        val navigationBudget = ctx.navigationBudget
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
                if (!isWithinWorkRange(origin, result.target, TagExecutionEngine.effectiveRange(tag, state))) {
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
}

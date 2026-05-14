package com.cobblepalsworld.navigation

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.state.WorkerState
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.util.math.BlockPos
import kotlin.math.sqrt

object WorkerNavigationManager {
    private const val PATHFIND_THROTTLE_TICKS = 10L
    private const val WALK_SPEED = 0.35
    private const val STUCK_GRACE_TICKS = 35L
    private const val MAX_NAVIGATION_TICKS = 20L * 45L
    private const val MIN_PROGRESS_DISTANCE_SQ = 0.04
    private const val MAX_FAILED_ATTEMPTS = 6
    private const val FAILURE_CACHE_TICKS = 20L * 15L

    private val failedDestinations = mutableMapOf<FailureKey, Long>()

    fun navigateTo(
        entity: PokemonEntity,
        destination: BlockPos,
        state: WorkerState,
        budget: NavigationBudget? = null,
        purpose: MovementPurpose = MovementPurpose.WORK_TARGET
    ): NavigationAttempt {
        val world = entity.world
        val now = world.time
        val immutableDestination = destination.toImmutable()
        val failureKey = FailureKey(world.registryKey.value.toString(), immutableDestination, purpose)
        val failedUntil = failedDestinations[failureKey]
        if (failedUntil != null) {
            if (now < failedUntil) return NavigationAttempt.UNREACHABLE
            failedDestinations.remove(failureKey)
        }

        val travelTarget = SafePositionResolver.standNear(world, immutableDestination, entity.blockPos)
            ?: run {
                failedDestinations[failureKey] = now + FAILURE_CACHE_TICKS
                return NavigationAttempt.UNREACHABLE
            }
        ensureSession(entity, immutableDestination, travelTarget, state, purpose)

        recoveryAttempt(entity, immutableDestination, travelTarget, state, purpose)?.let { attempt ->
            if (attempt == NavigationAttempt.UNREACHABLE) {
                failedDestinations[failureKey] = now + FAILURE_CACHE_TICKS
            }
            return attempt
        }

        if (now - state.lastPathfindTick < PATHFIND_THROTTLE_TICKS) return NavigationAttempt.THROTTLED
        if (budget != null && !budget.tryConsumeStart()) return NavigationAttempt.BUDGETED

        state.lastPathfindTick = now
        return if (entity.navigation.startMovingTo(
                travelTarget.x + 0.5,
                travelTarget.y.toDouble(),
                travelTarget.z + 0.5,
                normalizedSpeed(entity)
            )) {
            NavigationAttempt.STARTED
        } else {
            registerFailure(state, failureKey, now)
        }
    }

    fun stop(entity: PokemonEntity, state: WorkerState? = null) {
        entity.navigation.stop()
        state?.resetNavigationSession()
    }

    fun isAtPosition(entity: PokemonEntity, pos: BlockPos, tolerance: Double): Boolean {
        val travelTarget = SafePositionResolver.standNear(entity.world, pos, entity.blockPos) ?: return false
        val dx = entity.x - (travelTarget.x + 0.5)
        val dy = entity.y - (travelTarget.y + 0.5)
        val dz = entity.z - (travelTarget.z + 0.5)
        return dx * dx + dy * dy + dz * dz <= tolerance * tolerance
    }

    fun clearFailureCache() {
        failedDestinations.clear()
    }

    fun pruneFailureCache(currentTime: Long) {
        failedDestinations.entries.removeIf { (_, failedUntil) -> currentTime >= failedUntil }
    }

    private fun ensureSession(
        entity: PokemonEntity,
        destination: BlockPos,
        travelTarget: BlockPos,
        state: WorkerState,
        purpose: MovementPurpose
    ) {
        if (state.navigationDestination == destination && state.navigationTravelTarget == travelTarget && state.navigationPurpose == purpose.id) {
            updateProgress(entity, state)
            return
        }

        state.navigationDestination = destination
        state.navigationTravelTarget = travelTarget
        state.navigationPurpose = purpose.id
        state.navigationStartedTick = entity.world.time
        state.navigationLastProgressTick = entity.world.time
        state.navigationLastX = entity.x
        state.navigationLastY = entity.y
        state.navigationLastZ = entity.z
        state.navigationRecoveryStage = 0
        state.navigationRecoveryCooldownUntil = 0L
        state.navigationFailedAttempts = 0
    }

    private fun updateProgress(entity: PokemonEntity, state: WorkerState) {
        if (state.navigationLastX.isNaN()) {
            state.navigationLastX = entity.x
            state.navigationLastY = entity.y
            state.navigationLastZ = entity.z
            state.navigationLastProgressTick = entity.world.time
            return
        }

        val dx = entity.x - state.navigationLastX
        val dy = entity.y - state.navigationLastY
        val dz = entity.z - state.navigationLastZ
        if (dx * dx + dy * dy + dz * dz >= MIN_PROGRESS_DISTANCE_SQ) {
            state.navigationLastX = entity.x
            state.navigationLastY = entity.y
            state.navigationLastZ = entity.z
            state.navigationLastProgressTick = entity.world.time
            state.navigationRecoveryStage = 0
        }
    }

    private fun recoveryAttempt(
        entity: PokemonEntity,
        destination: BlockPos,
        travelTarget: BlockPos,
        state: WorkerState,
        purpose: MovementPurpose
    ): NavigationAttempt? {
        val now = entity.world.time
        if (now - state.navigationStartedTick > MAX_NAVIGATION_TICKS) return NavigationAttempt.UNREACHABLE
        if (now - state.navigationLastProgressTick < STUCK_GRACE_TICKS) return null
        if (now < state.navigationRecoveryCooldownUntil) return NavigationAttempt.RECOVERING

        return when (state.navigationRecoveryStage) {
            0 -> {
                forwardHop(entity, travelTarget)
                state.navigationRecoveryStage = 1
                state.navigationRecoveryCooldownUntil = now + 12L
                NavigationAttempt.RECOVERING
            }
            1 -> {
                val escape = SafePositionResolver.escapeNear(entity.world, entity.blockPos, travelTarget)
                if (escape != null) {
                    phaseReseat(entity, escape)
                    state.navigationRecoveryStage = 2
                    state.navigationRecoveryCooldownUntil = now + 8L
                    NavigationAttempt.RESCUED
                } else {
                    state.navigationRecoveryStage = 2
                    NavigationAttempt.RECOVERING
                }
            }
            2 -> {
                entity.navigation.stop()
                state.lastPathfindTick = 0L
                state.navigationRecoveryStage = 3
                state.navigationRecoveryCooldownUntil = now + 8L
                NavigationAttempt.RECOVERING
            }
            else -> {
                state.navigationFailedAttempts++
                if (state.navigationFailedAttempts >= MAX_FAILED_ATTEMPTS || purpose == MovementPurpose.IDLE) {
                    NavigationAttempt.UNREACHABLE
                } else {
                    state.navigationLastProgressTick = now
                    state.navigationRecoveryStage = 0
                    NavigationAttempt.RECOVERING
                }
            }
        }
    }

    private fun forwardHop(entity: PokemonEntity, target: BlockPos) {
        val dx = target.x + 0.5 - entity.x
        val dz = target.z + 0.5 - entity.z
        val length = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001)
        entity.navigation.stop()
        entity.setVelocity(dx / length * 0.32, 0.42, dz / length * 0.32)
    }

    private fun phaseReseat(entity: PokemonEntity, pos: BlockPos) {
        entity.navigation.stop()
        entity.teleport(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, false)
        entity.setVelocity(0.0, 0.0, 0.0)
    }

    private fun registerFailure(state: WorkerState, key: FailureKey, now: Long): NavigationAttempt {
        state.navigationFailedAttempts++
        if (state.navigationFailedAttempts >= MAX_FAILED_ATTEMPTS) {
            failedDestinations[key] = now + FAILURE_CACHE_TICKS
            return NavigationAttempt.UNREACHABLE
        }
        return NavigationAttempt.FAILED
    }

    private fun normalizedSpeed(entity: PokemonEntity): Double {
        val base = entity.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)
        if (base <= 0.0) return 1.0
        return (WALK_SPEED / base).coerceAtMost(1.0)
    }

    private data class FailureKey(val dimensionId: String, val destination: BlockPos, val purpose: MovementPurpose)
}
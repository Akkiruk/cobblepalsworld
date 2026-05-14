package com.cobblepalsworld.navigation

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.state.WorkerState
import net.minecraft.util.math.BlockPos

enum class NavigationAttempt {
    STARTED,
    THROTTLED,
    BUDGETED,
    RECOVERING,
    RESCUED,
    UNREACHABLE,
    FAILED
}

object NavigationHelper {
    private const val DEFAULT_ARRIVAL_TOLERANCE = 3.0

    fun navigateTo(
        entity: PokemonEntity,
        pos: BlockPos,
        state: WorkerState,
        budget: NavigationBudget? = null,
        purpose: MovementPurpose = MovementPurpose.WORK_TARGET
    ): NavigationAttempt {
        return WorkerNavigationManager.navigateTo(entity, pos, state, budget, purpose)
    }

    fun stopNavigation(entity: PokemonEntity, state: WorkerState? = null) {
        WorkerNavigationManager.stop(entity, state)
    }

    fun isAtPosition(entity: PokemonEntity, pos: BlockPos, tolerance: Double = DEFAULT_ARRIVAL_TOLERANCE): Boolean {
        return WorkerNavigationManager.isAtPosition(entity, pos, tolerance)
    }
}

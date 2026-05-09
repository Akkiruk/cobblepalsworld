package com.cobblepalsworld.navigation

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.state.WorkerState
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.util.math.BlockPos

object NavigationHelper {
    private const val PATHFIND_THROTTLE_TICKS = 5L
    private const val WALK_SPEED = 0.35
    private const val DEFAULT_ARRIVAL_TOLERANCE = 3.0

    fun navigateTo(entity: PokemonEntity, pos: BlockPos, state: WorkerState): Boolean {
        val now = entity.world.time
        if (now - state.lastPathfindTick < PATHFIND_THROTTLE_TICKS) return false
        state.lastPathfindTick = now
        return entity.navigation.startMovingTo(
            pos.x + 0.5,
            pos.y.toDouble(),
            pos.z + 0.5,
            normalizedSpeed(entity)
        )
    }

    fun stopNavigation(entity: PokemonEntity) {
        entity.navigation.stop()
    }

    fun isAtPosition(entity: PokemonEntity, pos: BlockPos, tolerance: Double = DEFAULT_ARRIVAL_TOLERANCE): Boolean {
        val dx = entity.x - (pos.x + 0.5)
        val dz = entity.z - (pos.z + 0.5)
        return dx * dx + dz * dz <= tolerance * tolerance
    }

    private fun normalizedSpeed(entity: PokemonEntity): Double {
        val base = entity.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)
        if (base <= 0.0) return 1.0
        return (WALK_SPEED / base).coerceAtMost(1.0)
    }
}

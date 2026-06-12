package com.cobblepalsworld.behavior.phase

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.mastery.WorkMasteryManager
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.navigation.NavigationAttempt
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.visual.WorkVisualHandler
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import kotlin.math.abs

internal object PhaseSupport {
    private const val WORKSPACE_PADDING_BLOCKS = 2

    internal fun applyNavigationStatus(
        state: WorkerState,
        attempt: NavigationAttempt,
        activeReason: WorkerStatusReason,
        activeDetail: String,
        failedDetail: String
    ) {
        when (attempt) {
            NavigationAttempt.STARTED, NavigationAttempt.THROTTLED -> state.setStatus(activeReason, activeDetail)
            NavigationAttempt.BUDGETED -> state.setStatus(WorkerStatusReason.PATH_BUDGET, "status.cobblepalsworld.detail.path_budget")
            NavigationAttempt.RECOVERING -> state.setStatus(WorkerStatusReason.MOVEMENT_RECOVERY, "status.cobblepalsworld.detail.recovery_unsticking")
            NavigationAttempt.RESCUED -> state.setStatus(WorkerStatusReason.MOVEMENT_RECOVERY, "status.cobblepalsworld.detail.recovery_reseated")
            NavigationAttempt.UNREACHABLE -> state.setStatus(WorkerStatusReason.PATHING_STALLED, failedDetail)
            NavigationAttempt.FAILED -> state.setStatus(WorkerStatusReason.PATHING_STALLED, failedDetail)
        }
    }

    internal fun resetToIdle(state: WorkerState) {
        state.reset()
    }

    internal fun releaseClaimAndReset(world: World, state: WorkerState) {
        state.targetPos?.let { ClaimManager.release(it, world) }
        state.reset()
    }

    internal fun isWithinWorkRange(origin: BlockPos, target: BlockPos, range: Int): Boolean {
        val horizontalRange = range + WORKSPACE_PADDING_BLOCKS
        val verticalRange = maxOf(horizontalRange, 16)
        return isLessThanOrEqual(abs(target.x - origin.x), horizontalRange) &&
            isLessThanOrEqual(abs(target.z - origin.z), horizontalRange) &&
            isLessThanOrEqual(abs(target.y - origin.y), verticalRange)
    }

    private fun isLessThanOrEqual(value: Int, limit: Int): Boolean {
        return java.lang.Integer.compare(value, limit).let { it == -1 || it == 0 }
    }

    internal fun storeItems(world: World, entity: PokemonEntity, pokemon: Pokemon, items: List<ItemStack>) {
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

    internal fun dropAllItems(world: World, entity: PokemonEntity, inventory: SimpleInventory) {
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

    /**
     * Credits one completed job toward this worker's mastery for the active role.
     * On a tier-up, recompiles the cached cooldown/range so the new perks apply
     * immediately, and plays an in-world celebration.
     */
    internal fun awardMastery(world: World, entity: PokemonEntity, pokemon: Pokemon, tag: TagInstance, state: WorkerState) {
        val newTier = WorkMasteryManager.recordJob(pokemon.uuid, tag.type)
        (world as? ServerWorld)?.let(CobblePalsSaveData::markDirty)
        if (newTier != null) {
            state.invalidateCache()
            WorkVisualHandler.onMasteryTierUp(world, entity)
        }
    }
}

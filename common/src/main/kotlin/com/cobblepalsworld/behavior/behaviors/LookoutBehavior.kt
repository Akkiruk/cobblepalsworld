package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.router.RouterBlockEntity
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.filter.FilterMatcher
import net.minecraft.inventory.Inventory
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object LookoutBehavior : TagBehavior {
    override val tagType = TagType.LOOKOUT
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range

    override fun idleRetryTicks(tag: TagInstance, state: WorkerState): Long = 20L

    override fun findTarget(world: World, origin: BlockPos, entity: PokemonEntity, tag: TagInstance, state: WorkerState): BlockPos? {
        val range = effectiveRange(tag, state)
        val bound = tag.boundPos?.toImmutable()
        if (bound != null) {
            val inventory = ContainerFinder.getInventoryAt(world, bound)
            val active = inventory != null && inventoryHasMatch(inventory, tag)
            updateOutput(world, tag, origin, active)
            return if (active) bound else null
        }

        val found = ContainerFinder.findControllerFirstCachedMatching(world, origin, tag, state, range) { inventory, _ ->
            inventoryHasMatch(inventory, tag)
        }
        updateOutput(world, tag, origin, found != null)
        return found
    }

    override fun doWork(world: World, entity: PokemonEntity, target: BlockPos, tag: TagInstance, state: WorkerState): WorkResult {
        val inventory = ContainerFinder.getInventoryAt(world, target)
        val active = inventory != null && inventoryHasMatch(inventory, tag)
        updateOutput(world, tag, entity.blockPos, active)
        state.setStatus(
            com.cobblepalsworld.behavior.state.WorkerStatusReason.WORKING,
            if (active) "Output high: matching inventory contents detected" else "Output low: no matching contents detected"
        )
        return WorkResult.Done()
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return ContainerFinder.getInventoryAt(world, target)?.let { inventoryHasMatch(it, tag) } == true
    }

    private fun inventoryHasMatch(inventory: Inventory, tag: TagInstance): Boolean {
        for (slot in 0 until inventory.size()) {
            val stack = inventory.getStack(slot)
            if (!stack.isEmpty && FilterMatcher.matches(stack, tag.filter)) return true
        }
        return false
    }

    private fun updateOutput(world: World, tag: TagInstance, fallbackOrigin: BlockPos, powered: Boolean) {
        val controllerPos = tag.controllerPos ?: fallbackOrigin
        (world.getBlockEntity(controllerPos) as? RouterBlockEntity)?.updatePowered(powered)
    }
}
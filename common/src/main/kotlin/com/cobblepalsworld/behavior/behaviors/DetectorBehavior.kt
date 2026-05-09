package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.filter.FilterMatcher
import net.minecraft.block.Blocks
import net.minecraft.block.RedstoneBlock
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Monitors a container and toggles a redstone lamp or lever based on
 * whether the container holds matching items.
 *
 * Workflow:
 * 1. Navigate to bound container (or nearest)
 * 2. Check if filter matches contents → set adjacent redstone lamp ON/OFF
 * 3. Repeat on cooldown
 */
object DetectorBehavior : TagBehavior {
    override val tagType = TagType.LOOKOUT
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val range = effectiveRange(tag, state)

        // Bound tag = exclusive: only monitor that specific container
        val boundPos = tag.boundPos
        if (boundPos != null) {
            return if (ContainerFinder.isContainer(world, boundPos)) boundPos else null
        }

        return BlockPos.iterateOutwards(origin, range, range / 2, range)
            .firstOrNull { pos -> ContainerFinder.isContainer(world, pos) }
            ?.toImmutable()
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        val container = ContainerFinder.getInventoryAt(world, target) ?: return WorkResult.Done()

        val matchCount = countMatchingItems(container, tag)
        val hasMatch = matchCount > 0

        // Toggle adjacent redstone lamp by placing/removing a redstone block behind it
        for (dir in net.minecraft.util.math.Direction.values()) {
            val lampPos = target.offset(dir)
            val lampState = world.getBlockState(lampPos)
            if (lampState.isOf(Blocks.REDSTONE_LAMP)) {
                val isLit = lampState.get(net.minecraft.state.property.Properties.LIT)
                // The power block goes on the opposite side of the lamp from the container
                val powerPos = lampPos.offset(dir)
                if (hasMatch && !isLit) {
                    // Place a redstone block to power the lamp persistently
                    val powerState = world.getBlockState(powerPos)
                    if (powerState.isAir || powerState.isReplaceable) {
                        world.setBlockState(powerPos, Blocks.REDSTONE_BLOCK.defaultState)
                    }
                } else if (!hasMatch && isLit) {
                    // Remove the redstone block to unpower the lamp
                    if (world.getBlockState(powerPos).block is RedstoneBlock) {
                        world.setBlockState(powerPos, Blocks.AIR.defaultState)
                    }
                }
            }
        }

        // Repeat — come back after cooldown and check again
        return WorkResult.Repeat
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return ContainerFinder.isContainer(world, target)
    }

    private fun countMatchingItems(container: Inventory, tag: TagInstance): Int {
        var count = 0
        for (slot in 0 until container.size()) {
            val stack = container.getStack(slot)
            if (!stack.isEmpty && FilterMatcher.matches(stack, tag.filter)) {
                count += stack.count
            }
        }
        return count
    }
}

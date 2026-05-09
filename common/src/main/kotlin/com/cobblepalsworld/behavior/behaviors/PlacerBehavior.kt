package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.inventory.PokemonInventory
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.pasture.PastureWorkerManager
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.filter.FilterMatcher
import com.cobblepalsworld.tag.filter.TagFilter
import net.minecraft.block.Block
import net.minecraft.block.CropBlock
import net.minecraft.block.FlowerBlock
import net.minecraft.block.MushroomPlantBlock
import net.minecraft.block.SaplingBlock
import net.minecraft.block.SweetBerryBushBlock
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemStack
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Placer: takes matching block items from nearby containers and places them in the world.
 *
 * Binding determines WHERE to plant, NOT the source.
 * - Bound → plants at/near the bound position
 * - Unbound → plants at any suitable spot near the pasture
 *
 * Source containers are found automatically within range.
 */
object PlacerBehavior : TagBehavior {
    override val tagType = TagType.PLANTER
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range
    override val handlesOwnInventory = true

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val pokemonId = entity.pokemon.uuid
        val inventory = InventoryManager.get(pokemonId)
        val hasItems = inventory != null && hasBlockItems(inventory, tag.filter)
        val range = effectiveRange(tag, state)

        return if (hasItems) {
            // Has block items → find where to place them
            findPlacementTarget(world, origin, pokemonId, tag, range)
        } else {
            // No items → find a container to restock from
            findSourceContainer(world, origin, tag, range)
        }
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        val inventory = InventoryManager.getOrCreate(entity.pokemon)

        // Target is a container → extract block items
        if (ContainerFinder.isContainer(world, target)) {
            return extractFromContainer(world, target, inventory, tag.filter)
        }

        // Target is a placeable spot → place a block
        if (world.getBlockState(target).isReplaceable) {
            return placeBlock(world, target, inventory, tag.filter)
        }

        return WorkResult.Done()
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return ContainerFinder.isContainer(world, target) || world.getBlockState(target).isReplaceable
    }

    /**
     * Find where to place blocks.
     * Bound → place at/near the bound position.
     * Unbound → find any suitable replaceable spot near origin.
     */
    private fun findPlacementTarget(
        world: World, origin: BlockPos, pokemonId: java.util.UUID,
        tag: TagInstance, range: Int
    ): BlockPos? {
        val boundPos = tag.boundPos
        if (boundPos != null) {
            // Bound position IS the placement target — check it and nearby spots
            if (isPlaceable(world, boundPos, pokemonId)) return boundPos
            // Check 1-block radius around bound pos for adjacent openings
            return BlockPos.iterateOutwards(boundPos, 1, 1, 1)
                .firstOrNull { isPlaceable(world, it, pokemonId) }
                ?.toImmutable()
        }

        // Unbound → search outward from origin
        return BlockPos.iterateOutwards(origin, range, range / 2, range)
            .firstOrNull { isPlaceable(world, it, pokemonId) }
            ?.toImmutable()
    }

    /**
     * Find a container to restock from. Searches near origin, excluding:
     * - The pasture origin itself
     * - The bound position (that's where we're PLACING, not extracting)
     */
    private fun findSourceContainer(
        world: World, origin: BlockPos, tag: TagInstance, range: Int
    ): BlockPos? {
        val exclude = mutableSetOf(origin)
        tag.boundPos?.let { exclude.add(it) }

        return ContainerFinder.findClosestMatching(world, origin, range, exclude) { _, pos ->
            containerHasBlockItems(world, pos, tag.filter)
        }
    }

    private fun extractFromContainer(
        world: World, target: BlockPos,
        pokemonInventory: PokemonInventory,
        filter: TagFilter
    ): WorkResult {
        val container = ContainerFinder.getInventoryAt(world, target) ?: return WorkResult.Done()

        for (slot in 0 until container.size()) {
            val stack = container.getStack(slot)
            if (stack.isEmpty || !isPlaceableBlock(stack)) continue
            if (!FilterMatcher.matches(stack, filter)) continue

            // Take one stack (or partial) and carry it
            val toTake = stack.copyWithCount(1)
            val remainder = pokemonInventory.insertStack(toTake)
            if (!remainder.isEmpty) continue

            stack.decrement(1)
            container.setStack(slot, if (stack.isEmpty) ItemStack.EMPTY else stack)
            container.markDirty()
            PastureWorkerManager.markDirtyNow(world)
            return WorkResult.Done()
        }

        return WorkResult.Done()
    }

    private fun placeBlock(
        world: World, target: BlockPos,
        pokemonInventory: net.minecraft.inventory.SimpleInventory, filter: TagFilter
    ): WorkResult {
        for (slot in 0 until pokemonInventory.size()) {
            val stack = pokemonInventory.getStack(slot)
            if (stack.isEmpty) continue
            val blockItem = stack.item as? BlockItem ?: continue
            if (!isPlaceableBlock(stack)) continue
            if (!FilterMatcher.matches(stack, filter)) continue

            val blockState = blockItem.block.defaultState
            if (!blockState.canPlaceAt(world, target)) continue

            world.setBlockState(target, blockState, Block.NOTIFY_ALL)
            if (world is ServerWorld) {
                val soundGroup = blockState.soundGroup
                world.playSound(
                    null, target, soundGroup.placeSound,
                    SoundCategory.BLOCKS, soundGroup.volume * 0.5f, soundGroup.pitch
                )
            }

            stack.decrement(1)
            pokemonInventory.setStack(slot, if (stack.isEmpty) ItemStack.EMPTY else stack)
            PastureWorkerManager.markDirtyNow(world)
            return WorkResult.Done()
        }
        return WorkResult.Done()
    }

    private fun isPlaceable(world: World, pos: BlockPos, pokemonId: java.util.UUID): Boolean {
        return world.getBlockState(pos).isReplaceable
            && hasAdjacentSolid(world, pos)
            && !ClaimManager.isClaimedByOther(pos, pokemonId, world)
    }

    private fun hasBlockItems(inventory: net.minecraft.inventory.SimpleInventory, filter: TagFilter): Boolean {
        for (slot in 0 until inventory.size()) {
            val stack = inventory.getStack(slot)
            if (!stack.isEmpty && isPlaceableBlock(stack) && FilterMatcher.matches(stack, filter)) return true
        }
        return false
    }

    private fun containerHasBlockItems(world: World, pos: BlockPos, filter: TagFilter): Boolean {
        val inventory = ContainerFinder.getInventoryAt(world, pos) ?: return false
        for (slot in 0 until inventory.size()) {
            val stack = inventory.getStack(slot)
            if (!stack.isEmpty && isPlaceableBlock(stack) && FilterMatcher.matches(stack, filter)) return true
        }
        return false
    }

    private fun isPlaceableBlock(stack: ItemStack): Boolean {
        val item = stack.item
        return item is BlockItem && item.block.defaultState.block !is net.minecraft.block.AirBlock
    }

    private fun hasAdjacentSolid(world: World, pos: BlockPos): Boolean {
        return net.minecraft.util.math.Direction.values().any { dir ->
            val adjacent = pos.offset(dir)
            world.getBlockState(adjacent).isSolidBlock(world, adjacent)
        }
    }
}

package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.filter.FilterMatcher
import com.cobblepalsworld.tag.filter.TagFilter
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Sender: collects items from nearby containers, then deposits them to the BOUND target.
 * Dual-phase:
 *   Phase 1 (no items): Find a nearby container with matching items → extract
 *   Phase 2 (has items): Navigate to bound/target container → deposit
 */
object SenderBehavior : TagBehavior {
    override val tagType = TagType.COURIER
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range
    override val handlesOwnInventory = true

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val pokemonInv = InventoryManager.get(entity.pokemon.uuid)
        val hasItems = pokemonInv != null && (0 until pokemonInv.size()).any { slot ->
            val stack = pokemonInv.getStack(slot)
            !stack.isEmpty && FilterMatcher.matches(stack, tag.filter)
        }

        val pokemonId = entity.pokemon.uuid
        val range = effectiveRange(tag, state)

        return if (hasItems) {
            // Phase 2: has items → find deposit target (bound preferred)
            findDepositTarget(world, origin, tag, range)
        } else {
            // Phase 1: no items → find source container near pasture to extract from
            findSourceContainer(world, origin, tag, pokemonId, range)
        }
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        val pokemonInv = InventoryManager.getOrCreate(entity.pokemon)
        val hasItems = (0 until pokemonInv.size()).any { !pokemonInv.getStack(it).isEmpty }

        return if (!hasItems) {
            // Phase 1: extract from source container
            extractFromSource(world, target, pokemonInv, tag, state)
        } else {
            // Phase 2: deposit into target container
            depositToTarget(world, target, pokemonInv, tag)
        }
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return ContainerFinder.isContainer(world, target)
    }

    private fun findSourceContainer(
        world: World, origin: BlockPos, tag: TagInstance, pokemonId: java.util.UUID, range: Int
    ): BlockPos? {
        // Exclude the bound container — that's the DESTINATION, not the source
        val excludeBound = tag.boundPos
        return BlockPos.iterateOutwards(origin, range, range / 2, range)
            .firstOrNull { pos ->
                (excludeBound == null || pos != excludeBound)
                    && ContainerFinder.isContainer(world, pos)
                    && !ClaimManager.isClaimedByOther(pos, pokemonId, world)
                    && containerHasMatchingItems(world, pos, tag.filter)
            }?.toImmutable()
    }

    private fun findDepositTarget(
        world: World, origin: BlockPos, tag: TagInstance, range: Int
    ): BlockPos? {
        val boundPos = tag.boundPos
        if (boundPos != null) {
            return if (ContainerFinder.isContainer(world, boundPos) && containerHasSpace(world, boundPos))
                boundPos else null
        }
        return BlockPos.iterateOutwards(origin, range, range / 2, range)
            .firstOrNull { pos -> ContainerFinder.isContainer(world, pos) && containerHasSpace(world, pos) }
            ?.toImmutable()
    }

    private fun extractFromSource(
        world: World, target: BlockPos,
        pokemonInv: net.minecraft.inventory.SimpleInventory,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        val container = ContainerFinder.getInventoryAt(world, target) ?: return WorkResult.Done()
        val maxItems = effectiveMaxItems(tag, state)
        var itemsExtracted = 0

        for (slot in 0 until container.size()) {
            if (itemsExtracted >= maxItems) break
            val stack = container.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val freeSlot = (0 until pokemonInv.size()).firstOrNull { pokemonInv.getStack(it).isEmpty } ?: break
            val toTake = minOf(stack.count, maxItems - itemsExtracted)
            pokemonInv.setStack(freeSlot, stack.copyWithCount(toTake))
            stack.decrement(toTake)
            if (stack.isEmpty) container.setStack(slot, ItemStack.EMPTY)
            itemsExtracted += toTake
        }
        container.markDirty()
        return WorkResult.Done()  // Items stored directly — engine won't deposit-first
    }

    private fun depositToTarget(
        world: World, target: BlockPos,
        pokemonInv: net.minecraft.inventory.SimpleInventory,
        tag: TagInstance
    ): WorkResult {
        val container = ContainerFinder.getInventoryAt(world, target) ?: return WorkResult.Done()

        for (slot in 0 until pokemonInv.size()) {
            val stack = pokemonInv.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val remaining = ContainerFinder.insertStack(container, stack.copy())
            pokemonInv.setStack(slot, remaining)
        }
        container.markDirty()
        return WorkResult.Done()
    }

    private fun containerHasMatchingItems(world: World, pos: BlockPos, filter: TagFilter): Boolean {
        val inventory = ContainerFinder.getInventoryAt(world, pos) ?: return false
        for (slot in 0 until inventory.size()) {
            val stack = inventory.getStack(slot)
            if (!stack.isEmpty && FilterMatcher.matches(stack, filter)) return true
        }
        return false
    }

    private fun containerHasSpace(world: World, pos: BlockPos): Boolean {
        val inventory = ContainerFinder.getInventoryAt(world, pos) ?: return false
        return (0 until inventory.size()).any { inventory.getStack(it).isEmpty || inventory.getStack(it).count < inventory.getStack(it).maxCount }
    }
}

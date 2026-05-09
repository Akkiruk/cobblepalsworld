package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.filter.FilterMatcher
import net.minecraft.entity.ItemEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Extracts items from a container and drops them into the world as entities.
 * Useful for feeding items to other systems (hoppers underneath, other Pokémon vacuums, etc.).
 */
object DropperBehavior : TagBehavior {
    override val tagType = TagType.DROPPER
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range
    override val handlesOwnInventory = true

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val pokemonInv = InventoryManager.get(entity.pokemon.uuid)
        val hasItems = pokemonInv != null && (0 until pokemonInv.size()).any {
            val stack = pokemonInv.getStack(it)
            !stack.isEmpty && FilterMatcher.matches(stack, tag.filter)
        }

        val range = effectiveRange(tag, state)

        // Phase 1: Has items → find a drop point
        // When bound to a source container, drop at origin (pasture) to avoid cycling
        if (hasItems) {
            return origin
        }

        // Phase 2: No items → find a source container
        // Bound tag = exclusive: only pull from that specific container
        val boundPos = tag.boundPos
        if (boundPos != null) {
            return if (ContainerFinder.isContainer(world, boundPos) && containerHasMatchingItems(world, boundPos, tag))
                boundPos else null
        }

        return BlockPos.iterateOutwards(origin, range, range / 2, range)
            .firstOrNull { pos ->
                ContainerFinder.isContainer(world, pos) && containerHasMatchingItems(world, pos, tag)
            }?.toImmutable()
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        // At a container → extract items
        if (ContainerFinder.isContainer(world, target)) {
            val container = ContainerFinder.getInventoryAt(world, target) ?: return WorkResult.Done()
            return extractItems(container, entity, tag, state)
        }

        // At a drop location → drop items from inventory
        val pokemonInv = InventoryManager.get(entity.pokemon.uuid) ?: return WorkResult.Done()
        for (slot in 0 until pokemonInv.size()) {
            val stack = pokemonInv.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val itemEntity = ItemEntity(
                world,
                target.x + 0.5, target.y + 1.0, target.z + 0.5,
                stack.copy()
            )
            itemEntity.setPickupDelay(20) // 1 second before pickup
            world.spawnEntity(itemEntity)
            pokemonInv.setStack(slot, ItemStack.EMPTY)
        }

        return WorkResult.Done()
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return true
    }

    private fun extractItems(
        container: Inventory, entity: PokemonEntity, tag: TagInstance, state: WorkerState
    ): WorkResult {
        val pokemonInv = InventoryManager.getOrCreate(entity.pokemon)
        val maxItems = effectiveMaxItems(tag, state)
        var count = 0

        for (slot in 0 until container.size()) {
            if (count >= maxItems) break
            val stack = container.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val freeSlot = (0 until pokemonInv.size()).firstOrNull { pokemonInv.getStack(it).isEmpty } ?: break
            val toTake = minOf(stack.count, maxItems - count)
            pokemonInv.setStack(freeSlot, stack.copyWithCount(toTake))
            stack.decrement(toTake)
            if (stack.isEmpty) container.setStack(slot, ItemStack.EMPTY)
            count += toTake
        }

        container.markDirty()
        return WorkResult.Done()  // Items stored directly for Phase 2 dropping
    }

    private fun containerHasMatchingItems(world: World, pos: BlockPos, tag: TagInstance): Boolean {
        val container = ContainerFinder.getInventoryAt(world, pos) ?: return false
        for (slot in 0 until container.size()) {
            val stack = container.getStack(slot)
            if (!stack.isEmpty && FilterMatcher.matches(stack, tag.filter)) return true
        }
        return false
    }
}

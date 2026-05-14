package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.inventory.PokemonInventory
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.filter.FilterMatcher
import com.cobblepalsworld.tag.filter.TagFilter
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object SenderBehavior : TagBehavior {
    override val tagType = TagType.COURIER
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range
    override val handlesOwnInventory = true

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val commandPostPos = ContainerFinder.controllerBufferPos(world, tag) ?: return null
        val destinationPos = tag.boundPos ?: return null
        val pokemonInv = InventoryManager.get(entity.pokemon.uuid)
        val hasItems = pokemonInv != null && (0 until pokemonInv.size()).any { slot ->
            val stack = pokemonInv.getStack(slot)
            !stack.isEmpty && FilterMatcher.matches(stack, tag.filter)
        }

        return if (hasItems) {
            if (ContainerFinder.isContainer(world, destinationPos) && containerHasSpace(world, destinationPos)) destinationPos else null
        } else {
            if (ContainerFinder.isContainer(world, commandPostPos) && containerHasMatchingItems(world, commandPostPos, tag.filter)) commandPostPos else null
        }
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        val pokemonInv = InventoryManager.getOrCreate(entity.pokemon)
        val commandPostPos = ContainerFinder.controllerBufferPos(world, tag)

        return if (commandPostPos != null && target == commandPostPos) {
            extractFromSource(world, target, pokemonInv, tag, state)
        } else {
            depositToTarget(world, target, pokemonInv, tag)
        }
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return ContainerFinder.isContainer(world, target)
    }

    private fun extractFromSource(
        world: World,
        target: BlockPos,
        pokemonInv: PokemonInventory,
        tag: TagInstance,
        state: WorkerState
    ): WorkResult {
        val container = ContainerFinder.getInventoryAt(world, target) ?: return WorkResult.Done()
        val maxItems = effectiveMaxItems(tag, state)
        var itemsExtracted = 0

        for (slot in 0 until container.size()) {
            if (itemsExtracted >= maxItems) break
            val stack = container.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val requested = stack.copyWithCount(minOf(stack.count, maxItems - itemsExtracted))
            val remainder = pokemonInv.insertStack(requested)
            val inserted = requested.count - remainder.count
            if (inserted <= 0) continue

            stack.decrement(inserted)
            if (stack.isEmpty) container.setStack(slot, ItemStack.EMPTY)
            itemsExtracted += inserted
        }
        container.markDirty()
        if (itemsExtracted > 0) {
            (world as? net.minecraft.server.world.ServerWorld)?.let(CobblePalsSaveData::markDirty)
        }
        return WorkResult.Done()
    }

    private fun depositToTarget(
        world: World,
        target: BlockPos,
        pokemonInv: PokemonInventory,
        tag: TagInstance
    ): WorkResult {
        val container = ContainerFinder.getInventoryAt(world, target) ?: return WorkResult.Done()
        var changed = false

        for (slot in 0 until pokemonInv.size()) {
            val stack = pokemonInv.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val remaining = ContainerFinder.insertStack(container, stack.copy())
            if (remaining.count != stack.count) changed = true
            pokemonInv.setStack(slot, remaining)
        }
        container.markDirty()
        if (changed) {
            (world as? net.minecraft.server.world.ServerWorld)?.let(CobblePalsSaveData::markDirty)
        }
        return WorkResult.Done()
    }

    fun cleanup(pokemonId: java.util.UUID) {}

    fun clearAllRuntimeState() {}

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
        return ContainerFinder.hasSpace(inventory)
    }
}

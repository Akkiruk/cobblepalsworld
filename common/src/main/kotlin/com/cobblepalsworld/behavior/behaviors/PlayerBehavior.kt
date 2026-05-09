package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.inventory.PokemonInventory
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.pasture.PastureWorkerManager
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.filter.FilterMatcher
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object PlayerBehavior : TagBehavior {
    override val tagType = TagType.PLAYER
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range
    override val handlesOwnInventory = true

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val pokemonInventory = InventoryManager.get(entity.pokemon.uuid)
        val hasItems = pokemonInventory != null && (0 until pokemonInventory.size()).any { slot ->
            val stack = pokemonInventory.getStack(slot)
            !stack.isEmpty && FilterMatcher.matches(stack, tag.filter)
        }

        return if (hasItems) {
            findOwner(entity, world, origin, effectiveRange(tag, state))?.blockPos
        } else {
            ContainerFinder.findClosestMatching(world, origin, effectiveRange(tag, state)) { _, pos ->
                containerHasMatchingItems(world, pos, tag)
            }
        }
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        val pokemonInventory = InventoryManager.getOrCreate(entity.pokemon)
        return if (ContainerFinder.isContainer(world, target)) {
            extractFromSource(world, target, pokemonInventory, tag, state)
        } else {
            deliverToOwner(world, entity, pokemonInventory, tag, state)
        }
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return ContainerFinder.isContainer(world, target) || true
    }

    private fun extractFromSource(
        world: World,
        target: BlockPos,
        pokemonInventory: PokemonInventory,
        tag: TagInstance,
        state: WorkerState
    ): WorkResult {
        val container = ContainerFinder.getInventoryAt(world, target) ?: return WorkResult.Done()
        var extracted = 0
        val maxItems = effectiveMaxItems(tag, state)

        for (slot in 0 until container.size()) {
            if (extracted >= maxItems) break
            val stack = container.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val requested = stack.copyWithCount(minOf(stack.count, maxItems - extracted))
            val remainder = pokemonInventory.insertStack(requested)
            val inserted = requested.count - remainder.count
            if (inserted <= 0) continue

            stack.decrement(inserted)
            if (stack.isEmpty) container.setStack(slot, ItemStack.EMPTY)
            extracted += inserted
        }

        container.markDirty()
        if (extracted > 0) {
            PastureWorkerManager.markDirtyNow(world)
        }
        return WorkResult.Done()
    }

    private fun deliverToOwner(
        world: World,
        entity: PokemonEntity,
        pokemonInventory: PokemonInventory,
        tag: TagInstance,
        state: WorkerState
    ): WorkResult {
        val owner = findOwner(entity, world, entity.blockPos, effectiveRange(tag, state)) ?: return WorkResult.Done()
        var changed = false

        for (slot in 0 until pokemonInventory.size()) {
            val stack = pokemonInventory.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val remaining = ContainerFinder.insertStack(owner.inventory, stack.copy())
            if (remaining.count != stack.count) changed = true
            pokemonInventory.setStack(slot, remaining)
        }

        owner.inventory.markDirty()
        owner.currentScreenHandler.sendContentUpdates()
        if (changed) {
            PastureWorkerManager.markDirtyNow(world)
        }
        return WorkResult.Done()
    }

    private fun findOwner(entity: PokemonEntity, world: World, origin: BlockPos, range: Int): PlayerEntity? {
        val owner = entity.pokemon.getOwnerPlayer() ?: return null
        if (owner.world != world) return null
        if (owner.blockPos.getSquaredDistance(origin) > range.toDouble() * range) return null
        return owner
    }

    private fun containerHasMatchingItems(world: World, pos: BlockPos, tag: TagInstance): Boolean {
        val inventory = ContainerFinder.getInventoryAt(world, pos) ?: return false
        for (slot in 0 until inventory.size()) {
            val stack = inventory.getStack(slot)
            if (!stack.isEmpty && FilterMatcher.matches(stack, tag.filter)) return true
        }
        return false
    }
}
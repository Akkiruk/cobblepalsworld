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
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object PullerBehavior : TagBehavior {
    override val tagType = TagType.PULLER
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range
    override val handlesOwnInventory = true

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val sourcePos = tag.boundPos ?: return null
        val commandPostPos = ContainerFinder.controllerBufferPos(world, tag) ?: return null
        val pokemonInv = InventoryManager.get(entity.pokemon.uuid)
        val carryingItems = pokemonInv != null && (0 until pokemonInv.size()).any { slot ->
            val stack = pokemonInv.getStack(slot)
            !stack.isEmpty
        }

        return if (carryingItems) {
            val inventory = ContainerFinder.getInventoryAt(world, commandPostPos) ?: return null
            if (ContainerFinder.hasSpace(inventory)) commandPostPos else null
        } else {
            if (ContainerFinder.isContainer(world, sourcePos) && containerHasMatchingItems(world, sourcePos, tag)) sourcePos else null
        }
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        val pokemonInv = InventoryManager.getOrCreate(entity.pokemon)
        return if (target == tag.boundPos) {
            extractFromSource(world, target, pokemonInv, tag, state)
        } else {
            ContainerFinder.depositFromInventory(world, target, pokemonInv)
            WorkResult.Done()
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
        var extracted = 0
        val maxItems = effectiveMaxItems(tag, state)

        for (slot in 0 until container.size()) {
            if (extracted >= maxItems) break
            val stack = container.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val requested = stack.copyWithCount(minOf(stack.count, maxItems - extracted))
            val remainder = pokemonInv.insertStack(requested)
            val inserted = requested.count - remainder.count
            if (inserted <= 0) continue

            stack.decrement(inserted)
            if (stack.isEmpty) container.setStack(slot, ItemStack.EMPTY)
            extracted += inserted
        }

        container.markDirty()
        if (extracted > 0) {
            (world as? net.minecraft.server.world.ServerWorld)?.let(CobblePalsSaveData::markDirty)
        }
        return WorkResult.Done()
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
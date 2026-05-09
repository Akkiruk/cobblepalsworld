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
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

/**
 * Round-robin distributes items from a source into multiple target containers.
 * Binds to a source container. Finds all other containers in range as targets.
 * Cycles through targets distributing matching items evenly.
 */
object DistributorBehavior : TagBehavior {
    override val tagType = TagType.STASHER
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range
    override val handlesOwnInventory = true

    // Track round-robin index per Pokémon
    private val roundRobinIndex = mutableMapOf<UUID, Int>()

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val pokemonInv = InventoryManager.get(entity.pokemon.uuid)
        val hasItems = pokemonInv != null && (0 until pokemonInv.size()).any { !pokemonInv.getStack(it).isEmpty }

        val range = effectiveRange(tag, state)

        // Phase 1: No items → go to source container to pull
        if (!hasItems) {
            val sourcePos = tag.boundPos ?: return null
            if (!ContainerFinder.isContainer(world, sourcePos)) return null
            return sourcePos
        }

        // Phase 2: Has items → find next round-robin target container
        val targets = findTargetContainers(world, origin, tag, range)
        if (targets.isEmpty()) return null

        val idx = roundRobinIndex.getOrDefault(entity.pokemon.uuid, 0) % targets.size
        return targets[idx]
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        val pokemonInv = InventoryManager.getOrCreate(entity.pokemon)
        val container = ContainerFinder.getInventoryAt(world, target) ?: return WorkResult.Done()

        // At source → extract items
        if (tag.boundPos != null && target == tag.boundPos) {
            return extractItems(container, pokemonInv, tag, state)
        }

        // At target → deposit items
        depositItems(container, pokemonInv, tag, state)
        container.markDirty()

        // Advance round-robin
        val range = effectiveRange(tag, state)
        val targets = findTargetContainers(world, target, tag, range)
        val currentIdx = roundRobinIndex.getOrDefault(entity.pokemon.uuid, 0)
        roundRobinIndex[entity.pokemon.uuid] = (currentIdx + 1) % maxOf(1, targets.size)

        // If still has items, go to next target
        val stillHasItems = (0 until pokemonInv.size()).any {
            val stack = pokemonInv.getStack(it)
            !stack.isEmpty && FilterMatcher.matches(stack, tag.filter)
        }
        if (stillHasItems && targets.isNotEmpty()) {
            val nextIdx = roundRobinIndex[entity.pokemon.uuid]!!
            return WorkResult.MoveTo(targets[nextIdx % targets.size])
        }

        return WorkResult.Done()
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return ContainerFinder.isContainer(world, target)
    }

    fun cleanup(pokemonId: UUID) {
        roundRobinIndex.remove(pokemonId)
    }

    private fun findTargetContainers(world: World, origin: BlockPos, tag: TagInstance, range: Int): List<BlockPos> {
        val sourcePos = tag.boundPos
        return BlockPos.iterateOutwards(origin, range, range / 2, range)
            .filter { pos ->
                ContainerFinder.isContainer(world, pos) && pos != sourcePos
            }
            .map { it.toImmutable() }
            .toList()
    }

    private fun extractItems(
        source: Inventory, pokemonInv: net.minecraft.inventory.SimpleInventory, tag: TagInstance, state: WorkerState
    ): WorkResult {
        val maxItems = effectiveMaxItems(tag, state)
        var extracted = 0

        for (slot in 0 until source.size()) {
            if (extracted >= maxItems) break
            val stack = source.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val freeSlot = (0 until pokemonInv.size()).firstOrNull { pokemonInv.getStack(it).isEmpty } ?: break
            val toTake = minOf(stack.count, maxItems - extracted)
            pokemonInv.setStack(freeSlot, stack.copyWithCount(toTake))
            stack.decrement(toTake)
            if (stack.isEmpty) source.setStack(slot, ItemStack.EMPTY)
            extracted += toTake
        }
        source.markDirty()
        return WorkResult.Done()  // Items stored directly for Phase 2 distribution
    }

    private fun depositItems(
        target: Inventory, pokemonInv: net.minecraft.inventory.SimpleInventory, tag: TagInstance, state: WorkerState
    ) {
        val maxItems = effectiveMaxItems(tag, state)
        var deposited = 0

        for (slot in 0 until pokemonInv.size()) {
            if (deposited >= maxItems) break
            val stack = pokemonInv.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val toDeposit = minOf(stack.count, maxItems - deposited)
            val depositStack = stack.copyWithCount(toDeposit)
            val remaining = ContainerFinder.insertStack(target, depositStack)
            val actuallyDeposited = toDeposit - remaining.count
            stack.decrement(actuallyDeposited)
            if (stack.isEmpty) pokemonInv.setStack(slot, ItemStack.EMPTY)
            deposited += actuallyDeposited
        }
    }
}

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
import com.cobblepalsworld.tag.TargetStrategy
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

        return selectTarget(world, entity.pokemon.uuid, targets, tag)
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        val pokemonInv = InventoryManager.getOrCreate(entity.pokemon)
        val container = ContainerFinder.getInventoryAt(world, target) ?: return WorkResult.Done()

        // At source → extract items
        if (tag.boundPos != null && target == tag.boundPos) {
            return extractItems(world, container, pokemonInv, tag, state)
        }

        // At target → deposit items
        depositItems(world, container, pokemonInv, tag, state)
        container.markDirty()

        if (tag.settings.terminateAfterSuccess) {
            return WorkResult.Done()
        }

        // Advance round-robin
        val range = effectiveRange(tag, state)
        val targets = findTargetContainers(world, target, tag, range)
        advanceTargetIndex(entity.pokemon.uuid, tag, targets.size)

        // If still has items, go to next target
        val stillHasItems = (0 until pokemonInv.size()).any {
            val stack = pokemonInv.getStack(it)
            !stack.isEmpty && FilterMatcher.matches(stack, tag.filter)
        }
        if (stillHasItems && targets.isNotEmpty()) {
            return WorkResult.MoveTo(selectTarget(world, entity.pokemon.uuid, targets, tag))
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
        val explicitTargets = tag.settings.extraTargets
            .filter { it.dimensionId == world.registryKey.value.toString() }
            .map { it.pos }
            .filter { it != sourcePos && ContainerFinder.isContainer(world, it) }
            .distinct()

        val targets = if (explicitTargets.isNotEmpty()) {
            explicitTargets
        } else {
            ContainerFinder.findAllMatching(world, origin, range, setOfNotNull(sourcePos))
        }

        return when (tag.settings.targetStrategy) {
            TargetStrategy.ROUND_ROBIN -> targets
            TargetStrategy.NEAREST_FIRST -> targets.sortedBy { it.getSquaredDistance(origin) }
            TargetStrategy.FURTHEST_FIRST -> targets.sortedByDescending { it.getSquaredDistance(origin) }
            TargetStrategy.RANDOM -> targets.shuffled()
        }
    }

    private fun selectTarget(world: World, pokemonId: UUID, targets: List<BlockPos>, tag: TagInstance): BlockPos {
        if (tag.settings.targetStrategy == TargetStrategy.RANDOM) {
            return targets[world.random.nextInt(targets.size)]
        }

        val index = roundRobinIndex.getOrDefault(pokemonId, 0) % maxOf(1, targets.size)
        return targets[index]
    }

    private fun advanceTargetIndex(pokemonId: UUID, tag: TagInstance, targetCount: Int) {
        if (targetCount <= 1 || tag.settings.targetStrategy == TargetStrategy.RANDOM) return
        val currentIdx = roundRobinIndex.getOrDefault(pokemonId, 0)
        roundRobinIndex[pokemonId] = (currentIdx + 1) % maxOf(1, targetCount)
    }

    private fun extractItems(
        world: World,
        source: Inventory,
        pokemonInv: PokemonInventory,
        tag: TagInstance,
        state: WorkerState
    ): WorkResult {
        val maxItems = effectiveMaxItems(tag, state)
        var extracted = 0

        for (slot in 0 until source.size()) {
            if (extracted >= maxItems) break
            val stack = source.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val requested = stack.copyWithCount(minOf(stack.count, maxItems - extracted))
            val remainder = pokemonInv.insertStack(requested)
            val inserted = requested.count - remainder.count
            if (inserted <= 0) continue

            stack.decrement(inserted)
            if (stack.isEmpty) source.setStack(slot, ItemStack.EMPTY)
            extracted += inserted
        }
        source.markDirty()
        if (extracted > 0) {
            PastureWorkerManager.markDirtyNow(world)
        }
        return WorkResult.Done()  // Items stored directly for Phase 2 distribution
    }

    private fun depositItems(
        world: World,
        target: Inventory,
        pokemonInv: PokemonInventory,
        tag: TagInstance,
        state: WorkerState
    ) {
        val maxItems = effectiveMaxItems(tag, state)
        var deposited = 0
        var changed = false

        for (slot in 0 until pokemonInv.size()) {
            if (deposited >= maxItems) break
            val stack = pokemonInv.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val toDeposit = minOf(stack.count, maxItems - deposited)
            val depositStack = stack.copyWithCount(toDeposit)
            val remaining = ContainerFinder.insertStack(target, depositStack)
            val actuallyDeposited = toDeposit - remaining.count
            if (actuallyDeposited <= 0) continue

            stack.decrement(actuallyDeposited)
            if (stack.isEmpty) pokemonInv.setStack(slot, ItemStack.EMPTY)
            deposited += actuallyDeposited
            changed = true
        }

        if (changed) {
            PastureWorkerManager.markDirtyNow(world)
        }
    }
}

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
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

object FlingerBehavior : TagBehavior {
    override val tagType = TagType.FLINGER
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range
    override val handlesOwnInventory = true

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val inventory = InventoryManager.get(entity.pokemon.uuid)
        val hasItems = inventory != null && (0 until inventory.size()).any { slot ->
            val stack = inventory.getStack(slot)
            !stack.isEmpty && FilterMatcher.matches(stack, tag.filter)
        }

        return if (hasItems) {
            origin
        } else {
            val exclude = setOfNotNull(tag.boundPos)
            ContainerFinder.findClosestMatching(world, origin, effectiveRange(tag, state), exclude) { _, pos ->
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
            flingItems(world, entity, pokemonInventory, tag)
        }
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean = true

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

    private fun flingItems(world: World, entity: PokemonEntity, pokemonInventory: PokemonInventory, tag: TagInstance): WorkResult {
        for (slot in 0 until pokemonInventory.size()) {
            val stack = pokemonInventory.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val launched = stack.copy()
            val itemEntity = ItemEntity(world, entity.x, entity.y + 0.5, entity.z, launched)
            val direction = if (tag.boundPos != null && tag.boundPos != entity.blockPos) {
                Vec3d(
                    tag.boundPos.x + 0.5 - entity.x,
                    tag.boundPos.y + 0.5 - (entity.y + 0.5),
                    tag.boundPos.z + 0.5 - entity.z
                ).normalize()
            } else {
                entity.rotationVector.normalize()
            }
            itemEntity.setVelocity(direction.multiply(0.9).add(0.0, 0.15, 0.0))
            world.spawnEntity(itemEntity)
            pokemonInventory.setStack(slot, ItemStack.EMPTY)
            PastureWorkerManager.markDirtyNow(world)
            return WorkResult.Done()
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
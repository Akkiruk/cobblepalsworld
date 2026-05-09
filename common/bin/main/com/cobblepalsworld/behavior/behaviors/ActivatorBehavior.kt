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
import net.minecraft.block.*
import net.minecraft.inventory.Inventory
import net.minecraft.item.BoneMealItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Simulates right-clicking blocks. Pulls usable items from a bound/nearby container,
 * then uses them on valid targets (bonemeal crops, till dirt, shear sheep-like blocks, etc.).
 */
object ActivatorBehavior : TagBehavior {
    override val tagType = TagType.ACTIVATOR
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range
    override val handlesOwnInventory = true

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val pokemonInv = InventoryManager.get(entity.pokemon.uuid)
        val hasUsableItems = pokemonInv != null && (0 until pokemonInv.size()).any { slot ->
            val stack = pokemonInv.getStack(slot)
            !stack.isEmpty && isUsableItem(stack)
        }

        val range = effectiveRange(tag, state)

        // Phase 1: If no usable items, find a source container
        if (!hasUsableItems) {
            // Bound tag = exclusive: only use that specific container as source
            val boundPos = tag.boundPos
            if (boundPos != null) {
                return if (ContainerFinder.isContainer(world, boundPos)
                    && containerHasUsableItems(world, boundPos, tag)
                ) boundPos else null
            }

            return BlockPos.iterateOutwards(origin, range, range / 2, range)
                .firstOrNull { pos ->
                    ContainerFinder.isContainer(world, pos) && containerHasUsableItems(world, pos, tag)
                }?.toImmutable()
        }

        // Phase 2: Find a block to use the item on
        return BlockPos.iterateOutwards(origin, range, range / 2, range)
            .firstOrNull { pos ->
                canActivate(world, pos, pokemonInv!!)
                    && !ClaimManager.isClaimedByOther(pos, entity.pokemon.uuid, world)
            }?.toImmutable()
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        // At a container → extract usable items
        if (ContainerFinder.isContainer(world, target)) {
            return extractUsableItems(world, target, entity, tag, state)
        }

        // At a target block → use item
        val pokemonInv = InventoryManager.get(entity.pokemon.uuid) ?: return WorkResult.Done()
        val serverWorld = world as? ServerWorld ?: return WorkResult.Done()

        for (slot in 0 until pokemonInv.size()) {
            val stack = pokemonInv.getStack(slot)
            if (stack.isEmpty) continue

            if (tryActivate(serverWorld, target, stack)) {
                if (stack.isEmpty) pokemonInv.setStack(slot, ItemStack.EMPTY)
                return WorkResult.Done()
            }
        }

        return WorkResult.Done()
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        if (ContainerFinder.isContainer(world, target)) return true
        val blockState = world.getBlockState(target)
        return !blockState.isAir
    }

    private fun isUsableItem(stack: ItemStack): Boolean {
        return stack.item == Items.BONE_MEAL
            || stack.item is net.minecraft.item.HoeItem
    }

    private fun canActivate(world: World, pos: BlockPos, inventory: net.minecraft.inventory.SimpleInventory): Boolean {
        val blockState = world.getBlockState(pos)
        for (slot in 0 until inventory.size()) {
            val stack = inventory.getStack(slot)
            if (stack.isEmpty) continue

            // Bone meal on fertilizable blocks
            if (stack.item == Items.BONE_MEAL && blockState.block is Fertilizable) {
                val fertilizable = blockState.block as Fertilizable
                if (fertilizable.isFertilizable(world, pos, blockState)) return true
            }

            // Hoe on dirt/grass → farmland
            if (stack.item is net.minecraft.item.HoeItem) {
                if (blockState.isOf(Blocks.DIRT) || blockState.isOf(Blocks.GRASS_BLOCK)
                    || blockState.isOf(Blocks.DIRT_PATH)
                ) return true
            }
        }
        return false
    }

    private fun tryActivate(world: ServerWorld, pos: BlockPos, stack: ItemStack): Boolean {
        val blockState = world.getBlockState(pos)

        // Bone meal
        if (stack.item == Items.BONE_MEAL && blockState.block is Fertilizable) {
            val fertilizable = blockState.block as Fertilizable
            if (fertilizable.isFertilizable(world, pos, blockState)) {
                if (fertilizable.canGrow(world, world.random, pos, blockState)) {
                    fertilizable.grow(world, world.random, pos, blockState)
                    world.syncWorldEvent(net.minecraft.world.WorldEvents.BONE_MEAL_USED, pos, 0)
                    stack.decrement(1)
                    return true
                }
            }
        }

        // Hoe → farmland
        if (stack.item is net.minecraft.item.HoeItem) {
            if (blockState.isOf(Blocks.DIRT) || blockState.isOf(Blocks.GRASS_BLOCK)
                || blockState.isOf(Blocks.DIRT_PATH)
            ) {
                world.setBlockState(pos, Blocks.FARMLAND.defaultState)
                world.playSound(null, pos, net.minecraft.sound.SoundEvents.ITEM_HOE_TILL, net.minecraft.sound.SoundCategory.BLOCKS)
                return true // Hoes don't consume
            }
        }

        return false
    }

    private fun extractUsableItems(
        world: World, target: BlockPos, entity: PokemonEntity, tag: TagInstance, state: WorkerState
    ): WorkResult {
        val container = ContainerFinder.getInventoryAt(world, target) ?: return WorkResult.Done()
        val pokemonInv = InventoryManager.getOrCreate(entity.pokemon)
        val maxItems = effectiveMaxItems(tag, state)
        var extracted = 0

        for (slot in 0 until container.size()) {
            if (extracted >= maxItems) break
            val stack = container.getStack(slot)
            if (stack.isEmpty || !isUsableItem(stack)) continue
            if (!FilterMatcher.matches(stack, tag.filter)) continue

            val freeSlot = (0 until pokemonInv.size()).firstOrNull { pokemonInv.getStack(it).isEmpty } ?: break
            val toTake = minOf(stack.count, maxItems - extracted)
            pokemonInv.setStack(freeSlot, stack.copyWithCount(toTake))
            stack.decrement(toTake)
            if (stack.isEmpty) container.setStack(slot, ItemStack.EMPTY)
            extracted += toTake
        }
        container.markDirty()
        return WorkResult.Done()
    }

    private fun containerHasUsableItems(world: World, pos: BlockPos, tag: TagInstance): Boolean {
        val container = ContainerFinder.getInventoryAt(world, pos) ?: return false
        for (slot in 0 until container.size()) {
            val stack = container.getStack(slot)
            if (!stack.isEmpty && isUsableItem(stack) && FilterMatcher.matches(stack, tag.filter)) return true
        }
        return false
    }
}

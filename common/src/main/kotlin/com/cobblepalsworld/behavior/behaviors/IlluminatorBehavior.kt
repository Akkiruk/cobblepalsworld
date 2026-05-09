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
import net.minecraft.block.Block
import net.minecraft.block.TorchBlock
import net.minecraft.block.WallTorchBlock
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraft.world.LightType
import net.minecraft.world.World

/**
 * Lights up dark areas by placing torches from containers.
 * Phase 1: Extract torches from a nearby container.
 * Phase 2: Find a dark spot (block light < 7) and place a torch.
 *
 * Bound → source container for torches.
 */
object IlluminatorBehavior : TagBehavior {
    override val tagType = TagType.ILLUMINATOR
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range
    override val handlesOwnInventory = true

    private const val DARKNESS_THRESHOLD = 7

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val pokemonInv = InventoryManager.get(entity.pokemon.uuid)
        val hasTorches = pokemonInv != null && (0 until pokemonInv.size()).any {
            isTorchItem(pokemonInv.getStack(it))
        }

        val range = effectiveRange(tag, state)
        val pokemonId = entity.pokemon.uuid

        if (hasTorches) {
            // Phase 2: find dark spot to place torch
            return findDarkSpot(world, origin, pokemonId, range)
        }

        // Phase 1: find container with torches
        return findTorchContainer(world, origin, tag, range)
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        if (world !is ServerWorld) return WorkResult.Done()
        val pokemonInv = InventoryManager.getOrCreate(entity.pokemon)

        // At container → extract torches
        if (ContainerFinder.isContainer(world, target)) {
            return extractTorches(world, target, pokemonInv, tag, state)
        }

        // At dark spot → place torch
        if (world.getBlockState(target).isReplaceable && isDark(world, target)) {
            return placeTorch(world, target, pokemonInv)
        }

        return WorkResult.Done()
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        if (ContainerFinder.isContainer(world, target)) return true
        return world.getBlockState(target).isReplaceable && isDark(world, target)
    }

    private fun findDarkSpot(world: World, origin: BlockPos, pokemonId: java.util.UUID, range: Int): BlockPos? {
        return BlockPos.iterateOutwards(origin, range, range / 2, range)
            .firstOrNull { pos ->
                isDark(world, pos)
                    && world.getBlockState(pos).isReplaceable
                    && hasAdjacentSolid(world, pos)
                    && !ClaimManager.isClaimedByOther(pos, pokemonId, world)
            }?.toImmutable()
    }

    private fun findTorchContainer(world: World, origin: BlockPos, tag: TagInstance, range: Int): BlockPos? {
        val boundPos = tag.boundPos
        if (boundPos != null) {
            return if (ContainerFinder.isContainer(world, boundPos) && containerHasTorches(world, boundPos))
                boundPos else null
        }
        return ContainerFinder.findClosestMatching(world, origin, range) { _, pos ->
            containerHasTorches(world, pos)
        }
    }

    private fun extractTorches(
        world: World, target: BlockPos,
        pokemonInv: PokemonInventory,
        tag: TagInstance,
        state: WorkerState
    ): WorkResult {
        val container = ContainerFinder.getInventoryAt(world, target) ?: return WorkResult.Done()
        var extracted = 0
        val maxCarry = minOf(16, effectiveMaxItems(tag, state))

        for (slot in 0 until container.size()) {
            if (extracted >= maxCarry) break
            val stack = container.getStack(slot)
            if (stack.isEmpty || !isTorchItem(stack)) continue

            val requested = stack.copyWithCount(minOf(stack.count, maxCarry - extracted))
            val remainder = pokemonInv.insertStack(requested)
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

    private fun placeTorch(
        world: World, target: BlockPos,
        pokemonInv: net.minecraft.inventory.SimpleInventory
    ): WorkResult {
        for (slot in 0 until pokemonInv.size()) {
            val stack = pokemonInv.getStack(slot)
            if (stack.isEmpty || !isTorchItem(stack)) continue

            val blockItem = stack.item as? BlockItem ?: continue
            val blockState = blockItem.block.defaultState
            if (blockState.canPlaceAt(world, target)) {
                world.setBlockState(target, blockState, Block.NOTIFY_ALL)
                if (world is ServerWorld) {
                    val soundGroup = blockState.soundGroup
                    world.playSound(
                        null, target, soundGroup.placeSound,
                        SoundCategory.BLOCKS, soundGroup.volume * 0.5f, soundGroup.pitch
                    )
                }
                stack.decrement(1)
                if (stack.isEmpty) pokemonInv.setStack(slot, ItemStack.EMPTY)
                PastureWorkerManager.markDirtyNow(world)
                return WorkResult.Done()
            }
        }
        return WorkResult.Done()
    }

    private fun isDark(world: World, pos: BlockPos): Boolean {
        return world.getLightLevel(LightType.BLOCK, pos) < DARKNESS_THRESHOLD
    }

    private fun isTorchItem(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val item = stack.item
        return item == Items.TORCH || item == Items.SOUL_TORCH
    }

    private fun containerHasTorches(world: World, pos: BlockPos): Boolean {
        val container = ContainerFinder.getInventoryAt(world, pos) ?: return false
        for (slot in 0 until container.size()) {
            if (isTorchItem(container.getStack(slot))) return true
        }
        return false
    }

    private fun hasAdjacentSolid(world: World, pos: BlockPos): Boolean {
        return net.minecraft.util.math.Direction.values().any { dir ->
            val adjacent = pos.offset(dir)
            world.getBlockState(adjacent).isSolidBlock(world, adjacent)
        }
    }
}

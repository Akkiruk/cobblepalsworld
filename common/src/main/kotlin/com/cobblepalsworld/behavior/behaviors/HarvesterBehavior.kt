package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import net.minecraft.block.*
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Harvests mature crops and replants them automatically.
 * Handles: wheat, carrots, potatoes, beetroot, nether wart,
 * sweet berry bushes, cocoa beans, melons, pumpkins, sugar cane (above base).
 */
object HarvesterBehavior : TagBehavior {
    override val tagType = TagType.HARVESTER
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val range = effectiveRange(tag, state)
        val pokemonId = entity.pokemon.uuid
        return BlockPos.iterateOutwards(origin, range, range / 2, range)
            .firstOrNull { pos ->
                isMatureCrop(world, pos) && !ClaimManager.isClaimedByOther(pos, pokemonId, world)
            }?.toImmutable()
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        if (world !is ServerWorld) return WorkResult.Done()
        val blockState = world.getBlockState(target)
        val block = blockState.block

        val drops = mutableListOf<ItemStack>()

        when {
            // Sweet berry bush: harvest berries, reset to age 1
            block is SweetBerryBushBlock -> {
                val age = blockState.get(SweetBerryBushBlock.AGE)
                val berryCount = if (age == 3) 2 + world.random.nextInt(2) else 1 + world.random.nextInt(2)
                drops.add(ItemStack(Items.SWEET_BERRIES, berryCount))
                world.setBlockState(target, blockState.with(SweetBerryBushBlock.AGE, 1), Block.NOTIFY_ALL)
            }
            // Sugar cane: only break above the base block (leave bottom intact)
            block is SugarCaneBlock -> {
                val below = world.getBlockState(target.down())
                if (below.block is SugarCaneBlock) {
                    drops.addAll(Block.getDroppedStacks(blockState, world, target, null))
                    world.breakBlock(target, false)
                }
            }
            // Melon/pumpkin: break the fruit block (stem stays)
            blockState.isOf(Blocks.MELON) || blockState.isOf(Blocks.PUMPKIN) -> {
                drops.addAll(Block.getDroppedStacks(blockState, world, target, null))
                world.breakBlock(target, false)
            }
            // Cocoa beans
            block is CocoaBlock -> {
                drops.addAll(Block.getDroppedStacks(blockState, world, target, null))
                world.setBlockState(target, blockState.with(CocoaBlock.AGE, 0), Block.NOTIFY_ALL)
            }
            // Standard crops (wheat, carrot, potato, beetroot) + nether wart
            block is CropBlock -> {
                drops.addAll(Block.getDroppedStacks(blockState, world, target, null))
                // Replant: set back to age 0
                world.setBlockState(target, block.defaultState, Block.NOTIFY_ALL)
            }
            block is NetherWartBlock -> {
                drops.addAll(Block.getDroppedStacks(blockState, world, target, null))
                world.setBlockState(target, block.defaultState, Block.NOTIFY_ALL)
            }
        }

        // Remove one seed from drops (it was replanted)
        if (block is CropBlock || block is NetherWartBlock) {
            removeSeedFromDrops(drops, block)
        }

        return WorkResult.Done(drops)
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return isMatureCrop(world, target)
    }

    private fun isMatureCrop(world: World, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        val block = state.block
        return when {
            block is CropBlock -> block.isMature(state)
            block is NetherWartBlock -> state.get(NetherWartBlock.AGE) >= 3
            block is SweetBerryBushBlock -> state.get(SweetBerryBushBlock.AGE) >= 2
            block is CocoaBlock -> state.get(CocoaBlock.AGE) >= 2
            state.isOf(Blocks.MELON) -> true
            state.isOf(Blocks.PUMPKIN) && !state.isOf(Blocks.CARVED_PUMPKIN) -> true
            block is SugarCaneBlock -> {
                // Only harvest if there's sugar cane below (don't uproot the base)
                world.getBlockState(pos.down()).block is SugarCaneBlock
            }
            else -> false
        }
    }

    private fun removeSeedFromDrops(drops: MutableList<ItemStack>, block: Block) {
        val seedItem = when (block) {
            is CropBlock -> block.getPickStack(null, null, block.defaultState)?.item
            is NetherWartBlock -> Items.NETHER_WART
            else -> null
        } ?: return

        for (i in drops.indices) {
            if (drops[i].item == seedItem && drops[i].count > 0) {
                drops[i].decrement(1)
                if (drops[i].isEmpty) drops[i] = ItemStack.EMPTY
                break
            }
        }
        drops.removeAll { it.isEmpty }
    }
}

package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Water-type Pokémon sits by water and periodically catches fish.
 * Finds a water block, navigates to it, and produces fish + occasional loot.
 */
object FisherBehavior : TagBehavior {
    override val tagType = TagType.FISHER
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val range = effectiveRange(tag, state)
        val pokemonId = entity.pokemon.uuid
        // Find the nearest water-adjacent block (solid block next to water for standing)
        return BlockPos.iterateOutwards(origin, range, range, range)
            .firstOrNull { pos ->
                isWater(world, pos) && !ClaimManager.isClaimedByOther(pos, pokemonId, world)
            }?.toImmutable()
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        if (world !is ServerWorld) return WorkResult.Done()

        val catches = mutableListOf<ItemStack>()
        val random = world.random

        // Main catch: weighted fish selection
        val roll = random.nextFloat()
        val fish = when {
            roll < 0.50f -> ItemStack(Items.COD)
            roll < 0.80f -> ItemStack(Items.SALMON)
            roll < 0.90f -> ItemStack(Items.TROPICAL_FISH)
            else -> ItemStack(Items.PUFFERFISH)
        }
        catches.add(fish)

        // ~15% chance of bonus treasure
        if (random.nextFloat() < 0.15f) {
            val treasure = when (random.nextInt(6)) {
                0 -> ItemStack(Items.LILY_PAD)
                1 -> ItemStack(Items.BOWL)
                2 -> ItemStack(Items.INK_SAC)
                3 -> ItemStack(Items.BONE)
                4 -> ItemStack(Items.STRING)
                else -> ItemStack(Items.PRISMARINE_SHARD)
            }
            catches.add(treasure)
        }

        return WorkResult.Done(catches)
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return isWater(world, target)
    }

    private fun isWater(world: World, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return state.isOf(Blocks.WATER)
    }
}

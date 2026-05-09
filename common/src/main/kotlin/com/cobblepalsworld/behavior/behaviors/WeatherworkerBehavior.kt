package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import net.minecraft.block.CropBlock
import net.minecraft.block.NetherWartBlock
import net.minecraft.block.SweetBerryBushBlock
import net.minecraft.block.CocoaBlock
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Boosts crop growth in the surrounding area by periodically applying
 * random ticks to nearby crops. Simulates the effect of favorable weather
 * on farmland. Unlike Activator+bonemeal, this doesn't consume resources —
 * just a slow passive growth acceleration.
 *
 * Each work cycle, randomly selects 2-4 crops in range and applies
 * a random tick to each (same effect as Minecraft's random tick mechanic).
 */
object WeatherworkerBehavior : TagBehavior {
    override val tagType = TagType.WEATHERWORKER
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val range = effectiveRange(tag, state)
        // Find any growing crop in range
        return BlockPos.iterateOutwards(origin, range, range, range)
            .firstOrNull { pos -> isGrowingCrop(world, pos) }
            ?.toImmutable()
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        if (world !is ServerWorld) return WorkResult.Done()

        val range = effectiveRange(tag, state)
        val origin = target
        val random = world.random

        // Collect all growing crops in range
        val crops = BlockPos.iterateOutwards(origin, range, range, range)
            .filter { pos -> isGrowingCrop(world, pos) }
            .map { it.toImmutable() }
            .toList()

        if (crops.isEmpty()) return WorkResult.Done()

        // Apply random ticks to 2-4 random crops
        val tickCount = 2 + random.nextInt(3)
        val shuffled = crops.toMutableList()
        for (i in shuffled.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = shuffled[i]; shuffled[i] = shuffled[j]; shuffled[j] = tmp
        }
        val selected = shuffled.take(tickCount)

        for (pos in selected) {
            val blockState = world.getBlockState(pos)
            blockState.randomTick(world, pos, random)
        }

        return WorkResult.Repeat
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        // Valid as long as there's at least one growing crop nearby
        val range = 3
        return BlockPos.iterateOutwards(target, range, range, range)
            .any { pos -> isGrowingCrop(world, pos) }
    }

    private fun isGrowingCrop(world: World, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        val block = state.block
        return when {
            block is CropBlock -> !block.isMature(state)
            block is NetherWartBlock -> state.get(NetherWartBlock.AGE) < 3
            block is SweetBerryBushBlock -> state.get(SweetBerryBushBlock.AGE) < 3
            block is CocoaBlock -> state.get(CocoaBlock.AGE) < 2
            else -> false
        }
    }
}

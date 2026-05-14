package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.WorkTargetCache
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.navigation.SafePositionResolver
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.tag.FluidTags
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World

object FisherBehavior : TagBehavior {
    override val tagType = TagType.FISHER
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range

    private val horizontalDirections = listOf(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)

    override fun idleRetryTicks(tag: TagInstance, state: WorkerState): Long = 60L
    override fun arrivalTolerance(tag: TagInstance, state: WorkerState): Double = 1.8
    override fun cooldownTicks(tag: TagInstance, state: WorkerState): Long = super.cooldownTicks(tag, state).coerceAtLeast(80L)

    override fun findTarget(world: World, origin: BlockPos, entity: PokemonEntity, tag: TagInstance, state: WorkerState): BlockPos? {
        val range = effectiveRange(tag, state)
        val anchor = tag.boundPos ?: origin
        return WorkTargetCache.getOrFind(world, "fisher", anchor, range) {
            findShorelineStand(world, anchor, range)
        }
    }

    override fun doWork(world: World, entity: PokemonEntity, target: BlockPos, tag: TagInstance, state: WorkerState): WorkResult {
        state.setStatus(WorkerStatusReason.WORKING, "Fishing from a safe shoreline spot")
        val serverWorld = world as? ServerWorld
        val water = adjacentWater(world, target)
        if (serverWorld != null && water != null) {
            serverWorld.spawnParticles(ParticleTypes.SPLASH, water.x + 0.5, water.y + 0.9, water.z + 0.5, 8, 0.25, 0.05, 0.25, 0.02)
            serverWorld.playSound(null, water, SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.NEUTRAL, 0.7f, 1.0f)
        }
        return WorkResult.Done(listOf(randomCatch(world)))
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return SafePositionResolver.isSafeStandPosition(world, target) && adjacentWater(world, target) != null
    }

    private fun findShorelineStand(world: World, anchor: BlockPos, range: Int): BlockPos? {
        for (waterPos in BlockPos.iterateOutwards(anchor, range, 6, range)) {
            if (!isWater(world, waterPos)) continue
            horizontalDirections.forEach { direction ->
                val standPos = waterPos.offset(direction).toImmutable()
                if (SafePositionResolver.isSafeStandPosition(world, standPos)) return standPos
            }
        }
        return null
    }

    private fun adjacentWater(world: World, standPos: BlockPos): BlockPos? {
        return horizontalDirections
            .map { standPos.offset(it).toImmutable() }
            .firstOrNull { isWater(world, it) }
    }

    private fun isWater(world: World, pos: BlockPos): Boolean {
        return world.getFluidState(pos).isIn(FluidTags.WATER)
    }

    private fun randomCatch(world: World): ItemStack {
        return when (world.random.nextInt(100)) {
            in 0..69 -> ItemStack(Items.COD)
            in 70..89 -> ItemStack(Items.SALMON)
            in 90..96 -> ItemStack(Items.PUFFERFISH)
            else -> ItemStack(Items.TROPICAL_FISH)
        }
    }
}
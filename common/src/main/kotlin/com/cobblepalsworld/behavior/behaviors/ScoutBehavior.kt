package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.WorkTargetCache
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import net.minecraft.block.Blocks
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.Registries
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object ScoutBehavior : TagBehavior {
    override val tagType = TagType.SCOUT
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range

    override fun idleRetryTicks(tag: TagInstance, state: WorkerState): Long = 80L
    override fun arrivalTolerance(tag: TagInstance, state: WorkerState): Double = 2.5
    override fun cooldownTicks(tag: TagInstance, state: WorkerState): Long = super.cooldownTicks(tag, state).coerceAtLeast(80L)

    override fun findTarget(world: World, origin: BlockPos, entity: PokemonEntity, tag: TagInstance, state: WorkerState): BlockPos? {
        val range = effectiveRange(tag, state)
        return WorkTargetCache.getOrFind(world, "scout", origin, range) {
            findDiscovery(world, origin, range)
        }
    }

    override fun doWork(world: World, entity: PokemonEntity, target: BlockPos, tag: TagInstance, state: WorkerState): WorkResult {
        val label = discoveryLabel(world, target)
        state.setStatus(WorkerStatusReason.WORKING, "Marked $label at ${target.x}, ${target.y}, ${target.z}")
        val serverWorld = world as? ServerWorld
        serverWorld?.spawnParticles(ParticleTypes.HAPPY_VILLAGER, target.x + 0.5, target.y + 1.1, target.z + 0.5, 12, 0.35, 0.45, 0.35, 0.02)
        serverWorld?.playSound(null, target, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 0.6f, 1.25f)
        return WorkResult.Done()
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return isInteresting(world, target)
    }

    private fun findDiscovery(world: World, origin: BlockPos, range: Int): BlockPos? {
        var best: BlockPos? = null
        var bestScore = Int.MIN_VALUE
        for (pos in BlockPos.iterateOutwards(origin, range, range.coerceAtMost(12), range)) {
            val immutable = pos.toImmutable()
            val score = discoveryScore(world, immutable)
            if (score > bestScore) {
                bestScore = score
                best = immutable
            }
        }
        return best.takeIf { bestScore > 0 }
    }

    private fun isInteresting(world: World, pos: BlockPos): Boolean = discoveryScore(world, pos) > 0

    private fun discoveryScore(world: World, pos: BlockPos): Int {
        if (ContainerFinder.isContainer(world, pos)) return 80
        val state = world.getBlockState(pos)
        if (state.isAir) return 0
        val block = state.block
        val id = Registries.BLOCK.getId(block).path
        return when {
            block == Blocks.SPAWNER -> 120
            id.contains("ancient_debris") -> 110
            id.contains("ore") -> 90
            id.contains("amethyst") -> 70
            id.contains("chest") || id.contains("barrel") -> 80
            else -> 0
        }
    }

    private fun discoveryLabel(world: World, pos: BlockPos): String {
        if (ContainerFinder.isContainer(world, pos)) return "container"
        return Registries.BLOCK.getId(world.getBlockState(pos).block).path.replace('_', ' ')
    }
}
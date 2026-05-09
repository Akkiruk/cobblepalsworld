package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import net.minecraft.block.Blocks
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Explores nearby underground areas and highlights ores and points of interest
 * with persistent particle effects. The Scout Pokémon uses its keen senses
 * to detect valuable blocks hidden in the walls.
 *
 * Scans a random section of the search area each cycle, spawning particles
 * at ore/spawner locations so the player can see them.
 */
object ScoutBehavior : TagBehavior {
    override val tagType = TagType.SCOUT
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range

    // Blocks considered "interesting" for scouting
    private val INTERESTING_BLOCKS = setOf(
        Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
        Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
        Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
        Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
        Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE,
        Blocks.ANCIENT_DEBRIS,
        Blocks.SPAWNER,
        Blocks.CHEST, Blocks.BARREL
    )

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val range = effectiveRange(tag, state)
        // Pick a random offset to scan (doesn't always scan the same spot)
        val random = world.random
        val scanX = origin.x + random.nextBetween(-range, range)
        val scanY = origin.y + random.nextBetween(-range / 2, range / 2)
        val scanZ = origin.z + random.nextBetween(-range, range)
        return BlockPos(scanX, scanY, scanZ)
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        if (world !is ServerWorld) return WorkResult.Done()

        // Scan a 5x5x5 area around the target position
        val scanRange = 2
        var found = 0

        for (x in -scanRange..scanRange) {
            for (y in -scanRange..scanRange) {
                for (z in -scanRange..scanRange) {
                    val pos = target.add(x, y, z)
                    val block = world.getBlockState(pos).block

                    if (block in INTERESTING_BLOCKS) {
                        // Spawn highlight particles at the ore location
                        val particle = when (block) {
                            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE
                                -> ParticleTypes.END_ROD
                            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE
                                -> ParticleTypes.HAPPY_VILLAGER
                            Blocks.ANCIENT_DEBRIS -> ParticleTypes.SOUL_FIRE_FLAME
                            Blocks.SPAWNER -> ParticleTypes.WITCH
                            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE -> ParticleTypes.DUST_PLUME
                            else -> ParticleTypes.END_ROD
                        }

                        world.spawnParticles(
                            particle,
                            pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
                            3, 0.2, 0.2, 0.2, 0.01
                        )
                        found++
                    }
                }
            }
        }

        // Scout always repeats — it's a continuous scanner
        return WorkResult.Repeat
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return true // Scout targets are random scan areas, always valid
    }
}

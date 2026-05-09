package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World

/**
 * Attacks hostile mobs within range. Damage scales with Pokémon level.
 * The Pokémon navigates to the mob's position and strikes on arrival.
 * Mob drops fall naturally for a Vacuum Pokémon to collect.
 */
object GuardianBehavior : TagBehavior {
    override val tagType = TagType.GUARDIAN
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val range = effectiveRange(tag, state).toDouble()
        val box = Box(origin).expand(range)
        val hostile = world.getEntitiesByClass(HostileEntity::class.java, box) { it.isAlive }
            .minByOrNull { it.squaredDistanceTo(entity) }
            ?: return null
        return hostile.blockPos
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        if (world !is ServerWorld) return WorkResult.Done()

        val range = 3.0
        val box = Box(target).expand(range)
        val hostile = world.getEntitiesByClass(HostileEntity::class.java, box) { it.isAlive }
            .minByOrNull { it.squaredDistanceTo(entity) }
            ?: return WorkResult.Done()

        // Damage scales with Pokémon level: base 4 + level/5
        val level = entity.pokemon.level
        val damage = 4.0f + (level / 5.0f)
        hostile.damage(world.damageSources.mobAttack(entity), damage)

        world.playSound(
            null, entity.x, entity.y, entity.z,
            SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, entity.soundCategory,
            1.0f, 1.0f
        )

        return WorkResult.Done()
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        val box = Box(target).expand(3.0)
        return world.getEntitiesByClass(HostileEntity::class.java, box) { it.isAlive }.isNotEmpty()
    }
}

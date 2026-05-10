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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Attacks hostile mobs within range. Damage scales with Pokémon level.
 * The Pokémon navigates to the mob's position and strikes on arrival.
 * Mob drops fall naturally for a Vacuum Pokémon to collect.
 */
object GuardianBehavior : TagBehavior {
    override val tagType = TagType.GUARDIAN
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range
    override fun arrivalDelayTicks(tag: TagInstance, state: WorkerState): Long = 0L
    override fun arrivalTolerance(tag: TagInstance, state: WorkerState): Double = ATTACK_RANGE
    override fun cooldownTicks(tag: TagInstance, state: WorkerState): Long = 10L
    override fun idleRetryTicks(tag: TagInstance, state: WorkerState): Long = 20L

    private const val ATTACK_RANGE = 2.75
    private const val TARGET_LEASH = 6.0
    private val trackedTargets = ConcurrentHashMap<UUID, UUID>()

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val range = effectiveRange(tag, state).toDouble()
        resolveTrackedTarget(world, entity.pokemon.uuid, origin, range)?.let { return it.blockPos.toImmutable() }

        val hostile = findNearestHostile(world, origin, range) ?: return null
        trackedTargets[entity.pokemon.uuid] = hostile.uuid
        return hostile.blockPos.toImmutable()
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        val serverWorld = world as? ServerWorld ?: return WorkResult.Done()

        val hostile = resolveTrackedTarget(serverWorld, entity.pokemon.uuid, entity.blockPos, effectiveRange(tag, state).toDouble())
            ?: findNearestHostile(serverWorld, target, TARGET_LEASH)
            ?: run {
                trackedTargets.remove(entity.pokemon.uuid)
                return WorkResult.Done()
            }

        trackedTargets[entity.pokemon.uuid] = hostile.uuid
        if (hostile.squaredDistanceTo(entity) > ATTACK_RANGE * ATTACK_RANGE) {
            return WorkResult.MoveTo(hostile.blockPos.toImmutable())
        }

        // Damage scales with Pokémon level: base 4 + level/5
        val level = entity.pokemon.level
        val damage = 4.0f + (level / 5.0f)
        hostile.damage(serverWorld.damageSources.mobAttack(entity), damage)

        serverWorld.playSound(
            null, entity.x, entity.y, entity.z,
            SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, entity.soundCategory,
            1.0f, 1.0f
        )

        if (!hostile.isAlive) {
            trackedTargets.remove(entity.pokemon.uuid)
        }

        return WorkResult.Done()
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        val box = Box(target).expand(TARGET_LEASH)
        return world.getEntitiesByClass(HostileEntity::class.java, box) { it.isAlive }.isNotEmpty()
    }

    fun cleanup(pokemonId: UUID) {
        trackedTargets.remove(pokemonId)
    }

    fun clearAllRuntimeState() {
        trackedTargets.clear()
    }

    private fun resolveTrackedTarget(world: World, pokemonId: UUID, center: BlockPos, range: Double): HostileEntity? {
        val serverWorld = world as? ServerWorld ?: return null
        val trackedId = trackedTargets[pokemonId] ?: return null
        val hostile = serverWorld.getEntity(trackedId) as? HostileEntity ?: run {
            trackedTargets.remove(pokemonId)
            return null
        }
        if (!hostile.isAlive || hostile.squaredDistanceTo(center.x + 0.5, center.y + 0.5, center.z + 0.5) > range * range) {
            trackedTargets.remove(pokemonId)
            return null
        }
        return hostile
    }

    private fun findNearestHostile(world: World, center: BlockPos, range: Double): HostileEntity? {
        val box = Box(center).expand(range)
        var nearest: HostileEntity? = null
        var nearestDistance = Double.MAX_VALUE
        for (hostile in world.getEntitiesByClass(HostileEntity::class.java, box) { it.isAlive }) {
            val distance = hostile.squaredDistanceTo(center.x + 0.5, center.y + 0.5, center.z + 0.5)
            if (distance < nearestDistance) {
                nearest = hostile
                nearestDistance = distance
            }
        }
        return nearest
    }
}

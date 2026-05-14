package com.cobblepalsworld.visual

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.tag.TagType
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object WorkVisualHandler {

    fun onArrival(world: World, entity: PokemonEntity, target: BlockPos, tagType: TagType) {
        lookAt(entity, target)
        spawnParticles(world, target, ParticleConfig.getArrivalParticle(tagType), 4)
    }

    fun onWorkComplete(world: World, entity: PokemonEntity, target: BlockPos, tagType: TagType) {
        spawnParticles(world, target, ParticleConfig.getWorkParticle(tagType), 6)
        spawnParticlesAtEntity(world, entity, completionAccentParticle(tagType), 4)
        world.playSound(null, entity.blockPos, tagType.workSound, SoundCategory.BLOCKS, 0.5f, 1.0f + (world.random.nextFloat() - 0.5f) * 0.2f)
    }

    fun onDeposit(world: World, entity: PokemonEntity, containerPos: BlockPos) {
        spawnParticles(world, containerPos, ParticleConfig.getDepositParticle(), 4)
        world.playSound(null, containerPos, SoundEvents.BLOCK_BARREL_OPEN, SoundCategory.BLOCKS, 0.4f, 1.2f)
    }

    fun onWorking(world: World, entity: PokemonEntity, tagType: TagType) {
        spawnParticlesAtEntity(world, entity, ParticleConfig.getWorkParticle(tagType), 2)
    }

    fun onBlocked(world: World, entity: PokemonEntity, reason: WorkerStatusReason) {
        val particle = when (reason) {
            WorkerStatusReason.REDSTONE_OFF -> ParticleTypes.ELECTRIC_SPARK
            WorkerStatusReason.NO_DEPOSIT -> ParticleTypes.ANGRY_VILLAGER
            WorkerStatusReason.MOVEMENT_RECOVERY -> ParticleTypes.CLOUD
            WorkerStatusReason.PATHING_STALLED -> ParticleTypes.SMOKE
            WorkerStatusReason.TAG_DISABLED -> ParticleTypes.LARGE_SMOKE
            else -> ParticleTypes.SMOKE
        }
        spawnParticlesAtEntity(world, entity, particle, 4)
        world.playSound(null, entity.blockPos, SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.BLOCKS, 0.2f, 0.7f)
    }

    fun lookAt(entity: PokemonEntity, pos: BlockPos) {
        entity.lookControl.lookAt(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
    }

    private fun spawnParticles(world: World, pos: BlockPos, particle: ParticleEffect, count: Int) {
        val sw = world as? ServerWorld ?: return
        sw.spawnParticles(
            particle,
            pos.x + 0.5, pos.y + 1.0, pos.z + 0.5,
            count,
            0.3, 0.3, 0.3,
            0.02
        )
    }

    private fun spawnParticlesAtEntity(world: World, entity: PokemonEntity, particle: ParticleEffect, count: Int) {
        val sw = world as? ServerWorld ?: return
        sw.spawnParticles(
            particle,
            entity.x, entity.y + entity.height + 0.3, entity.z,
            count,
            0.2, 0.1, 0.2,
            0.01
        )
    }

    private fun completionAccentParticle(tagType: TagType): ParticleEffect = when (tagType) {
        TagType.BREAKER, TagType.GUARDIAN -> ParticleTypes.CRIT
        TagType.HARVESTER, TagType.SHEPHERD -> ParticleTypes.HAPPY_VILLAGER
        TagType.VACUUM, TagType.COURIER, TagType.PULLER, TagType.STASHER, TagType.DROPPER, TagType.VOID -> ParticleTypes.ENCHANT
        TagType.ACTIVATOR -> ParticleTypes.WAX_ON
    }
}

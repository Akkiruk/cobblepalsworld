package com.cobblepalsworld.visual

import com.cobblepalsworld.tag.TagType
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes

object ParticleConfig {
    fun getArrivalParticle(type: TagType): ParticleEffect = type.arrivalParticle
    fun getWorkParticle(type: TagType): ParticleEffect = type.workParticle
    fun getDepositParticle(): ParticleEffect = ParticleTypes.HAPPY_VILLAGER
}

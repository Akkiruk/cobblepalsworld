package com.cobblepalsworld.tag

import net.minecraft.particle.ParticleEffect
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Formatting

/**
 * Stable keys for the built-in tag roles.
 *
 * The enum holds nothing but the persistent id; everything a role does —
 * capabilities, presentation, feedback — lives in its [TagTypeDefinition],
 * resolved through [TagTypeDefinitions]. To add a new role:
 * 1. Add an entry here (the persistent id)
 * 2. Register a TagTypeDefinition in TagTypeDefinitions
 * 3. Create a TagBehavior implementation and register it in CobblePalsWorld.registerBehaviors()
 * 4. Add recipe JSON + lang key + item model
 * Existing roles can be reconfigured at runtime via TagTypeDefinitions.override()
 * without touching this enum or any call site.
 */
enum class BindingMode {
    NONE,
    CONTAINER,
    POSITION,
    AREA,
}

enum class TagType(val id: String) {
    // --- Core combat / world interaction ---
    BREAKER("breaker"),

    // --- Gathering ---
    HARVESTER("harvester"),
    VACUUM("vacuum"),

    // --- Logistics ---
    SENDER("sender"),
    PULLER("puller"),
    DISTRIBUTOR("distributor"),
    DROPPER("dropper"),
    VOID("void"),

    // --- Interaction ---
    ACTIVATOR("activator"),
    ;

    /** The full role definition backing this key. */
    val definition: TagTypeDefinition get() = TagTypeDefinitions.get(this)

    val bindingMode: BindingMode get() = definition.bindingMode
    val usesFilter: Boolean get() = definition.usesFilter
    val supportsTargetList: Boolean get() = definition.supportsTargetList
    val description: String get() = definition.description
    val color: Formatting get() = definition.color
    val arrivalParticle: ParticleEffect get() = definition.arrivalParticle
    val workParticle: ParticleEffect get() = definition.workParticle
    val workSound: SoundEvent get() = definition.workSound
    val family: TagRoleFamily get() = definition.family

    val supportsBinding: Boolean get() = bindingMode != BindingMode.NONE

    companion object {
        private val byId: Map<String, TagType> = TagType.entries.associateBy { it.id }
        fun fromId(id: String): TagType? = byId[id]
    }
}

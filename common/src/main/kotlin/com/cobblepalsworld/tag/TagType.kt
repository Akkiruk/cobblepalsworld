package com.cobblepalsworld.tag

import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Formatting

/**
 * Single source of truth for tag capabilities.
 * To add a new tag:
 * 1. Add an entry here
 * 2. Create a TagBehavior implementation
 * 3. Register it in CobblePalsWorld.registerBehaviors()
 * 4. Add recipe JSON + lang key + item model
 */
enum class BindingMode {
    NONE,
    CONTAINER,
    POSITION,
    AREA,
}

enum class TagType(
    val id: String,
    val bindingMode: BindingMode = BindingMode.NONE,
    val usesFilter: Boolean = true,
    val supportsTargetList: Boolean = false,
    val description: String,
    val color: Formatting = Formatting.WHITE,
    val arrivalParticle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER,
    val workParticle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER,
    val workSound: SoundEvent = SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP
) {
    // --- Core combat / world interaction ---
    BREAKER(
        id = "breaker",
        bindingMode = BindingMode.POSITION,
        description = "Breaks one exact bound block, then returns the drops to the Command Post",
        color = Formatting.RED,
        arrivalParticle = ParticleTypes.CRIT,
        workParticle = ParticleTypes.EXPLOSION,
        workSound = SoundEvents.BLOCK_STONE_BREAK
    ),
    GUARDIAN(
        id = "guardian",
        description = "Attacks hostile mobs near the Command Post",
        usesFilter = false,
        color = Formatting.DARK_RED,
        arrivalParticle = ParticleTypes.ANGRY_VILLAGER,
        workParticle = ParticleTypes.CRIT,
        workSound = SoundEvents.ENTITY_PLAYER_ATTACK_STRONG
    ),

    // --- Gathering ---
    HARVESTER(
        id = "harvester",
        bindingMode = BindingMode.AREA,
        description = "Harvests mature crops only inside the selected work box into the Command Post buffer",
        usesFilter = false,
        color = Formatting.DARK_GREEN,
        arrivalParticle = ParticleTypes.HAPPY_VILLAGER,
        workParticle = ParticleTypes.COMPOSTER,
        workSound = SoundEvents.BLOCK_CROP_BREAK
    ),
    VACUUM(
        id = "vacuum",
        description = "Collects dropped items near the Command Post into its buffer",
        color = Formatting.AQUA,
        arrivalParticle = ParticleTypes.PORTAL,
        workParticle = ParticleTypes.PORTAL,
        workSound = SoundEvents.ENTITY_ITEM_PICKUP
    ),
    FISHER(
        id = "fisher",
        bindingMode = BindingMode.AREA,
        description = "Finds a safe shoreline spot and catches fish for the Command Post buffer",
        usesFilter = false,
        color = Formatting.AQUA,
        arrivalParticle = ParticleTypes.SPLASH,
        workParticle = ParticleTypes.FISHING,
        workSound = SoundEvents.ENTITY_FISHING_BOBBER_SPLASH
    ),
    SCOUT(
        id = "scout",
        description = "Marks stable nearby discoveries so players can inspect useful blocks instead of chasing random pings",
        usesFilter = false,
        color = Formatting.GREEN,
        arrivalParticle = ParticleTypes.COMPOSTER,
        workParticle = ParticleTypes.HAPPY_VILLAGER,
        workSound = SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP
    ),

    // --- Logistics ---
    SENDER(
        id = "sender",
        bindingMode = BindingMode.CONTAINER,
        description = "Sends filtered items from the Command Post buffer to one bound container",
        color = Formatting.LIGHT_PURPLE,
        arrivalParticle = ParticleTypes.ENCHANT,
        workParticle = ParticleTypes.ENCHANT,
        workSound = SoundEvents.BLOCK_CHEST_CLOSE
    ),
    PULLER(
        id = "puller",
        bindingMode = BindingMode.CONTAINER,
        description = "Pulls filtered items from one bound container into the Command Post buffer",
        color = Formatting.BLUE,
        arrivalParticle = ParticleTypes.ENCHANT,
        workParticle = ParticleTypes.ENCHANT,
        workSound = SoundEvents.BLOCK_CHEST_OPEN
    ),
    DISTRIBUTOR(
        id = "distributor",
        bindingMode = BindingMode.CONTAINER,
        supportsTargetList = true,
        description = "Distributes filtered items from the Command Post buffer across multiple containers",
        color = Formatting.DARK_PURPLE,
        arrivalParticle = ParticleTypes.ENCHANT,
        workParticle = ParticleTypes.ENCHANT,
        workSound = SoundEvents.BLOCK_BARREL_OPEN
    ),
    DROPPER(
        id = "dropper",
        bindingMode = BindingMode.POSITION,
        description = "Drops matching items from the Command Post buffer at a bound location",
        color = Formatting.DARK_GRAY,
        arrivalParticle = ParticleTypes.SMOKE,
        workParticle = ParticleTypes.SMOKE,
        workSound = SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM
    ),
    VOID(
        id = "void",
        description = "Deletes matching items directly from the Command Post buffer",
        color = Formatting.DARK_RED,
        arrivalParticle = ParticleTypes.LARGE_SMOKE,
        workParticle = ParticleTypes.LARGE_SMOKE,
        workSound = SoundEvents.BLOCK_LAVA_EXTINGUISH
    ),
    // --- Interaction ---
    ACTIVATOR(
        id = "activator",
        bindingMode = BindingMode.POSITION,
        description = "Right-clicks one exact bound target with filtered items from the Command Post buffer",
        color = Formatting.YELLOW,
        arrivalParticle = ParticleTypes.WAX_ON,
        workParticle = ParticleTypes.WAX_ON,
        workSound = SoundEvents.BLOCK_DISPENSER_DISPENSE
    ),
    LOOKOUT(
        id = "lookout",
        bindingMode = BindingMode.CONTAINER,
        description = "Turns the Command Post's redstone output on when a bound or nearby inventory contains matching items",
        color = Formatting.GOLD,
        arrivalParticle = ParticleTypes.ELECTRIC_SPARK,
        workParticle = ParticleTypes.ELECTRIC_SPARK,
        workSound = SoundEvents.BLOCK_LEVER_CLICK
    ),
    SHEPHERD(
        id = "shepherd",
        bindingMode = BindingMode.POSITION,
        description = "Feeds and breeds animals near a bound pen using food from the Command Post buffer",
        usesFilter = false,
        color = Formatting.WHITE,
        arrivalParticle = ParticleTypes.HEART,
        workParticle = ParticleTypes.HEART,
        workSound = SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP
    ),
    ;

    val supportsBinding: Boolean get() = bindingMode != BindingMode.NONE

    companion object {
        private val byId: Map<String, TagType> = TagType.entries.associateBy { it.id }
        fun fromId(id: String): TagType? = byId[id]
    }
}

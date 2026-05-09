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
        description = "Breaks matching blocks in range",
        color = Formatting.RED,
        arrivalParticle = ParticleTypes.CRIT,
        workParticle = ParticleTypes.EXPLOSION,
        workSound = SoundEvents.BLOCK_STONE_BREAK
    ),
    GUARDIAN(
        id = "guardian",
        description = "Attacks hostile mobs in range",
        usesFilter = false,
        color = Formatting.DARK_RED,
        arrivalParticle = ParticleTypes.ANGRY_VILLAGER,
        workParticle = ParticleTypes.CRIT,
        workSound = SoundEvents.ENTITY_PLAYER_ATTACK_STRONG
    ),

    // --- Gathering ---
    HARVESTER(
        id = "harvester",
        description = "Harvests mature crops and replants them",
        usesFilter = false,
        color = Formatting.DARK_GREEN,
        arrivalParticle = ParticleTypes.HAPPY_VILLAGER,
        workParticle = ParticleTypes.COMPOSTER,
        workSound = SoundEvents.BLOCK_CROP_BREAK
    ),
    FISHER(
        id = "fisher",
        description = "Catches fish from nearby water",
        usesFilter = false,
        color = Formatting.BLUE,
        arrivalParticle = ParticleTypes.BUBBLE,
        workParticle = ParticleTypes.FISHING,
        workSound = SoundEvents.ENTITY_FISHING_BOBBER_SPLASH
    ),
    VACUUM(
        id = "vacuum",
        description = "Picks up dropped items nearby",
        color = Formatting.AQUA,
        arrivalParticle = ParticleTypes.PORTAL,
        workParticle = ParticleTypes.PORTAL,
        workSound = SoundEvents.ENTITY_ITEM_PICKUP
    ),

    // --- Processing ---
    SMELTER(
        id = "smelter",
        bindingMode = BindingMode.CONTAINER,
        description = "Smelts raw items from containers using fire power",
        color = Formatting.GOLD,
        arrivalParticle = ParticleTypes.FLAME,
        workParticle = ParticleTypes.LAVA,
        workSound = SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE
    ),

    // --- Logistics ---
    COURIER(
        id = "courier",
        bindingMode = BindingMode.CONTAINER,
        supportsTargetList = true,
        description = "Carries items from nearby containers to a bound destination",
        color = Formatting.LIGHT_PURPLE,
        arrivalParticle = ParticleTypes.ENCHANT,
        workParticle = ParticleTypes.ENCHANT,
        workSound = SoundEvents.BLOCK_CHEST_CLOSE
    ),
    STASHER(
        id = "stasher",
        bindingMode = BindingMode.CONTAINER,
        supportsTargetList = true,
        description = "Distributes items from a source into nearby containers",
        color = Formatting.DARK_PURPLE,
        arrivalParticle = ParticleTypes.ENCHANT,
        workParticle = ParticleTypes.ENCHANT,
        workSound = SoundEvents.BLOCK_BARREL_OPEN
    ),
    DROPPER(
        id = "dropper",
        bindingMode = BindingMode.POSITION,
        description = "Drops matching items from containers at a bound location",
        color = Formatting.DARK_GRAY,
        arrivalParticle = ParticleTypes.SMOKE,
        workParticle = ParticleTypes.SMOKE,
        workSound = SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM
    ),
    PLAYER(
        id = "player",
        description = "Delivers matching items from containers into the owner's inventory",
        color = Formatting.GOLD,
        arrivalParticle = ParticleTypes.END_ROD,
        workParticle = ParticleTypes.END_ROD,
        workSound = SoundEvents.ENTITY_ITEM_PICKUP
    ),

    // --- Placement / world building ---
    PLANTER(
        id = "planter",
        bindingMode = BindingMode.POSITION,
        description = "Places matching block items from containers at a bound location",
        color = Formatting.GREEN,
        arrivalParticle = ParticleTypes.HAPPY_VILLAGER,
        workParticle = ParticleTypes.COMPOSTER,
        workSound = SoundEvents.BLOCK_GRASS_PLACE
    ),
    ILLUMINATOR(
        id = "illuminator",
        bindingMode = BindingMode.CONTAINER,
        description = "Lights up dark areas with torches from containers",
        color = Formatting.YELLOW,
        arrivalParticle = ParticleTypes.FLAME,
        workParticle = ParticleTypes.FLAME,
        workSound = SoundEvents.BLOCK_WOOD_PLACE
    ),

    // --- Interaction ---
    ACTIVATOR(
        id = "activator",
        bindingMode = BindingMode.CONTAINER,
        description = "Right-clicks blocks with usable items like bonemeal and hoes",
        color = Formatting.YELLOW,
        arrivalParticle = ParticleTypes.WAX_ON,
        workParticle = ParticleTypes.WAX_ON,
        workSound = SoundEvents.BLOCK_DISPENSER_DISPENSE
    ),
    SHEPHERD(
        id = "shepherd",
        bindingMode = BindingMode.POSITION,
        description = "Feeds and breeds animals near a bound pen area",
        usesFilter = false,
        color = Formatting.WHITE,
        arrivalParticle = ParticleTypes.HEART,
        workParticle = ParticleTypes.HEART,
        workSound = SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP
    ),

    // --- Sensing / utility ---
    LOOKOUT(
        id = "lookout",
        bindingMode = BindingMode.CONTAINER,
        usesFilter = true,
        description = "Monitors a container and emits a nearby redstone signal when conditions match",
        color = Formatting.DARK_AQUA,
        arrivalParticle = ParticleTypes.ELECTRIC_SPARK,
        workParticle = ParticleTypes.ELECTRIC_SPARK,
        workSound = SoundEvents.BLOCK_LEVER_CLICK
    ),
    SCOUT(
        id = "scout",
        description = "Explores nearby and highlights ores and points of interest",
        usesFilter = false,
        color = Formatting.GRAY,
        arrivalParticle = ParticleTypes.END_ROD,
        workParticle = ParticleTypes.END_ROD,
        workSound = SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME
    ),
    WEATHERWORKER(
        id = "weatherworker",
        description = "Boosts crop growth in the surrounding area",
        usesFilter = false,
        color = Formatting.DARK_BLUE,
        arrivalParticle = ParticleTypes.RAIN,
        workParticle = ParticleTypes.RAIN,
        workSound = SoundEvents.WEATHER_RAIN
    );

    val supportsBinding: Boolean get() = bindingMode != BindingMode.NONE

    companion object {
        private val byId: Map<String, TagType> = buildMap {
            TagType.entries.forEach { put(it.id, it) }
            // Backward compat: old save IDs → new tag types
            put("sender", COURIER)
            put("placer", PLANTER)
            put("detector", LOOKOUT)
            put("distributor", STASHER)
            // Retired tags map to closest equivalent
            put("puller", COURIER)
        }
        fun fromId(id: String): TagType? = byId[id]
    }
}

package com.cobblepalsworld.tag

import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Formatting

/**
 * The full definition of a tag role: capabilities, presentation and feedback.
 *
 * [TagType] entries are stable keys; everything a role *does* lives here and is
 * resolved through [TagTypeDefinitions] at runtime. Addons (or future datapack
 * loading) can replace a definition with [TagTypeDefinitions.override] without
 * touching the enum or any call site.
 */
data class TagTypeDefinition(
    val id: String,
    val family: TagRoleFamily,
    val bindingMode: BindingMode = BindingMode.NONE,
    val usesFilter: Boolean = true,
    val supportsTargetList: Boolean = false,
    val description: String,
    val color: Formatting = Formatting.WHITE,
    val arrivalParticle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER,
    val workParticle: ParticleEffect = ParticleTypes.HAPPY_VILLAGER,
    val workSound: SoundEvent = SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP
)

/**
 * Identifier-backed registry of role definitions.
 *
 * Built-in roles are registered at class load; [override] swaps a definition at
 * runtime (validated against the role's id) so role capabilities are data, not
 * code. Lookup by enum is allocation-free and total: every [TagType] always has
 * a definition.
 */
object TagTypeDefinitions {
    private val definitions = java.util.concurrent.ConcurrentHashMap<TagType, TagTypeDefinition>()

    init {
        register(
            TagType.BREAKER, TagTypeDefinition(
                id = "breaker",
                family = TagRoleFamily.Gathering,
                bindingMode = BindingMode.POSITION,
                description = "Breaks one exact bound block, then returns the drops to the Command Post",
                color = Formatting.RED,
                arrivalParticle = ParticleTypes.CRIT,
                workParticle = ParticleTypes.EXPLOSION,
                workSound = SoundEvents.BLOCK_STONE_BREAK
            )
        )
        register(
            TagType.HARVESTER, TagTypeDefinition(
                id = "harvester",
                family = TagRoleFamily.Gathering,
                bindingMode = BindingMode.AREA,
                description = "Harvests mature crops only inside the selected work box into the Command Post buffer",
                usesFilter = false,
                color = Formatting.DARK_GREEN,
                arrivalParticle = ParticleTypes.HAPPY_VILLAGER,
                workParticle = ParticleTypes.COMPOSTER,
                workSound = SoundEvents.BLOCK_CROP_BREAK
            )
        )
        register(
            TagType.VACUUM, TagTypeDefinition(
                id = "vacuum",
                family = TagRoleFamily.Gathering,
                description = "Collects dropped items near the Command Post into its buffer",
                color = Formatting.AQUA,
                arrivalParticle = ParticleTypes.PORTAL,
                workParticle = ParticleTypes.PORTAL,
                workSound = SoundEvents.ENTITY_ITEM_PICKUP
            )
        )
        register(
            TagType.SENDER, TagTypeDefinition(
                id = "sender",
                family = TagRoleFamily.Logistics,
                bindingMode = BindingMode.CONTAINER,
                description = "Sends filtered items from the Command Post buffer to one bound container",
                color = Formatting.LIGHT_PURPLE,
                arrivalParticle = ParticleTypes.ENCHANT,
                workParticle = ParticleTypes.ENCHANT,
                workSound = SoundEvents.BLOCK_CHEST_CLOSE
            )
        )
        register(
            TagType.PULLER, TagTypeDefinition(
                id = "puller",
                family = TagRoleFamily.Logistics,
                bindingMode = BindingMode.CONTAINER,
                description = "Pulls filtered items from one bound container into the Command Post buffer",
                color = Formatting.BLUE,
                arrivalParticle = ParticleTypes.ENCHANT,
                workParticle = ParticleTypes.ENCHANT,
                workSound = SoundEvents.BLOCK_CHEST_OPEN
            )
        )
        register(
            TagType.DISTRIBUTOR, TagTypeDefinition(
                id = "distributor",
                family = TagRoleFamily.Logistics,
                bindingMode = BindingMode.CONTAINER,
                supportsTargetList = true,
                description = "Distributes filtered items from the Command Post buffer across multiple containers",
                color = Formatting.DARK_PURPLE,
                arrivalParticle = ParticleTypes.ENCHANT,
                workParticle = ParticleTypes.ENCHANT,
                workSound = SoundEvents.BLOCK_BARREL_OPEN
            )
        )
        register(
            TagType.DROPPER, TagTypeDefinition(
                id = "dropper",
                family = TagRoleFamily.Logistics,
                bindingMode = BindingMode.POSITION,
                description = "Drops matching items from the Command Post buffer at a bound location",
                color = Formatting.DARK_GRAY,
                arrivalParticle = ParticleTypes.SMOKE,
                workParticle = ParticleTypes.SMOKE,
                workSound = SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM
            )
        )
        register(
            TagType.VOID, TagTypeDefinition(
                id = "void",
                family = TagRoleFamily.Logistics,
                description = "Deletes matching items directly from the Command Post buffer",
                color = Formatting.DARK_RED,
                arrivalParticle = ParticleTypes.LARGE_SMOKE,
                workParticle = ParticleTypes.LARGE_SMOKE,
                workSound = SoundEvents.BLOCK_LAVA_EXTINGUISH
            )
        )
        register(
            TagType.ACTIVATOR, TagTypeDefinition(
                id = "activator",
                family = TagRoleFamily.Interaction,
                bindingMode = BindingMode.POSITION,
                description = "Right-clicks one exact bound target with filtered items from the Command Post buffer",
                color = Formatting.YELLOW,
                arrivalParticle = ParticleTypes.WAX_ON,
                workParticle = ParticleTypes.WAX_ON,
                workSound = SoundEvents.BLOCK_DISPENSER_DISPENSE
            )
        )
    }

    private fun register(type: TagType, definition: TagTypeDefinition) {
        require(definition.id == type.id) { "Definition id '${definition.id}' does not match role id '${type.id}'" }
        definitions[type] = definition
    }

    /** Replaces a role's definition. The id must stay stable; capabilities and presentation may change. */
    fun override(type: TagType, definition: TagTypeDefinition) {
        register(type, definition)
    }

    fun get(type: TagType): TagTypeDefinition =
        definitions[type] ?: error("No definition registered for tag type '${type.id}'")
}

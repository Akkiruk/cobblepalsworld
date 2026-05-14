package com.cobblepalsworld.augment

/**
 * All augment types. Each modifies tag behavior in a specific way.
 * To add a new augment:
 * 1. Add entry here
 * 2. Register the item in AugmentRegistry
 * 3. Add recipe + lang + model
 *
 * Augments are stackable (maxLevel). Effect scales linearly unless overridden.
 */
enum class AugmentType(
    val id: String,
    val maxLevel: Int = 1,
    val description: String,
    val stackable: Boolean = true
) {
    SPEED(
        id = "speed",
        maxLevel = 3,
        description = "Reduces work cooldown by 25% per level"
    ),
    STACK(
        id = "stack",
        maxLevel = 3,
        description = "Increases items per trip by 16 per level"
    ),
    RANGE(
        id = "range",
        maxLevel = 3,
        description = "Increases search range by 4 blocks per level"
    ),
    REGULATOR(
        id = "regulator",
        maxLevel = 1,
        description = "Maintains exact item counts in target container",
        stackable = false
    ),
    REDSTONE(
        id = "redstone",
        maxLevel = 1,
        description = "Pauses work when the Command Post receives redstone signal",
        stackable = false
    ),
    XP_VACUUM(
        id = "xp_vacuum",
        maxLevel = 1,
        description = "Vacuum also collects XP orbs nearby",
        stackable = false
    ),
    PUSHING(
        id = "pushing",
        maxLevel = 1,
        description = "Deposits into entity inventories (minecarts, etc.)",
        stackable = false
    );

    companion object {
        private val byId = entries.associateBy { it.id }
        fun fromId(id: String): AugmentType? = byId[id]
    }
}

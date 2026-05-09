package com.cobblepalsworld.augment

/**
 * Immutable snapshot of augments applied to a tag instance.
 * Stored as a list of (type, level) pairs.
 */
data class AugmentSet(
    val augments: List<AugmentEntry> = emptyList()
) {
    data class AugmentEntry(val type: AugmentType, val level: Int = 1)

    fun hasAugment(type: AugmentType): Boolean = augments.any { it.type == type }

    fun getLevel(type: AugmentType): Int = augments.firstOrNull { it.type == type }?.level ?: 0

    fun speedMultiplier(): Double {
        val level = getLevel(AugmentType.SPEED)
        return if (level > 0) 1.0 - (level * 0.25) else 1.0
    }

    fun extraItemsPerTrip(): Int = getLevel(AugmentType.STACK) * 16

    fun extraRange(): Int = getLevel(AugmentType.RANGE) * 4

    fun isRedstoneControlled(): Boolean = hasAugment(AugmentType.REDSTONE)

    fun isRegulator(): Boolean = hasAugment(AugmentType.REGULATOR)

    fun vacuumsXp(): Boolean = hasAugment(AugmentType.XP_VACUUM)

    fun canPushToEntities(): Boolean = hasAugment(AugmentType.PUSHING)

    companion object {
        val EMPTY = AugmentSet()
        const val MAX_AUGMENT_SLOTS = 3
    }
}

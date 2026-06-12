package com.cobblepalsworld.mastery

import net.minecraft.util.Formatting

/**
 * Mastery tiers a worker Pokemon climbs by completing jobs of one role.
 * Each tier grants real work perks: a cooldown multiplier (work faster)
 * and bonus search range, both stacking on top of augments.
 */
enum class MasteryTier(
    val label: String,
    /** Completed jobs in one role required to reach this tier. */
    val requiredJobs: Long,
    /** Multiplier applied to the effective work cooldown (lower is faster). */
    val cooldownMultiplier: Double,
    /** Extra search range in blocks granted by this tier. */
    val bonusRange: Int,
    val color: Formatting
) {
    NOVICE("Novice", 0L, 1.00, 0, Formatting.GRAY),
    APPRENTICE("Apprentice", 25L, 0.95, 0, Formatting.WHITE),
    ADEPT("Adept", 100L, 0.90, 2, Formatting.GREEN),
    EXPERT("Expert", 300L, 0.85, 4, Formatting.AQUA),
    MASTER("Master", 750L, 0.75, 8, Formatting.GOLD),
    ;

    val nextTier: MasteryTier? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun forJobs(jobs: Long): MasteryTier {
            var tier = NOVICE
            for (candidate in entries) {
                if (jobs >= candidate.requiredJobs) tier = candidate else break
            }
            return tier
        }
    }
}

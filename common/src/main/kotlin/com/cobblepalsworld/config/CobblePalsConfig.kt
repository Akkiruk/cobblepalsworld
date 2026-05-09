package com.cobblepalsworld.config

import com.cobblepalsworld.tag.TagType

data class CobblePalsConfig(
    val general: GeneralConfig = GeneralConfig(),
    val tags: MutableMap<String, TagConfig> = mutableMapOf()
) {
    data class GeneralConfig(
        val tickInterval: Int = 5,
        val maxWorkersPerPasture: Int = 6,
        val inventoryBstDivisor: Int = 100,
        val inventoryMinSlots: Int = 3,
        val inventoryMaxSlots: Int = 9,
        val workCooldownTicks: Int = 40,
        val arrivalDelayTicks: Int = 30,
        /** Ticks of idle before entering eco mode (default ~10s). */
        val ecoTimeoutTicks: Int = 200,
        /** Tick interval multiplier when in eco mode (4x = check every 20 ticks instead of 5). */
        val ecoTickMultiplier: Int = 4,
        /** How long (ticks) to cache a found container position before re-searching. */
        val containerCacheTicks: Int = 100
    ) {
        fun validated() = GeneralConfig(
            tickInterval = tickInterval.coerceAtLeast(1),
            maxWorkersPerPasture = maxWorkersPerPasture.coerceAtLeast(1),
            inventoryBstDivisor = inventoryBstDivisor.coerceAtLeast(1),
            inventoryMinSlots = inventoryMinSlots.coerceAtLeast(1),
            inventoryMaxSlots = inventoryMaxSlots.coerceAtLeast(inventoryMinSlots.coerceAtLeast(1)),
            workCooldownTicks = workCooldownTicks.coerceAtLeast(1),
            arrivalDelayTicks = arrivalDelayTicks.coerceAtLeast(0),
            ecoTimeoutTicks = ecoTimeoutTicks.coerceAtLeast(1),
            ecoTickMultiplier = ecoTickMultiplier.coerceAtLeast(1),
            containerCacheTicks = containerCacheTicks.coerceAtLeast(1)
        )
    }

    data class TagConfig(
        val enabled: Boolean = true,
        val range: Int = 8,
        val maxItemsPerTrip: Int = 64
    )

    fun getTagConfig(type: TagType): TagConfig = tags.getOrDefault(type.id, TAG_DEFAULTS[type.id] ?: TagConfig())

    companion object {
        val TAG_DEFAULTS = mapOf(
            "breaker" to TagConfig(enabled = true, range = 8),
            "guardian" to TagConfig(enabled = true, range = 12),
            "harvester" to TagConfig(enabled = true, range = 8),
            "fisher" to TagConfig(enabled = true, range = 10),
            "vacuum" to TagConfig(enabled = true, range = 8),
            "smelter" to TagConfig(enabled = true, range = 12, maxItemsPerTrip = 8),
            "courier" to TagConfig(enabled = true, range = 16),
            "stasher" to TagConfig(enabled = true, range = 16, maxItemsPerTrip = 16),
            "dropper" to TagConfig(enabled = true, range = 16, maxItemsPerTrip = 16),
            "player" to TagConfig(enabled = true, range = 16, maxItemsPerTrip = 16),
            "planter" to TagConfig(enabled = true, range = 8),
            "illuminator" to TagConfig(enabled = true, range = 12),
            "activator" to TagConfig(enabled = true, range = 8),
            "shepherd" to TagConfig(enabled = true, range = 12),
            "lookout" to TagConfig(enabled = true, range = 16),
            "scout" to TagConfig(enabled = true, range = 16),
            "weatherworker" to TagConfig(enabled = true, range = 8)
        )

        fun withDefaults(): CobblePalsConfig {
            return CobblePalsConfig(tags = TAG_DEFAULTS.toMutableMap())
        }
    }
}

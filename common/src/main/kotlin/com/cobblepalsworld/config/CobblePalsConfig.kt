package com.cobblepalsworld.config

import com.cobblepalsworld.tag.TagType

data class CobblePalsConfig(
    val general: GeneralConfig = GeneralConfig(),
    val tags: MutableMap<String, TagConfig> = mutableMapOf()
) {
    data class GeneralConfig(
        val tickInterval: Int = 5,
        val idleSearchRetryTicks: Int = 20,
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
            idleSearchRetryTicks = idleSearchRetryTicks.coerceAtLeast(tickInterval.coerceAtLeast(1) * 4),
            maxWorkersPerPasture = maxWorkersPerPasture.coerceAtLeast(1),
            inventoryBstDivisor = inventoryBstDivisor.coerceAtLeast(1),
            inventoryMinSlots = inventoryMinSlots.coerceAtLeast(1),
            inventoryMaxSlots = inventoryMaxSlots.coerceAtLeast(inventoryMinSlots.coerceAtLeast(1)),
            workCooldownTicks = workCooldownTicks.coerceAtLeast(1),
            arrivalDelayTicks = arrivalDelayTicks.coerceAtLeast(0),
            ecoTimeoutTicks = ecoTimeoutTicks.coerceAtLeast(1),
            ecoTickMultiplier = ecoTickMultiplier.coerceAtLeast(1),
            containerCacheTicks = containerCacheTicks.coerceAtLeast(200)
        )
    }

    data class TagConfig(
        val enabled: Boolean = true,
        val range: Int = 8,
        val maxItemsPerTrip: Int = 64
    )

    fun getTagConfig(type: TagType): TagConfig {
        val direct = tags[type.id]
        if (direct != null) return direct

        val legacy = when (type) {
            TagType.COURIER -> tags["courier"]
            TagType.STASHER -> tags["stasher"]
            else -> null
        }
        if (legacy != null) return legacy

        return TAG_DEFAULTS[type.id] ?: when (type) {
            TagType.COURIER -> TAG_DEFAULTS["courier"]
            TagType.STASHER -> TAG_DEFAULTS["stasher"]
            else -> null
        } ?: TagConfig()
    }

    companion object {
        val TAG_DEFAULTS = mapOf(
            "breaker" to TagConfig(enabled = true, range = 8),
            "guardian" to TagConfig(enabled = true, range = 12),
            "harvester" to TagConfig(enabled = true, range = 8),
            "vacuum" to TagConfig(enabled = true, range = 8),
            "sender" to TagConfig(enabled = true, range = 16),
            "courier" to TagConfig(enabled = true, range = 16),
            "puller" to TagConfig(enabled = true, range = 16),
            "distributor" to TagConfig(enabled = true, range = 16, maxItemsPerTrip = 16),
            "stasher" to TagConfig(enabled = true, range = 16, maxItemsPerTrip = 16),
            "dropper" to TagConfig(enabled = true, range = 16, maxItemsPerTrip = 16),
            "void" to TagConfig(enabled = true, range = 16, maxItemsPerTrip = 16),
            "activator" to TagConfig(enabled = true, range = 8),
            "shepherd" to TagConfig(enabled = true, range = 12)
        )

        fun withDefaults(): CobblePalsConfig {
            return CobblePalsConfig(tags = TAG_DEFAULTS.toMutableMap())
        }
    }
}

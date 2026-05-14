package com.cobblepalsworld.config

import com.cobblepalsworld.tag.TagType

data class CobblePalsConfig(
    val general: GeneralConfig = GeneralConfig(),
    val tags: MutableMap<String, TagConfig> = mutableMapOf()
) {
    data class GeneralConfig(
        val tickInterval: Int = 5,
        /** Full-speed worker ticking radius around a Command Post. */
        val nearbyPlayerRange: Int = 48,
        /** Additional multiplier applied to worksite ticks when nobody is nearby. */
        val distantTickMultiplier: Int = 4,
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
        val containerCacheTicks: Int = 100,
        /** How many new pathing starts one Command Post may trigger in a single tick pass. */
        val maxPathStartsPerPastureTick: Int = 3,
        /** Server-wide cap for fresh pathing starts, preventing many loaded worksites from stampeding at once. */
        val maxGlobalPathStartsPerTick: Int = 64,
        /** Minimum ticks between unchanged worker visual packets for a Command Post. */
        val visualUpdateIntervalTicks: Int = 10
    ) {
        fun validated() = GeneralConfig(
            tickInterval = tickInterval.coerceAtLeast(1),
            nearbyPlayerRange = nearbyPlayerRange.coerceAtLeast(8),
            distantTickMultiplier = distantTickMultiplier.coerceAtLeast(1),
            idleSearchRetryTicks = idleSearchRetryTicks.coerceAtLeast(tickInterval.coerceAtLeast(1) * 4),
            maxWorkersPerPasture = maxWorkersPerPasture.coerceAtLeast(1),
            inventoryBstDivisor = inventoryBstDivisor.coerceAtLeast(1),
            inventoryMinSlots = inventoryMinSlots.coerceAtLeast(1),
            inventoryMaxSlots = inventoryMaxSlots.coerceAtLeast(inventoryMinSlots.coerceAtLeast(1)),
            workCooldownTicks = workCooldownTicks.coerceAtLeast(1),
            arrivalDelayTicks = arrivalDelayTicks.coerceAtLeast(0),
            ecoTimeoutTicks = ecoTimeoutTicks.coerceAtLeast(1),
            ecoTickMultiplier = ecoTickMultiplier.coerceAtLeast(1),
            containerCacheTicks = containerCacheTicks.coerceAtLeast(200),
            maxPathStartsPerPastureTick = maxPathStartsPerPastureTick.coerceAtLeast(1),
            maxGlobalPathStartsPerTick = if (maxGlobalPathStartsPerTick <= 0) 64 else maxGlobalPathStartsPerTick,
            visualUpdateIntervalTicks = if (visualUpdateIntervalTicks <= 0) 10 else visualUpdateIntervalTicks
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
        return TAG_DEFAULTS[type.id] ?: TagConfig()
    }

    companion object {
        val TAG_DEFAULTS = mapOf(
            "breaker" to TagConfig(enabled = true, range = 8),
            "guardian" to TagConfig(enabled = true, range = 12),
            "harvester" to TagConfig(enabled = true, range = 8),
            "vacuum" to TagConfig(enabled = true, range = 8),
            "fisher" to TagConfig(enabled = true, range = 12),
            "scout" to TagConfig(enabled = true, range = 16),
            "sender" to TagConfig(enabled = true, range = 16),
            "puller" to TagConfig(enabled = true, range = 16),
            "distributor" to TagConfig(enabled = true, range = 16, maxItemsPerTrip = 16),
            "dropper" to TagConfig(enabled = true, range = 16, maxItemsPerTrip = 16),
            "void" to TagConfig(enabled = true, range = 16, maxItemsPerTrip = 16),
            "activator" to TagConfig(enabled = true, range = 8),
            "lookout" to TagConfig(enabled = true, range = 16),
            "shepherd" to TagConfig(enabled = true, range = 12)
        )

        fun withDefaults(): CobblePalsConfig {
            return CobblePalsConfig(tags = TAG_DEFAULTS.toMutableMap())
        }
    }
}

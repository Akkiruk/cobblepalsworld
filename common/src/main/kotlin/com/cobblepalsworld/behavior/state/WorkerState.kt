package com.cobblepalsworld.behavior.state

import net.minecraft.util.math.BlockPos
import java.util.UUID

class WorkerState(val pokemonId: UUID) {
    var phase: WorkerPhase = WorkerPhase.IDLE
    var targetPos: BlockPos? = null
    var depositPos: BlockPos? = null
    var cooldownUntil: Long = 0L
    var arrivalTick: Long? = null
    var lastPathfindTick: Long = 0L
    var lastSeenTick: Long = 0L

    // --- Eco mode (inspired by Modular Routers) ---
    /** Ticks since this worker last did useful work. Reset on any successful action. */
    var idleTicks: Int = 0
    /** When true, worker is in low-power mode and ticks at ecoTickRate instead of normal rate. */
    var ecoMode: Boolean = false
    /** Counts pasture ticks in eco mode — skips processing until it reaches ecoTickMultiplier. */
    var ecoSkipCounter: Int = 0

    // --- Compiled cache (avoid recomputing per-tick) ---
    /** Cached effective cooldown in ticks, recomputed when tag augments change. */
    var cachedCooldown: Long = -1L
    /** Cached effective range (config + augment bonus). */
    var cachedRange: Int = -1
    /** Cached effective max items per trip. */
    var cachedMaxItems: Int = -1

    // --- Container search cache ---
    /** Last known deposit container position. Revalidated before use. */
    var cachedContainerPos: BlockPos? = null
    /** World time when cachedContainerPos was found. */
    var containerCacheTime: Long = 0L

    // --- Last-match slot (avoid scanning from slot 0 every time) ---
    /** Last slot index where a matching item was found in the source container. */
    var lastMatchSlot: Int = 0

    // --- Source tracking (prevents depositing back to the same container we pulled from) ---
    /** The BlockPos that the behavior extracted items FROM. Excluded during deposit search. */
    var workSourcePos: BlockPos? = null

    /** Tracks the previous redstone level for pulse-triggered tags. */
    var lastRedstonePower: Boolean = false

    fun reset() {
        phase = WorkerPhase.IDLE
        targetPos = null
        depositPos = null
        arrivalTick = null
        workSourcePos = null
        lastRedstonePower = false
        idleTicks = 0
        ecoMode = false
        ecoSkipCounter = 0
    }

    fun markDidWork() {
        idleTicks = 0
        ecoMode = false
        ecoSkipCounter = 0
    }

    fun invalidateCache() {
        cachedCooldown = -1L
        cachedRange = -1
        cachedMaxItems = -1
        cachedContainerPos = null
    }
}

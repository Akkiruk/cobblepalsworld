package com.cobblepalsworld.behavior.state

import net.minecraft.util.math.BlockPos
import java.util.UUID

class WorkerState(val pokemonId: UUID) {
    var phase: WorkerPhase = WorkerPhase.IDLE
    var statusReason: WorkerStatusReason = WorkerStatusReason.READY
    var statusDetail: String = ""
    var targetPos: BlockPos? = null
    var depositPos: BlockPos? = null
    var cooldownUntil: Long = 0L
    var arrivalTick: Long? = null
    var lastPathfindTick: Long = 0L
    var lastSeenTick: Long = 0L
    var nextTargetSearchTick: Long = 0L

    var navigationDestination: BlockPos? = null
    var navigationTravelTarget: BlockPos? = null
    var navigationPurpose: String = ""
    var navigationStartedTick: Long = 0L
    var navigationLastProgressTick: Long = 0L
    var navigationLastX: Double = Double.NaN
    var navigationLastY: Double = Double.NaN
    var navigationLastZ: Double = Double.NaN
    var navigationRecoveryStage: Int = 0
    var navigationRecoveryCooldownUntil: Long = 0L
    var navigationFailedAttempts: Int = 0

    // --- Eco mode (skip full work scans after a worker has been idle for a while) ---
    /** Ticks since this worker last did useful work. Reset on any successful action. */
    var idleTicks: Int = 0
    /** When true, worker is in low-power mode and ticks at ecoTickRate instead of normal rate. */
    var ecoMode: Boolean = false
    /** Counts worksite ticks in eco mode so processing can be skipped until ecoTickMultiplier is reached. */
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
    /** Last known source/search container position for behaviors that repeatedly restock. */
    var cachedSourceContainerPos: BlockPos? = null
    /** World time when cachedSourceContainerPos was found. */
    var sourceContainerCacheTime: Long = 0L

    // --- Last-match slot (avoid scanning from slot 0 every time) ---
    /** Last slot index where a matching item was found in the source container. */
    var lastMatchSlot: Int = 0
    /** Rolling scan cursor for behaviors that search large fixed spaces like harvest boxes. */
    var searchCursor: Int = 0

    // --- Source tracking (prevents depositing back to the same container we pulled from) ---
    /** The BlockPos that the behavior extracted items FROM. Excluded during deposit search. */
    var workSourcePos: BlockPos? = null

    /** Tracks the previous redstone level for pulse-triggered tags. */
    var lastRedstonePower: Boolean = false

    fun reset() {
        phase = WorkerPhase.IDLE
        statusReason = WorkerStatusReason.READY
        statusDetail = ""
        targetPos = null
        depositPos = null
        arrivalTick = null
        workSourcePos = null
        nextTargetSearchTick = 0L
        idleTicks = 0
        ecoMode = false
        ecoSkipCounter = 0
        resetNavigationSession()
    }

    fun markDidWork() {
        idleTicks = 0
        ecoMode = false
        ecoSkipCounter = 0
    }

    fun setStatus(reason: WorkerStatusReason, detail: String = "") {
        statusReason = reason
        statusDetail = detail
    }

    fun invalidateCache() {
        cachedCooldown = -1L
        cachedRange = -1
        cachedMaxItems = -1
        cachedContainerPos = null
        cachedSourceContainerPos = null
    }

    fun resetNavigationSession() {
        navigationDestination = null
        navigationTravelTarget = null
        navigationPurpose = ""
        navigationStartedTick = 0L
        navigationLastProgressTick = 0L
        navigationLastX = Double.NaN
        navigationLastY = Double.NaN
        navigationLastZ = Double.NaN
        navigationRecoveryStage = 0
        navigationRecoveryCooldownUntil = 0L
        navigationFailedAttempts = 0
    }
}

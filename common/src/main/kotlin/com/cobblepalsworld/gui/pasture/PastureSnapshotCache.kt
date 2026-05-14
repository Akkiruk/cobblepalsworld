package com.cobblepalsworld.gui.pasture

import net.minecraft.util.math.BlockPos

object PastureSnapshotCache {
    private val snapshots = mutableMapOf<BlockPos, PastureSnapshot>()
    private var pendingOpenAny = false
    private var pendingOpenPasture: BlockPos? = null

    fun store(snapshot: PastureSnapshot) {
        snapshots[snapshot.pasturePos.toImmutable()] = snapshot
    }

    fun get(pasturePos: BlockPos?): PastureSnapshot? {
        return pasturePos?.let { snapshots[it.toImmutable()] }
    }

    fun expectManagerOpen(pasturePos: BlockPos? = null) {
        if (pasturePos == null) {
            pendingOpenAny = true
            return
        }
        pendingOpenPasture = pasturePos.toImmutable()
    }

    fun consumeManagerOpen(pasturePos: BlockPos): Boolean {
        val immutablePos = pasturePos.toImmutable()
        return when {
            pendingOpenPasture == immutablePos -> {
                pendingOpenPasture = null
                true
            }

            pendingOpenAny -> {
                pendingOpenAny = false
                true
            }

            else -> false
        }
    }
}
package com.cobblepalsworld.gui.crew

import net.minecraft.util.math.BlockPos

object CrewSourceSnapshotCache {
    private val snapshotsByRouter = mutableMapOf<BlockPos, List<CrewSourceSnapshot>>()

    fun store(routerPos: BlockPos, snapshots: List<CrewSourceSnapshot>) {
        snapshotsByRouter[routerPos.toImmutable()] = snapshots
    }

    fun get(routerPos: BlockPos): List<CrewSourceSnapshot> = snapshotsByRouter[routerPos.toImmutable()].orEmpty()
}
package com.cobblepalsworld.gui.crew

import net.minecraft.util.math.BlockPos

object CommandPostCrewSnapshotCache {
    private val snapshots = mutableMapOf<BlockPos, CommandPostCrewSnapshot>()

    fun store(snapshot: CommandPostCrewSnapshot) {
        snapshots[snapshot.routerPos.toImmutable()] = snapshot
    }

    fun get(routerPos: BlockPos): CommandPostCrewSnapshot? = snapshots[routerPos.toImmutable()]

    fun clear(routerPos: BlockPos) {
        snapshots.remove(routerPos.toImmutable())
    }
}

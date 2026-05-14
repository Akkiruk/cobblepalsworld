package com.cobblepalsworld.navigation

import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import kotlin.math.abs

object SafePositionResolver {
    fun standNear(world: World, target: BlockPos, origin: BlockPos? = null): BlockPos? {
        if (isSafeStandPosition(world, target)) return target.toImmutable()

        return candidateOffsets(2)
            .asSequence()
            .filterNot { it.x == 0 && it.z == 0 }
            .map { target.add(it.x, it.y, it.z) }
            .filter { isSafeStandPosition(world, it) }
            .sortedWith(
                compareBy<BlockPos> { horizontalDistanceSquared(it, target) }
                    .thenBy { abs(it.y - target.y) }
                    .thenBy { origin?.let(it::getSquaredDistance) ?: 0.0 }
            )
            .firstOrNull()
            ?.toImmutable()
    }

    fun escapeNear(world: World, current: BlockPos, destination: BlockPos): BlockPos? {
        return candidateOffsets(2)
            .asSequence()
            .map { current.add(it.x, it.y, it.z) }
            .filter { isSafeStandPosition(world, it) }
            .sortedWith(
                compareBy<BlockPos> { horizontalDistanceSquared(it, destination) }
                    .thenBy { abs(it.y - destination.y) }
                    .thenBy { it.getSquaredDistance(current) }
            )
            .firstOrNull()
            ?.toImmutable()
    }

    fun isSafeStandPosition(world: World, pos: BlockPos): Boolean {
        val feet = world.getBlockState(pos).getCollisionShape(world, pos)
        if (!feet.isEmpty) return false

        val headPos = pos.up()
        val head = world.getBlockState(headPos).getCollisionShape(world, headPos)
        if (!head.isEmpty) return false

        val floorPos = pos.down()
        val floor = world.getBlockState(floorPos).getCollisionShape(world, floorPos)
        return !floor.isEmpty
    }

    private fun horizontalDistanceSquared(pos: BlockPos, target: BlockPos): Int {
        val dx = pos.x - target.x
        val dz = pos.z - target.z
        return dx * dx + dz * dz
    }

    private fun candidateOffsets(radius: Int): List<BlockPos> {
        val offsets = mutableListOf<BlockPos>()
        for (y in -1..2) {
            for (x in -radius..radius) {
                for (z in -radius..radius) {
                    if (x == 0 && y == 0 && z == 0) continue
                    if (abs(x) + abs(z) > radius + 1) continue
                    offsets += BlockPos(x, y, z)
                }
            }
        }
        return offsets
    }
}
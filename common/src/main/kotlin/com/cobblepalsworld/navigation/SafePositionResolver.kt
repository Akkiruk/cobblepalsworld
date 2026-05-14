package com.cobblepalsworld.navigation

import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import kotlin.math.abs

object SafePositionResolver {
    fun standNear(world: World, target: BlockPos, origin: BlockPos? = null): BlockPos? {
        if (isSafeStandPosition(world, target)) return target.toImmutable()

        return candidateOffsets(2)
            .asSequence()
            .map { target.add(it.x, it.y, it.z) }
            .filter { isSafeStandPosition(world, it) }
            .sortedWith(compareBy<BlockPos> { it.getSquaredDistance(target) }.thenBy { origin?.let(it::getSquaredDistance) ?: 0.0 })
            .firstOrNull()
            ?.toImmutable()
    }

    fun escapeNear(world: World, current: BlockPos, destination: BlockPos): BlockPos? {
        return candidateOffsets(2)
            .asSequence()
            .map { current.add(it.x, it.y, it.z) }
            .filter { isSafeStandPosition(world, it) }
            .sortedWith(compareBy<BlockPos> { it.getSquaredDistance(destination) }.thenBy { it.getSquaredDistance(current) })
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
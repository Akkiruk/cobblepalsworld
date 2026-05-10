package com.cobblepalsworld.tag

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

data class BoundArea(
    val min: BlockPos,
    val max: BlockPos
) {
    companion object {
        fun of(first: BlockPos, second: BlockPos): BoundArea {
            return BoundArea(
                BlockPos(
                    minOf(first.x, second.x),
                    minOf(first.y, second.y),
                    minOf(first.z, second.z)
                ),
                BlockPos(
                    maxOf(first.x, second.x),
                    maxOf(first.y, second.y),
                    maxOf(first.z, second.z)
                )
            )
        }
    }

    fun iterate(): Iterable<BlockPos> = BlockPos.iterate(min, max)

    fun volume(): Int = width() * height() * depth()

    fun positionAt(index: Int): BlockPos {
        val volume = volume()
        if (volume <= 0) return min

        val normalizedIndex = Math.floorMod(index, volume)
        val width = width()
        val depth = depth()
        val layerSize = width * depth
        val yOffset = normalizedIndex / layerSize
        val withinLayer = normalizedIndex % layerSize
        val zOffset = withinLayer / width
        val xOffset = withinLayer % width

        return BlockPos(min.x + xOffset, min.y + yOffset, min.z + zOffset)
    }

    fun toBox(): Box = Box(
        min.x.toDouble(),
        min.y.toDouble(),
        min.z.toDouble(),
        max.x + 1.0,
        max.y + 1.0,
        max.z + 1.0
    )

    fun width(): Int = max.x - min.x + 1

    fun height(): Int = max.y - min.y + 1

    fun depth(): Int = max.z - min.z + 1
}
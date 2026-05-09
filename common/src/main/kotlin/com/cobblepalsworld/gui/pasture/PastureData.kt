package com.cobblepalsworld.gui.pasture

import net.minecraft.network.PacketByteBuf
import net.minecraft.util.math.BlockPos
import java.util.UUID

data class PalSnapshot(
    val pokemonId: UUID,
    val displayName: String,
    val species: String,
    val level: Int,
    val tagTypeId: String?,
    val boundPos: BlockPos?,
    val filterSummary: String,
    val augmentSummary: String,
    val carriedItemDescs: List<String>,
    val isFainted: Boolean
) {
    fun writeToBuf(buf: PacketByteBuf) {
        buf.writeUuid(pokemonId)
        buf.writeString(displayName)
        buf.writeString(species)
        buf.writeVarInt(level)
        buf.writeBoolean(tagTypeId != null)
        tagTypeId?.let { buf.writeString(it) }
        buf.writeBoolean(boundPos != null)
        boundPos?.let { buf.writeBlockPos(it) }
        buf.writeString(filterSummary)
        buf.writeString(augmentSummary)
        buf.writeVarInt(carriedItemDescs.size)
        carriedItemDescs.forEach { buf.writeString(it) }
        buf.writeBoolean(isFainted)
    }

    companion object {
        fun readFromBuf(buf: PacketByteBuf): PalSnapshot = PalSnapshot(
            pokemonId = buf.readUuid(),
            displayName = buf.readString(),
            species = buf.readString(),
            level = buf.readVarInt(),
            tagTypeId = if (buf.readBoolean()) buf.readString() else null,
            boundPos = if (buf.readBoolean()) buf.readBlockPos() else null,
            filterSummary = buf.readString(),
            augmentSummary = buf.readString(),
            carriedItemDescs = (0 until buf.readVarInt()).map { buf.readString() },
            isFainted = buf.readBoolean()
        )
    }
}

data class PastureSnapshot(
    val pasturePos: BlockPos,
    val pals: List<PalSnapshot>,
    val maxWorkers: Int,
    val ownerName: String
) {
    fun writeToBuf(buf: PacketByteBuf) {
        buf.writeBlockPos(pasturePos)
        buf.writeVarInt(pals.size)
        pals.forEach { it.writeToBuf(buf) }
        buf.writeVarInt(maxWorkers)
        buf.writeString(ownerName)
    }

    companion object {
        fun readFromBuf(buf: PacketByteBuf): PastureSnapshot = PastureSnapshot(
            pasturePos = buf.readBlockPos(),
            pals = (0 until buf.readVarInt()).map { PalSnapshot.readFromBuf(buf) },
            maxWorkers = buf.readVarInt(),
            ownerName = buf.readString()
        )
    }
}

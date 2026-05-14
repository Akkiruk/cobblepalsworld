package com.cobblepalsworld.gui.pasture

import com.cobblepalsworld.behavior.state.WorkerPhase
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
    val isFainted: Boolean,
    val phaseOrdinal: Int,
    val activeTargetPos: BlockPos?,
    val cooldownTicksRemaining: Int,
    val searchDelayTicksRemaining: Int,
    val carriedItemCount: Int,
    val carriedSlotCount: Int,
    val primaryCarriedItemId: String?,
    val isEcoMode: Boolean,
    val isManagedByCommandPost: Boolean
) {
    fun workerPhase(): WorkerPhase? = WorkerPhase.entries.getOrNull(phaseOrdinal)

    fun isActive(): Boolean = workerPhase()?.let { it != WorkerPhase.IDLE } == true

    fun hasCargo(): Boolean = carriedItemCount > 0

    fun statusLabel(): String = when {
        isFainted -> "Fainted"
        tagTypeId == null -> "Unassigned"
        workerPhase() == WorkerPhase.NAVIGATING -> "Moving"
        workerPhase() == WorkerPhase.ARRIVING -> "Arriving"
        workerPhase() == WorkerPhase.WORKING -> "Working"
        workerPhase() == WorkerPhase.DEPOSITING -> "Returning"
        hasCargo() -> "Loaded"
        cooldownTicksRemaining > 0 -> "Cooldown"
        searchDelayTicksRemaining > 0 -> "Awaiting"
        isEcoMode -> "Eco Idle"
        else -> "Ready"
    }

    fun sortRank(): Int = when {
        isFainted -> 5
        tagTypeId == null -> 4
        isActive() -> 0
        hasCargo() -> 1
        cooldownTicksRemaining > 0 || searchDelayTicksRemaining > 0 -> 2
        isEcoMode -> 3
        else -> 2
    }

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
        buf.writeVarInt(phaseOrdinal)
        buf.writeBoolean(activeTargetPos != null)
        activeTargetPos?.let { buf.writeBlockPos(it) }
        buf.writeVarInt(cooldownTicksRemaining)
        buf.writeVarInt(searchDelayTicksRemaining)
        buf.writeVarInt(carriedItemCount)
        buf.writeVarInt(carriedSlotCount)
        buf.writeBoolean(primaryCarriedItemId != null)
        primaryCarriedItemId?.let { buf.writeString(it) }
        buf.writeBoolean(isEcoMode)
        buf.writeBoolean(isManagedByCommandPost)
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
            isFainted = buf.readBoolean(),
            phaseOrdinal = buf.readVarInt(),
            activeTargetPos = if (buf.readBoolean()) buf.readBlockPos() else null,
            cooldownTicksRemaining = buf.readVarInt(),
            searchDelayTicksRemaining = buf.readVarInt(),
            carriedItemCount = buf.readVarInt(),
            carriedSlotCount = buf.readVarInt(),
            primaryCarriedItemId = if (buf.readBoolean()) buf.readString() else null,
            isEcoMode = buf.readBoolean(),
            isManagedByCommandPost = buf.readBoolean()
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

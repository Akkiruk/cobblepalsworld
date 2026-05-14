package com.cobblepalsworld.gui.pasture

import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.behavior.state.WorkerStatusKind
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.pasture.WorkerAssignmentMode
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
    val isManagedByCommandPost: Boolean,
    val assignmentModeOrdinal: Int,
    val allowFallback: Boolean,
    val statusReasonOrdinal: Int,
    val statusDetail: String
) {
    fun workerPhase(): WorkerPhase? = WorkerPhase.entries.getOrNull(phaseOrdinal)

    fun statusReason(): WorkerStatusReason? = WorkerStatusReason.entries.getOrNull(statusReasonOrdinal)

    fun assignmentMode(): WorkerAssignmentMode = WorkerAssignmentMode.fromOrdinal(assignmentModeOrdinal)

    fun statusKind(): WorkerStatusKind? = statusReason()?.kind

    fun isActive(): Boolean = !isFainted && tagTypeId != null && statusKind() == WorkerStatusKind.ACTIVE

    fun hasCargo(): Boolean = carriedItemCount > 0

    fun isReady(): Boolean = !isFainted && tagTypeId != null && statusKind() == WorkerStatusKind.READY

    fun isWaiting(): Boolean = !isFainted && tagTypeId != null && statusKind() == WorkerStatusKind.WAITING

    fun isBlocked(): Boolean = !isFainted && tagTypeId != null && statusKind() == WorkerStatusKind.BLOCKED

    fun isStandby(): Boolean = !isFainted && tagTypeId != null && statusKind() == WorkerStatusKind.STANDBY

    fun assignmentLabel(): String = when {
        assignmentMode() == WorkerAssignmentMode.RESERVED -> "Reserved"
        assignmentMode() == WorkerAssignmentMode.PREFERRED && !allowFallback -> "Restricted"
        assignmentMode() == WorkerAssignmentMode.PREFERRED -> "Preferred"
        !allowFallback -> "Strict"
        else -> "General"
    }

    fun assignmentDetail(): String = when {
        assignmentMode() == WorkerAssignmentMode.RESERVED -> "Held out of general labor until released"
        assignmentMode() == WorkerAssignmentMode.PREFERRED && !allowFallback -> "Preferred for this role with fallback disabled"
        assignmentMode() == WorkerAssignmentMode.PREFERRED -> "Preferred for this role with fallback enabled"
        !allowFallback -> "Fallback disabled for this assignment"
        else -> "Runs as part of the general crew pool"
    }

    fun statusLabel(): String = when {
        isFainted -> "Fainted"
        tagTypeId == null -> "Unassigned"
        statusReason() != null -> statusReason()!!.label
        else -> "Ready"
    }

    fun statusDetailOrFallback(): String = when {
        isFainted -> "This pal must be healed before it can work again."
        tagTypeId == null -> "Assign a tag to give this pal a job."
        statusReason() == WorkerStatusReason.RESERVED_DUTY -> "Held in reserve until you release it back into general labor."
        statusReason() == WorkerStatusReason.ROLE_LOCKED -> "A restricted worker is currently holding this role."
        statusReason() == WorkerStatusReason.PATH_BUDGET -> "Queued behind the pasture pathing budget before it can move."
        statusReason() == WorkerStatusReason.MOVEMENT_RECOVERY -> "Trying to hop forward or reseat to a nearby safe spot."
        statusDetail.isNotBlank() -> statusDetail
        statusReason() == WorkerStatusReason.READY -> "Ready to look for a new assignment."
        hasCargo() -> "Holding cargo and ready to return it."
        else -> "No additional worker detail is available yet."
    }

    fun sortRank(): Int = when {
        isFainted -> 6
        tagTypeId == null -> 5
        isActive() -> 0
        isReady() -> 1
        isWaiting() || hasCargo() -> 2
        isBlocked() -> 3
        isStandby() -> 4
        isEcoMode -> 4
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
        buf.writeVarInt(assignmentModeOrdinal)
        buf.writeBoolean(allowFallback)
        buf.writeVarInt(statusReasonOrdinal)
        buf.writeString(statusDetail)
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
            isManagedByCommandPost = buf.readBoolean(),
            assignmentModeOrdinal = buf.readVarInt(),
            allowFallback = buf.readBoolean(),
            statusReasonOrdinal = buf.readVarInt(),
            statusDetail = buf.readString()
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

package com.cobblepalsworld.gui.crew

import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.behavior.state.WorkerStatusKind
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.assignment.WorkerAssignmentMode
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.math.BlockPos
import java.util.UUID

data class CommandPostCrewMemberSnapshot(
    val pokemonId: UUID,
    val displayName: String,
    val species: String,
    val speciesIdentifier: String,
    val aspects: Set<String>,
    val heldItemId: String,
    val level: Int,
    val sourceType: String,
    val boxIndex: Int,
    val slotIndex: Int,
    val tagTypeId: String?,
    val isFainted: Boolean,
    val isMissing: Boolean,
    val hasEntity: Boolean,
    val phaseOrdinal: Int,
    val statusReasonOrdinal: Int,
    val statusDetail: String,
    val carriedItemCount: Int,
    val carriedSlotCount: Int,
    val cargoSummary: String,
    val assignmentModeOrdinal: Int,
    val allowFallback: Boolean
) {
    fun phase(): WorkerPhase? = WorkerPhase.entries.getOrNull(phaseOrdinal)

    fun statusReason(): WorkerStatusReason? = WorkerStatusReason.entries.getOrNull(statusReasonOrdinal)

    fun assignmentMode(): WorkerAssignmentMode = WorkerAssignmentMode.fromOrdinal(assignmentModeOrdinal)

    fun sourceLabel(): String = when (sourceType) {
        CrewSourceType.PARTY.name -> "Party ${slotIndex + 1}"
        CrewSourceType.PC.name -> "Box ${boxIndex + 1} Slot ${slotIndex + 1}"
        else -> "Unknown source"
    }

    fun assignmentLabel(): String = when {
        assignmentMode() == WorkerAssignmentMode.RESERVED -> "Reserved"
        assignmentMode() == WorkerAssignmentMode.PREFERRED && !allowFallback -> "Restricted"
        assignmentMode() == WorkerAssignmentMode.PREFERRED -> "Preferred"
        !allowFallback -> "Strict"
        else -> "General"
    }

    fun statusLabel(): String = when {
        isMissing -> "Storage moved"
        isFainted -> "Fainted"
        tagTypeId == null -> "No role"
        statusReason() != null -> statusReason()!!.label
        hasEntity -> "Ready"
        else -> "Awaiting spawn"
    }

    fun statusDetailOrFallback(): String = when {
        isMissing -> "This leased Pokemon was not found in the owner's current Party or PC."
        isFainted -> "This Pokemon must be healed before it can work."
        statusDetail.isNotBlank() -> statusDetail
        tagTypeId == null -> "Install tag cards to give this pal work."
        !hasEntity -> "The Command Post will send this worker out when it is selected for work."
        else -> "Ready for Command Post work."
    }

    fun isActive(): Boolean = !isMissing && !isFainted && tagTypeId != null && statusReason()?.kind == WorkerStatusKind.ACTIVE

    fun isBlocked(): Boolean = !isMissing && !isFainted && tagTypeId != null && statusReason()?.kind == WorkerStatusKind.BLOCKED

    fun isReady(): Boolean = !isMissing && !isFainted && tagTypeId != null && statusReason()?.kind == WorkerStatusKind.READY

    fun sortRank(): Int = when {
        isMissing -> 7
        isFainted -> 6
        tagTypeId == null -> 5
        isActive() -> 0
        isReady() -> 1
        carriedItemCount > 0 -> 2
        isBlocked() -> 3
        else -> 4
    }

    fun writeToBuf(buf: PacketByteBuf) {
        buf.writeUuid(pokemonId)
        buf.writeString(displayName)
        buf.writeString(species)
        buf.writeString(speciesIdentifier)
        buf.writeVarInt(aspects.size)
        aspects.forEach(buf::writeString)
        buf.writeString(heldItemId)
        buf.writeVarInt(level)
        buf.writeString(sourceType)
        buf.writeVarInt(boxIndex)
        buf.writeVarInt(slotIndex)
        buf.writeBoolean(tagTypeId != null)
        tagTypeId?.let(buf::writeString)
        buf.writeBoolean(isFainted)
        buf.writeBoolean(isMissing)
        buf.writeBoolean(hasEntity)
        buf.writeVarInt(phaseOrdinal)
        buf.writeVarInt(statusReasonOrdinal)
        buf.writeString(statusDetail)
        buf.writeVarInt(carriedItemCount)
        buf.writeVarInt(carriedSlotCount)
        buf.writeString(cargoSummary)
        buf.writeVarInt(assignmentModeOrdinal)
        buf.writeBoolean(allowFallback)
    }

    companion object {
        fun readFromBuf(buf: PacketByteBuf): CommandPostCrewMemberSnapshot = CommandPostCrewMemberSnapshot(
            pokemonId = buf.readUuid(),
            displayName = buf.readString(),
            species = buf.readString(),
            speciesIdentifier = buf.readString(),
            aspects = (0 until buf.readVarInt()).map { buf.readString() }.toSet(),
            heldItemId = buf.readString(),
            level = buf.readVarInt(),
            sourceType = buf.readString(),
            boxIndex = buf.readVarInt(),
            slotIndex = buf.readVarInt(),
            tagTypeId = if (buf.readBoolean()) buf.readString() else null,
            isFainted = buf.readBoolean(),
            isMissing = buf.readBoolean(),
            hasEntity = buf.readBoolean(),
            phaseOrdinal = buf.readVarInt(),
            statusReasonOrdinal = buf.readVarInt(),
            statusDetail = buf.readString(),
            carriedItemCount = buf.readVarInt(),
            carriedSlotCount = buf.readVarInt(),
            cargoSummary = buf.readString(),
            assignmentModeOrdinal = buf.readVarInt(),
            allowFallback = buf.readBoolean()
        )
    }
}

data class CommandPostCrewSnapshot(
    val routerPos: BlockPos,
    val members: List<CommandPostCrewMemberSnapshot>,
    val maxWorkers: Int,
    val assignedCount: Int,
    val activeCount: Int
) {
    fun writeToBuf(buf: PacketByteBuf) {
        buf.writeBlockPos(routerPos)
        buf.writeVarInt(members.size)
        members.forEach { it.writeToBuf(buf) }
        buf.writeVarInt(maxWorkers)
        buf.writeVarInt(assignedCount)
        buf.writeVarInt(activeCount)
    }

    companion object {
        fun readFromBuf(buf: PacketByteBuf): CommandPostCrewSnapshot = CommandPostCrewSnapshot(
            routerPos = buf.readBlockPos(),
            members = (0 until buf.readVarInt()).map { CommandPostCrewMemberSnapshot.readFromBuf(buf) },
            maxWorkers = buf.readVarInt(),
            assignedCount = buf.readVarInt(),
            activeCount = buf.readVarInt()
        )
    }
}

package com.cobblepalsworld.gui.crew

import net.minecraft.network.PacketByteBuf
import java.util.UUID

enum class CrewSourceType(val label: String) {
    PARTY("Party"),
    PC("PC");

    companion object {
        fun fromOrdinal(ordinal: Int): CrewSourceType = entries.getOrNull(ordinal) ?: PARTY
    }
}

data class CrewSourcePokemonSnapshot(
    val pokemonId: UUID,
    val sourceType: CrewSourceType,
    val boxIndex: Int,
    val slotIndex: Int,
    val displayName: String,
    val species: String,
    val level: Int,
    val isFainted: Boolean,
    val isCrewMember: Boolean,
    val tagTypeId: String?,
    val workStatus: String,
    val cargoSummary: String,
    val isAvailable: Boolean,
    val unavailableReason: String
) {
    fun sourceLabel(): String = when (sourceType) {
        CrewSourceType.PARTY -> "Party ${slotIndex + 1}"
        CrewSourceType.PC -> "Box ${boxIndex + 1} / Slot ${slotIndex + 1}"
    }

    fun statusLabel(): String = when {
        isFainted -> "Fainted"
        isCrewMember && workStatus.isNotBlank() -> workStatus
        !isAvailable -> unavailableReason.ifBlank { "Unavailable" }
        else -> "Ready"
    }

    fun writeToBuf(buf: PacketByteBuf) {
        buf.writeUuid(pokemonId)
        buf.writeVarInt(sourceType.ordinal)
        buf.writeVarInt(boxIndex)
        buf.writeVarInt(slotIndex)
        buf.writeString(displayName)
        buf.writeString(species)
        buf.writeVarInt(level)
        buf.writeBoolean(isFainted)
        buf.writeBoolean(isCrewMember)
        buf.writeBoolean(tagTypeId != null)
        tagTypeId?.let(buf::writeString)
        buf.writeString(workStatus)
        buf.writeString(cargoSummary)
        buf.writeBoolean(isAvailable)
        buf.writeString(unavailableReason)
    }

    companion object {
        fun readFromBuf(buf: PacketByteBuf): CrewSourcePokemonSnapshot = CrewSourcePokemonSnapshot(
            pokemonId = buf.readUuid(),
            sourceType = CrewSourceType.fromOrdinal(buf.readVarInt()),
            boxIndex = buf.readVarInt(),
            slotIndex = buf.readVarInt(),
            displayName = buf.readString(),
            species = buf.readString(),
            level = buf.readVarInt(),
            isFainted = buf.readBoolean(),
            isCrewMember = buf.readBoolean(),
            tagTypeId = if (buf.readBoolean()) buf.readString() else null,
            workStatus = buf.readString(),
            cargoSummary = buf.readString(),
            isAvailable = buf.readBoolean(),
            unavailableReason = buf.readString()
        )
    }
}

data class CrewSourceSlotSnapshot(
    val sourceType: CrewSourceType,
    val boxIndex: Int,
    val slotIndex: Int,
    val pokemon: CrewSourcePokemonSnapshot?
) {
    fun writeToBuf(buf: PacketByteBuf) {
        buf.writeVarInt(sourceType.ordinal)
        buf.writeVarInt(boxIndex)
        buf.writeVarInt(slotIndex)
        buf.writeBoolean(pokemon != null)
        pokemon?.writeToBuf(buf)
    }

    companion object {
        fun readFromBuf(buf: PacketByteBuf): CrewSourceSlotSnapshot {
            val sourceType = CrewSourceType.fromOrdinal(buf.readVarInt())
            val boxIndex = buf.readVarInt()
            val slotIndex = buf.readVarInt()
            val pokemon = if (buf.readBoolean()) CrewSourcePokemonSnapshot.readFromBuf(buf) else null
            return CrewSourceSlotSnapshot(sourceType, boxIndex, slotIndex, pokemon)
        }
    }
}

data class CrewSourceBoxSnapshot(
    val boxIndex: Int,
    val label: String,
    val slots: List<CrewSourceSlotSnapshot>
) {
    val occupiedCount: Int get() = slots.count { it.pokemon != null }

    fun writeToBuf(buf: PacketByteBuf) {
        buf.writeVarInt(boxIndex)
        buf.writeString(label)
        buf.writeVarInt(slots.size)
        slots.forEach { it.writeToBuf(buf) }
    }

    companion object {
        fun readFromBuf(buf: PacketByteBuf): CrewSourceBoxSnapshot {
            val boxIndex = buf.readVarInt()
            val label = buf.readString()
            return CrewSourceBoxSnapshot(
                boxIndex = boxIndex,
                label = label,
                slots = (0 until buf.readVarInt()).map { CrewSourceSlotSnapshot.readFromBuf(buf) }
            )
        }
    }
}

data class CrewSourceSnapshot(
    val sourceType: CrewSourceType,
    val boxCount: Int,
    val slotCount: Int,
    val boxes: List<CrewSourceBoxSnapshot>
) {
    val entries: List<CrewSourcePokemonSnapshot> get() = boxes.flatMap { box -> box.slots.mapNotNull { it.pokemon } }

    fun writeToBuf(buf: PacketByteBuf) {
        buf.writeVarInt(sourceType.ordinal)
        buf.writeVarInt(boxCount)
        buf.writeVarInt(slotCount)
        buf.writeVarInt(boxes.size)
        boxes.forEach { it.writeToBuf(buf) }
    }

    companion object {
        fun readFromBuf(buf: PacketByteBuf): CrewSourceSnapshot {
            val sourceType = CrewSourceType.fromOrdinal(buf.readVarInt())
            val boxCount = buf.readVarInt()
            val slotCount = buf.readVarInt()
            return CrewSourceSnapshot(
                sourceType = sourceType,
                boxCount = boxCount,
                slotCount = slotCount,
                boxes = (0 until buf.readVarInt()).map { CrewSourceBoxSnapshot.readFromBuf(buf) }
            )
        }
    }
}
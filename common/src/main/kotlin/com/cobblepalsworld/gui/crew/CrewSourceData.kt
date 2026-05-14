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
    val isAvailable: Boolean,
    val unavailableReason: String
) {
    fun sourceLabel(): String = when (sourceType) {
        CrewSourceType.PARTY -> "Party ${slotIndex + 1}"
        CrewSourceType.PC -> "Box ${boxIndex + 1} / Slot ${slotIndex + 1}"
    }

    fun statusLabel(): String = when {
        isFainted -> "Fainted"
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
            isAvailable = buf.readBoolean(),
            unavailableReason = buf.readString()
        )
    }
}

data class CrewSourceSnapshot(
    val sourceType: CrewSourceType,
    val entries: List<CrewSourcePokemonSnapshot>
) {
    fun writeToBuf(buf: PacketByteBuf) {
        buf.writeVarInt(sourceType.ordinal)
        buf.writeVarInt(entries.size)
        entries.forEach { it.writeToBuf(buf) }
    }

    companion object {
        fun readFromBuf(buf: PacketByteBuf): CrewSourceSnapshot {
            val sourceType = CrewSourceType.fromOrdinal(buf.readVarInt())
            return CrewSourceSnapshot(
                sourceType = sourceType,
                entries = (0 until buf.readVarInt()).map { CrewSourcePokemonSnapshot.readFromBuf(buf) }
            )
        }
    }
}
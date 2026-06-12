package com.cobblepalsworld.networking.packets

import com.cobblepalsworld.CobblePalsWorld
import dev.architectury.networking.NetworkManager
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

data class WorkerVisualSnapshot(
    val entityId: Int,
    val tagTypeId: String,
    val phaseOrdinal: Int,
    val statusReasonOrdinal: Int,
    val primaryCarriedItemId: String?,
    val carriedItemCount: Int
) {
    fun writeToBuf(buf: RegistryByteBuf) {
        buf.writeVarInt(entityId)
        buf.writeString(tagTypeId)
        buf.writeVarInt(phaseOrdinal)
        buf.writeVarInt(statusReasonOrdinal)
        buf.writeBoolean(primaryCarriedItemId != null)
        primaryCarriedItemId?.let(buf::writeString)
        buf.writeVarInt(carriedItemCount)
    }

    companion object {
        fun readFromBuf(buf: RegistryByteBuf): WorkerVisualSnapshot {
            val entityId = buf.readVarInt()
            val tagTypeId = buf.readString()
            val phaseOrdinal = buf.readVarInt()
            val statusReasonOrdinal = buf.readVarInt()
            val primaryCarriedItemId = if (buf.readBoolean()) buf.readString() else null
            val carriedItemCount = buf.readVarInt()
            return WorkerVisualSnapshot(
                entityId = entityId,
                tagTypeId = tagTypeId,
                phaseOrdinal = phaseOrdinal,
                statusReasonOrdinal = statusReasonOrdinal,
                primaryCarriedItemId = primaryCarriedItemId,
                carriedItemCount = carriedItemCount
            )
        }
    }
}

class WorkerVisualsS2C(val worksitePos: BlockPos, val visuals: List<WorkerVisualSnapshot>) : CustomPayload {
    override fun getId() = TYPE

    companion object {
        val TYPE = CustomPayload.Id<WorkerVisualsS2C>(Identifier.of(CobblePalsWorld.MODID, "worker_visuals"))
        val CODEC = object : PacketCodec<RegistryByteBuf, WorkerVisualsS2C> {
            override fun encode(buf: RegistryByteBuf, value: WorkerVisualsS2C) {
                buf.writeBlockPos(value.worksitePos)
                buf.writeVarInt(value.visuals.size)
                value.visuals.forEach { it.writeToBuf(buf) }
            }

            override fun decode(buf: RegistryByteBuf): WorkerVisualsS2C {
                val worksitePos = buf.readBlockPos()
                val size = buf.readVarInt()
                val visuals = ArrayList<WorkerVisualSnapshot>(size)
                repeat(size) {
                    visuals += WorkerVisualSnapshot.readFromBuf(buf)
                }
                return WorkerVisualsS2C(worksitePos, visuals)
            }
        }

        fun handle(payload: WorkerVisualsS2C, context: NetworkManager.PacketContext, onWorkerVisuals: (BlockPos, List<WorkerVisualSnapshot>) -> Unit) {
            context.queue { onWorkerVisuals(payload.worksitePos, payload.visuals) }
        }
    }
}

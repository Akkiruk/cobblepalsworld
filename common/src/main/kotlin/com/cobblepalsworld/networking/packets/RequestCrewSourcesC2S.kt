package com.cobblepalsworld.networking.packets

import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.gui.crew.CrewSourceType
import dev.architectury.networking.NetworkManager
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

class RequestCrewSourcesC2S(
    val routerPos: BlockPos,
    val sourceType: CrewSourceType,
    val boxIndex: Int,
    val query: String
) : CustomPayload {
    override fun getId() = TYPE

    companion object {
        val TYPE = CustomPayload.Id<RequestCrewSourcesC2S>(Identifier.of(CobblePalsWorld.MODID, "request_crew_sources"))
        val CODEC = object : PacketCodec<RegistryByteBuf, RequestCrewSourcesC2S> {
            override fun encode(buf: RegistryByteBuf, value: RequestCrewSourcesC2S) {
                buf.writeBlockPos(value.routerPos)
                buf.writeVarInt(value.sourceType.ordinal)
                buf.writeVarInt(value.boxIndex)
                buf.writeString(value.query)
            }

            override fun decode(buf: RegistryByteBuf): RequestCrewSourcesC2S {
                return RequestCrewSourcesC2S(
                    routerPos = buf.readBlockPos(),
                    sourceType = CrewSourceType.fromOrdinal(buf.readVarInt()),
                    boxIndex = buf.readVarInt(),
                    query = buf.readString()
                )
            }
        }

        fun handle(payload: RequestCrewSourcesC2S, context: NetworkManager.PacketContext) {
            context.queue {
                val player = context.player as? ServerPlayerEntity ?: return@queue
                PacketHelpers.handleCrewSourceRequest(player, payload.routerPos, payload.sourceType, payload.boxIndex, payload.query)
            }
        }
    }
}

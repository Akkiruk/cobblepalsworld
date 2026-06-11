package com.cobblepalsworld.networking.packets

import com.cobblepalsworld.CobblePalsWorld
import dev.architectury.networking.NetworkManager
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

class RequestCommandPostCrewC2S(val routerPos: BlockPos) : CustomPayload {
    override fun getId() = TYPE

    companion object {
        val TYPE = CustomPayload.Id<RequestCommandPostCrewC2S>(Identifier.of(CobblePalsWorld.MODID, "request_command_post_crew"))
        val CODEC = object : PacketCodec<RegistryByteBuf, RequestCommandPostCrewC2S> {
            override fun encode(buf: RegistryByteBuf, value: RequestCommandPostCrewC2S) {
                buf.writeBlockPos(value.routerPos)
            }

            override fun decode(buf: RegistryByteBuf): RequestCommandPostCrewC2S {
                return RequestCommandPostCrewC2S(buf.readBlockPos())
            }
        }

        fun handle(payload: RequestCommandPostCrewC2S, context: NetworkManager.PacketContext) {
            context.queue {
                val player = context.player as? ServerPlayerEntity ?: return@queue
                PacketHelpers.handleCommandPostCrewRequest(player, payload.routerPos)
            }
        }
    }
}

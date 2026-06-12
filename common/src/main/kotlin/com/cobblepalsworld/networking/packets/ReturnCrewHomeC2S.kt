package com.cobblepalsworld.networking.packets

import com.cobblepalsworld.CobblePalsWorld
import dev.architectury.networking.NetworkManager
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import java.util.UUID

class ReturnCrewHomeC2S(val routerPos: BlockPos, val pokemonId: UUID) : CustomPayload {
    override fun getId() = TYPE

    companion object {
        val TYPE = CustomPayload.Id<ReturnCrewHomeC2S>(Identifier.of(CobblePalsWorld.MODID, "return_crew_home"))
        val CODEC = object : PacketCodec<RegistryByteBuf, ReturnCrewHomeC2S> {
            override fun encode(buf: RegistryByteBuf, value: ReturnCrewHomeC2S) {
                buf.writeBlockPos(value.routerPos)
                buf.writeUuid(value.pokemonId)
            }

            override fun decode(buf: RegistryByteBuf): ReturnCrewHomeC2S {
                return ReturnCrewHomeC2S(buf.readBlockPos(), buf.readUuid())
            }
        }

        fun handle(payload: ReturnCrewHomeC2S, context: NetworkManager.PacketContext) {
            context.queue {
                val player = context.player as? ServerPlayerEntity ?: return@queue
                PacketHelpers.handleReturnCrewHome(player, payload.routerPos, payload.pokemonId)
            }
        }
    }
}

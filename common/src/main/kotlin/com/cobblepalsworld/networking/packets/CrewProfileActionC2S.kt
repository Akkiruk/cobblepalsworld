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

class CrewProfileActionC2S(val routerPos: BlockPos, val pokemonId: UUID, val actionId: Int) : CustomPayload {
    override fun getId() = TYPE

    companion object {
        const val ACTION_CYCLE_MODE = 0
        const val ACTION_TOGGLE_FALLBACK = 1
        val TYPE = CustomPayload.Id<CrewProfileActionC2S>(Identifier.of(CobblePalsWorld.MODID, "crew_profile_action"))
        val CODEC = object : PacketCodec<RegistryByteBuf, CrewProfileActionC2S> {
            override fun encode(buf: RegistryByteBuf, value: CrewProfileActionC2S) {
                buf.writeBlockPos(value.routerPos)
                buf.writeUuid(value.pokemonId)
                buf.writeVarInt(value.actionId)
            }

            override fun decode(buf: RegistryByteBuf): CrewProfileActionC2S {
                return CrewProfileActionC2S(buf.readBlockPos(), buf.readUuid(), buf.readVarInt())
            }
        }

        fun handle(payload: CrewProfileActionC2S, context: NetworkManager.PacketContext) {
            context.queue {
                val player = context.player as? ServerPlayerEntity ?: return@queue
                PacketHelpers.handleCrewProfileAction(player, payload.routerPos, payload.pokemonId, payload.actionId)
            }
        }
    }
}

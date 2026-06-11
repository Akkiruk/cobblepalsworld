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

class MutateCrewC2S(val routerPos: BlockPos, val pokemonId: UUID, val addToCrew: Boolean) : CustomPayload {
    override fun getId() = TYPE

    companion object {
        val TYPE = CustomPayload.Id<MutateCrewC2S>(Identifier.of(CobblePalsWorld.MODID, "mutate_crew"))
        val CODEC = object : PacketCodec<RegistryByteBuf, MutateCrewC2S> {
            override fun encode(buf: RegistryByteBuf, value: MutateCrewC2S) {
                buf.writeBlockPos(value.routerPos)
                buf.writeUuid(value.pokemonId)
                buf.writeBoolean(value.addToCrew)
            }

            override fun decode(buf: RegistryByteBuf): MutateCrewC2S {
                return MutateCrewC2S(buf.readBlockPos(), buf.readUuid(), buf.readBoolean())
            }
        }

        fun handle(payload: MutateCrewC2S, context: NetworkManager.PacketContext) {
            context.queue {
                val player = context.player as? ServerPlayerEntity ?: return@queue
                PacketHelpers.handleCrewMutation(player, payload.routerPos, payload.pokemonId, payload.addToCrew)
            }
        }
    }
}

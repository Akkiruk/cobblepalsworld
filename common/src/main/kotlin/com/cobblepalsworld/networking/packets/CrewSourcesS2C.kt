package com.cobblepalsworld.networking.packets

import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.gui.crew.CrewSourceSnapshot
import dev.architectury.networking.NetworkManager
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

class CrewSourcesS2C(val routerPos: BlockPos, val sources: List<CrewSourceSnapshot>) : CustomPayload {
    override fun getId() = TYPE

    companion object {
        val TYPE = CustomPayload.Id<CrewSourcesS2C>(Identifier.of(CobblePalsWorld.MODID, "crew_sources"))
        val CODEC = object : PacketCodec<RegistryByteBuf, CrewSourcesS2C> {
            override fun encode(buf: RegistryByteBuf, value: CrewSourcesS2C) {
                buf.writeBlockPos(value.routerPos)
                buf.writeVarInt(value.sources.size)
                value.sources.forEach { it.writeToBuf(buf) }
            }

            override fun decode(buf: RegistryByteBuf): CrewSourcesS2C {
                val routerPos = buf.readBlockPos()
                return CrewSourcesS2C(
                    routerPos = routerPos,
                    sources = (0 until buf.readVarInt()).map { CrewSourceSnapshot.readFromBuf(buf) }
                )
            }
        }

        fun handle(payload: CrewSourcesS2C, context: NetworkManager.PacketContext, onCrewSources: (BlockPos, List<CrewSourceSnapshot>) -> Unit) {
            context.queue { onCrewSources(payload.routerPos, payload.sources) }
        }
    }
}

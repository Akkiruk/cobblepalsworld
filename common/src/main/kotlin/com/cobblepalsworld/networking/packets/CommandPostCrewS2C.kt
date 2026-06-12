package com.cobblepalsworld.networking.packets

import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.gui.crew.CommandPostCrewSnapshot
import dev.architectury.networking.NetworkManager
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

class CommandPostCrewS2C(val snapshot: CommandPostCrewSnapshot) : CustomPayload {
    override fun getId() = TYPE

    companion object {
        val TYPE = CustomPayload.Id<CommandPostCrewS2C>(Identifier.of(CobblePalsWorld.MODID, "command_post_crew"))
        val CODEC = object : PacketCodec<RegistryByteBuf, CommandPostCrewS2C> {
            override fun encode(buf: RegistryByteBuf, value: CommandPostCrewS2C) {
                value.snapshot.writeToBuf(buf)
            }

            override fun decode(buf: RegistryByteBuf): CommandPostCrewS2C {
                return CommandPostCrewS2C(CommandPostCrewSnapshot.readFromBuf(buf))
            }
        }

        fun handle(payload: CommandPostCrewS2C, context: NetworkManager.PacketContext, onCommandPostCrew: (CommandPostCrewSnapshot) -> Unit) {
            context.queue { onCommandPostCrew(payload.snapshot) }
        }
    }
}

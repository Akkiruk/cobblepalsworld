package com.cobblepalsworld.networking

import com.cobblemon.mod.common.api.pasture.PastureLinkManager
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.augment.AugmentType
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.gui.pasture.PalSnapshot
import com.cobblepalsworld.gui.pasture.PastureSnapshot
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.pasture.TagAssignmentManager
import com.cobblepalsworld.behavior.TagExecutionEngine
import dev.architectury.networking.NetworkManager
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import java.util.UUID

object CobblePalsNetworking {

    class OpenManagerC2S : CustomPayload {
        override fun getId() = TYPE
        companion object {
            val TYPE = CustomPayload.Id<OpenManagerC2S>(Identifier.of(CobblePalsWorld.MODID, "open_manager"))
            val CODEC = object : PacketCodec<RegistryByteBuf, OpenManagerC2S> {
                override fun encode(buf: RegistryByteBuf, value: OpenManagerC2S) {}
                override fun decode(buf: RegistryByteBuf) = OpenManagerC2S()
            }
        }
    }

    class ManagerDataS2C(val snapshot: PastureSnapshot) : CustomPayload {
        override fun getId() = TYPE
        companion object {
            val TYPE = CustomPayload.Id<ManagerDataS2C>(Identifier.of(CobblePalsWorld.MODID, "manager_data"))
            val CODEC = object : PacketCodec<RegistryByteBuf, ManagerDataS2C> {
                override fun encode(buf: RegistryByteBuf, value: ManagerDataS2C) {
                    value.snapshot.writeToBuf(buf)
                }
                override fun decode(buf: RegistryByteBuf): ManagerDataS2C {
                    return ManagerDataS2C(PastureSnapshot.readFromBuf(buf))
                }
            }
        }
    }

    class TeleportHomeC2S(val pasturePos: BlockPos, val pokemonId: UUID) : CustomPayload {
        override fun getId() = TYPE
        companion object {
            val TYPE = CustomPayload.Id<TeleportHomeC2S>(Identifier.of(CobblePalsWorld.MODID, "teleport_home"))
            val CODEC = object : PacketCodec<RegistryByteBuf, TeleportHomeC2S> {
                override fun encode(buf: RegistryByteBuf, value: TeleportHomeC2S) {
                    buf.writeBlockPos(value.pasturePos)
                    buf.writeUuid(value.pokemonId)
                }
                override fun decode(buf: RegistryByteBuf) = TeleportHomeC2S(buf.readBlockPos(), buf.readUuid())
            }
        }
    }

    fun registerS2CType() {
        NetworkManager.registerS2CPayloadType(ManagerDataS2C.TYPE, ManagerDataS2C.CODEC)
    }

    fun registerServer() {

        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            OpenManagerC2S.TYPE,
            OpenManagerC2S.CODEC
        ) { _, context ->
            context.queue {
                val player = context.player as? ServerPlayerEntity ?: return@queue
                handleOpenRequest(player)
            }
        }

        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            TeleportHomeC2S.TYPE,
            TeleportHomeC2S.CODEC
        ) { payload, context ->
            context.queue {
                val player = context.player as? ServerPlayerEntity ?: return@queue
                handleTeleportHome(player, payload.pasturePos, payload.pokemonId)
            }
        }
    }

    fun registerClient(onReceive: (PastureSnapshot) -> Unit) {
        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            ManagerDataS2C.TYPE,
            ManagerDataS2C.CODEC
        ) { payload, context ->
            context.queue { onReceive(payload.snapshot) }
        }
    }

    private fun handleOpenRequest(player: ServerPlayerEntity) {
        val link = PastureLinkManager.getLinkByPlayerId(player.uuid) ?: return
        handleOpenRequest(player, link.pos)
    }

    private fun handleOpenRequest(player: ServerPlayerEntity, pasturePos: BlockPos) {
        val world = player.serverWorld
        CobblePalsSaveData.ensureLoaded(world)
        val pos = pasturePos.toImmutable()
        val pasture = world.getBlockEntity(pos) as? PokemonPastureBlockEntity ?: return

        val pals = pasture.tetheredPokemon.mapNotNull { tethering ->
            val pokemon = try { tethering.getPokemon() } catch (_: Exception) { null }
                ?: return@mapNotNull null

            val tag = TagAssignmentManager.get(tethering.pokemonId)
            val inventory = InventoryManager.get(tethering.pokemonId)

            val carriedItemDescs = mutableListOf<String>()
            if (inventory != null) {
                val itemCounts = mutableMapOf<String, Int>()
                for (i in 0 until inventory.size()) {
                    val stack = inventory.getStack(i)
                    if (!stack.isEmpty) {
                        itemCounts[stack.name.string] = (itemCounts[stack.name.string] ?: 0) + stack.count
                    }
                }
                itemCounts.forEach { (name, count) -> carriedItemDescs.add("$name x$count") }
            }

            val filterSummary = if (tag != null && !tag.filter.isEmpty()) {
                val itemCount = tag.filter.items.count { !it.isEmpty }
                val parts = mutableListOf<String>()
                if (itemCount > 0) parts.add("$itemCount item${if (itemCount > 1) "s" else ""}")
                if (tag.filter.matchTags.isNotEmpty()) parts.add("${tag.filter.matchTags.size} tag${if (tag.filter.matchTags.size > 1) "s" else ""}")
                if (tag.filter.matchModIds.isNotEmpty()) parts.add("${tag.filter.matchModIds.size} mod${if (tag.filter.matchModIds.size > 1) "s" else ""}")
                "${parts.joinToString(", ")} (${if (tag.filter.whitelist) "whitelist" else "blacklist"})"
            } else "No filter"

            val augmentSummary = if (tag != null && tag.augments.augments.isNotEmpty()) {
                AugmentType.entries.mapNotNull { type ->
                    val level = tag.augments.getLevel(type)
                    if (level <= 0) null else "${type.name} $level"
                }.joinToString(", ")
            } else ""

            PalSnapshot(
                pokemonId = tethering.pokemonId,
                displayName = pokemon.getDisplayName().string,
                species = pokemon.species.resourceIdentifier.toString(),
                level = pokemon.level,
                tagTypeId = tag?.type?.id,
                boundPos = tag?.boundPos,
                filterSummary = filterSummary,
                augmentSummary = augmentSummary,
                carriedItemDescs = carriedItemDescs,
                isFainted = pokemon.isFainted()
            )
        }

        val snapshot = PastureSnapshot(
            pasturePos = pos,
            pals = pals,
            maxWorkers = ConfigManager.config.general.maxWorkersPerPasture,
            ownerName = pasture.ownerName
        )

        NetworkManager.sendToPlayer(player, ManagerDataS2C(snapshot))
    }

    fun sendOpenRequest() {
        NetworkManager.sendToServer(OpenManagerC2S())
    }

    fun sendTeleportHome(pasturePos: BlockPos, pokemonId: UUID) {
        NetworkManager.sendToServer(TeleportHomeC2S(pasturePos, pokemonId))
    }

    private fun handleTeleportHome(player: ServerPlayerEntity, pasturePos: BlockPos, pokemonId: UUID) {
        val world = player.serverWorld
        CobblePalsSaveData.ensureLoaded(world)
        val targetPasturePos = pasturePos.toImmutable()
        val pasture = world.getBlockEntity(targetPasturePos) as? PokemonPastureBlockEntity ?: return
        if (pasture.ownerName.isNotEmpty() && pasture.ownerName != player.name.string) return

        // Verify this pokemon belongs to this pasture
        val tethering = pasture.tetheredPokemon.find { it.pokemonId == pokemonId } ?: return
        val pokemon = try { tethering.getPokemon() } catch (_: Exception) { return } ?: return
        val entity = pokemon.entity

        // Clean up any active work state
        TagExecutionEngine.cleanup(pokemonId, world, entity?.blockPos ?: targetPasturePos)

        // Teleport to pasture block
        if (entity != null) {
            entity.teleport(targetPasturePos.x + 0.5, targetPasturePos.y + 1.0, targetPasturePos.z + 0.5, false)
            entity.navigation.stop()
        }

        // Re-send updated manager data
        handleOpenRequest(player, targetPasturePos)
    }
}

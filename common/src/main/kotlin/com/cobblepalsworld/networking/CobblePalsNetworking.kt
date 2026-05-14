package com.cobblepalsworld.networking

import com.cobblemon.mod.common.api.pasture.PastureLinkManager
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.augment.AugmentType
import com.cobblepalsworld.behavior.state.StateManager
import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.gui.pasture.PalSnapshot
import com.cobblepalsworld.gui.pasture.PastureSnapshot
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.pasture.TagAssignmentManager
import com.cobblepalsworld.pasture.WorkerAssignmentMode
import com.cobblepalsworld.behavior.TagExecutionEngine
import dev.architectury.networking.NetworkManager
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import java.util.UUID

object CobblePalsNetworking {

    class OpenManagerC2S(val pasturePos: BlockPos? = null) : CustomPayload {
        override fun getId() = TYPE
        companion object {
            val TYPE = CustomPayload.Id<OpenManagerC2S>(Identifier.of(CobblePalsWorld.MODID, "open_manager"))
            val CODEC = object : PacketCodec<RegistryByteBuf, OpenManagerC2S> {
                override fun encode(buf: RegistryByteBuf, value: OpenManagerC2S) {
                    buf.writeBoolean(value.pasturePos != null)
                    value.pasturePos?.let { buf.writeBlockPos(it) }
                }

                override fun decode(buf: RegistryByteBuf): OpenManagerC2S {
                    val pasturePos = if (buf.readBoolean()) buf.readBlockPos() else null
                    return OpenManagerC2S(pasturePos)
                }
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

    class WorkerVisualsS2C(val pasturePos: BlockPos, val visuals: List<WorkerVisualSnapshot>) : CustomPayload {
        override fun getId() = TYPE

        companion object {
            val TYPE = CustomPayload.Id<WorkerVisualsS2C>(Identifier.of(CobblePalsWorld.MODID, "worker_visuals"))
            val CODEC = object : PacketCodec<RegistryByteBuf, WorkerVisualsS2C> {
                override fun encode(buf: RegistryByteBuf, value: WorkerVisualsS2C) {
                    buf.writeBlockPos(value.pasturePos)
                    buf.writeVarInt(value.visuals.size)
                    value.visuals.forEach { it.writeToBuf(buf) }
                }

                override fun decode(buf: RegistryByteBuf): WorkerVisualsS2C {
                    val pasturePos = buf.readBlockPos()
                    val size = buf.readVarInt()
                    val visuals = ArrayList<WorkerVisualSnapshot>(size)
                    repeat(size) {
                        visuals += WorkerVisualSnapshot.readFromBuf(buf)
                    }
                    return WorkerVisualsS2C(pasturePos, visuals)
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
        NetworkManager.registerS2CPayloadType(WorkerVisualsS2C.TYPE, WorkerVisualsS2C.CODEC)
    }

    fun registerServer() {

        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            OpenManagerC2S.TYPE,
            OpenManagerC2S.CODEC
        ) { payload, context ->
            context.queue {
                val player = context.player as? ServerPlayerEntity ?: return@queue
                handleManagerRequest(player, payload.pasturePos)
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

    fun registerClient(
        onReceive: (PastureSnapshot) -> Unit,
        onWorkerVisuals: (BlockPos, List<WorkerVisualSnapshot>) -> Unit
    ) {
        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            ManagerDataS2C.TYPE,
            ManagerDataS2C.CODEC
        ) { payload, context ->
            context.queue { onReceive(payload.snapshot) }
        }

        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            WorkerVisualsS2C.TYPE,
            WorkerVisualsS2C.CODEC
        ) { payload, context ->
            context.queue { onWorkerVisuals(payload.pasturePos, payload.visuals) }
        }
    }

    fun sendWorkerVisuals(players: Collection<ServerPlayerEntity>, pasturePos: BlockPos, visuals: List<WorkerVisualSnapshot>) {
        if (players.isEmpty()) return
        val payload = WorkerVisualsS2C(pasturePos.toImmutable(), visuals)
        players.forEach { player -> NetworkManager.sendToPlayer(player, payload) }
    }

    private fun handleManagerRequest(player: ServerPlayerEntity, requestedPos: BlockPos? = null) {
        val link = PastureLinkManager.getLinkByPlayerId(player.uuid) ?: return
        val linkedPos = link.pos.toImmutable()
        val targetPos = requestedPos?.toImmutable() ?: linkedPos
        if (targetPos != linkedPos) return
        handleOpenRequest(player, targetPos)
    }

    private fun handleOpenRequest(player: ServerPlayerEntity, pasturePos: BlockPos) {
        val world = player.serverWorld
        CobblePalsSaveData.ensureLoaded(world)
        val pos = pasturePos.toImmutable()
        val pasture = world.getBlockEntity(pos) as? PokemonPastureBlockEntity ?: return

        val pals = pasture.tetheredPokemon.mapNotNull { tethering ->
            val pokemon = try { tethering.getPokemon() } catch (_: Exception) { null }
                ?: return@mapNotNull null
            buildPalSnapshot(world, tethering.pokemonId, pokemon)
        }.sortedWith(compareBy<PalSnapshot> { it.sortRank() }.thenBy { it.displayName.lowercase() })

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

    fun sendOpenRequest(pasturePos: BlockPos) {
        NetworkManager.sendToServer(OpenManagerC2S(pasturePos.toImmutable()))
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

    private fun buildPalSnapshot(world: net.minecraft.server.world.ServerWorld, pokemonId: UUID, pokemon: com.cobblemon.mod.common.pokemon.Pokemon): PalSnapshot {
        val assignmentView = TagAssignmentManager.getView(pokemonId)
        val tag = assignmentView?.tag
        val state = StateManager.get(pokemonId)
        val inventory = InventoryManager.get(pokemonId)

        val carriedItemDescs = mutableListOf<String>()
        var carriedItemCount = 0
        var carriedSlotCount = 0
        var primaryCarriedItemId: String? = null
        if (inventory != null) {
            val itemCounts = linkedMapOf<String, Int>()
            for (slot in 0 until inventory.size()) {
                val stack = inventory.getStack(slot)
                if (stack.isEmpty) continue

                carriedSlotCount++
                carriedItemCount += stack.count
                if (primaryCarriedItemId == null) {
                    primaryCarriedItemId = Registries.ITEM.getId(stack.item).toString()
                }
                itemCounts[stack.name.string] = (itemCounts[stack.name.string] ?: 0) + stack.count
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
        } else {
            "No filter"
        }

        val augmentSummary = if (tag != null && tag.augments.augments.isNotEmpty()) {
            AugmentType.entries.mapNotNull { type ->
                val level = tag.augments.getLevel(type)
                if (level <= 0) null else "${type.name} $level"
            }.joinToString(", ")
        } else {
            ""
        }

        val activeTargetPos = when (state?.phase) {
            WorkerPhase.DEPOSITING -> state.depositPos ?: state.targetPos
            null -> null
            else -> state.targetPos ?: state.depositPos
        }

        return PalSnapshot(
            pokemonId = pokemonId,
            displayName = pokemon.getDisplayName().string,
            species = pokemon.species.resourceIdentifier.toString(),
            level = pokemon.level,
            tagTypeId = tag?.type?.id,
            boundPos = tag?.boundPos,
            filterSummary = filterSummary,
            augmentSummary = augmentSummary,
            carriedItemDescs = carriedItemDescs,
            isFainted = pokemon.isFainted(),
            phaseOrdinal = state?.phase?.ordinal ?: -1,
            activeTargetPos = activeTargetPos,
            cooldownTicksRemaining = remainingTicks(state?.cooldownUntil, world.time),
            searchDelayTicksRemaining = remainingTicks(state?.nextTargetSearchTick, world.time),
            carriedItemCount = carriedItemCount,
            carriedSlotCount = carriedSlotCount,
            primaryCarriedItemId = primaryCarriedItemId,
            isEcoMode = state?.ecoMode == true,
            isManagedByCommandPost = assignmentView?.controllerBinding != null,
            assignmentModeOrdinal = assignmentView?.assignmentProfile?.mode?.ordinal ?: WorkerAssignmentMode.GENERAL.ordinal,
            allowFallback = assignmentView?.assignmentProfile?.allowFallback ?: true,
            statusReasonOrdinal = state?.statusReason?.ordinal ?: WorkerStatusReason.READY.ordinal,
            statusDetail = state?.statusDetail ?: ""
        )
    }

    private fun remainingTicks(targetTime: Long?, currentTime: Long): Int {
        if (targetTime == null) return 0
        return (targetTime - currentTime).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}

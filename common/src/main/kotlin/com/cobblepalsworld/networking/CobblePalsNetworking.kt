package com.cobblepalsworld.networking

import com.cobblemon.mod.common.api.pasture.PastureLinkManager
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.crew.CommandPostCrewManager
import com.cobblepalsworld.gui.crew.CrewSourcePokemonSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceType
import com.cobblepalsworld.gui.pasture.PastureSnapshotFactory
import com.cobblepalsworld.gui.pasture.PastureSnapshotCache
import com.cobblepalsworld.gui.pasture.PastureSnapshot
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.pasture.TagAssignmentManager
import com.cobblepalsworld.router.RouterBlockEntity
import com.cobblepalsworld.runtime.ServerScaleRuntime
import dev.architectury.networking.NetworkManager
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
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

    class RequestCrewSourcesC2S(val routerPos: BlockPos) : CustomPayload {
        override fun getId() = TYPE
        companion object {
            val TYPE = CustomPayload.Id<RequestCrewSourcesC2S>(Identifier.of(CobblePalsWorld.MODID, "request_crew_sources"))
            val CODEC = object : PacketCodec<RegistryByteBuf, RequestCrewSourcesC2S> {
                override fun encode(buf: RegistryByteBuf, value: RequestCrewSourcesC2S) {
                    buf.writeBlockPos(value.routerPos)
                }

                override fun decode(buf: RegistryByteBuf): RequestCrewSourcesC2S {
                    return RequestCrewSourcesC2S(buf.readBlockPos())
                }
            }
        }
    }

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
        }
    }

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
        NetworkManager.registerS2CPayloadType(CrewSourcesS2C.TYPE, CrewSourcesS2C.CODEC)
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

        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            RequestCrewSourcesC2S.TYPE,
            RequestCrewSourcesC2S.CODEC
        ) { payload, context ->
            context.queue {
                val player = context.player as? ServerPlayerEntity ?: return@queue
                handleCrewSourceRequest(player, payload.routerPos)
            }
        }

        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            MutateCrewC2S.TYPE,
            MutateCrewC2S.CODEC
        ) { payload, context ->
            context.queue {
                val player = context.player as? ServerPlayerEntity ?: return@queue
                handleCrewMutation(player, payload.routerPos, payload.pokemonId, payload.addToCrew)
            }
        }
    }

    fun registerClient(
        onReceive: (PastureSnapshot) -> Unit,
        onCrewSources: (BlockPos, List<CrewSourceSnapshot>) -> Unit,
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
            CrewSourcesS2C.TYPE,
            CrewSourcesS2C.CODEC
        ) { payload, context ->
            context.queue { onCrewSources(payload.routerPos, payload.sources) }
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

        val snapshot = ServerScaleRuntime.cachedSnapshot(world, pos) { PastureSnapshotFactory.create(world, pasture) }

        NetworkManager.sendToPlayer(player, ManagerDataS2C(snapshot))
    }

    fun sendOpenRequest() {
        PastureSnapshotCache.expectManagerOpen()
        NetworkManager.sendToServer(OpenManagerC2S())
    }

    fun sendOpenRequest(pasturePos: BlockPos) {
        PastureSnapshotCache.expectManagerOpen(pasturePos)
        NetworkManager.sendToServer(OpenManagerC2S(pasturePos.toImmutable()))
    }

    fun sendSnapshotRefresh(pasturePos: BlockPos) {
        NetworkManager.sendToServer(OpenManagerC2S(pasturePos.toImmutable()))
    }

    fun sendCrewSourceRefresh(routerPos: BlockPos) {
        NetworkManager.sendToServer(RequestCrewSourcesC2S(routerPos.toImmutable()))
    }

    fun sendAssignCrewPokemon(routerPos: BlockPos, pokemonId: UUID) {
        NetworkManager.sendToServer(MutateCrewC2S(routerPos.toImmutable(), pokemonId, true))
    }

    fun sendRemoveCrewPokemon(routerPos: BlockPos, pokemonId: UUID) {
        NetworkManager.sendToServer(MutateCrewC2S(routerPos.toImmutable(), pokemonId, false))
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
        ServerScaleRuntime.invalidateSnapshot(world, targetPasturePos)

        // Teleport to pasture block
        if (entity != null) {
            entity.teleport(targetPasturePos.x + 0.5, targetPasturePos.y + 1.0, targetPasturePos.z + 0.5, false)
            entity.navigation.stop()
        }

        // Re-send updated manager data
        handleOpenRequest(player, targetPasturePos)
    }

    private fun handleCrewSourceRequest(player: ServerPlayerEntity, routerPos: BlockPos) {
        val world = player.serverWorld
        CobblePalsSaveData.ensureLoaded(world)
        val router = world.getBlockEntity(routerPos) as? RouterBlockEntity ?: return
        if (!router.canAccess(player)) return

        val registries = player.server.registryManager
        val storage = Cobblemon.storage
        val party = storage.getParty(player.uuid, registries)
        val pc = storage.getPC(player.uuid, registries)
        val dimensionId = world.registryKey.value.toString()
        val controllerPos = router.pos.toImmutable()

        val partyEntries = (0 until party.size())
            .mapNotNull { slot -> party.get(slot)?.toCrewSourceSnapshot(CrewSourceType.PARTY, -1, slot, dimensionId, controllerPos) }

        val pcEntries = buildList {
            pc.boxes.forEachIndexed { boxIndex, box ->
                box.getNonEmptySlots().entries.sortedBy { it.key }.forEach { entry ->
                    add(entry.value.toCrewSourceSnapshot(CrewSourceType.PC, boxIndex, entry.key, dimensionId, controllerPos))
                }
            }
        }

        val sources = listOf(
            CrewSourceSnapshot(CrewSourceType.PARTY, partyEntries),
            CrewSourceSnapshot(CrewSourceType.PC, pcEntries)
        )
        NetworkManager.sendToPlayer(player, CrewSourcesS2C(controllerPos, sources))
    }

    private fun Pokemon.toCrewSourceSnapshot(
        sourceType: CrewSourceType,
        boxIndex: Int,
        slotIndex: Int,
        dimensionId: String,
        controllerPos: BlockPos
    ): CrewSourcePokemonSnapshot {
        val crewBinding = CommandPostCrewManager.bindingFor(uuid)
        val controllerBinding = TagAssignmentManager.getControllerBinding(uuid)
        val alreadyHere = crewBinding?.dimensionId == dimensionId && crewBinding.pos == controllerPos
        val unavailableReason = when {
            isFainted() -> "Needs healing"
            alreadyHere -> "In crew"
            crewBinding != null -> "Other post"
            controllerBinding != null -> "Assigned"
            else -> ""
        }
        return CrewSourcePokemonSnapshot(
            pokemonId = uuid,
            sourceType = sourceType,
            boxIndex = boxIndex,
            slotIndex = slotIndex,
            displayName = getDisplayName(false).string,
            species = species.name,
            level = level,
            isFainted = isFainted(),
            isCrewMember = alreadyHere,
            isAvailable = unavailableReason.isBlank(),
            unavailableReason = unavailableReason
        )
    }

    private fun handleCrewMutation(player: ServerPlayerEntity, routerPos: BlockPos, pokemonId: UUID, addToCrew: Boolean) {
        val world = player.serverWorld
        CobblePalsSaveData.ensureLoaded(world)
        val router = world.getBlockEntity(routerPos) as? RouterBlockEntity ?: return
        if (!router.canAccess(player)) return
        if (!playerOwnsPokemon(player, pokemonId)) return
        router.setOwner(player)

        val dimensionId = world.registryKey.value.toString()
        val controllerPos = router.pos.toImmutable()
        val changed = if (addToCrew) {
            val pokemon = findOwnedPokemon(player, pokemonId) ?: return
            if (pokemon.isFainted()) return
            CommandPostCrewManager.assign(pokemonId, dimensionId, controllerPos)
        } else {
            val removed = CommandPostCrewManager.remove(pokemonId, dimensionId, controllerPos)
            if (removed) {
                TagExecutionEngine.cleanup(pokemonId, world, controllerPos)
                TagAssignmentManager.removeIfControlledBy(pokemonId, dimensionId, controllerPos)
                router.removeAssignedWorker(pokemonId)
            }
            removed
        }

        if (changed) {
            CobblePalsSaveData.markDirty(world)
            router.markDirty()
        }
        handleCrewSourceRequest(player, controllerPos)
    }

    private fun playerOwnsPokemon(player: ServerPlayerEntity, pokemonId: UUID): Boolean = findOwnedPokemon(player, pokemonId) != null

    private fun findOwnedPokemon(player: ServerPlayerEntity, pokemonId: UUID): Pokemon? {
        val registries = player.server.registryManager
        val storage = Cobblemon.storage
        storage.getParty(player.uuid, registries).firstOrNull { it.uuid == pokemonId }?.let { return it }
        storage.getPC(player.uuid, registries).firstOrNull { it.uuid == pokemonId }?.let { return it }
        return null
    }
}

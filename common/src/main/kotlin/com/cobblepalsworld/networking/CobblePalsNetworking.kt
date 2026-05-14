package com.cobblepalsworld.networking

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.pc.POKEMON_PER_BOX
import com.cobblemon.mod.common.api.storage.party.PartyPosition
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.crew.CommandPostCrewLifecycle
import com.cobblepalsworld.crew.CommandPostCrewManager
import com.cobblepalsworld.behavior.state.StateManager
import com.cobblepalsworld.gui.crew.CrewSourceBoxSnapshot
import com.cobblepalsworld.gui.crew.CrewSourcePokemonSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceSlotSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceType
import com.cobblepalsworld.gui.crew.CommandPostCrewSnapshot
import com.cobblepalsworld.gui.crew.CommandPostCrewSnapshotFactory
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.assignment.TagAssignmentManager
import com.cobblepalsworld.router.RouterBlockEntity
import dev.architectury.networking.NetworkManager
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import java.util.UUID

object CobblePalsNetworking {
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
        }
    }

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
        }
    }

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

    class WorkerVisualsS2C(val worksitePos: BlockPos, val visuals: List<WorkerVisualSnapshot>) : CustomPayload {
        override fun getId() = TYPE

        companion object {
            val TYPE = CustomPayload.Id<WorkerVisualsS2C>(Identifier.of(CobblePalsWorld.MODID, "worker_visuals"))
            val CODEC = object : PacketCodec<RegistryByteBuf, WorkerVisualsS2C> {
                override fun encode(buf: RegistryByteBuf, value: WorkerVisualsS2C) {
                    buf.writeBlockPos(value.worksitePos)
                    buf.writeVarInt(value.visuals.size)
                    value.visuals.forEach { it.writeToBuf(buf) }
                }

                override fun decode(buf: RegistryByteBuf): WorkerVisualsS2C {
                    val worksitePos = buf.readBlockPos()
                    val size = buf.readVarInt()
                    val visuals = ArrayList<WorkerVisualSnapshot>(size)
                    repeat(size) {
                        visuals += WorkerVisualSnapshot.readFromBuf(buf)
                    }
                    return WorkerVisualsS2C(worksitePos, visuals)
                }
            }
        }
    }

    fun registerS2CType() {
        NetworkManager.registerS2CPayloadType(CrewSourcesS2C.TYPE, CrewSourcesS2C.CODEC)
        NetworkManager.registerS2CPayloadType(CommandPostCrewS2C.TYPE, CommandPostCrewS2C.CODEC)
        NetworkManager.registerS2CPayloadType(WorkerVisualsS2C.TYPE, WorkerVisualsS2C.CODEC)
    }

    fun registerServer() {
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
            RequestCommandPostCrewC2S.TYPE,
            RequestCommandPostCrewC2S.CODEC
        ) { payload, context ->
            context.queue {
                val player = context.player as? ServerPlayerEntity ?: return@queue
                handleCommandPostCrewRequest(player, payload.routerPos)
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

        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            ReturnCrewHomeC2S.TYPE,
            ReturnCrewHomeC2S.CODEC
        ) { payload, context ->
            context.queue {
                val player = context.player as? ServerPlayerEntity ?: return@queue
                handleReturnCrewHome(player, payload.routerPos, payload.pokemonId)
            }
        }

        NetworkManager.registerReceiver(
            NetworkManager.Side.C2S,
            CrewProfileActionC2S.TYPE,
            CrewProfileActionC2S.CODEC
        ) { payload, context ->
            context.queue {
                val player = context.player as? ServerPlayerEntity ?: return@queue
                handleCrewProfileAction(player, payload.routerPos, payload.pokemonId, payload.actionId)
            }
        }
    }

    fun registerClient(
        onCrewSources: (BlockPos, List<CrewSourceSnapshot>) -> Unit,
        onCommandPostCrew: (CommandPostCrewSnapshot) -> Unit,
        onWorkerVisuals: (BlockPos, List<WorkerVisualSnapshot>) -> Unit
    ) {
        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            CrewSourcesS2C.TYPE,
            CrewSourcesS2C.CODEC
        ) { payload, context ->
            context.queue { onCrewSources(payload.routerPos, payload.sources) }
        }

        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            CommandPostCrewS2C.TYPE,
            CommandPostCrewS2C.CODEC
        ) { payload, context ->
            context.queue { onCommandPostCrew(payload.snapshot) }
        }

        NetworkManager.registerReceiver(
            NetworkManager.Side.S2C,
            WorkerVisualsS2C.TYPE,
            WorkerVisualsS2C.CODEC
        ) { payload, context ->
            context.queue { onWorkerVisuals(payload.worksitePos, payload.visuals) }
        }
    }

    fun sendWorkerVisuals(players: Collection<ServerPlayerEntity>, worksitePos: BlockPos, visuals: List<WorkerVisualSnapshot>) {
        if (players.isEmpty()) return
        val payload = WorkerVisualsS2C(worksitePos.toImmutable(), visuals)
        players.forEach { player -> NetworkManager.sendToPlayer(player, payload) }
    }

    fun sendCrewSourceRefresh(routerPos: BlockPos) {
        NetworkManager.sendToServer(RequestCrewSourcesC2S(routerPos.toImmutable()))
    }

    fun sendCommandPostCrewRefresh(routerPos: BlockPos) {
        NetworkManager.sendToServer(RequestCommandPostCrewC2S(routerPos.toImmutable()))
    }

    fun sendAssignCrewPokemon(routerPos: BlockPos, pokemonId: UUID) {
        NetworkManager.sendToServer(MutateCrewC2S(routerPos.toImmutable(), pokemonId, true))
    }

    fun sendRemoveCrewPokemon(routerPos: BlockPos, pokemonId: UUID) {
        NetworkManager.sendToServer(MutateCrewC2S(routerPos.toImmutable(), pokemonId, false))
    }

    fun sendReturnCrewPokemon(routerPos: BlockPos, pokemonId: UUID) {
        NetworkManager.sendToServer(ReturnCrewHomeC2S(routerPos.toImmutable(), pokemonId))
    }

    fun sendCycleCrewMode(routerPos: BlockPos, pokemonId: UUID) {
        NetworkManager.sendToServer(CrewProfileActionC2S(routerPos.toImmutable(), pokemonId, CrewProfileActionC2S.ACTION_CYCLE_MODE))
    }

    fun sendToggleCrewFallback(routerPos: BlockPos, pokemonId: UUID) {
        NetworkManager.sendToServer(CrewProfileActionC2S(routerPos.toImmutable(), pokemonId, CrewProfileActionC2S.ACTION_TOGGLE_FALLBACK))
    }


    private fun handleCrewSourceRequest(player: ServerPlayerEntity, routerPos: BlockPos) {
        val world = player.serverWorld
        CobblePalsSaveData.ensureLoaded(world)
        val router = world.getBlockEntity(routerPos) as? RouterBlockEntity ?: return
        if (!router.canAccess(player)) return

        val registries = player.server.registryManager
        val storage = Cobblemon.storage
        val sourceOwnerUuid = router.ownerUuid() ?: player.uuid
        val party = storage.getParty(sourceOwnerUuid, registries)
        val pc = storage.getPC(sourceOwnerUuid, registries)
        val dimensionId = world.registryKey.value.toString()
        val controllerPos = router.pos.toImmutable()

        val partySlots = (0 until party.size()).map { slot ->
            CrewSourceSlotSnapshot(
                sourceType = CrewSourceType.PARTY,
                boxIndex = -1,
                slotIndex = slot,
                pokemon = party.get(slot)?.toCrewSourceSnapshot(CrewSourceType.PARTY, -1, slot, dimensionId, controllerPos)
            )
        }

        val pcBoxes = pc.boxes.mapIndexed { boxIndex, box ->
            val slots = (0 until POKEMON_PER_BOX).map { slot ->
                CrewSourceSlotSnapshot(
                    sourceType = CrewSourceType.PC,
                    boxIndex = boxIndex,
                    slotIndex = slot,
                    pokemon = box[slot]?.toCrewSourceSnapshot(CrewSourceType.PC, boxIndex, slot, dimensionId, controllerPos)
                )
            }
            CrewSourceBoxSnapshot(boxIndex, box.name ?: "Box ${boxIndex + 1}", slots)
        }

        val sources = listOf(
            CrewSourceSnapshot(
                sourceType = CrewSourceType.PARTY,
                boxCount = 1,
                slotCount = partySlots.size,
                boxes = listOf(CrewSourceBoxSnapshot(-1, "Party", partySlots))
            ),
            CrewSourceSnapshot(
                sourceType = CrewSourceType.PC,
                boxCount = pc.boxes.size,
                slotCount = POKEMON_PER_BOX,
                boxes = pcBoxes
            )
        )
        NetworkManager.sendToPlayer(player, CrewSourcesS2C(controllerPos, sources))
    }

    private fun handleCommandPostCrewRequest(player: ServerPlayerEntity, routerPos: BlockPos) {
        val world = player.serverWorld
        CobblePalsSaveData.ensureLoaded(world)
        val router = world.getBlockEntity(routerPos) as? RouterBlockEntity ?: return
        if (!router.canAccess(player)) return
        NetworkManager.sendToPlayer(player, CommandPostCrewS2C(CommandPostCrewSnapshotFactory.create(world, router)))
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
        val assignmentView = TagAssignmentManager.getView(uuid)
        val state = StateManager.get(uuid)
        val inventory = InventoryManager.get(uuid)
        val alreadyHere = crewBinding?.dimensionId == dimensionId && crewBinding.pos == controllerPos
        val cargoSummary = inventory?.let { inv ->
            val carried = (0 until inv.size()).sumOf { slot -> inv.getStack(slot).count }
            if (carried > 0) "Cargo $carried" else ""
        } ?: ""
        val workStatus = when {
            !alreadyHere -> ""
            state?.statusReason != null -> state.statusReason.label
            assignmentView?.tag != null -> "Assigned"
            else -> "Crew"
        }
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
            tagTypeId = assignmentView?.tag?.type?.id,
            workStatus = workStatus,
            cargoSummary = cargoSummary,
            isAvailable = unavailableReason.isBlank(),
            unavailableReason = unavailableReason
        )
    }

    private fun handleCrewMutation(player: ServerPlayerEntity, routerPos: BlockPos, pokemonId: UUID, addToCrew: Boolean) {
        val world = player.serverWorld
        CobblePalsSaveData.ensureLoaded(world)
        val router = world.getBlockEntity(routerPos) as? RouterBlockEntity ?: return
        if (!router.canAccess(player)) return
        val locatedPokemon = locateOwnedPokemon(player, pokemonId) ?: return
        router.setOwner(player)

        val dimensionId = world.registryKey.value.toString()
        val controllerPos = router.pos.toImmutable()
        val changed = if (addToCrew) {
            val pokemon = locatedPokemon.pokemon
            if (pokemon.isFainted()) return
            if (CommandPostCrewManager.bindingFor(pokemonId) != null) return
            val crewSource = if (locatedPokemon.sourceType == CrewSourceType.PARTY) {
                movePartyPokemonToPc(player, locatedPokemon) ?: return
            } else {
                locatedPokemon
            }
            CommandPostCrewManager.assign(
                pokemonId = pokemonId,
                ownerUuid = player.uuid,
                dimensionId = dimensionId,
                pos = controllerPos,
                sourceType = crewSource.sourceType.name,
                boxIndex = crewSource.boxIndex,
                slotIndex = crewSource.slotIndex,
                displayName = pokemon.getDisplayName(false).string,
                species = pokemon.species.name,
                level = pokemon.level
            )
        } else {
            val removed = CommandPostCrewManager.remove(pokemonId, dimensionId, controllerPos)
            if (removed != null) {
                TagExecutionEngine.cleanup(pokemonId, world, controllerPos)
                TagAssignmentManager.removeIfControlledBy(pokemonId, dimensionId, controllerPos)
                router.removeAssignedWorker(pokemonId)
                CommandPostCrewLifecycle.recall(world, removed, player.uuid)
            }
            removed != null
        }

        if (changed) {
            CobblePalsSaveData.markDirty(world)
            router.markDirty()
        }
        handleCommandPostCrewRequest(player, controllerPos)
        handleCrewSourceRequest(player, controllerPos)
    }

    private fun movePartyPokemonToPc(player: ServerPlayerEntity, locatedPokemon: LocatedPokemon): LocatedPokemon? {
        val registries = player.server.registryManager
        val storage = Cobblemon.storage
        val party = storage.getParty(player.uuid, registries)
        val pc = storage.getPC(player.uuid, registries)
        val pokemon = party.get(locatedPokemon.slotIndex) ?: return null
        if (pokemon.uuid != locatedPokemon.pokemon.uuid) return null
        if (party.filterNotNull().size == 1 && Cobblemon.config.preventCompletePartyDeposit) return null
        val pcPosition = pc.getFirstAvailablePosition() ?: return null
        party.remove(PartyPosition(locatedPokemon.slotIndex))
        pc[pcPosition] = pokemon
        return LocatedPokemon(pokemon, CrewSourceType.PC, pcPosition.box, pcPosition.slot)
    }

    private fun handleReturnCrewHome(player: ServerPlayerEntity, routerPos: BlockPos, pokemonId: UUID) {
        val world = player.serverWorld
        CobblePalsSaveData.ensureLoaded(world)
        val router = world.getBlockEntity(routerPos) as? RouterBlockEntity ?: return
        if (!router.canAccess(player)) return

        val dimensionId = world.registryKey.value.toString()
        val controllerPos = router.pos.toImmutable()
        val member = CommandPostCrewManager.memberFor(pokemonId) ?: return
        if (member.binding.dimensionId != dimensionId || member.binding.pos != controllerPos) return

        TagExecutionEngine.cleanup(pokemonId, world, controllerPos)
        CommandPostCrewLifecycle.returnHome(world, controllerPos, member, router.ownerUuid() ?: player.uuid)
        handleCommandPostCrewRequest(player, controllerPos)
        handleCrewSourceRequest(player, controllerPos)
    }

    private fun handleCrewProfileAction(player: ServerPlayerEntity, routerPos: BlockPos, pokemonId: UUID, actionId: Int) {
        val world = player.serverWorld
        CobblePalsSaveData.ensureLoaded(world)
        val router = world.getBlockEntity(routerPos) as? RouterBlockEntity ?: return
        if (!router.canAccess(player)) return

        val dimensionId = world.registryKey.value.toString()
        val controllerPos = router.pos.toImmutable()
        val member = CommandPostCrewManager.memberFor(pokemonId) ?: return
        if (member.binding.dimensionId != dimensionId || member.binding.pos != controllerPos) return

        val current = TagAssignmentManager.getProfile(pokemonId)
        when (actionId) {
            CrewProfileActionC2S.ACTION_CYCLE_MODE -> {
                val nextMode = com.cobblepalsworld.assignment.WorkerAssignmentMode.entries[
                    (current.mode.ordinal + 1) % com.cobblepalsworld.assignment.WorkerAssignmentMode.entries.size
                ]
                TagAssignmentManager.updateProfile(pokemonId, mode = nextMode)
            }
            CrewProfileActionC2S.ACTION_TOGGLE_FALLBACK -> {
                TagAssignmentManager.updateProfile(pokemonId, allowFallback = !current.allowFallback)
            }
            else -> return
        }

        CobblePalsSaveData.markDirty(world)
        router.markDirty()
        handleCommandPostCrewRequest(player, controllerPos)
        handleCrewSourceRequest(player, controllerPos)
    }

    private fun locateOwnedPokemon(player: ServerPlayerEntity, pokemonId: UUID): LocatedPokemon? {
        val registries = player.server.registryManager
        val storage = Cobblemon.storage
        val party = storage.getParty(player.uuid, registries)
        for (slot in 0 until party.size()) {
            val pokemon = party.get(slot) ?: continue
            if (pokemon.uuid == pokemonId) {
                return LocatedPokemon(pokemon, CrewSourceType.PARTY, -1, slot)
            }
        }
        val pc = storage.getPC(player.uuid, registries)
        pc.boxes.forEachIndexed { boxIndex, box ->
            for (slot in 0 until POKEMON_PER_BOX) {
                val pokemon = box[slot] ?: continue
                if (pokemon.uuid == pokemonId) {
                    return LocatedPokemon(pokemon, CrewSourceType.PC, boxIndex, slot)
                }
            }
        }
        return null
    }

    private data class LocatedPokemon(
        val pokemon: Pokemon,
        val sourceType: CrewSourceType,
        val boxIndex: Int,
        val slotIndex: Int
    )
}

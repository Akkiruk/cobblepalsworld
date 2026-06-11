package com.cobblepalsworld.networking.packets

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.pc.POKEMON_PER_BOX
import com.cobblemon.mod.common.api.storage.party.PartyPosition
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblepalsworld.assignment.TagAssignmentManager
import com.cobblepalsworld.assignment.WorkerAssignmentMode
import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.crew.CommandPostCrewLifecycle
import com.cobblepalsworld.crew.CommandPostCrewManager
import com.cobblepalsworld.gui.crew.CommandPostCrewSnapshotFactory
import com.cobblepalsworld.gui.crew.CrewSourceBoxSnapshot
import com.cobblepalsworld.gui.crew.CrewSourcePokemonSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceSlotSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceType
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.router.RouterBlockEntity
import com.cobblepalsworld.session.WorkerSessionManager
import dev.architectury.networking.NetworkManager
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import java.util.UUID

internal object PacketHelpers {
    fun handleCrewSourceRequest(player: ServerPlayerEntity, routerPos: BlockPos, requestedSourceType: CrewSourceType = CrewSourceType.PC, requestedBoxIndex: Int = 0, query: String = "") {
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
        val normalizedQuery = query.trim().lowercase()

        val partySlots = (0 until party.size()).map { slot ->
            val pokemon = party.get(slot)?.toCrewSourceSnapshot(CrewSourceType.PARTY, -1, slot, dimensionId, controllerPos)
            CrewSourceSlotSnapshot(
                sourceType = CrewSourceType.PARTY,
                boxIndex = -1,
                slotIndex = slot,
                pokemon = pokemon?.takeIf { it.matchesSourceQuery(normalizedQuery) }
            )
        }

        val source = if (requestedSourceType == CrewSourceType.PARTY) {
            CrewSourceSnapshot(
                sourceType = CrewSourceType.PARTY,
                boxCount = 1,
                slotCount = partySlots.size,
                boxes = listOf(CrewSourceBoxSnapshot(-1, "Party", partySlots))
            )
        } else {
            val boxCount = pc.boxes.size
            val boxIndex = requestedBoxIndex.coerceIn(0, (boxCount - 1).coerceAtLeast(0))
            val box = pc.boxes.getOrNull(boxIndex)
            val slots = (0 until POKEMON_PER_BOX).map { slot ->
                val pokemon = box?.get(slot)?.toCrewSourceSnapshot(CrewSourceType.PC, boxIndex, slot, dimensionId, controllerPos)
                CrewSourceSlotSnapshot(
                    sourceType = CrewSourceType.PC,
                    boxIndex = boxIndex,
                    slotIndex = slot,
                    pokemon = pokemon?.takeIf { it.matchesSourceQuery(normalizedQuery) }
                )
            }
            CrewSourceSnapshot(
                sourceType = CrewSourceType.PC,
                boxCount = boxCount,
                slotCount = POKEMON_PER_BOX,
                boxes = listOf(CrewSourceBoxSnapshot(boxIndex, box?.name ?: "Box ${boxIndex + 1}", slots))
            )
        }
        NetworkManager.sendToPlayer(player, CrewSourcesS2C(controllerPos, listOf(source)))
    }

    private fun CrewSourcePokemonSnapshot.matchesSourceQuery(query: String): Boolean {
        if (query.isBlank()) return true
        return displayName.lowercase().contains(query) ||
            species.lowercase().contains(query) ||
            sourceLabel().lowercase().contains(query) ||
            statusLabel().lowercase().contains(query) ||
            tagTypeId?.lowercase()?.contains(query) == true
    }

    fun handleCommandPostCrewRequest(player: ServerPlayerEntity, routerPos: BlockPos) {
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
        val state = WorkerSessionManager.getState(uuid)
        val inventory = InventoryManager.get(uuid)
        val alreadyHere = crewBinding?.dimensionId == dimensionId && crewBinding.pos == controllerPos
        val cargoSummary = inventory?.let { inv ->
            val carried = (0 until inv.size()).sumOf { slot -> inv.getStack(slot).count }
            if (carried.coerceAtLeast(1) == carried) "Cargo $carried" else ""
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
            speciesIdentifier = species.resourceIdentifier.toString(),
            aspects = aspects,
            heldItemId = heldItem().takeUnless { it.isEmpty }?.let { net.minecraft.registry.Registries.ITEM.getId(it.item).toString() } ?: "",
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

    fun handleCrewMutation(player: ServerPlayerEntity, routerPos: BlockPos, pokemonId: UUID, addToCrew: Boolean) {
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
            val maxWorkers = ConfigManager.config.general.maxWorkersPerPasture
            val currentWorkers = CommandPostCrewManager.countAt(dimensionId, controllerPos)
            if (maxOf(currentWorkers, maxWorkers) == currentWorkers) return
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
                speciesIdentifier = pokemon.species.resourceIdentifier.toString(),
                aspects = pokemon.aspects,
                heldItemId = pokemon.heldItem().takeUnless { it.isEmpty }?.let { net.minecraft.registry.Registries.ITEM.getId(it.item).toString() } ?: "",
                level = pokemon.level
            )
        } else {
            val removed = CommandPostCrewManager.remove(pokemonId, dimensionId, controllerPos)
            if (removed != null) {
                TagExecutionEngine.cleanup(pokemonId, world, controllerPos)
                TagAssignmentManager.removeIfControlledBy(pokemonId, dimensionId, controllerPos)
                TagAssignmentManager.resetProfile(pokemonId)
                router.removeAssignedWorker(pokemonId)
                CommandPostCrewLifecycle.releaseFromCommandPost(world, controllerPos, removed, player.uuid)
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

    fun handleReturnCrewHome(player: ServerPlayerEntity, routerPos: BlockPos, pokemonId: UUID) {
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

    fun handleCrewProfileAction(player: ServerPlayerEntity, routerPos: BlockPos, pokemonId: UUID, actionId: Int) {
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
                val nextMode = WorkerAssignmentMode.entries[
                    (current.mode.ordinal + 1) % WorkerAssignmentMode.entries.size
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

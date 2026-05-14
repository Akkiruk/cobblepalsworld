package com.cobblepalsworld.gui.crew

import com.cobblepalsworld.behavior.state.StateManager
import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.crew.CommandPostCrewLifecycle
import com.cobblepalsworld.crew.CommandPostCrewManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.assignment.TagAssignmentManager
import com.cobblepalsworld.assignment.WorkerAssignmentMode
import com.cobblepalsworld.router.RouterBlockEntity
import net.minecraft.registry.Registries
import net.minecraft.server.world.ServerWorld

object CommandPostCrewSnapshotFactory {
    fun create(world: ServerWorld, router: RouterBlockEntity): CommandPostCrewSnapshot {
        val dimensionId = world.registryKey.value.toString()
        val routerPos = router.pos.toImmutable()
        val fallbackOwnerUuid = router.ownerUuid()
        val members = CommandPostCrewManager.findMembers(dimensionId, routerPos)
            .map { member ->
                val pokemon = CommandPostCrewLifecycle.resolvePokemon(world, member, fallbackOwnerUuid)
                val assignmentView = TagAssignmentManager.getView(member.pokemonId)
                val state = StateManager.get(member.pokemonId)
                val inventory = InventoryManager.get(member.pokemonId)
                var carriedItemCount = 0
                var carriedSlotCount = 0
                val carriedDescriptions = mutableListOf<String>()
                if (inventory != null) {
                    val itemCounts = linkedMapOf<String, Int>()
                    for (slot in 0 until inventory.size()) {
                        val stack = inventory.getStack(slot)
                        if (stack.isEmpty) continue
                        carriedSlotCount++
                        carriedItemCount += stack.count
                        val itemId = Registries.ITEM.getId(stack.item).toString()
                        itemCounts[itemId] = (itemCounts[itemId] ?: 0) + stack.count
                    }
                    itemCounts.entries.take(2).forEach { (id, count) -> carriedDescriptions += "${id.substringAfter(':')} x$count" }
                }
                val entity = pokemon?.entity
                val hasEntity = entity != null && entity.world === world && !entity.isRemoved
                CommandPostCrewMemberSnapshot(
                    pokemonId = member.pokemonId,
                    displayName = pokemon?.getDisplayName(false)?.string ?: member.displayName,
                    species = pokemon?.species?.resourceIdentifier?.toString() ?: member.species,
                    level = pokemon?.level ?: member.level,
                    sourceType = member.sourceType,
                    boxIndex = member.boxIndex,
                    slotIndex = member.slotIndex,
                    tagTypeId = assignmentView?.tag?.type?.id,
                    isFainted = pokemon?.isFainted() == true,
                    isMissing = pokemon == null,
                    hasEntity = hasEntity,
                    phaseOrdinal = state?.phase?.ordinal ?: WorkerPhase.IDLE.ordinal,
                    statusReasonOrdinal = state?.statusReason?.ordinal ?: WorkerStatusReason.READY.ordinal,
                    statusDetail = state?.statusDetail ?: "",
                    carriedItemCount = carriedItemCount,
                    carriedSlotCount = carriedSlotCount,
                    cargoSummary = if (carriedDescriptions.isEmpty()) "Cargo empty" else carriedDescriptions.joinToString(", "),
                    assignmentModeOrdinal = assignmentView?.assignmentProfile?.mode?.ordinal ?: WorkerAssignmentMode.GENERAL.ordinal,
                    allowFallback = assignmentView?.assignmentProfile?.allowFallback ?: true
                )
            }
            .sortedWith(compareBy<CommandPostCrewMemberSnapshot> { it.sortRank() }.thenBy { it.displayName.lowercase() })

        val assignedCount = (0 until RouterBlockEntity.MODULE_SLOT_COUNT).count { router.assignedWorker(it) != null }
        val activeCount = members.count { member -> StateManager.get(member.pokemonId)?.phase?.let { it != WorkerPhase.IDLE } == true }
        return CommandPostCrewSnapshot(
            routerPos = routerPos,
            members = members,
            maxWorkers = ConfigManager.config.general.maxWorkersPerPasture,
            assignedCount = assignedCount,
            activeCount = activeCount
        )
    }
}

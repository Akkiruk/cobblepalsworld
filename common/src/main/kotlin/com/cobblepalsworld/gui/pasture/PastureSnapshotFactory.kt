package com.cobblepalsworld.gui.pasture

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblepalsworld.augment.AugmentType
import com.cobblepalsworld.behavior.state.StateManager
import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.pasture.TagAssignmentManager
import com.cobblepalsworld.pasture.WorkerAssignmentMode
import net.minecraft.registry.Registries
import net.minecraft.server.world.ServerWorld
import java.util.UUID

object PastureSnapshotFactory {
    fun create(world: ServerWorld, pasture: PokemonPastureBlockEntity): PastureSnapshot {
        val pals = pasture.tetheredPokemon.mapNotNull { tethering ->
            val pokemon = try { tethering.getPokemon() } catch (_: Exception) { null }
                ?: return@mapNotNull null
            buildPalSnapshot(world, tethering.pokemonId, pokemon)
        }.sortedWith(compareBy<PalSnapshot> { it.sortRank() }.thenBy { it.displayName.lowercase() })

        return PastureSnapshot(
            pasturePos = pasture.pos.toImmutable(),
            pals = pals,
            maxWorkers = ConfigManager.config.general.maxWorkersPerPasture,
            ownerName = pasture.ownerName
        )
    }

    private fun buildPalSnapshot(
        world: ServerWorld,
        pokemonId: UUID,
        pokemon: com.cobblemon.mod.common.pokemon.Pokemon
    ): PalSnapshot {
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
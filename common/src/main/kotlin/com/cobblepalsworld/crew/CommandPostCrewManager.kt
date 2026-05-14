package com.cobblepalsworld.crew

import net.minecraft.util.math.BlockPos
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class CommandPostCrewBinding(val dimensionId: String, val pos: BlockPos)

object CommandPostCrewManager {
    private val crewByPost = ConcurrentHashMap<CommandPostCrewBinding, MutableSet<UUID>>()
    private val postByPokemon = ConcurrentHashMap<UUID, CommandPostCrewBinding>()

    fun assign(pokemonId: UUID, dimensionId: String, pos: BlockPos): Boolean {
        val binding = CommandPostCrewBinding(dimensionId, pos.toImmutable())
        val current = postByPokemon[pokemonId]
        if (current == binding) return false
        if (current != null) return false

        crewByPost.getOrPut(binding) { ConcurrentHashMap.newKeySet() }.add(pokemonId)
        postByPokemon[pokemonId] = binding
        return true
    }

    fun remove(pokemonId: UUID, dimensionId: String, pos: BlockPos): Boolean {
        val binding = CommandPostCrewBinding(dimensionId, pos.toImmutable())
        if (postByPokemon[pokemonId] != binding) return false

        postByPokemon.remove(pokemonId, binding)
        crewByPost[binding]?.let { crew ->
            crew.remove(pokemonId)
            if (crew.isEmpty()) {
                crewByPost.remove(binding, crew)
            }
        }
        return true
    }

    fun bindingFor(pokemonId: UUID): CommandPostCrewBinding? = postByPokemon[pokemonId]

    fun isCrewMember(pokemonId: UUID, dimensionId: String, pos: BlockPos): Boolean {
        return postByPokemon[pokemonId] == CommandPostCrewBinding(dimensionId, pos.toImmutable())
    }

    fun findCrew(dimensionId: String, pos: BlockPos): Set<UUID> {
        return crewByPost[CommandPostCrewBinding(dimensionId, pos.toImmutable())]?.toSet().orEmpty()
    }

    fun countAt(dimensionId: String, pos: BlockPos): Int = crewByPost[CommandPostCrewBinding(dimensionId, pos.toImmutable())]?.size ?: 0

    fun forEachPost(action: (CommandPostCrewBinding, Set<UUID>) -> Unit) {
        crewByPost.forEach { (binding, ids) -> action(binding, ids.toSet()) }
    }

    fun load(binding: CommandPostCrewBinding, pokemonIds: Iterable<UUID>) {
        val immutableBinding = binding.copy(pos = binding.pos.toImmutable())
        pokemonIds.forEach { pokemonId ->
            if (postByPokemon.putIfAbsent(pokemonId, immutableBinding) == null) {
                crewByPost.getOrPut(immutableBinding) { ConcurrentHashMap.newKeySet() }.add(pokemonId)
            }
        }
    }

    fun clear() {
        crewByPost.clear()
        postByPokemon.clear()
    }
}
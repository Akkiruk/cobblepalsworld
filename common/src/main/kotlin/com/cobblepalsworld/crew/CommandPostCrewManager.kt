package com.cobblepalsworld.crew

import net.minecraft.util.math.BlockPos
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class CommandPostCrewBinding(val dimensionId: String, val pos: BlockPos)

data class CommandPostCrewMember(
    val pokemonId: UUID,
    val ownerUuid: UUID,
    val binding: CommandPostCrewBinding,
    val sourceType: String,
    val boxIndex: Int,
    val slotIndex: Int
)

object CommandPostCrewManager {
    private val crewByPost = ConcurrentHashMap<CommandPostCrewBinding, MutableSet<UUID>>()
    private val memberByPokemon = ConcurrentHashMap<UUID, CommandPostCrewMember>()

    fun assign(
        pokemonId: UUID,
        ownerUuid: UUID,
        dimensionId: String,
        pos: BlockPos,
        sourceType: String,
        boxIndex: Int,
        slotIndex: Int
    ): Boolean {
        val binding = CommandPostCrewBinding(dimensionId, pos.toImmutable())
        val current = memberByPokemon[pokemonId]?.binding
        if (current == binding) return false
        if (current != null) return false

        val member = CommandPostCrewMember(pokemonId, ownerUuid, binding, sourceType, boxIndex, slotIndex)
        crewByPost.getOrPut(binding) { ConcurrentHashMap.newKeySet() }.add(pokemonId)
        memberByPokemon[pokemonId] = member
        return true
    }

    fun remove(pokemonId: UUID, dimensionId: String, pos: BlockPos): CommandPostCrewMember? {
        val binding = CommandPostCrewBinding(dimensionId, pos.toImmutable())
        val member = memberByPokemon[pokemonId] ?: return null
        if (member.binding != binding) return null

        memberByPokemon.remove(pokemonId, member)
        crewByPost[binding]?.let { crew ->
            crew.remove(pokemonId)
            if (crew.isEmpty()) {
                crewByPost.remove(binding, crew)
            }
        }
        return member
    }

    fun bindingFor(pokemonId: UUID): CommandPostCrewBinding? = memberByPokemon[pokemonId]?.binding

    fun memberFor(pokemonId: UUID): CommandPostCrewMember? = memberByPokemon[pokemonId]

    fun isCrewMember(pokemonId: UUID, dimensionId: String, pos: BlockPos): Boolean {
        return memberByPokemon[pokemonId]?.binding == CommandPostCrewBinding(dimensionId, pos.toImmutable())
    }

    fun findCrew(dimensionId: String, pos: BlockPos): Set<UUID> {
        return crewByPost[CommandPostCrewBinding(dimensionId, pos.toImmutable())]?.toSet().orEmpty()
    }

    fun findMembers(dimensionId: String, pos: BlockPos): List<CommandPostCrewMember> {
        return findCrew(dimensionId, pos).mapNotNull(memberByPokemon::get)
    }

    fun countAt(dimensionId: String, pos: BlockPos): Int = crewByPost[CommandPostCrewBinding(dimensionId, pos.toImmutable())]?.size ?: 0

    fun forEachPost(action: (CommandPostCrewBinding, List<CommandPostCrewMember>) -> Unit) {
        crewByPost.forEach { (binding, ids) -> action(binding, ids.mapNotNull(memberByPokemon::get)) }
    }

    fun load(members: Iterable<CommandPostCrewMember>) {
        members.forEach { member ->
            val immutableMember = member.copy(binding = member.binding.copy(pos = member.binding.pos.toImmutable()))
            if (memberByPokemon.putIfAbsent(immutableMember.pokemonId, immutableMember) == null) {
                crewByPost.getOrPut(immutableMember.binding) { ConcurrentHashMap.newKeySet() }.add(immutableMember.pokemonId)
            }
        }
    }

    fun clear() {
        crewByPost.clear()
        memberByPokemon.clear()
    }
}
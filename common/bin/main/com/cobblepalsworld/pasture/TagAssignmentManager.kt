package com.cobblepalsworld.pasture

import com.cobblepalsworld.tag.TagInstance
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TagAssignmentManager {
    private val assignments = ConcurrentHashMap<UUID, TagInstance>()

    fun assign(pokemonId: UUID, tag: TagInstance) {
        assignments[pokemonId] = tag
    }

    fun get(pokemonId: UUID): TagInstance? = assignments[pokemonId]

    fun remove(pokemonId: UUID): TagInstance? = assignments.remove(pokemonId)

    fun has(pokemonId: UUID): Boolean = assignments.containsKey(pokemonId)

    fun forEach(action: (UUID, TagInstance) -> Unit) = assignments.forEach(action)

    fun clear() = assignments.clear()
}

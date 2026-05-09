package com.cobblepalsworld.behavior.state

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object StateManager {
    private val states = ConcurrentHashMap<UUID, WorkerState>()

    fun getOrCreate(pokemonId: UUID): WorkerState {
        return states.getOrPut(pokemonId) { WorkerState(pokemonId) }
    }

    fun get(pokemonId: UUID): WorkerState? = states[pokemonId]

    fun remove(pokemonId: UUID) {
        states.remove(pokemonId)
    }

    fun clear() = states.clear()
}

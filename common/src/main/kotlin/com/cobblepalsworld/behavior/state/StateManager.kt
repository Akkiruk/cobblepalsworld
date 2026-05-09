package com.cobblepalsworld.behavior.state

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object StateManager {
    private val states = ConcurrentHashMap<UUID, WorkerState>()

    fun getOrCreate(pokemonId: UUID): WorkerState {
        return states.getOrPut(pokemonId) { WorkerState(pokemonId) }
    }

    fun get(pokemonId: UUID): WorkerState? = states[pokemonId]

    fun pruneStale(currentTime: Long, staleAfterTicks: Long) {
        states.entries.removeIf { (_, state) ->
            state.lastSeenTick > 0L && currentTime - state.lastSeenTick > staleAfterTicks
        }
    }

    fun remove(pokemonId: UUID) {
        states.remove(pokemonId)
    }

    fun count(): Int = states.size

    fun clear() = states.clear()
}

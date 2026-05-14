package com.cobblepalsworld.behavior.state

import java.util.UUID
import com.cobblepalsworld.session.WorkerSessionManager

object StateManager {
    fun getOrCreate(pokemonId: UUID): WorkerState {
        return WorkerSessionManager.getOrCreateState(pokemonId)
    }

    fun get(pokemonId: UUID): WorkerState? = WorkerSessionManager.getState(pokemonId)

    fun pruneStale(currentTime: Long, staleAfterTicks: Long): Set<UUID> {
        return WorkerSessionManager.pruneStaleRuntime(currentTime, staleAfterTicks)
    }

    fun remove(pokemonId: UUID) {
        WorkerSessionManager.removeState(pokemonId)
    }

    fun count(): Int = WorkerSessionManager.countStates()

    fun clear() = WorkerSessionManager.clearStates()
}

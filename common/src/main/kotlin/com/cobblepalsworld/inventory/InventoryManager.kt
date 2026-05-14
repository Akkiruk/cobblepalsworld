package com.cobblepalsworld.inventory

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblepalsworld.session.WorkerSessionManager
import java.util.UUID

object InventoryManager {
    fun getOrCreate(pokemon: Pokemon): PokemonInventory {
        return WorkerSessionManager.getOrCreateInventory(pokemon)
    }

    fun get(pokemonId: UUID): PokemonInventory? = WorkerSessionManager.getInventory(pokemonId)

    fun remove(pokemonId: UUID): PokemonInventory? = WorkerSessionManager.removeInventory(pokemonId)

    fun put(pokemonId: UUID, inventory: PokemonInventory) {
        WorkerSessionManager.putInventory(pokemonId, inventory)
    }

    fun count(): Int = WorkerSessionManager.countInventories()

    fun forEach(action: (UUID, PokemonInventory) -> Unit) = WorkerSessionManager.forEachInventory(action)

    fun pruneStale(shouldKeep: (UUID, PokemonInventory) -> Boolean) {
        WorkerSessionManager.pruneInventories(shouldKeep)
    }

    fun clear() = WorkerSessionManager.clearInventories()
}

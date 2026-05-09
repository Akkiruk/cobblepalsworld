package com.cobblepalsworld.inventory

import com.cobblemon.mod.common.pokemon.Pokemon
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object InventoryManager {
    private val inventories = ConcurrentHashMap<UUID, PokemonInventory>()

    fun getOrCreate(pokemon: Pokemon): PokemonInventory {
        return inventories.getOrPut(pokemon.uuid) {
            PokemonInventory(pokemon.uuid, InventorySizeCalculator.calculate(pokemon))
        }
    }

    fun get(pokemonId: UUID): PokemonInventory? = inventories[pokemonId]

    fun remove(pokemonId: UUID): PokemonInventory? = inventories.remove(pokemonId)

    fun put(pokemonId: UUID, inventory: PokemonInventory) {
        inventories[pokemonId] = inventory
    }

    fun forEach(action: (UUID, PokemonInventory) -> Unit) = inventories.forEach(action)

    fun clear() = inventories.clear()
}

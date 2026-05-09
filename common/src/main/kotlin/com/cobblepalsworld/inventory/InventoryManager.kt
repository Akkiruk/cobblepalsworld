package com.cobblepalsworld.inventory

import com.cobblemon.mod.common.pokemon.Pokemon
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object InventoryManager {
    private val inventories = ConcurrentHashMap<UUID, PokemonInventory>()

    fun getOrCreate(pokemon: Pokemon): PokemonInventory {
        val current = inventories[pokemon.uuid]
        val desiredSlots = InventorySizeCalculator.calculate(pokemon)
        if (current == null) {
            return PokemonInventory(pokemon.uuid, desiredSlots).also { inventories[pokemon.uuid] = it }
        }

        if (desiredSlots <= current.size()) {
            return current
        }

        val expanded = PokemonInventory(pokemon.uuid, desiredSlots)
        for (slot in 0 until current.size()) {
            val stack = current.getStack(slot)
            if (!stack.isEmpty) {
                expanded.setStack(slot, stack.copy())
            }
        }
        inventories[pokemon.uuid] = expanded
        return expanded
    }

    fun get(pokemonId: UUID): PokemonInventory? = inventories[pokemonId]

    fun remove(pokemonId: UUID): PokemonInventory? = inventories.remove(pokemonId)

    fun put(pokemonId: UUID, inventory: PokemonInventory) {
        inventories[pokemonId] = inventory
    }

    fun forEach(action: (UUID, PokemonInventory) -> Unit) = inventories.forEach(action)

    fun clear() = inventories.clear()
}

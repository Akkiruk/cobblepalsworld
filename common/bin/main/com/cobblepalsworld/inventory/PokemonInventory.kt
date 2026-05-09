package com.cobblepalsworld.inventory

import net.minecraft.inventory.SimpleInventory
import java.util.UUID

class PokemonInventory(val pokemonId: UUID, slotCount: Int) : SimpleInventory(slotCount) {

    fun isFull(): Boolean = (0 until size()).none { getStack(it).isEmpty }

    fun hasSpace(): Boolean = !isFull()
}

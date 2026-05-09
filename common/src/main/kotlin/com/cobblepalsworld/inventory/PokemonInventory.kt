package com.cobblepalsworld.inventory

import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import java.util.UUID

class PokemonInventory(val pokemonId: UUID, slotCount: Int) : SimpleInventory(slotCount) {

    fun isFull(): Boolean = (0 until size()).none { getStack(it).isEmpty }

    fun hasSpace(): Boolean = !isFull()

    fun nonEmptySlotCount(): Int = (0 until size()).count { !getStack(it).isEmpty }

    fun copyForPlanning(): PokemonInventory {
        val copy = PokemonInventory(pokemonId, size())
        for (slot in 0 until size()) {
            val stack = getStack(slot)
            if (!stack.isEmpty) {
                copy.setStack(slot, stack.copy())
            }
        }
        return copy
    }

    fun insertStack(stack: ItemStack): ItemStack {
        if (stack.isEmpty) return ItemStack.EMPTY
        val remaining = stack.copy()

        for (slot in 0 until size()) {
            val existing = getStack(slot)
            if (existing.isEmpty || !ItemStack.areItemsAndComponentsEqual(existing, remaining)) continue
            if (existing.count >= existing.maxCount) continue

            val transfer = minOf(remaining.count, existing.maxCount - existing.count)
            if (transfer <= 0) continue

            existing.increment(transfer)
            setStack(slot, existing)
            remaining.decrement(transfer)
            if (remaining.isEmpty) return ItemStack.EMPTY
        }

        for (slot in 0 until size()) {
            if (!getStack(slot).isEmpty) continue

            val transfer = minOf(remaining.count, remaining.maxCount)
            setStack(slot, remaining.copyWithCount(transfer))
            remaining.decrement(transfer)
            if (remaining.isEmpty) return ItemStack.EMPTY
        }

        return remaining
    }
}

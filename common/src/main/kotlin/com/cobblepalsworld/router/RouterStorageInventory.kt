package com.cobblepalsworld.router

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack

class RouterStorageInventory(private val router: RouterBlockEntity) : Inventory {
    override fun size(): Int = RouterBlockEntity.STORAGE_SLOT_COUNT

    override fun isEmpty(): Boolean = (0 until size()).all { getStack(it).isEmpty }

    override fun getStack(slot: Int): ItemStack {
        if (slot !in 0 until size()) return ItemStack.EMPTY
        return router.getStack(RouterBlockEntity.STORAGE_SLOT_START + slot)
    }

    override fun removeStack(slot: Int, amount: Int): ItemStack {
        if (slot !in 0 until size()) return ItemStack.EMPTY
        return router.removeStack(RouterBlockEntity.STORAGE_SLOT_START + slot, amount)
    }

    override fun removeStack(slot: Int): ItemStack {
        if (slot !in 0 until size()) return ItemStack.EMPTY
        return router.removeStack(RouterBlockEntity.STORAGE_SLOT_START + slot)
    }

    override fun setStack(slot: Int, stack: ItemStack) {
        if (slot !in 0 until size()) return
        router.setStack(RouterBlockEntity.STORAGE_SLOT_START + slot, stack)
    }

    override fun markDirty() {
        router.markDirty()
    }

    override fun canPlayerUse(player: PlayerEntity): Boolean = router.canPlayerUse(player)

    override fun clear() {
        for (slot in 0 until size()) {
            setStack(slot, ItemStack.EMPTY)
        }
    }
}
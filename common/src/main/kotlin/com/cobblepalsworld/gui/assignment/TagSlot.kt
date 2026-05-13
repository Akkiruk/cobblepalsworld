package com.cobblepalsworld.gui.assignment

import net.minecraft.entity.player.PlayerEntity
import com.cobblepalsworld.tag.TagItem
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

class TagSlot(
    inventory: Inventory,
    index: Int,
    x: Int,
    y: Int,
    private val isLocked: () -> Boolean = { false }
) : Slot(inventory, index, x, y) {
    override fun canInsert(stack: ItemStack): Boolean = !isLocked() && stack.item is TagItem
    override fun canTakeItems(player: PlayerEntity): Boolean = !isLocked()
    override fun getMaxItemCount(): Int = 1
}

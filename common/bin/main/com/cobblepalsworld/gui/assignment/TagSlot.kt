package com.cobblepalsworld.gui.assignment

import com.cobblepalsworld.tag.TagItem
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

class TagSlot(inventory: Inventory, index: Int, x: Int, y: Int) : Slot(inventory, index, x, y) {
    override fun canInsert(stack: ItemStack): Boolean = stack.item is TagItem
    override fun getMaxItemCount(): Int = 1
}

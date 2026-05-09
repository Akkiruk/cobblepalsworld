package com.cobblepalsworld.gui.assignment

import com.cobblepalsworld.augment.AugmentItem
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

class AugmentSlot(inventory: Inventory, index: Int, x: Int, y: Int) : Slot(inventory, index, x, y) {
    override fun canInsert(stack: ItemStack): Boolean = stack.item is AugmentItem
    override fun getMaxItemCount(): Int = 1
}

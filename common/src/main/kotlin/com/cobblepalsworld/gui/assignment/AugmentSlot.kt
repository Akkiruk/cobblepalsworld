package com.cobblepalsworld.gui.assignment

import net.minecraft.entity.player.PlayerEntity
import com.cobblepalsworld.augment.AugmentItem
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

class AugmentSlot(
    inventory: Inventory,
    index: Int,
    x: Int,
    y: Int,
    private val isLocked: () -> Boolean = { false }
) : Slot(inventory, index, x, y) {
    override fun canInsert(stack: ItemStack): Boolean = !isLocked() && stack.item is AugmentItem
    override fun canTakeItems(player: PlayerEntity): Boolean = !isLocked()

    override fun getMaxItemCount(stack: ItemStack): Int {
        val augmentItem = stack.item as? AugmentItem ?: return super.getMaxItemCount(stack)
        return augmentItem.augmentType.maxLevel
    }

    override fun getMaxItemCount(): Int = 3
}

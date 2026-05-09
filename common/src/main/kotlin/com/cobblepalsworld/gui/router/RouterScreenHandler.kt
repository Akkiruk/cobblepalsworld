package com.cobblepalsworld.gui.router

import com.cobblepalsworld.augment.AugmentItem
import com.cobblepalsworld.gui.MenuTypes
import com.cobblepalsworld.router.RouterBlockEntity
import com.cobblepalsworld.tag.TagItem
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ArrayPropertyDelegate
import net.minecraft.screen.PropertyDelegate
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot

class RouterScreenHandler : ScreenHandler {
    private val routerInventory: Inventory
    private val routerData: PropertyDelegate

    companion object {
        const val MODULE_COLUMNS = 3
        const val MODULE_ROWS = 3
        const val PLAYER_INV_Y = 108
        const val COMMAND_SLOT_COUNT = RouterBlockEntity.MODULE_SLOT_COUNT + RouterBlockEntity.UPGRADE_SLOT_COUNT

        fun isCommandCard(stack: ItemStack): Boolean = stack.item is TagItem
    }

    constructor(syncId: Int, playerInventory: PlayerInventory) : super(MenuTypes.ROUTER.get(), syncId) {
        this.routerInventory = SimpleInventory(RouterBlockEntity.TOTAL_SLOTS)
        this.routerData = ArrayPropertyDelegate(4)
        setupSlots(playerInventory)
    }

    constructor(syncId: Int, playerInventory: PlayerInventory, routerInventory: Inventory, routerData: PropertyDelegate) : super(MenuTypes.ROUTER.get(), syncId) {
        checkSize(routerInventory, RouterBlockEntity.TOTAL_SLOTS)
        checkDataCount(routerData, 4)
        this.routerInventory = routerInventory
        this.routerData = routerData
        setupSlots(playerInventory)
    }

    val linked: Boolean get() = routerData.get(0) != 0
    val rosterCount: Int get() = routerData.get(1)
    val assignedCount: Int get() = routerData.get(2)
    val activeCount: Int get() = routerData.get(3)

    private fun setupSlots(playerInventory: PlayerInventory) {
        addProperties(routerData)

        for (row in 0 until MODULE_ROWS) {
            for (col in 0 until MODULE_COLUMNS) {
                val slotIndex = RouterBlockEntity.MODULE_SLOT_START + row * MODULE_COLUMNS + col
                addSlot(object : Slot(routerInventory, slotIndex, 26 + col * 18, 20 + row * 18) {
                    override fun canInsert(stack: ItemStack): Boolean = isCommandCard(stack)
                    override fun getMaxItemCount(): Int = 1
                })
            }
        }

        for (index in 0 until RouterBlockEntity.UPGRADE_SLOT_COUNT) {
            val slotIndex = RouterBlockEntity.UPGRADE_SLOT_START + index
            addSlot(object : Slot(routerInventory, slotIndex, 148, 20 + index * 18) {
                override fun canInsert(stack: ItemStack): Boolean = stack.item is AugmentItem
            })
        }

        for (row in 0..2) {
            for (col in 0..8) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18))
            }
        }

        for (col in 0..8) {
            addSlot(Slot(playerInventory, col, 8 + col * 18, PLAYER_INV_Y + 58))
        }
    }

    override fun quickMove(player: PlayerEntity, slotIndex: Int): ItemStack {
        val slot = slots.getOrNull(slotIndex) ?: return ItemStack.EMPTY
        if (!slot.hasStack()) return ItemStack.EMPTY

        val original = slot.stack
        val moved = original.copy()

        if (slotIndex < COMMAND_SLOT_COUNT) {
            if (!insertItem(original, COMMAND_SLOT_COUNT, slots.size, true)) {
                return ItemStack.EMPTY
            }
        } else {
            val inserted = when {
                isCommandCard(original) -> insertItem(original, 0, RouterBlockEntity.MODULE_SLOT_COUNT, false)
                original.item is AugmentItem -> insertItem(original, RouterBlockEntity.MODULE_SLOT_COUNT, COMMAND_SLOT_COUNT, false)
                else -> false
            }
            if (!inserted) {
                return ItemStack.EMPTY
            }
        }

        if (original.isEmpty) {
            slot.stack = ItemStack.EMPTY
        } else {
            slot.markDirty()
        }

        return moved
    }
    override fun canUse(player: PlayerEntity): Boolean = routerInventory.canPlayerUse(player)
}
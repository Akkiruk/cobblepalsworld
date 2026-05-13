package com.cobblepalsworld.gui.router

import com.cobblepalsworld.augment.AugmentItem
import com.cobblepalsworld.gui.filter.TagFilterScreenHandler
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
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.Slot
import net.minecraft.text.Text

class RouterScreenHandler : ScreenHandler {
    private val routerInventory: Inventory
    private val routerData: PropertyDelegate

    companion object {
        const val BACKGROUND_WIDTH = 230
        const val BACKGROUND_HEIGHT = 320
        const val MODULE_COLUMNS = 3
        const val MODULE_ROWS = 3
        const val MODULE_START_X = 20
        const val MODULE_START_Y = 40
        const val BOOST_START_X = 118
        const val BOOST_START_Y = 104
        const val STORAGE_COLUMNS = 9
        const val STORAGE_ROWS = 3
        const val STORAGE_START_X = 16
        const val STORAGE_START_Y = 164
        const val PLAYER_INV_X = 16
        const val PLAYER_INV_Y = 244
        const val MODULE_SCREEN_SLOT_COUNT = RouterBlockEntity.MODULE_SLOT_COUNT
        const val UPGRADE_SCREEN_SLOT_START = MODULE_SCREEN_SLOT_COUNT
        const val STORAGE_SCREEN_SLOT_START = UPGRADE_SCREEN_SLOT_START + RouterBlockEntity.UPGRADE_SLOT_COUNT
        const val COMMAND_SLOT_COUNT = STORAGE_SCREEN_SLOT_START + RouterBlockEntity.STORAGE_SLOT_COUNT

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
                addSlot(object : Slot(routerInventory, slotIndex, MODULE_START_X + col * 18, MODULE_START_Y + row * 18) {
                    override fun canInsert(stack: ItemStack): Boolean = isCommandCard(stack)
                    override fun getMaxItemCount(): Int = 1
                })
            }
        }

        for (index in 0 until RouterBlockEntity.UPGRADE_SLOT_COUNT) {
            val slotIndex = RouterBlockEntity.UPGRADE_SLOT_START + index
            addSlot(object : Slot(routerInventory, slotIndex, BOOST_START_X + index * 18, BOOST_START_Y) {
                override fun canInsert(stack: ItemStack): Boolean = stack.item is AugmentItem
            })
        }

        for (row in 0 until STORAGE_ROWS) {
            for (col in 0 until STORAGE_COLUMNS) {
                val slotIndex = RouterBlockEntity.STORAGE_SLOT_START + row * STORAGE_COLUMNS + col
                addSlot(Slot(routerInventory, slotIndex, STORAGE_START_X + col * 18, STORAGE_START_Y + row * 18))
            }
        }

        for (row in 0..2) {
            for (col in 0..8) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18))
            }
        }

        for (col in 0..8) {
            addSlot(Slot(playerInventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58))
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
                isCommandCard(original) -> {
                    insertItem(original, 0, MODULE_SCREEN_SLOT_COUNT, false) ||
                        insertItem(original, STORAGE_SCREEN_SLOT_START, COMMAND_SLOT_COUNT, false)
                }
                original.item is AugmentItem -> {
                    insertItem(original, UPGRADE_SCREEN_SLOT_START, STORAGE_SCREEN_SLOT_START, false) ||
                        insertItem(original, STORAGE_SCREEN_SLOT_START, COMMAND_SLOT_COUNT, false)
                }
                else -> insertItem(original, STORAGE_SCREEN_SLOT_START, COMMAND_SLOT_COUNT, false)
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

    override fun onButtonClick(player: PlayerEntity, id: Int): Boolean {
        val moduleIndex = id - 100
        if (moduleIndex !in 0 until RouterBlockEntity.MODULE_SLOT_COUNT) return false

        val inventorySlot = RouterBlockEntity.MODULE_SLOT_START + moduleIndex
        val stack = routerInventory.getStack(inventorySlot)
        if (stack.item !is TagItem) return false

        if (!player.world.isClient) {
            player.openHandledScreen(SimpleNamedScreenHandlerFactory(
                { syncId, inv, _ -> TagFilterScreenHandler(syncId, inv, routerInventory, inventorySlot) },
                Text.translatable("screen.cobblepalsworld.tag_filter")
            ))
        }
        return true
    }

    override fun canUse(player: PlayerEntity): Boolean = routerInventory.canPlayerUse(player)
}
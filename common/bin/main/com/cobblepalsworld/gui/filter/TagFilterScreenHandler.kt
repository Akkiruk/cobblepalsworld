package com.cobblepalsworld.gui.filter

import com.cobblepalsworld.gui.MenuTypes
import com.cobblepalsworld.tag.TagItem
import com.cobblepalsworld.tag.filter.TagFilter
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ArrayPropertyDelegate
import net.minecraft.screen.PropertyDelegate
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.Hand

class TagFilterScreenHandler : ScreenHandler {
    private val filterInventory: SimpleInventory
    private val filterData: PropertyDelegate
    private val itemSlot: Int

    // Client constructor — used by ScreenHandlerType factory
    constructor(syncId: Int, playerInventory: PlayerInventory) : super(MenuTypes.TAG_FILTER.get(), syncId) {
        this.filterInventory = SimpleInventory(TagFilter.MAX_FILTER_SLOTS)
        this.filterData = ArrayPropertyDelegate(2)
        this.itemSlot = -1
        setupSlots(playerInventory)
    }

    // Server constructor — used when opening menu from TagItem.use()
    constructor(syncId: Int, playerInventory: PlayerInventory, hand: Hand) : super(MenuTypes.TAG_FILTER.get(), syncId) {
        this.filterInventory = SimpleInventory(TagFilter.MAX_FILTER_SLOTS)
        this.filterData = ArrayPropertyDelegate(2)
        this.itemSlot = if (hand == Hand.MAIN_HAND) playerInventory.selectedSlot else 40

        val stack = playerInventory.player.getStackInHand(hand)
        if (stack.item is TagItem) {
            val registries = playerInventory.player.world.registryManager
            val filter = TagItem.getFilter(stack, registries)
            filterData.set(0, if (filter.whitelist) 1 else 0)
            filterData.set(1, if (filter.matchNbt) 1 else 0)
            for ((i, item) in filter.items.withIndex()) {
                if (i < TagFilter.MAX_FILTER_SLOTS) filterInventory.setStack(i, item.copy())
            }
        }
        setupSlots(playerInventory)
    }

    private fun setupSlots(playerInventory: PlayerInventory) {
        addProperties(filterData)

        // 3x3 ghost filter slots (left panel)
        for (row in 0..2) {
            for (col in 0..2) {
                addSlot(Slot(filterInventory, row * 3 + col, 8 + col * 18, 19 + row * 18))
            }
        }
        // Player inventory (3 rows)
        for (row in 0..2) {
            for (col in 0..8) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 83 + row * 18))
            }
        }
        // Hotbar
        for (col in 0..8) {
            addSlot(Slot(playerInventory, col, 8 + col * 18, 145))
        }
    }

    val isWhitelist: Boolean get() = filterData.get(0) != 0
    val isMatchNbt: Boolean get() = filterData.get(1) != 0

    // Ghost slot behavior: clicking copies cursor item (count=1) without consuming it
    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity) {
        if (slotIndex in 0 until TagFilter.MAX_FILTER_SLOTS) {
            val cursorStack = cursorStack
            if (cursorStack.isEmpty) {
                slots[slotIndex].stack = ItemStack.EMPTY
            } else {
                slots[slotIndex].stack = cursorStack.copyWithCount(1)
            }
            return
        }
        super.onSlotClick(slotIndex, button, actionType, player)
    }

    override fun onButtonClick(player: PlayerEntity, id: Int): Boolean {
        when (id) {
            0 -> filterData.set(0, if (filterData.get(0) == 0) 1 else 0)
            1 -> filterData.set(1, if (filterData.get(1) == 0) 1 else 0)
            else -> return false
        }
        return true
    }

    // Shift-click from player inventory copies item into first empty ghost slot
    override fun quickMove(player: PlayerEntity, slotIndex: Int): ItemStack {
        if (slotIndex in 0 until TagFilter.MAX_FILTER_SLOTS) return ItemStack.EMPTY
        val slot = slots.getOrNull(slotIndex) ?: return ItemStack.EMPTY
        if (!slot.hasStack()) return ItemStack.EMPTY
        for (i in 0 until TagFilter.MAX_FILTER_SLOTS) {
            if (slots[i].stack.isEmpty) {
                slots[i].stack = slot.stack.copyWithCount(1)
                break
            }
        }
        return ItemStack.EMPTY
    }

    override fun canUse(player: PlayerEntity): Boolean = true

    override fun onClosed(player: PlayerEntity) {
        super.onClosed(player)
        if (!player.world.isClient && itemSlot >= 0) {
            val stack = player.inventory.getStack(itemSlot)
            if (!stack.isEmpty && stack.item is TagItem) {
                val items = (0 until TagFilter.MAX_FILTER_SLOTS)
                    .map { filterInventory.getStack(it) }
                    .filter { !it.isEmpty }
                val filter = TagFilter(items, isWhitelist, isMatchNbt)
                TagItem.setFilter(stack, filter, player.world.registryManager)
            }
        }
    }
}

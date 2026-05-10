package com.cobblepalsworld.gui.filter

import com.cobblepalsworld.gui.MenuTypes
import com.cobblepalsworld.tag.TagItem
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.TagSettings
import com.cobblepalsworld.tag.TargetStrategy
import com.cobblepalsworld.tag.RedstoneControlMode
import com.cobblepalsworld.tag.filter.FilterMatchMode
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
    private val trackedTagStack: ItemStack?
    private val matchTags: List<String>
    private val matchModIds: List<String>

    companion object {
        private val REGULATOR_PRESETS = intArrayOf(1, 4, 8, 16, 32, 64)
    }

    // Client constructor — used by ScreenHandlerType factory
    constructor(syncId: Int, playerInventory: PlayerInventory) : super(MenuTypes.TAG_FILTER.get(), syncId) {
        this.filterInventory = SimpleInventory(TagFilter.MAX_FILTER_SLOTS)
        this.filterData = ArrayPropertyDelegate(8)
        this.itemSlot = -1
        this.trackedTagStack = null
        this.matchTags = emptyList()
        this.matchModIds = emptyList()
        setupSlots(playerInventory)
    }

    // Server constructor — used when opening menu from TagItem.use()
    constructor(syncId: Int, playerInventory: PlayerInventory, hand: Hand) : super(MenuTypes.TAG_FILTER.get(), syncId) {
        this.filterInventory = SimpleInventory(TagFilter.MAX_FILTER_SLOTS)
        this.filterData = ArrayPropertyDelegate(8)
        this.itemSlot = if (hand == Hand.MAIN_HAND) playerInventory.selectedSlot else 40

        val stack = playerInventory.player.getStackInHand(hand)
        this.trackedTagStack = stack
        if (stack.item is TagItem) {
            val registries = playerInventory.player.world.registryManager
            val filter = TagItem.getFilter(stack, registries)
            val settings = TagItem.getSettings(stack)
            val tagType = (stack.item as TagItem).tagType
            this.matchTags = filter.matchTags
            this.matchModIds = filter.matchModIds
            filterData.set(0, if (filter.whitelist) 1 else 0)
            filterData.set(1, if (filter.matchNbt) 1 else 0)
            filterData.set(2, filter.matchMode.ordinal)
            filterData.set(3, settings.targetStrategy.ordinal)
            filterData.set(4, settings.redstoneMode.ordinal)
            filterData.set(5, if (settings.terminateAfterSuccess) 1 else 0)
            filterData.set(6, settings.regulatorAmount)
            filterData.set(7, tagType.ordinal)
            if (tagType.usesFilter) {
                for ((i, item) in filter.items.withIndex()) {
                    if (i < TagFilter.MAX_FILTER_SLOTS) filterInventory.setStack(i, item.copy())
                }
            }
        } else {
            this.matchTags = emptyList()
            this.matchModIds = emptyList()
            filterData.set(6, 64)
            filterData.set(7, -1)
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
    val matchMode: FilterMatchMode get() = FilterMatchMode.entries.getOrElse(filterData.get(2)) { FilterMatchMode.ANY }
    val targetStrategy: TargetStrategy get() = TargetStrategy.entries.getOrElse(filterData.get(3)) { TargetStrategy.ROUND_ROBIN }
    val redstoneMode: RedstoneControlMode get() = RedstoneControlMode.entries.getOrElse(filterData.get(4)) { RedstoneControlMode.ALWAYS }
    val terminateAfterSuccess: Boolean get() = filterData.get(5) != 0
    val regulatorAmount: Int get() = filterData.get(6).coerceIn(1, 64)
    val tagType: TagType? get() = TagType.entries.getOrNull(filterData.get(7))
    val usesFilter: Boolean get() = tagType?.usesFilter != false

    private val protectedScreenSlotIndex: Int
        get() = when (itemSlot) {
            in 9..35 -> TagFilter.MAX_FILTER_SLOTS + (itemSlot - 9)
            in 0..8 -> TagFilter.MAX_FILTER_SLOTS + 27 + itemSlot
            else -> -1
        }

    // Ghost slot behavior: clicking copies cursor item (count=1) without consuming it
    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity) {
        if (slotIndex == protectedScreenSlotIndex) return
        if (actionType == SlotActionType.SWAP && itemSlot in 0..8 && button == itemSlot) return

        if (slotIndex in 0 until TagFilter.MAX_FILTER_SLOTS) {
            if (!usesFilter) return
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
            2 -> filterData.set(2, (filterData.get(2) + 1) % FilterMatchMode.entries.size)
            3 -> filterData.set(3, (filterData.get(3) + 1) % TargetStrategy.entries.size)
            4 -> filterData.set(4, (filterData.get(4) + 1) % RedstoneControlMode.entries.size)
            6 -> filterData.set(5, if (filterData.get(5) == 0) 1 else 0)
            7 -> stepRegulator(-1)
            8 -> stepRegulator(1)
            else -> return false
        }
        return true
    }

    private fun stepRegulator(direction: Int) {
        val current = regulatorAmount
        val index = REGULATOR_PRESETS.indexOf(current).let { if (it >= 0) it else REGULATOR_PRESETS.indexOfFirst { preset -> preset >= current }.coerceAtLeast(0) }
        val nextIndex = (index + direction).coerceIn(0, REGULATOR_PRESETS.lastIndex)
        filterData.set(6, REGULATOR_PRESETS[nextIndex])
    }

    // Shift-click from player inventory copies item into first empty ghost slot
    override fun quickMove(player: PlayerEntity, slotIndex: Int): ItemStack {
        if (slotIndex == protectedScreenSlotIndex) return ItemStack.EMPTY
        if (slotIndex in 0 until TagFilter.MAX_FILTER_SLOTS) return ItemStack.EMPTY
        if (!usesFilter) return ItemStack.EMPTY
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
            val stack = trackedTagStack ?: player.inventory.getStack(itemSlot)
            if (!stack.isEmpty && stack.item is TagItem) {
                val filter = if (usesFilter) {
                    val items = (0 until TagFilter.MAX_FILTER_SLOTS)
                        .map { filterInventory.getStack(it) }
                        .filter { !it.isEmpty }
                    TagFilter(items, isWhitelist, isMatchNbt, matchTags, matchModIds, matchMode)
                } else {
                    TagFilter.EMPTY
                }
                val existingSettings = TagItem.getSettings(stack)
                val settings = existingSettings.copy(
                    targetStrategy = targetStrategy,
                    redstoneMode = redstoneMode,
                    regulatorAmount = regulatorAmount,
                    terminateAfterSuccess = terminateAfterSuccess
                )
                TagItem.setFilter(stack, filter, player.world.registryManager)
                TagItem.setSettings(stack, settings)
            }
        }
    }
}

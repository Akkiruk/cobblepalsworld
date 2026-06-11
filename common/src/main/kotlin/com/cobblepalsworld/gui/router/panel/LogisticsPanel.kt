package com.cobblepalsworld.gui.router.panel

import com.cobblepalsworld.gui.router.CommandPostStorageWidget
import com.cobblepalsworld.gui.router.RouterScreenHandler
import com.cobblepalsworld.router.RouterBlockEntity
import com.cobblepalsworld.tag.TagRoleFamily
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.TagTypePresentation
import net.minecraft.client.gui.DrawContext

internal class LogisticsPanel(private val ctx: CommandPostPanelContext) : CommandPostPanel {
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        drawLogisticsPanel(context)
    }

    private fun drawLogisticsPanel(context: DrawContext) = with(ctx) {
                drawOperationalFrame(context, "LOGISTICS")
                val storageSlots = handler.slots.drop(RouterScreenHandler.STORAGE_SCREEN_SLOT_START).take(RouterBlockEntity.STORAGE_SLOT_COUNT)
                val usedSlots = storageSlots.count { it.hasStack() }
                val itemCount = storageSlots.sumOf { it.stack.count }
                val views = moduleViews()
                val logisticsViews = views.filter { TagTypePresentation.familyOf(it.tagType) == TagRoleFamily.Logistics }
                val logisticsCount = logisticsViews.size
                val starved = logisticsViews.count { !handler.moduleAssigned(it.moduleIndex) }
                val backingThreshold = RouterBlockEntity.STORAGE_SLOT_COUNT * 2 / 3
                val backedUp = if (usedSlots.compareTo(backingThreshold) >= 0) logisticsViews.count { it.tagType == TagType.PULLER || it.tagType == TagType.VACUUM } else 0
                val pressure = when {
                    usedSlots.compareTo(RouterBlockEntity.STORAGE_SLOT_COUNT) >= 0 -> "Full"
                    usedSlots.compareTo(backingThreshold) >= 0 -> "High"
                    usedSlots.compareTo(0) > 0 -> "Flowing"
                    else -> "Empty"
                }
                drawSmallText(context, "Buffer", CommandPostPanelContext.CENTER_INV_LEFT, CommandPostPanelContext.LOGISTICS_BUFFER_TOP - 9, 0xFFEAF4F5.toInt(), true)
                drawSmallText(context, "$usedSlots/${RouterBlockEntity.STORAGE_SLOT_COUNT}  $itemCount items  $pressure", CommandPostPanelContext.CENTER_INV_LEFT + 46, CommandPostPanelContext.LOGISTICS_BUFFER_TOP - 9, if (pressure == "Full") 0xFFFF7777.toInt() else 0xFFBFE7C4.toInt(), false)
                drawSlotFrames(context, RouterScreenHandler.STORAGE_SCREEN_SLOT_START until RouterScreenHandler.COMMAND_SLOT_COUNT, CommandPostPanelContext.SlotFrameStyle.COMPACT)
                drawSmallText(context, "Bag", CommandPostPanelContext.CENTER_INV_LEFT, CommandPostPanelContext.LOGISTICS_PLAYER_TOP - 9, 0xFFEAF4F5.toInt(), true)
                drawSmallText(context, "Routes $logisticsCount  Starved $starved  Backlog $backedUp", CommandPostPanelContext.CENTER_INV_LEFT + 26, CommandPostPanelContext.LOGISTICS_PLAYER_TOP - 9, if (starved > 0 || backedUp > 0) 0xFFFFD166.toInt() else 0xFFBFE7C4.toInt(), false)
                drawSlotFrames(context, RouterScreenHandler.COMMAND_SLOT_COUNT until handler.slots.size, CommandPostPanelContext.SlotFrameStyle.COMPACT)
                drawSmallText(context, if (usedSlots == 0) "Senders wait for buffer" else if (pressure == "Full") "Pullers may stall" else "Items can move", CommandPostPanelContext.CENTER_INV_LEFT, CommandPostPanelContext.LOGISTICS_HOTBAR_TOP + 19, 0xFFB8C3C7.toInt(), false)
                panelHits = emptyList()
                slotHits = emptyList()
            
    }
}

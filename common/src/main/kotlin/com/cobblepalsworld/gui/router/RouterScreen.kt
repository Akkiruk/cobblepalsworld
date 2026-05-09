package com.cobblepalsworld.gui.router

import com.cobblepalsworld.router.RouterBlockEntity
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text

class RouterScreen(
    handler: RouterScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<RouterScreenHandler>(handler, inventory, title) {

    override fun init() {
        backgroundWidth = 176
        backgroundHeight = 190
        super.init()
        titleX = 8
        titleY = 6
        playerInventoryTitleX = 8
        playerInventoryTitleY = 96
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF171A1F.toInt())
        context.fill(x + 6, y + 16, x + 108, y + 90, 0xFF232B31.toInt())
        context.fill(x + 112, y + 16, x + 170, y + 90, 0xFF28333A.toInt())
        context.fill(x + 140, y + 16, x + 170, y + 108, 0xFF314148.toInt())
        context.fill(x + 6, y + 104, x + 170, y + 182, 0xFF1F242A.toInt())
    }

    override fun drawForeground(context: DrawContext, mouseX: Int, mouseY: Int) {
        val jobCardCount = handler.slots.take(RouterBlockEntity.MODULE_SLOT_COUNT).count { it.hasStack() }
        val idleCount = (handler.assignedCount - handler.activeCount).coerceAtLeast(0)
        val hint = when {
            !handler.linked -> "Sneak-use a pasture to link"
            jobCardCount <= 0 -> "Add tags to the Jobs grid"
            handler.rosterCount <= 0 -> "No active pals in pasture"
            handler.assignedCount < jobCardCount -> "Some jobs are waiting on free pals"
            handler.activeCount <= 0 -> "Workers are ready"
            else -> "Sneak-use to relink"
        }

        context.drawText(textRenderer, title, titleX, titleY, 0xE6E6F0, false)
        context.drawText(textRenderer, Text.literal("Jobs"), 28, 6, 0xA9C7C7, false)
        context.drawText(textRenderer, Text.literal("Pasture"), 113, 6, 0xA9C7C7, false)
        context.drawText(textRenderer, Text.literal("Boosts"), 137, 6, 0xA9C7C7, false)

        val linkLabel = if (handler.linked) "Linked" else "Needs link"
        val linkColor = if (handler.linked) 0x50C864 else 0xD46A5C
        context.drawText(textRenderer, Text.literal(linkLabel), 116, 20, linkColor, false)
        context.drawText(textRenderer, Text.literal("Roster ${handler.rosterCount}"), 116, 34, 0xD5E6D5, false)
        context.drawText(textRenderer, Text.literal("Cards $jobCardCount"), 116, 48, 0xE0C878, false)
        context.drawText(textRenderer, Text.literal("Busy ${handler.activeCount}"), 116, 62, 0x78C8FF, false)
        context.drawText(textRenderer, Text.literal("Idle $idleCount"), 116, 76, 0xB4C0CC, false)
        context.drawText(textRenderer, Text.literal(hint), 12, 82, 0x8A9098, false)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)
    }
}
package com.cobblepalsworld.gui.router

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
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF1B1B22.toInt())
        context.fill(x + 6, y + 16, x + 170, y + 90, 0xFF282836.toInt())
        context.fill(x + 72, y + 16, x + 110, y + 54, 0xFF34344A.toInt())
        context.fill(x + 140, y + 16, x + 170, y + 108, 0xFF30303F.toInt())
        context.fill(x + 6, y + 104, x + 170, y + 182, 0xFF22222D.toInt())
    }

    override fun drawForeground(context: DrawContext, mouseX: Int, mouseY: Int) {
        context.drawText(textRenderer, title, titleX, titleY, 0xE6E6F0, false)
        context.drawText(textRenderer, Text.literal("Modules"), 24, 6, 0xA0A0C8, false)
        context.drawText(textRenderer, Text.literal("Buffer"), 76, 6, 0xA0A0C8, false)
        context.drawText(textRenderer, Text.literal("Upgrades"), 130, 6, 0xA0A0C8, false)

        val powerLabel = if (handler.powered) "Powered" else "Idle"
        val powerColor = if (handler.powered) 0x50C864 else 0x909098
        context.drawText(textRenderer, Text.literal(powerLabel), 72, 58, powerColor, false)

        val bufferLabel = if (handler.bufferCount > 0) "${handler.bufferCount}/${handler.bufferMax}" else "Empty"
        context.drawText(textRenderer, Text.literal(bufferLabel), 72, 68, 0xE0C878, false)

        val cooldownLabel = if (handler.cooldown > 0) "CD ${handler.cooldown}" else "Ready"
        context.drawText(textRenderer, Text.literal(cooldownLabel), 72, 78, 0x8CD2FF, false)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)
    }
}
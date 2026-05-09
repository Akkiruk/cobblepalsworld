package com.cobblepalsworld.gui.filter

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class TagFilterScreen(
    handler: TagFilterScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<TagFilterScreenHandler>(handler, inventory, title) {

    companion object {
        private val TEXTURE = Identifier.of("cobblepalsworld", "textures/gui/tag_filter.png")
    }

    override fun init() {
        backgroundWidth = 176
        backgroundHeight = 166
        super.init()
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2
        titleY = 4
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight)
    }

    override fun drawForeground(context: DrawContext, mouseX: Int, mouseY: Int) {
        context.drawText(textRenderer, title, titleX, titleY, 0xB4B4C3, false)

        // Whitelist/Blacklist toggle (button area: x=72..167, y=19..34)
        val wlText = if (handler.isWhitelist) "Whitelist" else "Blacklist"
        val wlColor = if (handler.isWhitelist) 0x50C864 else 0xC85050
        context.drawText(textRenderer, Text.literal(wlText), 76, 23, wlColor, false)

        // Match NBT toggle (button area: x=72..167, y=38..53)
        val nbtText = if (handler.isMatchNbt) "Match NBT" else "Ignore NBT"
        val nbtColor = if (handler.isMatchNbt) 0x50C864 else 0x909098
        context.drawText(textRenderer, Text.literal(nbtText), 76, 42, nbtColor, false)

        // Hover highlights
        val rx = mouseX - x
        val ry = mouseY - y
        if (rx in 72..167 && ry in 19..34) context.fill(72, 19, 168, 35, 0x18FFFFFF)
        if (rx in 72..167 && ry in 38..53) context.fill(72, 38, 168, 54, 0x18FFFFFF)

        // Ghost slot hint
        context.drawText(textRenderer, Text.literal("Click items to set filter"), 5, 76, 0x505058, false)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val rx = (mouseX - x).toInt()
            val ry = (mouseY - y).toInt()
            if (rx in 72..167 && ry in 19..34) {
                client!!.interactionManager!!.clickButton(handler.syncId, 0)
                return true
            }
            if (rx in 72..167 && ry in 38..53) {
                client!!.interactionManager!!.clickButton(handler.syncId, 1)
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)
    }
}

package com.cobblepalsworld.gui.filter

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.math.roundToInt

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

        var rowY = 20
        drawSettingRow(context, rowY, "Mode", if (handler.isWhitelist) "Whitelist" else "Blacklist", if (handler.isWhitelist) 0x50C864 else 0xC85050)
        rowY += 9
        drawSettingRow(context, rowY, "NBT", if (handler.isMatchNbt) "Match" else "Ignore", if (handler.isMatchNbt) 0x50C864 else 0x909098)
        rowY += 9
        drawSettingRow(context, rowY, "Filter", handler.matchMode.name, 0xA0A0C8)
        rowY += 9
        drawSettingRow(context, rowY, "Redstone", handler.redstoneMode.id.replace('_', ' '), 0xC8B43C)

        val tagType = handler.tagType
        if (tagType?.supportsTargetList == true) {
            rowY += 9
            drawSettingRow(context, rowY, "Targets", handler.targetStrategy.id.replace('_', ' '), 0x8CD2FF)
            rowY += 9
            drawSettingRow(context, rowY, "Stop", if (handler.terminateAfterSuccess) "After one" else "Keep going", if (handler.terminateAfterSuccess) 0xC8B43C else 0x909098)
            rowY += 9
            drawSettingRow(context, rowY, "Reg", handler.regulatorAmount.toString(), 0x50C864)
        } else if (tagType?.id == "activator") {
            rowY += 9
            drawSettingRow(context, rowY, "Action", handler.activatorMode.id.replace('_', ' '), 0xE0A050)
        }

        val rx = mouseX - x
        val ry = mouseY - y
        for ((startY, endY) in rowBounds()) {
            if (rx in 72..167 && ry in startY..endY) {
                context.fill(72, startY - 1, 168, endY + 1, 0x18FFFFFF)
            }
        }

        // Ghost slot hint
        context.drawText(textRenderer, Text.literal("Click items to set filter"), 5, 76, 0x505058, false)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val rx = (mouseX - x).roundToInt()
        val ry = (mouseY - y).roundToInt()
        if (rx in 72..167) {
            rowButtonId(ry)?.let { buttonId ->
                val actionId = when (buttonId) {
                    7 -> when (button) {
                        1 -> 7
                        0 -> 8
                        else -> null
                    }
                    else -> if (button == 0) buttonId else null
                }
                if (actionId != null) {
                    client!!.interactionManager!!.clickButton(handler.syncId, actionId)
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)
    }

    private fun drawSettingRow(context: DrawContext, y: Int, label: String, value: String, color: Int) {
        context.drawText(textRenderer, Text.literal(label), 76, y, 0x707078, false)
        context.drawText(textRenderer, Text.literal(value.replaceFirstChar(Char::titlecase)), 112, y, color, false)
    }

    private fun rowBounds(): List<Pair<Int, Int>> {
        val bounds = mutableListOf(20 to 27, 29 to 36, 38 to 45, 47 to 54)
        val tagType = handler.tagType
        if (tagType?.supportsTargetList == true) {
            bounds += 56 to 63
            bounds += 65 to 72
            bounds += 74 to 81
        } else if (tagType?.id == "activator") {
            bounds += 56 to 63
        }
        return bounds
    }

    private fun rowButtonId(localY: Int): Int? {
        val tagType = handler.tagType
        return when {
            localY in 20..27 -> 0
            localY in 29..36 -> 1
            localY in 38..45 -> 2
            localY in 47..54 -> 4
            tagType?.supportsTargetList == true && localY in 56..63 -> 3
            tagType?.supportsTargetList == true && localY in 65..72 -> 6
            tagType?.supportsTargetList == true && localY in 74..81 -> 7
            tagType?.id == "activator" && localY in 56..63 -> 5
            else -> null
        }
    }
}

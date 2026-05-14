/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblepalsworld.gui.router

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblepalsworld.gui.CobblemonUiChrome
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext

internal object CommandPostPastureWidget {
    data class RowBounds(val left: Int, val top: Int)

    const val X = 267
    const val Y = 8
    const val LIST_LEFT = X + 6
    const val LIST_TOP = Y + 31
    const val LIST_WIDTH = 70
    const val LIST_HEIGHT = 120
    const val SLOT_WIDTH = 62
    const val SLOT_HEIGHT = 29
    const val SLOT_SPACING = 3
    const val VISIBLE_ROWS = 4
    const val RECALL_LEFT = X + 6
    const val RECALL_TOP = Y + 153
    const val RECALL_WIDTH = 70
    const val RECALL_HEIGHT = 17
    const val SLOT_ICON_SIZE = 7

    val SLOT_MOVE_ICON = CommandPostPcShell.cobblemon("textures/gui/pasture/pc_slot_icon_move.png")
    val SLOT_DEFEND_ICON = CommandPostPcShell.cobblemon("textures/gui/pasture/pasture_slot_icon_defend.png")
    val SLOT_PASTURE_ICON = CommandPostPcShell.cobblemon("textures/gui/pasture/pc_slot_icon_pasture.png")
    val PASTURE_SLOT = CommandPostPcShell.cobblemon("textures/gui/pasture/pasture_slot.png")
    val PASTURE_SLOT_OWNER = CommandPostPcShell.cobblemon("textures/gui/pasture/pasture_slot_owner.png")

    private val PASTURE_PANEL = CommandPostPcShell.cobblemon("textures/gui/pasture/pasture_panel.png")
    private val PASTURE_SCROLL_OVERLAY = CommandPostPcShell.cobblemon("textures/gui/pasture/pasture_scroll_overlay.png")
    private val PASTURE_BUTTON = CommandPostPcShell.cobblemon("textures/gui/pasture/pasture_button.png")

    fun maxScroll(rowCount: Int): Int = kotlin.math.max(0, rowCount - VISIBLE_ROWS)

    fun rowBounds(rowIndex: Int): RowBounds {
        return RowBounds(LIST_LEFT - 4, LIST_TOP + rowIndex * (SLOT_HEIGHT + SLOT_SPACING))
    }

    fun drawPanel(context: DrawContext, textRenderer: TextRenderer, originX: Int, originY: Int) {
        blitk(matrixStack = context.matrices, texture = PASTURE_PANEL, x = originX + X, y = originY + Y, width = CommandPostPcShell.RIGHT_PANEL_WIDTH, height = CommandPostPcShell.RIGHT_PANEL_HEIGHT)
        CobblemonUiChrome.drawSmallText(context, textRenderer, "PASTURE", originX + X + 25, originY + Y + 4, 0xFFEFE7D7.toInt(), true)
    }

    fun drawControls(context: DrawContext, textRenderer: TextRenderer, originX: Int, originY: Int, memberCount: Int, maxWorkers: Int, localMouseX: Int, localMouseY: Int) {
        CobblemonUiChrome.drawSmallText(context, textRenderer, "$memberCount/$maxWorkers", originX + X + 36, originY + Y + 24, 0xFFFFFFFF.toInt(), true)
        drawRecallButton(context, textRenderer, originX, originY, localMouseX, localMouseY)
    }

    fun drawScrollOverlay(context: DrawContext, originX: Int, originY: Int) {
        blitk(matrixStack = context.matrices, texture = PASTURE_SCROLL_OVERLAY, x = originX + LIST_LEFT, y = originY + LIST_TOP - 13, width = LIST_WIDTH, height = 131)
    }

    fun drawRecallButton(context: DrawContext, textRenderer: TextRenderer, originX: Int, originY: Int, localMouseX: Int, localMouseY: Int) {
        CommandPostIconButton.draw(context, PASTURE_BUTTON, originX, originY, RECALL_LEFT, RECALL_TOP, RECALL_WIDTH, RECALL_HEIGHT, localMouseX, localMouseY)
        CobblemonUiChrome.drawSmallText(context, textRenderer, "Recall", originX + RECALL_LEFT + 22, originY + RECALL_TOP + 5, 0xFFFFFFFF.toInt(), true)
    }

    fun drawRowFrame(context: DrawContext, originX: Int, originY: Int, left: Int, top: Int, active: Boolean, hovered: Boolean) {
        blitk(
            matrixStack = context.matrices,
            texture = if (active) PASTURE_SLOT_OWNER else PASTURE_SLOT,
            x = originX + left,
            y = originY + top,
            width = SLOT_WIDTH,
            height = SLOT_HEIGHT,
            vOffset = if (hovered) SLOT_HEIGHT else 0,
            textureHeight = SLOT_HEIGHT * 2
        )
    }

    fun drawSlotIcon(context: DrawContext, originX: Int, originY: Int, texture: net.minecraft.util.Identifier, left: Int, top: Int, hovered: Boolean) {
        blitk(
            matrixStack = context.matrices,
            texture = texture,
            x = (originX + left) / CommandPostPcShell.TEXTURE_SCALE,
            y = (originY + top) / CommandPostPcShell.TEXTURE_SCALE,
            width = 14,
            height = 14,
            vOffset = if (hovered) 14 else 0,
            textureHeight = 28,
            scale = CommandPostPcShell.TEXTURE_SCALE
        )
    }

    fun listContains(mouseX: Int, mouseY: Int): Boolean {
        return CommandPostIconButton.contains(mouseX, mouseY, LIST_LEFT, LIST_TOP - 4, LIST_WIDTH, LIST_HEIGHT)
    }

    fun recallContains(mouseX: Int, mouseY: Int): Boolean {
        return CommandPostIconButton.contains(mouseX, mouseY, RECALL_LEFT, RECALL_TOP, RECALL_WIDTH, RECALL_HEIGHT)
    }
}

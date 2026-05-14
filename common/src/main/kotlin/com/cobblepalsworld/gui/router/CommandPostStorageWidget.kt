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
import net.minecraft.util.Identifier
import java.util.Locale
import kotlin.math.roundToInt

internal object CommandPostStorageWidget {
    const val X = 85
    const val Y = 27
    const val SCREEN_WIDTH = 174
    const val SCREEN_HEIGHT = 155
    const val BOX_COLUMNS = 6
    const val BOX_SLOT_COUNT = 30
    const val BOX_SLOT_START_X = 7
    const val BOX_SLOT_START_Y = 11
    const val BOX_SLOT_PADDING = 2
    const val SLOT_SIZE = 25

    const val PREV_LEFT = 117
    const val NEXT_LEFT = 220
    const val NAV_TOP = 16
    const val NAV_SIZE = 7

    private val SCREEN_GRID = CommandPostPcShell.cobblemon("textures/gui/pc/pc_screen_grid.png")
    private val SCREEN_OVERLAY = CommandPostPcShell.cobblemon("textures/gui/pc/pc_screen_overlay.png")
    private val SCREEN_GLOW = CommandPostPcShell.cobblemon("textures/gui/pc/pc_screen_glow.png")
    val NAV_PREVIOUS_TEXTURE = CommandPostPcShell.cobblemon("textures/gui/pc/pc_arrow_previous.png")
    val NAV_NEXT_TEXTURE = CommandPostPcShell.cobblemon("textures/gui/pc/pc_arrow_next.png")
    val JOBS_BACKGROUND = cobblePals("textures/gui/command_post/jobs_background.png")
    val POLICY_BACKGROUND = cobblePals("textures/gui/command_post/policy_background.png")
    val LOGISTICS_BACKGROUND = cobblePals("textures/gui/command_post/logistics_background.png")

    fun drawTitle(context: DrawContext, textRenderer: TextRenderer, originX: Int, originY: Int, title: String) {
        val display = title.uppercase(Locale.ROOT)
        val left = (originX + 151 - textRenderer.getWidth(display) * CommandPostPcShell.TEXTURE_SCALE / 2F).roundToInt()
        CobblemonUiChrome.drawSmallText(context, textRenderer, display, left, originY + 14, 0xFFFFFFFF.toInt(), true)
    }

    fun drawNavigation(context: DrawContext, originX: Int, originY: Int, localMouseX: Int, localMouseY: Int) {
        CommandPostIconButton.draw(context, NAV_PREVIOUS_TEXTURE, originX, originY, PREV_LEFT, NAV_TOP, NAV_SIZE, NAV_SIZE, localMouseX, localMouseY, scaled = true)
        CommandPostIconButton.draw(context, NAV_NEXT_TEXTURE, originX, originY, NEXT_LEFT, NAV_TOP, NAV_SIZE, NAV_SIZE, localMouseX, localMouseY, scaled = true)
    }

    fun drawFrame(context: DrawContext, originX: Int, originY: Int, background: Identifier = SCREEN_GRID) {
        blitk(matrixStack = context.matrices, texture = SCREEN_GLOW, x = originX + X - 17, y = originY + Y - 17, width = 208, height = 189, alpha = 0.82F)
        blitk(matrixStack = context.matrices, texture = background, x = originX + X + 7, y = originY + Y + 11, width = 160, height = 133)
        blitk(matrixStack = context.matrices, texture = SCREEN_OVERLAY, x = originX + X, y = originY + Y, width = SCREEN_WIDTH, height = SCREEN_HEIGHT)
    }

    private fun cobblePals(path: String): Identifier = Identifier.of("cobblepalsworld", path)

    fun slotLeft(gridIndex: Int): Int {
        val col = gridIndex % BOX_COLUMNS
        return X + BOX_SLOT_START_X + col * (SLOT_SIZE + BOX_SLOT_PADDING)
    }

    fun slotTop(gridIndex: Int): Int {
        val row = gridIndex / BOX_COLUMNS
        return Y + BOX_SLOT_START_Y + row * (SLOT_SIZE + BOX_SLOT_PADDING)
    }

    fun contains(mouseX: Int, mouseY: Int): Boolean {
        return CommandPostIconButton.contains(mouseX, mouseY, X, Y, SCREEN_WIDTH, SCREEN_HEIGHT)
    }
}

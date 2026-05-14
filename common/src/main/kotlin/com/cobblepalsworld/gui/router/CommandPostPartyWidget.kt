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

internal object CommandPostPartyWidget {
    const val PANEL_LEFT = 131
    const val PANEL_TOP = 8
    const val PANEL_WIDTH = CommandPostPcShell.RIGHT_PANEL_WIDTH
    const val PANEL_HEIGHT = CommandPostPcShell.RIGHT_PANEL_HEIGHT
    const val SLOT_START_X = PANEL_LEFT + 11
    const val SLOT_START_Y = PANEL_TOP + 27
    const val SLOT_PADDING = 6
    const val SLOT_SIZE = CommandPostStorageWidget.SLOT_SIZE

    private val PARTY_PANEL = CommandPostPcShell.cobblemon("textures/gui/pc/party_panel.png")

    fun drawPanel(context: DrawContext, textRenderer: TextRenderer, originX: Int, originY: Int) {
        blitk(matrixStack = context.matrices, texture = PARTY_PANEL, x = originX + PANEL_LEFT, y = originY + PANEL_TOP, width = PANEL_WIDTH, height = PANEL_HEIGHT)
        CobblemonUiChrome.drawSmallText(context, textRenderer, "PARTY", originX + PANEL_LEFT + 30, originY + PANEL_TOP + 4, 0xFFEFE7D7.toInt(), true)
    }

    fun slotLeft(partyIndex: Int): Int {
        val isEven = partyIndex % 2 == 0
        return SLOT_START_X + if (partyIndex == 0 || isEven) 0 else SLOT_SIZE + SLOT_PADDING
    }

    fun slotTop(partyIndex: Int): Int {
        if (partyIndex == 0) return SLOT_START_Y
        val isEven = partyIndex % 2 == 0
        val offsetIndex = (partyIndex - if (isEven) 0 else 1) / 2
        val offsetY = if (isEven) 0 else 8
        return SLOT_START_Y + (SLOT_SIZE + SLOT_PADDING) * offsetIndex + offsetY
    }
}
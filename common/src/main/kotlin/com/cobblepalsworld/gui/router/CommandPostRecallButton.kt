/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblepalsworld.gui.router

import com.cobblepalsworld.gui.CobblemonUiChrome
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext

internal object CommandPostRecallButton {
    private val BUTTON = CommandPostPcShell.cobblemon("textures/gui/pasture/pasture_button.png")

    fun draw(context: DrawContext, textRenderer: TextRenderer, originX: Int, originY: Int, localMouseX: Int, localMouseY: Int) {
        CommandPostIconButton.draw(
            context = context,
            texture = BUTTON,
            originX = originX,
            originY = originY,
            left = CommandPostPastureWidget.RECALL_LEFT,
            top = CommandPostPastureWidget.RECALL_TOP,
            width = CommandPostPastureWidget.RECALL_WIDTH,
            height = CommandPostPastureWidget.RECALL_HEIGHT,
            localMouseX = localMouseX,
            localMouseY = localMouseY
        )
        CobblemonUiChrome.drawSmallText(context, textRenderer, "Recall", originX + CommandPostPastureWidget.RECALL_LEFT + 22, originY + CommandPostPastureWidget.RECALL_TOP + 5, 0xFFFFFFFF.toInt(), true)
    }

    fun contains(mouseX: Int, mouseY: Int): Boolean {
        return CommandPostIconButton.contains(mouseX, mouseY, CommandPostPastureWidget.RECALL_LEFT, CommandPostPastureWidget.RECALL_TOP, CommandPostPastureWidget.RECALL_WIDTH, CommandPostPastureWidget.RECALL_HEIGHT)
    }
}
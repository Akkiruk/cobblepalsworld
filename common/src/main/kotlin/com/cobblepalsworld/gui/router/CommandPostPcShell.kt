/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblepalsworld.gui.router

import com.cobblemon.mod.common.api.gui.blitk
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier

internal object CommandPostPcShell {
    const val BASE_WIDTH = 349
    const val BASE_HEIGHT = 205
    const val RIGHT_PANEL_WIDTH = 82
    const val RIGHT_PANEL_HEIGHT = 169
    const val PORTRAIT_SIZE = 66
    const val INFO_BOX_WIDTH = 63
    const val INFO_BOX_HEIGHT = 69
    const val TEXTURE_SCALE = 0.5F

    val PC_BASE = cobblemon("textures/gui/pc/pc_base.png")
    val PORTRAIT_BACKGROUND = cobblemon("textures/gui/pc/portrait_background.png")
    val INFO_BOX = cobblemon("textures/gui/pc/info_box.png")
    val SLOT_OVERLAY = cobblemon("textures/gui/pc/pc_slot_overlay.png")
    val POINTER = cobblemon("textures/gui/pc/pc_pointer.png")
    val BACK_BUTTON = cobblemon("textures/gui/common/back_button.png")
    val BACK_BUTTON_ICON = cobblemon("textures/gui/common/back_button_icon.png")
    val GENDER_MALE = cobblemon("textures/gui/pc/gender_icon_male.png")
    val GENDER_FEMALE = cobblemon("textures/gui/pc/gender_icon_female.png")

    fun drawBase(context: DrawContext, originX: Int, originY: Int) {
        blitk(matrixStack = context.matrices, texture = PC_BASE, x = originX, y = originY, width = BASE_WIDTH, height = BASE_HEIGHT)
    }

    fun drawPortraitPanel(context: DrawContext, originX: Int, originY: Int) {
        blitk(matrixStack = context.matrices, texture = PORTRAIT_BACKGROUND, x = originX + 6, y = originY + 27, width = PORTRAIT_SIZE, height = PORTRAIT_SIZE)
    }

    fun drawInfoBox(context: DrawContext, originX: Int, originY: Int) {
        blitk(matrixStack = context.matrices, texture = INFO_BOX, x = originX + 9, y = originY + 128, width = INFO_BOX_WIDTH, height = INFO_BOX_HEIGHT)
    }

    fun drawExitButton(context: DrawContext, originX: Int, originY: Int, localMouseX: Int, localMouseY: Int) {
        CommandPostIconButton.draw(context, BACK_BUTTON, originX, originY, EXIT_LEFT, EXIT_TOP, EXIT_WIDTH, EXIT_HEIGHT, localMouseX, localMouseY)
        blitk(
            matrixStack = context.matrices,
            texture = BACK_BUTTON_ICON,
            x = (originX + EXIT_LEFT + 7) / TEXTURE_SCALE,
            y = (originY + EXIT_TOP + 4) / TEXTURE_SCALE,
            width = 21,
            height = 11,
            scale = TEXTURE_SCALE
        )
    }

    const val EXIT_LEFT = 320
    const val EXIT_TOP = 186
    const val EXIT_WIDTH = 26
    const val EXIT_HEIGHT = 13

    fun cobblemon(path: String): Identifier = Identifier.of("cobblemon", path)
}

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
import kotlin.math.floor

internal object CommandPostIconButton {
    fun draw(
        context: DrawContext,
        texture: Identifier,
        originX: Int,
        originY: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        localMouseX: Int,
        localMouseY: Int,
        scaled: Boolean = false,
        textureScale: Float = CommandPostPcShell.TEXTURE_SCALE
    ) {
        val hovered = contains(localMouseX, localMouseY, left, top, width, height)
        if (scaled) {
            val scaledWidth = floor(width / textureScale).toInt()
            val scaledHeight = floor(height / textureScale).toInt()
            blitk(
                matrixStack = context.matrices,
                texture = texture,
                x = (originX + left) / textureScale,
                y = (originY + top) / textureScale,
                width = scaledWidth,
                height = scaledHeight,
                vOffset = if (hovered) scaledHeight else 0,
                textureHeight = scaledHeight * 2,
                scale = textureScale
            )
        } else {
            blitk(
                matrixStack = context.matrices,
                texture = texture,
                x = originX + left,
                y = originY + top,
                width = width,
                height = height,
                vOffset = if (hovered) height else 0,
                textureHeight = height * 2
            )
        }
    }

    fun contains(mouseX: Int, mouseY: Int, left: Int, top: Int, width: Int, height: Int): Boolean {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height
    }
}

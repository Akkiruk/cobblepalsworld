/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblepalsworld.gui.router

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.math.floor

internal class CommandPostTextureButtonWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val texture: Identifier,
    private val scaled: Boolean = false,
    private val textureScale: Float = CommandPostPcShell.TEXTURE_SCALE,
    private val highlighted: () -> Boolean = { false },
    private val onPress: () -> Unit
) : ClickableWidget(x, y, width, height, Text.empty()) {

    override fun onClick(mouseX: Double, mouseY: Double) {
        onPress()
    }

    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        if (scaled) {
            val scaledWidth = floor(width / textureScale).toInt()
            val scaledHeight = floor(height / textureScale).toInt()
            blitk(
                matrixStack = context.matrices,
                texture = texture,
                x = x / textureScale,
                y = y / textureScale,
                width = scaledWidth,
                height = scaledHeight,
                vOffset = if (isHovered || highlighted()) scaledHeight else 0,
                textureHeight = scaledHeight * 2,
                scale = textureScale
            )
        } else {
            blitk(
                matrixStack = context.matrices,
                texture = texture,
                x = x,
                y = y,
                width = width,
                height = height,
                vOffset = if (isHovered || highlighted()) height else 0,
                textureHeight = height * 2
            )
        }
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        appendDefaultNarrations(builder)
    }

    override fun playDownSound(soundManager: net.minecraft.client.sound.SoundManager) {
        MinecraftClient.getInstance().soundManager.play(PositionedSoundInstance.master(CobblemonSounds.PC_CLICK, 1.0F))
    }
}
/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblepalsworld.gui

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.render.drawScaledText
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.math.floor

object CobblemonUiChrome {
    const val BASE_WIDTH = 349
    const val BASE_HEIGHT = 205
    const val STORAGE_X = 85
    const val STORAGE_Y = 27
    const val SCREEN_WIDTH = 174
    const val SCREEN_HEIGHT = 155
    const val RIGHT_PANEL_X = 267
    const val RIGHT_PANEL_Y = 8
    const val RIGHT_PANEL_WIDTH = 82
    const val RIGHT_PANEL_HEIGHT = 169
    const val TEXTURE_SCALE = 0.5F

    const val TEXT_DARK = 0xFF303030.toInt()
    const val TEXT_MUTED = 0xFF5D6570.toInt()
    const val TEXT_FAINT = 0xFF89919B.toInt()
    const val TEXT_LIGHT = 0xFFE7F5FF.toInt()
    const val ACCENT_BLUE = 0xFF5AAED8.toInt()
    const val ACCENT_GREEN = 0xFF78C45D.toInt()
    const val ACCENT_GOLD = 0xFFE4B84A.toInt()
    const val ACCENT_RED = 0xFFE26B58.toInt()
    const val ACCENT_PURPLE = 0xFF9D83D9.toInt()

    data class TextureButton(val left: Int, val top: Int, val width: Int, val height: Int, val texture: Identifier, val scaled: Boolean = false)

    val PC_BASE = cobblemon("textures/gui/pc/pc_base.png")
    val PORTRAIT_BACKGROUND = cobblemon("textures/gui/pc/portrait_background.png")
    val INFO_BOX = cobblemon("textures/gui/pc/info_box.png")
    val SCREEN_GRID = cobblemon("textures/gui/pc/pc_screen_grid.png")
    val SCREEN_OVERLAY = cobblemon("textures/gui/pc/pc_screen_overlay.png")
    val SCREEN_GLOW = cobblemon("textures/gui/pc/pc_screen_glow.png")
    val SLOT_OVERLAY = cobblemon("textures/gui/pc/pc_slot_overlay.png")
    val POINTER = cobblemon("textures/gui/pc/pc_pointer.png")
    val NAV_PREVIOUS = cobblemon("textures/gui/pc/pc_arrow_previous.png")
    val NAV_NEXT = cobblemon("textures/gui/pc/pc_arrow_next.png")
    val ICON_FILTER = cobblemon("textures/gui/pc/pc_icon_filter.png")
    val ICON_OPTIONS = cobblemon("textures/gui/pc/pc_icon_options.png")
    val PASTURE_PANEL = cobblemon("textures/gui/pasture/pasture_panel.png")
    val PASTURE_SCROLL_OVERLAY = cobblemon("textures/gui/pasture/pasture_scroll_overlay.png")
    val PASTURE_BUTTON = cobblemon("textures/gui/pasture/pasture_button.png")
    val PASTURE_SLOT_BUTTON = cobblemon("textures/gui/pasture/pasture_slot_button.png")
    val PASTURE_SLOT_BUTTON_ACTIVE = cobblemon("textures/gui/pasture/pasture_slot_button_active.png")
    val BACK_BUTTON = cobblemon("textures/gui/common/back_button.png")
    val BACK_BUTTON_ICON = cobblemon("textures/gui/common/back_button_icon.png")

    fun drawPcBase(context: DrawContext, left: Int, top: Int) {
        blitk(matrixStack = context.matrices, texture = PC_BASE, x = left, y = top, width = BASE_WIDTH, height = BASE_HEIGHT)
    }

    fun drawStorageScreen(context: DrawContext, left: Int, top: Int, glowAlpha: Float = 0.82F) {
        blitk(matrixStack = context.matrices, texture = SCREEN_GLOW, x = left + STORAGE_X - 17, y = top + STORAGE_Y - 17, width = 208, height = 189, alpha = glowAlpha)
        blitk(matrixStack = context.matrices, texture = SCREEN_GRID, x = left + STORAGE_X + 7, y = top + STORAGE_Y + 11, width = 160, height = 133)
        blitk(matrixStack = context.matrices, texture = SCREEN_OVERLAY, x = left + STORAGE_X, y = top + STORAGE_Y, width = SCREEN_WIDTH, height = SCREEN_HEIGHT)
    }

    fun drawPasturePanel(context: DrawContext, left: Int, top: Int) {
        blitk(matrixStack = context.matrices, texture = PASTURE_PANEL, x = left + RIGHT_PANEL_X, y = top + RIGHT_PANEL_Y, width = RIGHT_PANEL_WIDTH, height = RIGHT_PANEL_HEIGHT)
    }

    fun drawInfoBox(context: DrawContext, left: Int, top: Int) {
        blitk(matrixStack = context.matrices, texture = INFO_BOX, x = left + 9, y = top + 128, width = 63, height = 69)
    }

    fun drawPortraitPanel(context: DrawContext, left: Int, top: Int) {
        blitk(matrixStack = context.matrices, texture = PORTRAIT_BACKGROUND, x = left + 6, y = top + 27, width = 66, height = 66)
    }

    fun drawButton(context: DrawContext, rootLeft: Int, rootTop: Int, button: TextureButton, localMouseX: Int, localMouseY: Int) {
        val hovered = contains(localMouseX, localMouseY, button.left, button.top, button.width, button.height)
        if (button.scaled) {
            val textureWidth = floor(button.width / TEXTURE_SCALE).toInt()
            val textureHeight = floor(button.height / TEXTURE_SCALE).toInt()
            blitk(
                matrixStack = context.matrices,
                texture = button.texture,
                x = (rootLeft + button.left) / TEXTURE_SCALE,
                y = (rootTop + button.top) / TEXTURE_SCALE,
                width = textureWidth,
                height = textureHeight,
                vOffset = if (hovered) textureHeight else 0,
                textureHeight = textureHeight * 2,
                scale = TEXTURE_SCALE
            )
        } else {
            blitk(
                matrixStack = context.matrices,
                texture = button.texture,
                x = rootLeft + button.left,
                y = rootTop + button.top,
                width = button.width,
                height = button.height,
                vOffset = if (hovered) button.height else 0,
                textureHeight = button.height * 2
            )
        }
    }

    fun drawBackButton(context: DrawContext, rootLeft: Int, rootTop: Int, localMouseX: Int, localMouseY: Int, left: Int = 320, top: Int = 186) {
        val hovered = contains(localMouseX, localMouseY, left, top, 26, 13)
        blitk(matrixStack = context.matrices, texture = BACK_BUTTON, x = rootLeft + left, y = rootTop + top, width = 26, height = 13, vOffset = if (hovered) 13 else 0, textureHeight = 26)
        blitk(matrixStack = context.matrices, texture = BACK_BUTTON_ICON, x = (rootLeft + left + 7) / TEXTURE_SCALE, y = (rootTop + top + 4) / TEXTURE_SCALE, width = 21, height = 11, scale = TEXTURE_SCALE)
    }

    fun drawSlotFrame(context: DrawContext, rootLeft: Int, rootTop: Int, localX: Int, localY: Int, alpha: Float = 0.58F) {
        blitk(matrixStack = context.matrices, texture = SLOT_OVERLAY, x = rootLeft + localX - 4, y = rootTop + localY - 4, width = 25, height = 25, alpha = alpha)
    }

    fun drawInfoButton(context: DrawContext, rootLeft: Int, rootTop: Int, localX: Int, localY: Int, width: Int, hovered: Boolean, active: Boolean) {
        val texture = if (active) PASTURE_SLOT_BUTTON_ACTIVE else PASTURE_BUTTON
        val height = 17
        blitk(
            matrixStack = context.matrices,
            texture = texture,
            x = rootLeft + localX,
            y = rootTop + localY,
            width = width,
            height = height,
            vOffset = if (hovered) height else 0,
            textureHeight = height * 2
        )
    }

    fun drawSmallText(context: DrawContext, textRenderer: TextRenderer, value: String, x: Int, y: Int, color: Int = TEXT_DARK, shadow: Boolean = false, scale: Float = TEXTURE_SCALE) {
        drawScaledText(
            context = context,
            font = if (scale >= 0.65F) CobblemonResources.DEFAULT_LARGE else null,
            text = Text.literal(value),
            x = x,
            y = y,
            scale = scale,
            colour = color,
            shadow = shadow
        )
    }

    fun playClick() = play(CobblemonSounds.PC_CLICK)

    fun play(sound: SoundEvent) {
        MinecraftClient.getInstance().soundManager.play(PositionedSoundInstance.master(sound, 1.0F))
    }

    fun contains(mouseX: Int, mouseY: Int, left: Int, top: Int, width: Int, height: Int): Boolean {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height
    }

    private fun cobblemon(path: String): Identifier = Identifier.of("cobblemon", path)
}
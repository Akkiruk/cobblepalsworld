/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblepalsworld.gui.router

import com.cobblemon.mod.common.client.render.renderScaledGuiItemIcon
import com.cobblepalsworld.gui.CobblemonUiChrome
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.item.ItemStack

internal object CommandPostInfoPanel {
    data class DetailLine(val value: String, val color: Int = 0xFFDDE7EA.toInt(), val shadow: Boolean = false)
    private val STATS_SECTION = CommandPostPcShell.cobblemon("textures/gui/pc/info_box_stats.png")

    fun draw(
        context: DrawContext,
        textRenderer: TextRenderer,
        originX: Int,
        originY: Int,
        preview: CommandPostPokemonRenderView?,
        detailLines: List<DetailLine>,
        heldStack: (String) -> ItemStack?,
        fit: (String, Int) -> String
    ) {
        CommandPostPcShell.drawInfoBox(context, originX, originY)
        CobblemonUiChrome.drawSmallText(context, textRenderer, "COMMAND POST", originX + 17, originY + 131, 0xFFEFE7D7.toInt(), true)

        if (preview == null) {
            CobblemonUiChrome.drawSmallText(context, textRenderer, "No Pokemon", originX + 18, originY + 150, 0xFFB8C3C7.toInt(), false)
            return
        }

        CobblemonUiChrome.drawSmallText(context, textRenderer, fit(preview.displayName, 58), originX + 15, originY + 145, 0xFFFFFFFF.toInt(), true)
        detailLines.take(6).forEachIndexed { index, line ->
            val top = 155 + index * 7
            context.matrices.push()
            context.matrices.translate(0.0, 0.0, 20.0)
            com.cobblemon.mod.common.api.gui.blitk(matrixStack = context.matrices, texture = STATS_SECTION, x = originX + 14, y = originY + top - 1, width = 55, height = 7, alpha = if (line.shadow) 0.72F else 0.42F)
            context.matrices.pop()
            CobblemonUiChrome.drawSmallText(context, textRenderer, fit(line.value, 54), originX + 16, originY + top + 1, line.color, line.shadow)
        }
        heldStack(preview.heldItemId)?.let { stack ->
            renderScaledGuiItemIcon(itemStack = stack, x = originX + 55.0, y = originY + 176.0, scale = 0.5, matrixStack = context.matrices)
        }
    }
}
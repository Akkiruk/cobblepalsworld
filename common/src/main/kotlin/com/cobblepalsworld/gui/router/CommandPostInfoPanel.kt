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
            CobblemonUiChrome.drawSmallText(context, textRenderer, "No Pokemon", originX + 18, originY + 145, 0xFFB8C3C7.toInt(), false)
            CobblemonUiChrome.drawSmallText(context, textRenderer, "Hover a slot", originX + 18, originY + 156, 0xFF8FA0A8.toInt(), false)
            return
        }

        CobblemonUiChrome.drawSmallText(context, textRenderer, fit(preview.displayName, 58), originX + 15, originY + 145, 0xFFFFFFFF.toInt(), true)
        detailLines.take(4).forEachIndexed { index, line ->
            CobblemonUiChrome.drawSmallText(context, textRenderer, fit(line.value, 58), originX + 15, originY + 156 + index * 10, line.color, line.shadow)
        }
        heldStack(preview.heldItemId)?.let { stack ->
            renderScaledGuiItemIcon(itemStack = stack, x = originX + 55.0, y = originY + 176.0, scale = 0.5, matrixStack = context.matrices)
        }
    }
}
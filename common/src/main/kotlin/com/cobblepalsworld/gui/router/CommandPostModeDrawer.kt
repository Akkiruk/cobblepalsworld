/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblepalsworld.gui.router

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.render.renderScaledGuiItemIcon
import com.cobblepalsworld.gui.CobblemonUiChrome
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.item.ItemStack
import net.minecraft.item.Items

internal enum class CommandPostMode(val tooltip: String, val icon: ItemStack) {
    SOURCE("Party and PC", ItemStack(Items.NAME_TAG)),
    JOBS("Jobs", ItemStack(Items.COMPASS)),
    POLICY("Policy", ItemStack(Items.WRITABLE_BOOK)),
    LOGISTICS("Logistics", ItemStack(Items.CHEST))
}

internal enum class CommandPostRosterFilter(val label: String) {
    ALL("All"),
    READY("Ready"),
    ACTIVE("Active"),
    BLOCKED("Blocked"),
    CARGO("Cargo"),
    NEEDS_HELP("Needs Help")
}

internal enum class CommandPostRosterSort(val label: String) {
    STATUS("Status"),
    ROLE("Role"),
    FAMILY("Family"),
    ASSIGNMENT("Assign"),
    SOURCE("Source")
}

internal object CommandPostModeDrawer {
    data class ModeHit(val mode: CommandPostMode, val left: Int, val top: Int)

    const val MODE_LEFT = 92
    const val MODE_TOP = 186
    const val MODE_SIZE = 13
    const val MODE_GAP = 3
    const val FILTER_LEFT = 230
    const val OPTIONS_LEFT = 242
    const val TOOL_TOP = 186
    const val TOOL_SIZE = 8

    val BUTTON_TEXTURE = CommandPostPcShell.cobblemon("textures/gui/pasture/pasture_button.png")
    val FILTER_TEXTURE = CommandPostPcShell.cobblemon("textures/gui/pc/pc_icon_filter.png")
    val OPTIONS_TEXTURE = CommandPostPcShell.cobblemon("textures/gui/pc/pc_icon_options.png")

    fun drawModeButtons(context: DrawContext, originX: Int, originY: Int, localMouseX: Int, localMouseY: Int, selectedMode: CommandPostMode): List<ModeHit> {
        return CommandPostMode.entries.mapIndexed { index, mode ->
            val left = MODE_LEFT + index * (MODE_SIZE + MODE_GAP)
            val hovered = CommandPostIconButton.contains(localMouseX, localMouseY, left, MODE_TOP, MODE_SIZE, MODE_SIZE)
            blitk(
                matrixStack = context.matrices,
                texture = BUTTON_TEXTURE,
                x = originX + left,
                y = originY + MODE_TOP,
                width = MODE_SIZE,
                height = MODE_SIZE,
                vOffset = if (hovered || mode == selectedMode) 17 else 0,
                textureHeight = 34
            )
            renderScaledGuiItemIcon(itemStack = mode.icon, x = originX + left + 2.0, y = originY + MODE_TOP + 2.0, scale = 0.5, matrixStack = context.matrices)
            ModeHit(mode, left, MODE_TOP)
        }
    }

    fun drawToolButtons(context: DrawContext, originX: Int, originY: Int, localMouseX: Int, localMouseY: Int) {
        CommandPostIconButton.draw(context, FILTER_TEXTURE, originX, originY, FILTER_LEFT, TOOL_TOP, TOOL_SIZE, TOOL_SIZE, localMouseX, localMouseY, scaled = true)
        CommandPostIconButton.draw(context, OPTIONS_TEXTURE, originX, originY, OPTIONS_LEFT, TOOL_TOP, TOOL_SIZE, TOOL_SIZE, localMouseX, localMouseY, scaled = true)
    }

    fun drawDrawerLabel(
        context: DrawContext,
        textRenderer: TextRenderer,
        originX: Int,
        originY: Int,
        role: CommandPostRoleFamilyFilter,
        state: CommandPostStateFilter,
        sort: CommandPostRosterSort
    ) {
        CobblemonUiChrome.drawSmallText(context, textRenderer, "${role.label} / ${state.label} / ${sort.label}", originX + 144, originY + 188, 0xFFEFE7D7.toInt(), true)
    }
}
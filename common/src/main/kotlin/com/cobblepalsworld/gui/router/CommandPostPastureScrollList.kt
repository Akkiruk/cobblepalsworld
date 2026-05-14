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
import com.cobblepalsworld.gui.crew.CommandPostCrewMemberSnapshot
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.item.ItemStack
import java.util.UUID

internal object CommandPostPastureScrollList {
    data class CrewHit(val member: CommandPostCrewMemberSnapshot, val left: Int, val top: Int)
    data class Hover(val member: CommandPostCrewMemberSnapshot)

    fun draw(
        context: DrawContext,
        textRenderer: TextRenderer,
        originX: Int,
        originY: Int,
        members: List<CommandPostCrewMemberSnapshot>,
        scrollIndex: Int,
        selectedPokemonId: UUID?,
        localMouseX: Int,
        localMouseY: Int,
        delta: Float,
        heldStack: (String) -> ItemStack?,
        fit: (String, Int) -> String,
        renderPokemon: (CommandPostPokemonRenderView, Int, Int, Float, Float, Float) -> Unit
    ): Pair<List<CrewHit>, Hover?> {
        var hover: Hover? = null
        val hits = mutableListOf<CrewHit>()
        val visibleMembers = members.drop(scrollIndex).take(CommandPostPastureWidget.VISIBLE_ROWS)

        context.enableScissor(
            originX + CommandPostPastureWidget.LIST_LEFT,
            originY + CommandPostPastureWidget.LIST_TOP - 1,
            originX + CommandPostPastureWidget.LIST_LEFT + CommandPostPastureWidget.LIST_WIDTH,
            originY + CommandPostPastureWidget.LIST_TOP + CommandPostPastureWidget.LIST_HEIGHT
        )
        visibleMembers.forEachIndexed { index, member ->
            val row = CommandPostPastureWidget.rowBounds(index)
            hits += CrewHit(member, row.left, row.top)
            val rowHover = drawRow(context, textRenderer, originX, originY, member, row.left, row.top, selectedPokemonId, localMouseX, localMouseY, delta, heldStack, fit, renderPokemon)
            if (rowHover != null) hover = rowHover
        }
        context.disableScissor()

        return hits to hover
    }

    private fun drawRow(
        context: DrawContext,
        textRenderer: TextRenderer,
        originX: Int,
        originY: Int,
        member: CommandPostCrewMemberSnapshot,
        left: Int,
        top: Int,
        selectedPokemonId: UUID?,
        localMouseX: Int,
        localMouseY: Int,
        delta: Float,
        heldStack: (String) -> ItemStack?,
        fit: (String, Int) -> String,
        renderPokemon: (CommandPostPokemonRenderView, Int, Int, Float, Float, Float) -> Unit
    ): Hover? {
        val hovered = CommandPostIconButton.contains(localMouseX, localMouseY, left, top, CommandPostPastureWidget.SLOT_WIDTH, CommandPostPastureWidget.SLOT_HEIGHT)
        CommandPostPastureWidget.drawRowFrame(context, originX, originY, left, top, member.isActive() || selectedPokemonId == member.pokemonId, hovered)

        renderPokemon(CrewPokemonRenderView(member), originX + left + 23, originY + top - 1, delta, 4.5F, 2.5F)
        heldStack(member.heldItemId)?.let { stack ->
            renderScaledGuiItemIcon(itemStack = stack, x = originX + left + 23.5, y = originY + top + 13.0, scale = 0.5, matrixStack = context.matrices)
        }
        CobblemonUiChrome.drawSmallText(context, textRenderer, "Lv.${member.level}", originX + left + 44, originY + top + 17, 0xFFFFFFFF.toInt(), true)
        CobblemonUiChrome.drawSmallText(context, textRenderer, fit(member.displayName, 50), originX + left + 11, originY + top + 24, 0xFFEAF4F5.toInt(), false)

        CommandPostPastureWidget.drawSlotIcon(context, originX, originY, CommandPostPastureWidget.SLOT_MOVE_ICON, left + 2, top + 11, hovered = CommandPostIconButton.contains(localMouseX, localMouseY, left + 2, top + 11, CommandPostPastureWidget.SLOT_ICON_SIZE, CommandPostPastureWidget.SLOT_ICON_SIZE))
        CommandPostPastureWidget.drawSlotIcon(context, originX, originY, CommandPostPastureWidget.SLOT_DEFEND_ICON, left + 44, top + 3, hovered = CommandPostIconButton.contains(localMouseX, localMouseY, left + 44, top + 3, CommandPostPastureWidget.SLOT_ICON_SIZE, CommandPostPastureWidget.SLOT_ICON_SIZE))

        val statusColor = when {
            member.isBlocked() -> 0xFFFF5555.toInt()
            member.isReady() -> 0xFF74E08A.toInt()
            member.carriedItemCount > 0 -> 0xFFFFD166.toInt()
            member.isFainted || member.isMissing -> 0xFF9DA7AF.toInt()
            else -> 0xFF77B6FF.toInt()
        }
        context.fill(originX + left + 1, originY + top + 1, originX + left + 4, originY + top + 4, statusColor)
        return if (hovered) Hover(member) else null
    }
}
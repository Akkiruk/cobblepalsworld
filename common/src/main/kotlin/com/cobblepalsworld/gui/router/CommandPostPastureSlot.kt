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

internal object CommandPostPastureSlot {
    enum class Action { SELECT, RECALL, MODE }
    data class Hit(val member: CommandPostCrewMemberSnapshot, val left: Int, val top: Int, val action: Action)
    data class Hover(val member: CommandPostCrewMemberSnapshot)

    fun draw(
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
        renderPokemon: (CommandPostPokemonRenderView, Int, Int, Float, Float, Float, Boolean) -> Unit
    ): Pair<List<Hit>, Hover?> {
        val hovered = CommandPostIconButton.contains(localMouseX, localMouseY, left, top, CommandPostPastureWidget.SLOT_WIDTH, CommandPostPastureWidget.SLOT_HEIGHT)
        CommandPostPastureWidget.drawRowFrame(context, originX, originY, left, top, member.isActive() || selectedPokemonId == member.pokemonId, hovered)

        renderPokemon(CrewPokemonRenderView(member), originX + left + 23, originY + top - 1, delta, 4.5F, 2.5F, hovered || selectedPokemonId == member.pokemonId)
        heldStack(member.heldItemId)?.let { stack ->
            renderScaledGuiItemIcon(itemStack = stack, x = originX + left + 23.5, y = originY + top + 13.0, scale = 0.5, matrixStack = context.matrices)
        }
        CobblemonUiChrome.drawSmallText(context, textRenderer, "Lv.${member.level}", originX + left + 44, originY + top + 17, 0xFFFFFFFF.toInt(), true)
        CobblemonUiChrome.drawSmallText(context, textRenderer, fit(member.displayName, 50), originX + left + 11, originY + top + 24, 0xFFEAF4F5.toInt(), false)

        val recallHover = containsRecall(localMouseX, localMouseY, left, top)
        val modeHover = containsMode(localMouseX, localMouseY, left, top)
        CommandPostPastureWidget.drawSlotIcon(context, originX, originY, CommandPostPastureWidget.SLOT_MOVE_ICON, left + 2, top + 11, hovered = recallHover)
        CommandPostPastureWidget.drawSlotIcon(context, originX, originY, CommandPostPastureWidget.SLOT_DEFEND_ICON, left + 44, top + 3, hovered = modeHover)

        context.fill(originX + left + 1, originY + top + 1, originX + left + 4, originY + top + 4, statusColor(member))

        val hits = listOf(
            Hit(member, left, top, Action.SELECT),
            Hit(member, left + 2, top + 11, Action.RECALL),
            Hit(member, left + 44, top + 3, Action.MODE)
        )
        return hits to if (hovered) Hover(member) else null
    }

    fun contains(mouseX: Int, mouseY: Int, hit: Hit): Boolean {
        return when (hit.action) {
            Action.SELECT -> CommandPostIconButton.contains(mouseX, mouseY, hit.left, hit.top, CommandPostPastureWidget.SLOT_WIDTH, CommandPostPastureWidget.SLOT_HEIGHT)
            Action.RECALL, Action.MODE -> CommandPostIconButton.contains(mouseX, mouseY, hit.left, hit.top, CommandPostPastureWidget.SLOT_ICON_SIZE, CommandPostPastureWidget.SLOT_ICON_SIZE)
        }
    }

    private fun containsRecall(mouseX: Int, mouseY: Int, left: Int, top: Int): Boolean {
        return CommandPostIconButton.contains(mouseX, mouseY, left + 2, top + 11, CommandPostPastureWidget.SLOT_ICON_SIZE, CommandPostPastureWidget.SLOT_ICON_SIZE)
    }

    private fun containsMode(mouseX: Int, mouseY: Int, left: Int, top: Int): Boolean {
        return CommandPostIconButton.contains(mouseX, mouseY, left + 44, top + 3, CommandPostPastureWidget.SLOT_ICON_SIZE, CommandPostPastureWidget.SLOT_ICON_SIZE)
    }

    private fun statusColor(member: CommandPostCrewMemberSnapshot): Int = when {
        member.isBlocked() -> 0xFFFF5555.toInt()
        member.isReady() -> 0xFF74E08A.toInt()
        member.carriedItemCount > 0 -> 0xFFFFD166.toInt()
        member.isFainted || member.isMissing -> 0xFF9DA7AF.toInt()
        else -> 0xFF77B6FF.toInt()
    }
}
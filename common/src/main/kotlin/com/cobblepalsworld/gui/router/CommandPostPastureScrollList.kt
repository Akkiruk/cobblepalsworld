/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblepalsworld.gui.router

import com.cobblepalsworld.gui.crew.CommandPostCrewMemberSnapshot
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.item.ItemStack
import java.util.UUID

internal object CommandPostPastureScrollList {
    data class CrewHit(val member: CommandPostCrewMemberSnapshot, val left: Int, val top: Int, val action: CommandPostPastureSlot.Action)
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
            val (rowHits, rowHover) = CommandPostPastureSlot.draw(context, textRenderer, originX, originY, member, row.left, row.top, selectedPokemonId, localMouseX, localMouseY, delta, heldStack, fit, renderPokemon)
            hits += rowHits.map { CrewHit(it.member, it.left, it.top, it.action) }
            if (rowHover != null) hover = Hover(rowHover.member)
        }
        context.disableScissor()

        return hits to hover
    }
}
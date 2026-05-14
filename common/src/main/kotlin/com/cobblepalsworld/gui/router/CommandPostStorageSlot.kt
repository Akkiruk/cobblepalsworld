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
import com.cobblepalsworld.gui.crew.CrewSourcePokemonSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceSlotSnapshot
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.item.ItemStack
import java.util.UUID

internal object CommandPostStorageSlot {
    data class Hit(val slot: CrewSourceSlotSnapshot, val left: Int, val top: Int, val size: Int)
    data class Hover(val pokemon: CrewSourcePokemonSnapshot, val selected: Boolean)

    fun draw(
        context: DrawContext,
        textRenderer: TextRenderer,
        originX: Int,
        originY: Int,
        slot: CrewSourceSlotSnapshot,
        left: Int,
        top: Int,
        size: Int,
        localMouseX: Int,
        localMouseY: Int,
        delta: Float,
        selectedPokemonId: UUID?,
        pointerOffsetY: Int,
        heldStack: (String) -> ItemStack?,
        renderPokemon: (CommandPostPokemonRenderView, Int, Int, Float, Float, Float) -> Unit
    ): Hover? {
        val pokemon = slot.pokemon ?: return null
        val hovered = CommandPostIconButton.contains(localMouseX, localMouseY, left, top, size, size)
        val selected = selectedPokemonId == pokemon.pokemonId || hovered

        renderPokemon(SourcePokemonRenderView(pokemon), originX + left + size / 2, originY + top + 1, delta, 4.5F, 2.5F)

        context.matrices.push()
        context.matrices.translate(0.0, 0.0, 140.0)
        CobblemonUiChrome.drawSmallText(context, textRenderer, "Lv.${pokemon.level}", originX + left + 1, originY + top + 1, 0xFFFFFFFF.toInt(), true)
        if ("male" in pokemon.aspects || "female" in pokemon.aspects) {
            blitk(
                matrixStack = context.matrices,
                texture = if ("male" in pokemon.aspects) CommandPostPcShell.GENDER_MALE else CommandPostPcShell.GENDER_FEMALE,
                x = (originX + left + size - 8) / CommandPostPcShell.TEXTURE_SCALE,
                y = (originY + top + 1) / CommandPostPcShell.TEXTURE_SCALE,
                width = 6,
                height = 8,
                scale = CommandPostPcShell.TEXTURE_SCALE
            )
        }
        heldStack(pokemon.heldItemId)?.let { stack ->
            renderScaledGuiItemIcon(itemStack = stack, x = originX + left + size - 9.0, y = originY + top + size - 9.0, scale = 0.5, matrixStack = context.matrices)
        }
        context.matrices.pop()

        context.matrices.push()
        context.matrices.translate(0.0, 0.0, 500.0)
        if (pokemon.isCrewMember || !pokemon.isAvailable) {
            blitk(
                matrixStack = context.matrices,
                texture = CommandPostPcShell.SLOT_OVERLAY,
                x = originX + left,
                y = originY + top,
                width = size,
                height = size,
                alpha = if (pokemon.isCrewMember) 1.0F else 0.72F
            )
        }
        if (pokemon.isCrewMember) {
            blitk(matrixStack = context.matrices, texture = CommandPostPastureWidget.SLOT_PASTURE_ICON, x = (originX + left + 7.5) / CommandPostPcShell.TEXTURE_SCALE, y = (originY + top + 7.5) / CommandPostPcShell.TEXTURE_SCALE, width = 20, height = 20, scale = CommandPostPcShell.TEXTURE_SCALE)
        } else if (hovered && pokemon.isAvailable) {
            blitk(matrixStack = context.matrices, texture = CommandPostPcShell.SLOT_OVERLAY, x = originX + left, y = originY + top, width = size, height = size)
            blitk(matrixStack = context.matrices, texture = CommandPostPastureWidget.SLOT_MOVE_ICON, x = (originX + left + 7.5) / CommandPostPcShell.TEXTURE_SCALE, y = (originY + top + 7.5) / CommandPostPcShell.TEXTURE_SCALE, width = 20, height = 20, scale = CommandPostPcShell.TEXTURE_SCALE)
        }
        if (selected) {
            blitk(matrixStack = context.matrices, texture = CommandPostPcShell.POINTER, x = (originX + left + 10) / CommandPostPcShell.TEXTURE_SCALE, y = ((originY + top - 3) / CommandPostPcShell.TEXTURE_SCALE) - pointerOffsetY, width = 11, height = 8, scale = CommandPostPcShell.TEXTURE_SCALE)
        }
        context.matrices.pop()

        return if (hovered) Hover(pokemon, selected) else null
    }
}
/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblepalsworld.gui.router

import com.cobblepalsworld.gui.crew.CommandPostCrewMemberSnapshot
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import java.util.UUID

internal class CommandPostPastureListWidget(
    x: Int,
    y: Int,
    private val originX: () -> Int,
    private val originY: () -> Int,
    private val membersProvider: () -> List<CommandPostCrewMemberSnapshot>,
    private val selectedPokemonId: () -> UUID?,
    private val scrollIndexProvider: () -> Int,
    private val setScrollIndex: (Int) -> Unit,
    private val onHover: (CommandPostCrewMemberSnapshot?) -> Unit,
    private val onSelect: (CommandPostCrewMemberSnapshot) -> Unit,
    private val onRecall: (CommandPostCrewMemberSnapshot) -> Unit,
    private val onMode: (CommandPostCrewMemberSnapshot) -> Unit,
    private val heldStack: (String) -> ItemStack?,
    private val fit: (String, Int) -> String,
    private val renderPokemon: (DrawContext, CommandPostPokemonRenderView, Int, Int, Float, Float, Float, Boolean) -> Unit
) : ClickableWidget(x, y, CommandPostPastureWidget.LIST_WIDTH, CommandPostPastureWidget.LIST_HEIGHT, Text.empty()) {

    private var draggingScrollbar = false
    private var hits: List<CommandPostPastureScrollList.CrewHit> = emptyList()

    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        x = originX() + CommandPostPastureWidget.LIST_LEFT
        y = originY() + CommandPostPastureWidget.LIST_TOP - 4

        val originX = originX()
        val originY = originY()
        val localMouseX = mouseX - originX
        val localMouseY = mouseY - originY
        val members = membersProvider()
        val maxScroll = CommandPostPastureWidget.maxScroll(members.size)
        val scrollIndex = scrollIndexProvider().coerceIn(0, maxScroll)
        if (scrollIndex != scrollIndexProvider()) {
            setScrollIndex(scrollIndex)
        }

        var hoveredMember: CommandPostCrewMemberSnapshot? = null
        val nextHits = mutableListOf<CommandPostPastureScrollList.CrewHit>()

        context.enableScissor(
            originX + CommandPostPastureWidget.LIST_LEFT,
            originY + CommandPostPastureWidget.LIST_TOP - 1,
            originX + CommandPostPastureWidget.LIST_LEFT + CommandPostPastureWidget.LIST_WIDTH,
            originY + CommandPostPastureWidget.LIST_TOP + CommandPostPastureWidget.LIST_HEIGHT
        )
        members.drop(scrollIndex).take(CommandPostPastureWidget.VISIBLE_ROWS).forEachIndexed { index, member ->
            val row = CommandPostPastureWidget.rowBounds(index)
            val (rowHits, rowHover) = CommandPostPastureSlot.draw(
                context = context,
                textRenderer = MinecraftText.textRenderer,
                originX = originX,
                originY = originY,
                member = member,
                left = row.left,
                top = row.top,
                selectedPokemonId = selectedPokemonId(),
                localMouseX = localMouseX,
                localMouseY = localMouseY,
                delta = delta,
                heldStack = heldStack,
                fit = fit,
                renderPokemon = { view, centerX, topY, renderDelta, modelScale, matrixScale, animate ->
                    renderPokemon(context, view, centerX, topY, renderDelta, modelScale, matrixScale, animate)
                }
            )
            nextHits += rowHits.map { CommandPostPastureScrollList.CrewHit(it.member, it.left, it.top, it.action) }
            if (rowHover != null) {
                hoveredMember = rowHover.member
            }
        }
        context.disableScissor()

        hits = nextHits
        onHover(hoveredMember)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!visible || !active) return false
        if (!isMouseOver(mouseX, mouseY) && !isScrollbarHit(mouseX, mouseY)) return false

        if (isScrollbarHit(mouseX, mouseY)) {
            draggingScrollbar = true
            return true
        }

        val localMouseX = (mouseX - originX()).toInt()
        val localMouseY = (mouseY - originY()).toInt()
        val hit = hits.firstOrNull { CommandPostPastureSlot.contains(localMouseX, localMouseY, CommandPostPastureSlot.Hit(it.member, it.left, it.top, it.action)) }
            ?: return false
        when (hit.action) {
            CommandPostPastureSlot.Action.SELECT -> onSelect(hit.member)
            CommandPostPastureSlot.Action.RECALL -> onRecall(hit.member)
            CommandPostPastureSlot.Action.MODE -> onMode(hit.member)
        }
        return true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        draggingScrollbar = false
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (!draggingScrollbar) return false
        adjustScroll(deltaY.toInt())
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false
        adjustScroll(-verticalAmount.toInt())
        return true
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        appendDefaultNarrations(builder)
    }

    private fun adjustScroll(amount: Int) {
        if (amount == 0) return
        val members = membersProvider()
        val maxScroll = CommandPostPastureWidget.maxScroll(members.size)
        setScrollIndex((scrollIndexProvider() + amount).coerceIn(0, maxScroll))
    }

    private fun isScrollbarHit(mouseX: Double, mouseY: Double): Boolean {
        val left = originX() + CommandPostPastureWidget.LIST_LEFT + CommandPostPastureWidget.LIST_WIDTH - 3
        val top = originY() + CommandPostPastureWidget.LIST_TOP
        return mouseX >= left && mouseX < left + 3 && mouseY >= top && mouseY < top + CommandPostPastureWidget.LIST_HEIGHT
    }

    private object MinecraftText {
        val textRenderer
            get() = net.minecraft.client.MinecraftClient.getInstance().textRenderer
    }
}
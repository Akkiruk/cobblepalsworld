/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblepalsworld.gui.router

import com.cobblepalsworld.gui.CobblemonUiChrome
import com.cobblepalsworld.tag.TagRoleFamily
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext

internal enum class CommandPostDrawerMode { CLOSED, FILTERS, OPTIONS }

internal enum class CommandPostRoleFamilyFilter(val label: String, val family: TagRoleFamily?) {
    ALL("All", null),
    GATHERING("Gather", TagRoleFamily.Gathering),
    LOGISTICS("Logi", TagRoleFamily.Logistics),
    DEFENSE("Guard", TagRoleFamily.Defense),
    INTERACTION("Use", TagRoleFamily.Interaction),
    CARE("Care", TagRoleFamily.Care)
}

internal enum class CommandPostStateFilter(val label: String) {
    ALL("All"),
    READY("Ready"),
    ACTIVE("Active"),
    BLOCKED("Blocked"),
    WAITING("Waiting"),
    STANDBY("Standby"),
    FAINTED("Fainted"),
    MISSING("Missing"),
    NO_ROLE("No Role"),
    CARGO("Cargo")
}

internal enum class CommandPostAssignmentFilter(val label: String) {
    ALL("All"),
    GENERAL("General"),
    PREFERRED("Preferred"),
    RESERVED("Reserved"),
    STRICT("Strict")
}

internal enum class CommandPostAvailabilityFilter(val label: String) {
    ALL("All"),
    AVAILABLE("Available"),
    UNAVAILABLE("Unavailable")
}

internal enum class CommandPostAssignedFilter(val label: String) {
    ALL("All"),
    ASSIGNED("Assigned"),
    UNASSIGNED("Unassigned")
}

internal object CommandPostFilterDrawer {
    enum class Action { SEARCH, ROLE, STATE, ASSIGNMENT, AVAILABILITY, ASSIGNED, SORT }
    data class Hit(val action: Action, val left: Int, val top: Int, val width: Int, val height: Int)

    const val LEFT = CommandPostStorageWidget.X + 5
    const val TOP = 157
    const val WIDTH = 164
    const val HEIGHT = 26
    const val SEARCH_LEFT = LEFT + 4
    const val SEARCH_TOP = TOP + 4
    const val SEARCH_WIDTH = 62
    const val SEARCH_HEIGHT = 11

    fun drawFilters(
        context: DrawContext,
        textRenderer: TextRenderer,
        originX: Int,
        originY: Int,
        localMouseX: Int,
        localMouseY: Int,
        query: String,
        searchActive: Boolean,
        role: CommandPostRoleFamilyFilter,
        state: CommandPostStateFilter,
        assignment: CommandPostAssignmentFilter,
        availability: CommandPostAvailabilityFilter,
        assigned: CommandPostAssignedFilter,
        fit: (String, Int) -> String
    ): List<Hit> {
        drawPanel(context, originX, originY)
        drawSearch(context, textRenderer, originX, originY, query, searchActive, fit)
        return listOf(
            Hit(Action.SEARCH, SEARCH_LEFT, SEARCH_TOP, SEARCH_WIDTH, SEARCH_HEIGHT),
            drawChip(context, textRenderer, originX, originY, localMouseX, localMouseY, Action.ROLE, LEFT + 70, TOP + 4, 24, role.label, fit),
            drawChip(context, textRenderer, originX, originY, localMouseX, localMouseY, Action.STATE, LEFT + 96, TOP + 4, 31, state.label, fit),
            drawChip(context, textRenderer, originX, originY, localMouseX, localMouseY, Action.ASSIGNMENT, LEFT + 129, TOP + 4, 31, assignment.label, fit),
            drawChip(context, textRenderer, originX, originY, localMouseX, localMouseY, Action.AVAILABILITY, LEFT + 70, TOP + 16, 41, availability.label, fit),
            drawChip(context, textRenderer, originX, originY, localMouseX, localMouseY, Action.ASSIGNED, LEFT + 113, TOP + 16, 47, assigned.label, fit)
        )
    }

    fun drawOptions(
        context: DrawContext,
        textRenderer: TextRenderer,
        originX: Int,
        originY: Int,
        localMouseX: Int,
        localMouseY: Int,
        sort: CommandPostRosterSort,
        fit: (String, Int) -> String
    ): List<Hit> {
        drawPanel(context, originX, originY)
        CobblemonUiChrome.drawSmallText(context, textRenderer, "ORDER", originX + SEARCH_LEFT, originY + TOP + 7, 0xFFEFE7D7.toInt(), true)
        return listOf(drawChip(context, textRenderer, originX, originY, localMouseX, localMouseY, Action.SORT, LEFT + 45, TOP + 6, 54, sort.label, fit))
    }

    fun contains(mouseX: Int, mouseY: Int, hit: Hit): Boolean {
        return CommandPostIconButton.contains(mouseX, mouseY, hit.left, hit.top, hit.width, hit.height)
    }

    private fun drawPanel(context: DrawContext, originX: Int, originY: Int) {
        context.fill(originX + LEFT, originY + TOP, originX + LEFT + WIDTH, originY + TOP + HEIGHT, 0xDD111D23.toInt())
        context.fill(originX + LEFT + 1, originY + TOP + 1, originX + LEFT + WIDTH - 1, originY + TOP + HEIGHT - 1, 0xCC213138.toInt())
    }

    private fun drawSearch(context: DrawContext, textRenderer: TextRenderer, originX: Int, originY: Int, query: String, active: Boolean, fit: (String, Int) -> String) {
        val color = if (active) 0xCC3E5962.toInt() else 0xAA18242A.toInt()
        context.fill(originX + SEARCH_LEFT, originY + SEARCH_TOP, originX + SEARCH_LEFT + SEARCH_WIDTH, originY + SEARCH_TOP + SEARCH_HEIGHT, color)
        val text = if (query.isBlank()) "Search" else query
        val textColor = if (query.isBlank()) 0xFF8FA0A8.toInt() else 0xFFEAF4F5.toInt()
        CobblemonUiChrome.drawSmallText(context, textRenderer, fit(text, SEARCH_WIDTH - 6), originX + SEARCH_LEFT + 3, originY + SEARCH_TOP + 3, textColor, false)
    }

    private fun drawChip(context: DrawContext, textRenderer: TextRenderer, originX: Int, originY: Int, localMouseX: Int, localMouseY: Int, action: Action, left: Int, top: Int, width: Int, label: String, fit: (String, Int) -> String): Hit {
        val hovered = CommandPostIconButton.contains(localMouseX, localMouseY, left, top, width, 9)
        context.fill(originX + left, originY + top, originX + left + width, originY + top + 9, if (hovered) 0xCC415761.toInt() else 0xAA26373E.toInt())
        CobblemonUiChrome.drawSmallText(context, textRenderer, fit(label, width - 4), originX + left + 2, originY + top + 2, 0xFFEAF4F5.toInt(), false)
        return Hit(action, left, top, width, 9)
    }
}
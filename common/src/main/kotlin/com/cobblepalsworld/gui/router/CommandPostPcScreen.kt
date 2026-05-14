/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblepalsworld.gui.router

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblepalsworld.gui.CobblemonUiChrome
import com.cobblepalsworld.gui.crew.CommandPostCrewMemberSnapshot
import com.cobblepalsworld.gui.crew.CommandPostCrewSnapshotCache
import com.cobblepalsworld.gui.crew.CrewSourceBoxSnapshot
import com.cobblepalsworld.gui.crew.CrewSourcePokemonSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceSnapshotCache
import com.cobblepalsworld.gui.crew.CrewSourceSlotSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceType
import com.cobblepalsworld.networking.CobblePalsNetworking
import com.cobblepalsworld.router.RouterBlockEntity
import com.cobblepalsworld.tag.TagItem
import com.cobblepalsworld.tag.TagSpec
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.TagTypePresentation
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.joml.Quaternionf
import java.util.UUID
import java.util.Locale

class CommandPostPcScreen(
    handler: RouterScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<RouterScreenHandler>(handler, inventory, title) {
    private data class HoverTooltip(val id: String, val lines: List<Text>)
    private data class TextureButton(val id: String, val left: Int, val top: Int, val width: Int, val height: Int, val texture: Identifier)
    private data class PanelHit(val left: Int, val top: Int, val width: Int, val height: Int, val action: () -> Boolean)
    private data class ModuleView(val rowIndex: Int, val moduleIndex: Int, val stack: ItemStack, val tagType: TagType, val spec: TagSpec)

    private var sourceType = CrewSourceType.PC
    private var sourceBoxIndex = 0
    private var pastureScrollIndex = 0
    private var commandMode = CommandPostMode.SOURCE
    private var rosterFilter = CommandPostRosterFilter.ALL
    private var rosterSort = CommandPostRosterSort.STATUS
    private var selectedPokemonId: UUID? = null
    private var nextCrewRefreshAtMs = 0L
    private var nextSourceRefreshAtMs = 0L
    private var pointerOffsetY = 0
    private var pointerAscending = true

    private var hoveredSource: CrewSourcePokemonSnapshot? = null
    private var hoveredCrew: CommandPostCrewMemberSnapshot? = null
    private var hoveredTooltip: HoverTooltip? = null
    private var slotHits: List<CommandPostStorageSlot.Hit> = emptyList()
    private var crewHits: List<CommandPostPastureScrollList.CrewHit> = emptyList()
    private var modeHits: List<CommandPostModeDrawer.ModeHit> = emptyList()
    private var panelHits: List<PanelHit> = emptyList()

    private val renderStates = mutableMapOf<UUID, FloatingState>()

    init {
        backgroundWidth = CommandPostPcShell.BASE_WIDTH
        backgroundHeight = CommandPostPcShell.BASE_HEIGHT
        playerInventoryTitleY = 10_000
        titleY = 10_000
    }

    override fun init() {
        backgroundWidth = CommandPostPcShell.BASE_WIDTH
        backgroundHeight = CommandPostPcShell.BASE_HEIGHT
        super.init()
        applySlotLayout()
        requestCrewRefresh()
        requestSourceRefresh()
        nextCrewRefreshAtMs = System.currentTimeMillis() + 1_000L
        nextSourceRefreshAtMs = System.currentTimeMillis() + 3_000L
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val now = System.currentTimeMillis()
        if (now >= nextCrewRefreshAtMs) {
            requestCrewRefresh()
            nextCrewRefreshAtMs = now + 1_000L
        }
        if (now >= nextSourceRefreshAtMs) {
            requestSourceRefresh()
            nextSourceRefreshAtMs = now + 4_000L
        }

        pointerOffsetY = if (pointerAscending) pointerOffsetY + 1 else pointerOffsetY - 1
        if (pointerOffsetY >= 3) pointerAscending = false
        if (pointerOffsetY <= 0) pointerAscending = true

        hoveredSource = null
        hoveredCrew = null
        hoveredTooltip = null
        panelHits = emptyList()
        renderBackground(context, mouseX, mouseY, delta)
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)
        hoveredTooltip?.let { context.drawTooltip(textRenderer, it.lines, mouseX, mouseY) }
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        val localMouseX = mouseX - x
        val localMouseY = mouseY - y
        val sources = CrewSourceSnapshotCache.get(handler.routerPos)
        val crew = CommandPostCrewSnapshotCache.get(handler.routerPos)
        val currentBox = currentSourceBox(sources)
        val allMembers = crew?.members.orEmpty()
        val members = filteredMembers(allMembers)
        val preview = previewPokemon(currentBox, allMembers)

        drawPortrait(context, preview, delta)
        drawBase(context)
        drawModeControls(context, localMouseX, localMouseY)
        when (commandMode) {
            CommandPostMode.SOURCE -> drawStorageScreen(context, currentBox, localMouseX, localMouseY, delta)
            CommandPostMode.JOBS -> drawJobsPanel(context, localMouseX, localMouseY)
            CommandPostMode.POLICY -> drawPolicyPanel(context, localMouseX, localMouseY)
            CommandPostMode.LOGISTICS -> drawLogisticsPanel(context)
        }
        drawPasturePanel(context, members, localMouseX, localMouseY, delta)
        drawInfoBox(context, preview)
        drawRouterSlots(context)
        drawExitButton(context, localMouseX, localMouseY)
    }

    override fun drawForeground(context: DrawContext, mouseX: Int, mouseY: Int) {
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val localMouseX = (mouseX - x).toInt()
        val localMouseY = (mouseY - y).toInt()

        if (contains(localMouseX, localMouseY, CommandPostPcShell.EXIT_LEFT, CommandPostPcShell.EXIT_TOP, CommandPostPcShell.EXIT_WIDTH, CommandPostPcShell.EXIT_HEIGHT)) {
            play(CobblemonSounds.PC_CLICK)
            close()
            return true
        }

        modeHits.firstOrNull { hit -> contains(localMouseX, localMouseY, hit.left, hit.top, CommandPostModeDrawer.MODE_SIZE, CommandPostModeDrawer.MODE_SIZE) }?.let { hit ->
            commandMode = hit.mode
            selectedPokemonId = null
            play(CobblemonSounds.PC_CLICK)
            return true
        }

        if (contains(localMouseX, localMouseY, CommandPostModeDrawer.FILTER_LEFT, CommandPostModeDrawer.TOOL_TOP, CommandPostModeDrawer.TOOL_SIZE, CommandPostModeDrawer.TOOL_SIZE)) {
            rosterFilter = CommandPostRosterFilter.entries[(rosterFilter.ordinal + 1) % CommandPostRosterFilter.entries.size]
            pastureScrollIndex = 0
            play(CobblemonSounds.PC_CLICK)
            return true
        }

        if (contains(localMouseX, localMouseY, CommandPostModeDrawer.OPTIONS_LEFT, CommandPostModeDrawer.TOOL_TOP, CommandPostModeDrawer.TOOL_SIZE, CommandPostModeDrawer.TOOL_SIZE)) {
            rosterSort = CommandPostRosterSort.entries[(rosterSort.ordinal + 1) % CommandPostRosterSort.entries.size]
            pastureScrollIndex = 0
            play(CobblemonSounds.PC_CLICK)
            return true
        }

        panelHits.firstOrNull { hit -> contains(localMouseX, localMouseY, hit.left, hit.top, hit.width, hit.height) }?.let { hit ->
            if (hit.action()) {
                play(CobblemonSounds.PC_CLICK)
                return true
            }
        }

        if (commandMode == CommandPostMode.SOURCE && sourceType == CrewSourceType.PC && contains(localMouseX, localMouseY, CommandPostStorageWidget.PREV_LEFT, CommandPostStorageWidget.NAV_TOP, CommandPostStorageWidget.NAV_SIZE, CommandPostStorageWidget.NAV_SIZE)) {
            changeBox(-1)
            return true
        }
        if (commandMode == CommandPostMode.SOURCE && sourceType == CrewSourceType.PC && contains(localMouseX, localMouseY, CommandPostStorageWidget.NEXT_LEFT, CommandPostStorageWidget.NAV_TOP, CommandPostStorageWidget.NAV_SIZE, CommandPostStorageWidget.NAV_SIZE)) {
            changeBox(1)
            return true
        }
        if (commandMode == CommandPostMode.SOURCE && contains(localMouseX, localMouseY, SOURCE_BUTTON_LEFT, SOURCE_BUTTON_TOP, SOURCE_BUTTON_SIZE, SOURCE_BUTTON_SIZE)) {
            sourceType = if (sourceType == CrewSourceType.PC) CrewSourceType.PARTY else CrewSourceType.PC
            selectedPokemonId = null
            play(CobblemonSounds.PC_CLICK)
            requestSourceRefresh()
            return true
        }

        if (CommandPostPastureWidget.recallContains(localMouseX, localMouseY)) {
            val members = CommandPostCrewSnapshotCache.get(handler.routerPos)?.members.orEmpty()
            members.forEach { CobblePalsNetworking.sendRemoveCrewPokemon(handler.routerPos, it.pokemonId) }
            play(CobblemonSounds.PC_RELEASE)
            requestCrewRefresh()
            requestSourceRefresh()
            return true
        }

        crewHits.firstOrNull { hit -> contains(localMouseX, localMouseY, hit.left + 2, hit.top + 11, CommandPostPastureWidget.SLOT_ICON_SIZE, CommandPostPastureWidget.SLOT_ICON_SIZE) }?.let { hit ->
            selectedPokemonId = hit.member.pokemonId
            CobblePalsNetworking.sendRemoveCrewPokemon(handler.routerPos, hit.member.pokemonId)
            play(CobblemonSounds.PC_DROP)
            requestCrewRefresh()
            requestSourceRefresh()
            return true
        }

        crewHits.firstOrNull { hit -> contains(localMouseX, localMouseY, hit.left + 44, hit.top + 3, CommandPostPastureWidget.SLOT_ICON_SIZE, CommandPostPastureWidget.SLOT_ICON_SIZE) }?.let { hit ->
            selectedPokemonId = hit.member.pokemonId
            CobblePalsNetworking.sendCycleCrewMode(handler.routerPos, hit.member.pokemonId)
            play(CobblemonSounds.PC_CLICK)
            requestCrewRefresh()
            return true
        }

        crewHits.firstOrNull { hit -> contains(localMouseX, localMouseY, hit.left, hit.top, CommandPostPastureWidget.SLOT_WIDTH, CommandPostPastureWidget.SLOT_HEIGHT) }?.let { hit ->
            selectedPokemonId = hit.member.pokemonId
            play(CobblemonSounds.PC_CLICK)
            return true
        }

        if (commandMode == CommandPostMode.SOURCE) slotHits.firstOrNull { hit -> contains(localMouseX, localMouseY, hit.left, hit.top, hit.size, hit.size) }?.slot?.pokemon?.let { pokemon ->
            selectedPokemonId = pokemon.pokemonId
            if (pokemon.isCrewMember) {
                CobblePalsNetworking.sendRemoveCrewPokemon(handler.routerPos, pokemon.pokemonId)
                play(CobblemonSounds.PC_DROP)
                requestCrewRefresh()
                requestSourceRefresh()
                return true
            }
            if (pokemon.isAvailable) {
                CobblePalsNetworking.sendAssignCrewPokemon(handler.routerPos, pokemon.pokemonId)
                play(CobblemonSounds.PC_GRAB)
                requestCrewRefresh()
                requestSourceRefresh()
                return true
            }
            play(CobblemonSounds.PC_CLICK)
            return true
        }

        if (button == 1) {
            hoveredModuleSlot(localMouseX, localMouseY)?.let { moduleIndex ->
                val slot = handler.slots.getOrNull(moduleIndex)
                if (slot?.stack?.item is TagItem) {
                    client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.ACTION_EDIT_MODULE_BASE + moduleIndex)
                    play(CobblemonSounds.PC_CLICK)
                    return true
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val localMouseX = (mouseX - x).toInt()
        val localMouseY = (mouseY - y).toInt()
        if (CommandPostPastureWidget.listContains(localMouseX, localMouseY)) {
            val members = CommandPostCrewSnapshotCache.get(handler.routerPos)?.members.orEmpty()
            val maxScroll = CommandPostPastureWidget.maxScroll(members.size)
            pastureScrollIndex = (pastureScrollIndex - verticalAmount.toInt()).coerceIn(0, maxScroll)
            return true
        }
        if (commandMode == CommandPostMode.SOURCE && sourceType == CrewSourceType.PC && CommandPostStorageWidget.contains(localMouseX, localMouseY)) {
            changeBox(if (verticalAmount > 0) -1 else 1)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    private fun drawBase(context: DrawContext) {
        CommandPostPcShell.drawBase(context, x, y)
    }

    private fun drawPortrait(context: DrawContext, preview: CommandPostPokemonRenderView?, delta: Float) {
        CommandPostPcShell.drawPortraitPanel(context, x, y)
        if (preview != null) {
            renderPokemon(context, preview, x + 39, y + 26, delta, 7.0F, 3.2F)
        }
    }

    private fun drawInfoBox(context: DrawContext, preview: CommandPostPokemonRenderView?) {
        CommandPostInfoPanel.draw(context, textRenderer, x, y, preview, detailLines(preview), ::heldStack, ::fit)
    }

    private fun drawStorageScreen(context: DrawContext, box: CrewSourceBoxSnapshot?, localMouseX: Int, localMouseY: Int, delta: Float) {
        val title = if (sourceType == CrewSourceType.PARTY) "Party" else box?.label ?: "Box ${sourceBoxIndex + 1}"
        CommandPostStorageWidget.drawTitle(context, textRenderer, x, y, title)

        if (sourceType == CrewSourceType.PARTY) {
            CommandPostPartyWidget.drawPanel(context, textRenderer, x, y)
        } else {
            CommandPostStorageWidget.drawNavigation(context, x, y, localMouseX, localMouseY)
            CommandPostStorageWidget.drawFrame(context, x, y)
        }

        val hits = mutableListOf<CommandPostStorageSlot.Hit>()
        val slots = box?.slots.orEmpty()
        slots.forEachIndexed { index, slot ->
            val left: Int
            val top: Int
            val size: Int
            if (sourceType == CrewSourceType.PARTY) {
                if (index !in 0..5) return@forEachIndexed
                left = CommandPostPartyWidget.slotLeft(index)
                top = CommandPostPartyWidget.slotTop(index)
                size = CommandPostPartyWidget.SLOT_SIZE
            } else {
                val gridIndex = slot.slotIndex
                if (gridIndex !in 0 until CommandPostStorageWidget.BOX_SLOT_COUNT) return@forEachIndexed
                left = CommandPostStorageWidget.slotLeft(gridIndex)
                top = CommandPostStorageWidget.slotTop(gridIndex)
                size = CommandPostStorageWidget.SLOT_SIZE
            }
            hits += CommandPostStorageSlot.Hit(slot, left, top, size)
            CommandPostStorageSlot.draw(context, textRenderer, x, y, slot, left, top, size, localMouseX, localMouseY, delta, selectedPokemonId, pointerOffsetY, ::heldStack) { view, centerX, topY, renderDelta, modelScale, matrixScale ->
                renderPokemon(context, view, centerX, topY, renderDelta, modelScale, matrixScale)
            }?.let { hover ->
                hoveredSource = hover.pokemon
                hoveredTooltip = HoverTooltip("source-${hover.pokemon.pokemonId}", sourceTooltip(hover.pokemon))
            }
        }
        slotHits = hits
    }

    private fun drawPasturePanel(context: DrawContext, members: List<CommandPostCrewMemberSnapshot>, localMouseX: Int, localMouseY: Int, delta: Float) {
        val maxScroll = CommandPostPastureWidget.maxScroll(members.size)
        pastureScrollIndex = pastureScrollIndex.coerceIn(0, maxScroll)
        val maxWorkers = CommandPostCrewSnapshotCache.get(handler.routerPos)?.maxWorkers ?: 0

        CommandPostPastureWidget.drawPanel(context, textRenderer, x, y)
        val (hits, hover) = CommandPostPastureScrollList.draw(context, textRenderer, x, y, members, pastureScrollIndex, selectedPokemonId, localMouseX, localMouseY, delta, ::heldStack, ::fit) { view, centerX, topY, renderDelta, modelScale, matrixScale ->
            renderPokemon(context, view, centerX, topY, renderDelta, modelScale, matrixScale)
        }
        crewHits = hits
        hover?.let {
            hoveredCrew = it.member
            hoveredTooltip = HoverTooltip("crew-${it.member.pokemonId}", crewTooltip(it.member))
        }

        CommandPostPastureWidget.drawScrollOverlay(context, x, y)
        CommandPostPastureWidget.drawControls(context, textRenderer, x, y, members.size, maxWorkers, localMouseX, localMouseY)
        if (CommandPostPastureWidget.recallContains(localMouseX, localMouseY)) {
            hoveredTooltip = HoverTooltip("recall", listOf(Text.literal("Recall all Command Post Pokemon")))
        }
    }

    private fun drawRouterSlots(context: DrawContext) {
        if (commandMode == CommandPostMode.SOURCE) return
        for (row in 0 until RouterScreenHandler.MODULE_ROWS) {
            for (col in 0 until RouterScreenHandler.MODULE_COLUMNS) {
                val left = MODULE_SLOT_LEFT + col * 18
                val top = MODULE_SLOT_TOP + row * 18
                blitk(matrixStack = context.matrices, texture = CommandPostPcShell.SLOT_OVERLAY, x = x + left - 4, y = y + top - 4, width = CommandPostStorageWidget.SLOT_SIZE, height = CommandPostStorageWidget.SLOT_SIZE, alpha = 0.55F)
            }
        }
        for (index in 0 until RouterBlockEntity.UPGRADE_SLOT_COUNT) {
            val left = UPGRADE_SLOT_LEFT + (index % 3) * 18
            val top = UPGRADE_SLOT_TOP + (index / 3) * 18
            blitk(matrixStack = context.matrices, texture = CommandPostPcShell.SLOT_OVERLAY, x = x + left - 4, y = y + top - 4, width = CommandPostStorageWidget.SLOT_SIZE, height = CommandPostStorageWidget.SLOT_SIZE, alpha = 0.38F)
        }
    }

    private fun drawModeControls(context: DrawContext, localMouseX: Int, localMouseY: Int) {
        modeHits = CommandPostModeDrawer.drawModeButtons(context, x, y, localMouseX, localMouseY, commandMode)
        CommandPostModeDrawer.drawToolButtons(context, x, y, localMouseX, localMouseY)
        CommandPostModeDrawer.drawDrawerLabel(context, textRenderer, x, y, rosterFilter, rosterSort)
        modeHits.firstOrNull { hit -> contains(localMouseX, localMouseY, hit.left, hit.top, CommandPostModeDrawer.MODE_SIZE, CommandPostModeDrawer.MODE_SIZE) }?.let { hit ->
            hoveredTooltip = HoverTooltip("mode-${hit.mode.name}", listOf(Text.literal(hit.mode.tooltip)))
        }
        if (contains(localMouseX, localMouseY, CommandPostModeDrawer.FILTER_LEFT, CommandPostModeDrawer.TOOL_TOP, CommandPostModeDrawer.TOOL_SIZE, CommandPostModeDrawer.TOOL_SIZE)) {
            hoveredTooltip = HoverTooltip("filter", listOf(Text.literal("Roster filter: ${rosterFilter.label}")))
        }
        if (contains(localMouseX, localMouseY, CommandPostModeDrawer.OPTIONS_LEFT, CommandPostModeDrawer.TOOL_TOP, CommandPostModeDrawer.TOOL_SIZE, CommandPostModeDrawer.TOOL_SIZE)) {
            hoveredTooltip = HoverTooltip("sort", listOf(Text.literal("Roster sort: ${rosterSort.label}")))
        }
        if (commandMode == CommandPostMode.SOURCE) {
            drawSourceModeButton(context, localMouseX, localMouseY)
        }
    }

    private fun drawJobsPanel(context: DrawContext, localMouseX: Int, localMouseY: Int) {
        drawOperationalFrame(context, "JOBS")
        val views = moduleViews()
        if (views.isEmpty()) {
            drawSmallText(context, "Install tag cards", CommandPostStorageWidget.X + 14, CommandPostStorageWidget.Y + 24, 0xFFB8C3C7.toInt(), false)
            slotHits = emptyList()
            return
        }
        val hits = mutableListOf<PanelHit>()
        views.take(6).forEachIndexed { row, view ->
            val top = CommandPostStorageWidget.Y + 20 + row * 19
            val active = handler.moduleActive(view.moduleIndex)
            val assigned = handler.moduleAssigned(view.moduleIndex)
            val rowColor = when {
                active -> 0xFF6FEA8A.toInt()
                assigned -> 0xFFFFD166.toInt()
                else -> 0xFFEAF4F5.toInt()
            }
            context.fill(x + CommandPostStorageWidget.X + 8, y + top - 2, x + CommandPostStorageWidget.X + 164, y + top + 15, if (contains(localMouseX, localMouseY, CommandPostStorageWidget.X + 8, top - 2, 156, 17)) 0x5522343A else 0x3318242A)
            drawSmallText(context, TagTypePresentation.roleLabel(view.tagType), CommandPostStorageWidget.X + 14, top, rowColor, true)
            drawSmallText(context, TagTypePresentation.familyOf(view.tagType).label, CommandPostStorageWidget.X + 92, top, 0xFFB8C3C7.toInt(), false)
            drawSmallText(context, if (active) "Active" else if (assigned) "Staffed" else "Open", CommandPostStorageWidget.X + 124, top + 8, rowColor, false)
            hits += PanelHit(CommandPostStorageWidget.X + 8, top - 2, 156, 17) {
                client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.ACTION_OPEN_POLICY_ROW_BASE + view.rowIndex)
                true
            }
        }
        panelHits = hits
        slotHits = emptyList()
    }

    private fun drawPolicyPanel(context: DrawContext, localMouseX: Int, localMouseY: Int) {
        drawOperationalFrame(context, "POLICY")
        val views = moduleViews()
        if (views.isEmpty()) {
            drawSmallText(context, "No role cards", CommandPostStorageWidget.X + 14, CommandPostStorageWidget.Y + 24, 0xFFB8C3C7.toInt(), false)
            slotHits = emptyList()
            return
        }
        val hits = mutableListOf<PanelHit>()
        views.take(5).forEachIndexed { row, view ->
            val top = CommandPostStorageWidget.Y + 18 + row * 24
            context.fill(x + CommandPostStorageWidget.X + 8, y + top - 2, x + CommandPostStorageWidget.X + 164, y + top + 20, if (contains(localMouseX, localMouseY, CommandPostStorageWidget.X + 8, top - 2, 156, 20)) 0x5522343A else 0x3318242A)
            drawSmallText(context, TagTypePresentation.roleLabel(view.tagType), CommandPostStorageWidget.X + 13, top, 0xFFEAF4F5.toInt(), true)
            drawSmallText(context, "${humanValue(view.spec.filter.matchMode.name)} ${if (view.spec.filter.whitelist) "Allow" else "Deny"}", CommandPostStorageWidget.X + 13, top + 9, 0xFFB8C3C7.toInt(), false)
            val signalLeft = CommandPostStorageWidget.X + 91
            val targetLeft = CommandPostStorageWidget.X + 115
            val runLeft = CommandPostStorageWidget.X + 139
            drawMiniChip(context, signalLeft, top + 5, compactValue(view.spec.settings.redstoneMode.id), localMouseX, localMouseY)
            drawMiniChip(context, targetLeft, top + 5, compactValue(view.spec.settings.targetStrategy.id), localMouseX, localMouseY)
            drawMiniChip(context, runLeft, top + 5, if (view.spec.settings.terminateAfterSuccess) "1" else "Loop", localMouseX, localMouseY)
            hits += PanelHit(CommandPostStorageWidget.X + 8, top - 2, 80, 20) {
                client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.ACTION_OPEN_POLICY_ROW_BASE + view.rowIndex)
                true
            }
            hits += PanelHit(signalLeft, top + 5, 20, 10) {
                client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.policyQuickActionId(view.rowIndex, RouterScreenHandler.POLICY_ACTION_CYCLE_SIGNAL))
                true
            }
            hits += PanelHit(targetLeft, top + 5, 20, 10) {
                client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.policyQuickActionId(view.rowIndex, RouterScreenHandler.POLICY_ACTION_CYCLE_TARGET))
                true
            }
            hits += PanelHit(runLeft, top + 5, 20, 10) {
                client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.policyQuickActionId(view.rowIndex, RouterScreenHandler.POLICY_ACTION_TOGGLE_RUN))
                true
            }
        }
        panelHits = hits
        slotHits = emptyList()
    }

    private fun drawLogisticsPanel(context: DrawContext) {
        drawOperationalFrame(context, "LOGISTICS")
        val storageSlots = handler.slots.drop(RouterScreenHandler.STORAGE_SCREEN_SLOT_START).take(RouterBlockEntity.STORAGE_SLOT_COUNT)
        val usedSlots = storageSlots.count { it.hasStack() }
        val itemCount = storageSlots.sumOf { it.stack.count }
        val moduleCount = moduleViews().size
        val logisticsCount = moduleViews().count { TagTypePresentation.familyOf(it.tagType) == com.cobblepalsworld.tag.TagRoleFamily.Logistics }
        val pressure = when {
            usedSlots >= RouterBlockEntity.STORAGE_SLOT_COUNT -> "Full"
            usedSlots >= RouterBlockEntity.STORAGE_SLOT_COUNT * 2 / 3 -> "High"
            usedSlots > 0 -> "Flowing"
            else -> "Empty"
        }
        drawSmallText(context, "Buffer", CommandPostStorageWidget.X + 13, CommandPostStorageWidget.Y + 24, 0xFFEAF4F5.toInt(), true)
        drawSmallText(context, "$usedSlots/${RouterBlockEntity.STORAGE_SLOT_COUNT} slots", CommandPostStorageWidget.X + 86, CommandPostStorageWidget.Y + 24, 0xFFDDE7EA.toInt(), false)
        drawSmallText(context, "$itemCount items", CommandPostStorageWidget.X + 13, CommandPostStorageWidget.Y + 40, 0xFFB8C3C7.toInt(), false)
        drawSmallText(context, "Pressure $pressure", CommandPostStorageWidget.X + 86, CommandPostStorageWidget.Y + 40, if (pressure == "Full") 0xFFFF7777.toInt() else 0xFFBFE7C4.toInt(), false)
        drawSmallText(context, "Role cards", CommandPostStorageWidget.X + 13, CommandPostStorageWidget.Y + 60, 0xFFEAF4F5.toInt(), true)
        drawSmallText(context, "$moduleCount installed", CommandPostStorageWidget.X + 86, CommandPostStorageWidget.Y + 60, 0xFFDDE7EA.toInt(), false)
        drawSmallText(context, "Logistics", CommandPostStorageWidget.X + 13, CommandPostStorageWidget.Y + 76, 0xFFEAF4F5.toInt(), true)
        drawSmallText(context, "$logisticsCount routes", CommandPostStorageWidget.X + 86, CommandPostStorageWidget.Y + 76, 0xFFDDE7EA.toInt(), false)
        drawSmallText(context, "Upgrade slots", CommandPostStorageWidget.X + 13, CommandPostStorageWidget.Y + 96, 0xFFEAF4F5.toInt(), true)
        drawSmallText(context, "Use lower-left sockets", CommandPostStorageWidget.X + 13, CommandPostStorageWidget.Y + 108, 0xFFB8C3C7.toInt(), false)
        panelHits = emptyList()
        slotHits = emptyList()
    }

    private fun drawOperationalFrame(context: DrawContext, title: String) {
        CommandPostStorageWidget.drawFrame(context, x, y)
        CommandPostStorageWidget.drawTitle(context, textRenderer, x, y, title)
    }

    private fun drawMiniChip(context: DrawContext, left: Int, top: Int, value: String, localMouseX: Int, localMouseY: Int) {
        val hovered = contains(localMouseX, localMouseY, left, top, 20, 10)
        context.fill(x + left, y + top, x + left + 20, y + top + 10, if (hovered) 0xAA31444C.toInt() else 0x88233238.toInt())
        drawSmallText(context, fit(value, 18), left + 2, top + 2, 0xFFEAF4F5.toInt(), false)
    }

    private fun drawSourceModeButton(context: DrawContext, localMouseX: Int, localMouseY: Int) {
        drawTextureButton(context, TextureButton("source", SOURCE_BUTTON_LEFT, SOURCE_BUTTON_TOP, SOURCE_BUTTON_SIZE, SOURCE_BUTTON_SIZE, SOURCE_BUTTON_TEXTURE), localMouseX, localMouseY, scaled = true)
        if (contains(localMouseX, localMouseY, SOURCE_BUTTON_LEFT, SOURCE_BUTTON_TOP, SOURCE_BUTTON_SIZE, SOURCE_BUTTON_SIZE)) {
            hoveredTooltip = HoverTooltip("source-toggle", listOf(Text.literal(if (sourceType == CrewSourceType.PC) "Show Party" else "Show PC")))
        }
    }

    private fun drawExitButton(context: DrawContext, localMouseX: Int, localMouseY: Int) {
        CommandPostPcShell.drawExitButton(context, x, y, localMouseX, localMouseY)
    }

    private fun drawTextureButton(context: DrawContext, button: TextureButton, localMouseX: Int, localMouseY: Int, scaled: Boolean) {
        CommandPostIconButton.draw(context, button.texture, x, y, button.left, button.top, button.width, button.height, localMouseX, localMouseY, scaled)
    }

    private fun renderPokemon(context: DrawContext, view: CommandPostPokemonRenderView, centerX: Int, topY: Int, delta: Float, modelScale: Float, matrixScale: Float) {
        val renderable = renderablePokemon(view) ?: return
        val state = renderStates.getOrPut(view.pokemonId) { FloatingState() }
        val clipHalfWidth = if (matrixScale > 3.0F) 36 else 18
        val clipHeight = if (matrixScale > 3.0F) 68 else 32
        context.enableScissor(centerX - clipHalfWidth, topY, centerX + clipHalfWidth, topY + clipHeight)
        context.matrices.push()
        context.matrices.translate(centerX.toDouble(), topY.toDouble(), 0.0)
        context.matrices.scale(matrixScale, matrixScale, 1F)
        drawProfilePokemon(
            renderablePokemon = renderable,
            matrixStack = context.matrices,
            rotation = Quaternionf().rotateXYZ(Math.toRadians(13.0).toFloat(), Math.toRadians(35.0).toFloat(), 0F),
            state = state,
            partialTicks = if (view.isFainted) 0F else delta,
            scale = modelScale
        )
        context.matrices.pop()
        context.disableScissor()
    }

    private fun renderablePokemon(view: CommandPostPokemonRenderView): RenderablePokemon? {
        val identifier = runCatching { Identifier.of(view.speciesIdentifier) }.getOrNull()
        val species = identifier?.let(PokemonSpecies::getByIdentifier)
            ?: PokemonSpecies.getByName(view.species.substringAfter(':'))
            ?: return null
        return RenderablePokemon(species, view.aspects, heldStack(view.heldItemId) ?: ItemStack.EMPTY)
    }

    private fun heldStack(itemId: String): ItemStack? {
        if (itemId.isBlank()) return null
        val identifier = runCatching { Identifier.of(itemId) }.getOrNull() ?: return null
        val item = Registries.ITEM.get(identifier)
        if (item == Items.AIR) return null
        return ItemStack(item)
    }

    private fun currentSourceBox(sources: List<CrewSourceSnapshot>): CrewSourceBoxSnapshot? {
        val source = sources.firstOrNull { it.sourceType == sourceType } ?: return null
        if (sourceType == CrewSourceType.PARTY) return source.boxes.firstOrNull()
        val boxCount = source.boxCount
        if (boxCount <= 0) return null
        sourceBoxIndex = sourceBoxIndex.coerceIn(0, boxCount - 1)
        return source.boxes.firstOrNull { it.boxIndex == sourceBoxIndex } ?: source.boxes.firstOrNull()
    }

    private fun previewPokemon(box: CrewSourceBoxSnapshot?, members: List<CommandPostCrewMemberSnapshot>): CommandPostPokemonRenderView? {
        hoveredSource?.let { return SourcePokemonRenderView(it) }
        hoveredCrew?.let { return CrewPokemonRenderView(it) }
        selectedPokemonId?.let { selected ->
            box?.slots?.firstNotNullOfOrNull { it.pokemon?.takeIf { pokemon -> pokemon.pokemonId == selected } }?.let { return SourcePokemonRenderView(it) }
            members.firstOrNull { it.pokemonId == selected }?.let { return CrewPokemonRenderView(it) }
        }
        members.firstOrNull()?.let { return CrewPokemonRenderView(it) }
        box?.slots?.firstNotNullOfOrNull { it.pokemon }?.let { return SourcePokemonRenderView(it) }
        return null
    }

    private fun changeBox(delta: Int) {
        val source = CrewSourceSnapshotCache.get(handler.routerPos).firstOrNull { it.sourceType == CrewSourceType.PC } ?: return
        if (source.boxCount <= 0) return
        sourceBoxIndex = Math.floorMod(sourceBoxIndex + delta, source.boxCount)
        selectedPokemonId = null
        play(CobblemonSounds.PC_CLICK)
        requestSourceRefresh()
    }

    private fun requestCrewRefresh() {
        CobblePalsNetworking.sendCommandPostCrewRefresh(handler.routerPos)
    }

    private fun requestSourceRefresh() {
        CobblePalsNetworking.sendCrewSourceRefresh(handler.routerPos, sourceType, sourceBoxIndex, "")
    }

    private fun filteredMembers(members: List<CommandPostCrewMemberSnapshot>): List<CommandPostCrewMemberSnapshot> {
        val filtered = members.filter { member ->
            when (rosterFilter) {
                CommandPostRosterFilter.ALL -> true
                CommandPostRosterFilter.READY -> member.isReady()
                CommandPostRosterFilter.ACTIVE -> member.isActive()
                CommandPostRosterFilter.BLOCKED -> member.isBlocked()
                CommandPostRosterFilter.CARGO -> member.carriedItemCount > 0
                CommandPostRosterFilter.NEEDS_HELP -> member.isMissing || member.isFainted || member.isBlocked() || member.tagTypeId == null
            }
        }
        return when (rosterSort) {
            CommandPostRosterSort.STATUS -> filtered.sortedWith(compareBy<CommandPostCrewMemberSnapshot> { it.sortRank() }.thenBy { it.displayName })
            CommandPostRosterSort.ROLE -> filtered.sortedWith(compareBy<CommandPostCrewMemberSnapshot> { it.tagTypeId ?: "" }.thenBy { it.displayName })
            CommandPostRosterSort.SOURCE -> filtered.sortedWith(compareBy<CommandPostCrewMemberSnapshot> { it.sourceType }.thenBy { it.boxIndex }.thenBy { it.slotIndex })
        }
    }

    private fun detailLines(preview: CommandPostPokemonRenderView?): List<CommandPostInfoPanel.DetailLine> {
        if (preview == null) return emptyList()
        val gender = when {
            "male" in preview.aspects -> " M"
            "female" in preview.aspects -> " F"
            else -> ""
        }
        val base = CommandPostInfoPanel.DetailLine("Lv.${preview.level} ${friendlySpecies(preview.species)}$gender")
        val status = when (preview) {
            is SourcePokemonRenderView -> CommandPostInfoPanel.DetailLine(preview.source.statusLabel(), if (preview.source.isAvailable) 0xFFBFE7C4.toInt() else 0xFFFFD166.toInt())
            is CrewPokemonRenderView -> CommandPostInfoPanel.DetailLine(preview.member.statusLabel(), if (preview.member.isBlocked() || preview.member.isFainted || preview.member.isMissing) 0xFFFF7777.toInt() else 0xFFBFE7C4.toInt())
            else -> CommandPostInfoPanel.DetailLine(if (preview.isFainted) "Fainted" else "Ready")
        }
        val source = when (preview) {
            is SourcePokemonRenderView -> CommandPostInfoPanel.DetailLine(preview.source.sourceLabel(), 0xFFB8C3C7.toInt())
            is CrewPokemonRenderView -> CommandPostInfoPanel.DetailLine(preview.member.sourceLabel(), 0xFFB8C3C7.toInt())
            else -> CommandPostInfoPanel.DetailLine("Selected", 0xFFB8C3C7.toInt())
        }
        val role = when (preview) {
            is SourcePokemonRenderView -> preview.source.tagTypeId?.let { tagRoleLine(it) }
                ?: CommandPostInfoPanel.DetailLine(preview.source.cargoSummary.ifBlank { "Crew candidate" }, 0xFF8FA0A8.toInt())
            is CrewPokemonRenderView -> preview.member.tagTypeId?.let { tagRoleLine(it) }
                ?: CommandPostInfoPanel.DetailLine(preview.member.assignmentLabel(), 0xFF8FA0A8.toInt())
            else -> CommandPostInfoPanel.DetailLine("Ready", 0xFF8FA0A8.toInt())
        }
        return listOf(base, status, source, role)
    }

    private fun tagRoleLine(tagId: String): CommandPostInfoPanel.DetailLine {
        val tagType = TagType.fromId(tagId)
        val label = tagType?.let(TagTypePresentation::roleLabel) ?: tagId
        return CommandPostInfoPanel.DetailLine(label, 0xFFEAF4F5.toInt(), true)
    }

    private fun moduleViews(): List<ModuleView> {
        val registries = client?.world?.registryManager ?: return emptyList()
        return (0 until RouterBlockEntity.MODULE_SLOT_COUNT).mapNotNull { moduleIndex ->
            val stack = handler.slots.getOrNull(moduleIndex)?.stack ?: return@mapNotNull null
            val tagItem = stack.item as? TagItem ?: return@mapNotNull null
            val spec = TagItem.getSpec(stack, registries) ?: TagSpec(type = tagItem.tagType)
            ModuleView(rowIndex = moduleIndex, moduleIndex = moduleIndex, stack = stack, tagType = tagItem.tagType, spec = spec)
        }
    }

    private fun play(sound: SoundEvent) {
        MinecraftClient.getInstance().soundManager.play(PositionedSoundInstance.master(sound, 1.0F))
    }

    private fun applySlotLayout() {
        for (row in 0 until RouterScreenHandler.MODULE_ROWS) {
            for (col in 0 until RouterScreenHandler.MODULE_COLUMNS) {
                val slotIndex = row * RouterScreenHandler.MODULE_COLUMNS + col
                setSlotPosition(slotIndex, MODULE_SLOT_LEFT + col * 18, MODULE_SLOT_TOP + row * 18)
            }
        }
        for (index in 0 until RouterBlockEntity.UPGRADE_SLOT_COUNT) {
            setSlotPosition(RouterScreenHandler.UPGRADE_SCREEN_SLOT_START + index, UPGRADE_SLOT_LEFT + (index % 3) * 18, UPGRADE_SLOT_TOP + (index / 3) * 18)
        }
        for (slotIndex in RouterScreenHandler.STORAGE_SCREEN_SLOT_START until handler.slots.size) {
            setSlotPosition(slotIndex, HIDDEN_SLOT_X, HIDDEN_SLOT_Y)
        }
    }

    private fun setSlotPosition(slotIndex: Int, slotX: Int, slotY: Int) {
        val slot = handler.slots.getOrNull(slotIndex) ?: return
        slot.x = slotX
        slot.y = slotY
    }

    private fun hoveredModuleSlot(localMouseX: Int, localMouseY: Int): Int? {
        for (row in 0 until RouterScreenHandler.MODULE_ROWS) {
            for (col in 0 until RouterScreenHandler.MODULE_COLUMNS) {
                val index = row * RouterScreenHandler.MODULE_COLUMNS + col
                if (contains(localMouseX, localMouseY, MODULE_SLOT_LEFT + col * 18, MODULE_SLOT_TOP + row * 18, 18, 18)) return index
            }
        }
        return null
    }

    private fun sourceTooltip(pokemon: CrewSourcePokemonSnapshot): List<Text> = buildList {
        add(Text.literal(pokemon.displayName))
        add(Text.literal("Lv.${pokemon.level} ${friendlySpecies(pokemon.species)}"))
        add(Text.literal(pokemon.sourceLabel()))
        add(Text.literal(pokemon.statusLabel()))
        if (pokemon.cargoSummary.isNotBlank()) add(Text.literal(pokemon.cargoSummary))
    }

    private fun crewTooltip(member: CommandPostCrewMemberSnapshot): List<Text> = buildList {
        add(Text.literal(member.displayName))
        add(Text.literal("Lv.${member.level} ${friendlySpecies(member.species)}"))
        add(Text.literal(member.sourceLabel()))
        add(Text.literal(member.statusLabel()))
        add(Text.literal(member.statusDetailOrFallback()))
        member.tagTypeId?.let { tagId ->
            val role = TagType.fromId(tagId)?.let(TagTypePresentation::roleLabel) ?: tagId
            add(Text.literal(role))
        }
        add(Text.literal(member.assignmentLabel()))
    }

    private fun drawSmallText(context: DrawContext, value: String, localX: Int, localY: Int, color: Int, shadow: Boolean) {
        drawSmallTextAbsolute(context, value, x + localX, y + localY, color, shadow)
    }

    private fun drawSmallTextAbsolute(context: DrawContext, value: String, absoluteX: Int, absoluteY: Int, color: Int, shadow: Boolean) {
        CobblemonUiChrome.drawSmallText(context, textRenderer, value, absoluteX, absoluteY, color, shadow)
    }

    private fun friendlySpecies(species: String): String = species.substringAfter(':').replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

    private fun humanValue(value: String): String = value
        .lowercase(Locale.ROOT)
        .split('_', '-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } }

    private fun compactValue(value: String): String {
        val human = humanValue(value)
        return when {
            human.equals("terminate after success", ignoreCase = true) -> "Once"
            human.length <= 4 -> human
            human.contains(' ') -> human.split(' ').joinToString("") { it.firstOrNull()?.uppercaseChar()?.toString().orEmpty() }
            else -> human.take(4)
        }
    }

    private fun fit(value: String, maxWidth: Int): String {
        if (textRenderer.getWidth(value) * CommandPostPcShell.TEXTURE_SCALE <= maxWidth) return value
        var result = value
        while (result.length > 3 && textRenderer.getWidth("$result...") * CommandPostPcShell.TEXTURE_SCALE > maxWidth) {
            result = result.dropLast(1)
        }
        return "$result..."
    }

    private fun contains(mouseX: Int, mouseY: Int, left: Int, top: Int, width: Int, height: Int): Boolean {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height
    }

    companion object {
        private const val SOURCE_BUTTON_LEFT = 242
        private const val SOURCE_BUTTON_TOP = 186
        private const val SOURCE_BUTTON_SIZE = 8

        private const val MODULE_SLOT_LEFT = 14
        private const val MODULE_SLOT_TOP = 139
        private const val UPGRADE_SLOT_LEFT = 8
        private const val UPGRADE_SLOT_TOP = 99
        private const val HIDDEN_SLOT_X = -10_000
        private const val HIDDEN_SLOT_Y = -10_000

        private val SOURCE_BUTTON_TEXTURE = CommandPostPcShell.cobblemon("textures/gui/pc/pc_icon_filter.png")
    }
}
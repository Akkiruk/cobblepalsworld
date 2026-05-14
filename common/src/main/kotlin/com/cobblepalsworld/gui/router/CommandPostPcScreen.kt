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
import com.cobblepalsworld.assignment.WorkerAssignmentMode
import com.cobblepalsworld.behavior.state.WorkerStatusKind
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
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW
import org.joml.Quaternionf
import java.util.UUID
import java.util.Locale

class CommandPostPcScreen(
    handler: RouterScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<RouterScreenHandler>(handler, inventory, title) {
    private data class HoverTooltip(val id: String, val lines: List<Text>)
    private data class PanelHit(val left: Int, val top: Int, val width: Int, val height: Int, val action: () -> Boolean)
    private data class ModuleView(val rowIndex: Int, val moduleIndex: Int, val stack: ItemStack, val tagType: TagType, val spec: TagSpec)
    private data class RenderableCacheEntry(val signature: String, val renderable: RenderablePokemon)

    private var sourceType = CrewSourceType.PC
    private var sourceBoxIndex = 0
    private var pastureScrollIndex = 0
    private var commandMode = CommandPostMode.SOURCE
    private var rosterSort = CommandPostRosterSort.STATUS
    private var drawerMode = CommandPostDrawerMode.CLOSED
    private var sourceQuery = ""
    private var sourceSearchActive = false
    private var roleFamilyFilter = CommandPostRoleFamilyFilter.ALL
    private var stateFilter = CommandPostStateFilter.ALL
    private var assignmentFilter = CommandPostAssignmentFilter.ALL
    private var availabilityFilter = CommandPostAvailabilityFilter.ALL
    private var assignedFilter = CommandPostAssignedFilter.ALL
    private var selectedPokemonId: UUID? = null
    private var selectedModuleIndex: Int? = null
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
    private var drawerHits: List<CommandPostFilterDrawer.Hit> = emptyList()
    private var panelHits: List<PanelHit> = emptyList()
    private var searchField: TextFieldWidget? = null
    private var pastureListWidget: CommandPostPastureListWidget? = null
    private val modeButtons = mutableMapOf<CommandPostMode, CommandPostTextureButtonWidget>()
    private var filterButton: CommandPostTextureButtonWidget? = null
    private var optionsButton: CommandPostTextureButtonWidget? = null
    private var exitButton: CommandPostTextureButtonWidget? = null
    private var sourceToggleButton: CommandPostTextureButtonWidget? = null
    private var previousBoxButton: CommandPostTextureButtonWidget? = null
    private var nextBoxButton: CommandPostTextureButtonWidget? = null

    private val renderStates = mutableMapOf<String, FloatingState>()
    private val renderableCache = mutableMapOf<UUID, RenderableCacheEntry>()
    private val heldItemCache = mutableMapOf<String, ItemStack?>()

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
        modeButtons.clear()
        CommandPostMode.entries.forEachIndexed { index, mode ->
            val left = x + CommandPostModeDrawer.MODE_LEFT + index * (CommandPostModeDrawer.MODE_SIZE + CommandPostModeDrawer.MODE_GAP)
            val top = y + CommandPostModeDrawer.MODE_TOP
            modeButtons[mode] = addDrawableChild(
                CommandPostTextureButtonWidget(
                    x = left,
                    y = top,
                    width = CommandPostModeDrawer.MODE_SIZE,
                    height = CommandPostModeDrawer.MODE_SIZE,
                    texture = CommandPostModeDrawer.BUTTON_TEXTURE,
                    highlighted = { commandMode == mode }
                ) {
                    commandMode = mode
                    selectedPokemonId = null
                    selectedModuleIndex = null
                    searchField?.setFocused(false)
                    sourceSearchActive = false
                }
            )
        }
        filterButton = addDrawableChild(
            CommandPostTextureButtonWidget(
                x = x + CommandPostModeDrawer.FILTER_LEFT,
                y = y + CommandPostModeDrawer.TOOL_TOP,
                width = CommandPostModeDrawer.TOOL_SIZE,
                height = CommandPostModeDrawer.TOOL_SIZE,
                texture = CommandPostModeDrawer.FILTER_TEXTURE,
                scaled = true,
                highlighted = { drawerMode == CommandPostDrawerMode.FILTERS }
            ) {
                drawerMode = if (drawerMode == CommandPostDrawerMode.FILTERS) CommandPostDrawerMode.CLOSED else CommandPostDrawerMode.FILTERS
                if (drawerMode != CommandPostDrawerMode.FILTERS) {
                    searchField?.setFocused(false)
                }
            }
        )
        optionsButton = addDrawableChild(
            CommandPostTextureButtonWidget(
                x = x + CommandPostModeDrawer.OPTIONS_LEFT,
                y = y + CommandPostModeDrawer.TOOL_TOP,
                width = CommandPostModeDrawer.TOOL_SIZE,
                height = CommandPostModeDrawer.TOOL_SIZE,
                texture = CommandPostModeDrawer.OPTIONS_TEXTURE,
                scaled = true,
                highlighted = { drawerMode == CommandPostDrawerMode.OPTIONS }
            ) {
                drawerMode = if (drawerMode == CommandPostDrawerMode.OPTIONS) CommandPostDrawerMode.CLOSED else CommandPostDrawerMode.OPTIONS
                if (drawerMode != CommandPostDrawerMode.FILTERS) {
                    searchField?.setFocused(false)
                }
            }
        )
        exitButton = addDrawableChild(
            CommandPostTextureButtonWidget(
                x = x + CommandPostPcShell.EXIT_LEFT,
                y = y + CommandPostPcShell.EXIT_TOP,
                width = CommandPostPcShell.EXIT_WIDTH,
                height = CommandPostPcShell.EXIT_HEIGHT,
                texture = CommandPostPcShell.BACK_BUTTON
            ) { close() }
        )
        sourceToggleButton = addDrawableChild(
            CommandPostTextureButtonWidget(
                x = x + SOURCE_BUTTON_LEFT,
                y = y + SOURCE_BUTTON_TOP,
                width = SOURCE_BUTTON_SIZE,
                height = SOURCE_BUTTON_SIZE,
                texture = SOURCE_BUTTON_TEXTURE,
                scaled = true
            ) {
                sourceType = if (sourceType == CrewSourceType.PC) CrewSourceType.PARTY else CrewSourceType.PC
                selectedPokemonId = null
                requestSourceRefresh()
            }
        )
        previousBoxButton = addDrawableChild(
            CommandPostTextureButtonWidget(
                x = x + CommandPostStorageWidget.PREV_LEFT,
                y = y + CommandPostStorageWidget.NAV_TOP,
                width = CommandPostStorageWidget.NAV_SIZE,
                height = CommandPostStorageWidget.NAV_SIZE,
                texture = CommandPostStorageWidget.NAV_PREVIOUS_TEXTURE,
                scaled = true
            ) { changeBox(-1) }
        )
        nextBoxButton = addDrawableChild(
            CommandPostTextureButtonWidget(
                x = x + CommandPostStorageWidget.NEXT_LEFT,
                y = y + CommandPostStorageWidget.NAV_TOP,
                width = CommandPostStorageWidget.NAV_SIZE,
                height = CommandPostStorageWidget.NAV_SIZE,
                texture = CommandPostStorageWidget.NAV_NEXT_TEXTURE,
                scaled = true
            ) { changeBox(1) }
        )
        pastureListWidget = addDrawableChild(
            CommandPostPastureListWidget(
                x = x + CommandPostPastureWidget.LIST_LEFT,
                y = y + CommandPostPastureWidget.LIST_TOP - 4,
                originX = { x },
                originY = { y },
                membersProvider = { filteredMembers(CommandPostCrewSnapshotCache.get(handler.routerPos)?.members.orEmpty()) },
                selectedPokemonId = { selectedPokemonId },
                scrollIndexProvider = { pastureScrollIndex },
                setScrollIndex = { pastureScrollIndex = it },
                onHover = { member ->
                    hoveredCrew = member
                    if (member != null) {
                        hoveredTooltip = HoverTooltip("crew-${member.pokemonId}", crewTooltip(member))
                    }
                },
                onSelect = { member ->
                    selectedPokemonId = member.pokemonId
                    play(CobblemonSounds.PC_CLICK)
                },
                onRecall = { member ->
                    selectedPokemonId = member.pokemonId
                    CobblePalsNetworking.sendRemoveCrewPokemon(handler.routerPos, member.pokemonId)
                    play(CobblemonSounds.PC_DROP)
                    requestCrewRefresh()
                    requestSourceRefresh()
                },
                onMode = { member ->
                    selectedPokemonId = member.pokemonId
                    CobblePalsNetworking.sendCycleCrewMode(handler.routerPos, member.pokemonId)
                    play(CobblemonSounds.PC_CLICK)
                    requestCrewRefresh()
                },
                heldStack = ::heldStack,
                fit = ::fit,
                renderPokemon = { context, view, centerX, topY, renderDelta, modelScale, matrixScale, animate ->
                    renderPokemon(
                        context = context,
                        view = view,
                        centerX = centerX,
                        topY = topY,
                        delta = renderDelta,
                        modelScale = modelScale,
                        matrixScale = matrixScale,
                        stateKey = "pasture-row-${view.pokemonId}",
                        animate = animate,
                        clipHalfWidth = 16,
                        clipHeight = 32
                    )
                }
            )
        )
        searchField = addDrawableChild(
            TextFieldWidget(
                textRenderer,
                x + CommandPostFilterDrawer.SEARCH_LEFT + 3,
                y + CommandPostFilterDrawer.SEARCH_TOP + 1,
                CommandPostFilterDrawer.SEARCH_WIDTH - 6,
                CommandPostFilterDrawer.SEARCH_HEIGHT + 2,
                Text.literal("Search")
            ).apply {
                setMaxLength(40)
                setDrawsBackground(false)
                setText(sourceQuery)
                setChangedListener { value ->
                    sourceQuery = value
                    requestSourceRefresh()
                }
                visible = drawerMode == CommandPostDrawerMode.FILTERS
            }
        )
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
        drawerHits = emptyList()
        panelHits = emptyList()
        searchField?.visible = drawerMode == CommandPostDrawerMode.FILTERS
        sourceSearchActive = searchField?.isFocused == true
        sourceToggleButton?.visible = commandMode == CommandPostMode.SOURCE
        previousBoxButton?.visible = commandMode == CommandPostMode.SOURCE && sourceType == CrewSourceType.PC
        nextBoxButton?.visible = commandMode == CommandPostMode.SOURCE && sourceType == CrewSourceType.PC
        pastureListWidget?.visible = true
        renderBackground(context, mouseX, mouseY, delta)
        super.render(context, mouseX, mouseY, delta)
        val members = filteredMembers(CommandPostCrewSnapshotCache.get(handler.routerPos)?.members.orEmpty())
        drawPastureChrome(context, members, mouseX - x, mouseY - y)
        drawMouseoverTooltip(context, mouseX, mouseY)
        hoveredTooltip?.let { context.drawTooltip(textRenderer, it.lines, mouseX, mouseY) }
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        val localMouseX = mouseX - x
        val localMouseY = mouseY - y
        applySlotLayout()
        val sources = CrewSourceSnapshotCache.get(handler.routerPos)
        val crew = CommandPostCrewSnapshotCache.get(handler.routerPos)
        val currentBox = currentSourceBox(sources)
        val allMembers = crew?.members.orEmpty()
        val members = filteredMembers(allMembers)
        primeHoveredPokemon(currentBox, members, localMouseX, localMouseY)
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
        drawFilterDrawer(context, localMouseX, localMouseY)
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

        drawerHits.firstOrNull { hit -> CommandPostFilterDrawer.contains(localMouseX, localMouseY, hit) }?.let { hit ->
            handleDrawerHit(hit.action)
            play(CobblemonSounds.PC_CLICK)
            return true
        }

        panelHits.firstOrNull { hit -> contains(localMouseX, localMouseY, hit.left, hit.top, hit.width, hit.height) }?.let { hit ->
            if (hit.action()) {
                play(CobblemonSounds.PC_CLICK)
                return true
            }
        }

        if (CommandPostPastureWidget.recallContains(localMouseX, localMouseY)) {
            val members = CommandPostCrewSnapshotCache.get(handler.routerPos)?.members.orEmpty()
            members.forEach { CobblePalsNetworking.sendRemoveCrewPokemon(handler.routerPos, it.pokemonId) }
            play(CobblemonSounds.PC_RELEASE)
            requestCrewRefresh()
            requestSourceRefresh()
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
        if (commandMode == CommandPostMode.SOURCE && sourceType == CrewSourceType.PC && CommandPostStorageWidget.contains(localMouseX, localMouseY)) {
            changeBox(if (verticalAmount > 0) -1 else 1)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        return super.charTyped(chr, modifiers)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (searchField?.isFocused == true && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            searchField?.setFocused(false)
            sourceSearchActive = false
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun drawBase(context: DrawContext) {
        CommandPostPcShell.drawBase(context, x, y)
    }

    private fun drawPortrait(context: DrawContext, preview: CommandPostPokemonRenderView?, delta: Float) {
        CommandPostPcShell.drawPortraitPanel(context, x, y)
        if (preview != null) {
            renderPokemon(context, preview, x + 39, y + 26, delta, 7.0F, 3.2F, stateKey = "portrait-${preview.pokemonId}", animate = true, clipHalfWidth = 36, clipHeight = 68)
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
            CommandPostStorageWidget.drawFrame(context, x, y)
        }

        val hits = mutableListOf<CommandPostStorageSlot.Hit>()
        val slots = box?.slots.orEmpty()
        slots.forEachIndexed { index, rawSlot ->
            val slot = rawSlot.copy(pokemon = rawSlot.pokemon?.takeIf(::sourceMatchesFilters))
            val left: Int
            val top: Int
            val size: Int
            if (sourceType == CrewSourceType.PARTY) {
                val bounds = CommandPostPartySlot.bounds(index) ?: return@forEachIndexed
                left = bounds.left
                top = bounds.top
                size = bounds.size
            } else {
                val gridIndex = slot.slotIndex
                if (gridIndex !in 0 until CommandPostStorageWidget.BOX_SLOT_COUNT) return@forEachIndexed
                left = CommandPostStorageWidget.slotLeft(gridIndex)
                top = CommandPostStorageWidget.slotTop(gridIndex)
                size = CommandPostStorageWidget.SLOT_SIZE
            }
            hits += CommandPostStorageSlot.Hit(slot, left, top, size)
            CommandPostStorageSlot.draw(context, textRenderer, x, y, slot, left, top, size, localMouseX, localMouseY, delta, selectedPokemonId, pointerOffsetY, ::heldStack) { view, centerX, topY, renderDelta, modelScale, matrixScale, animate ->
                renderPokemon(
                    context = context,
                    view = view,
                    centerX = centerX,
                    topY = topY,
                    delta = renderDelta,
                    modelScale = modelScale,
                    matrixScale = matrixScale,
                    stateKey = "source-slot-${sourceType.name.lowercase(Locale.ROOT)}-${slot.slotIndex}",
                    animate = animate,
                    clipHalfWidth = 16,
                    clipHeight = 29
                )
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

        CommandPostPastureWidget.drawPanel(context, textRenderer, x, y)
        crewHits = emptyList()
    }

    private fun drawPastureChrome(context: DrawContext, members: List<CommandPostCrewMemberSnapshot>, localMouseX: Int, localMouseY: Int) {
        val maxWorkers = CommandPostCrewSnapshotCache.get(handler.routerPos)?.maxWorkers ?: 0
        CommandPostPastureWidget.drawScrollOverlay(context, x, y)
        CommandPostPastureWidget.drawControls(context, textRenderer, x, y, members.size, maxWorkers, localMouseX, localMouseY)
        if (CommandPostPastureWidget.recallContains(localMouseX, localMouseY)) {
            hoveredTooltip = HoverTooltip("recall", listOf(Text.literal("Recall all Command Post Pokemon")))
        }
    }

    private fun drawRouterSlots(context: DrawContext) {
    }

    private enum class SlotFrameStyle { COBBLEMON, COMPACT }

    private fun drawSlotFrames(context: DrawContext, slotRange: IntRange, style: SlotFrameStyle) {
        slotRange.forEach { slotIndex ->
            val slot = handler.slots.getOrNull(slotIndex) ?: return@forEach
            if (slot.x == HIDDEN_SLOT_X && slot.y == HIDDEN_SLOT_Y) return@forEach
            when (style) {
                SlotFrameStyle.COBBLEMON -> blitk(
                    matrixStack = context.matrices,
                    texture = CommandPostPcShell.SLOT_OVERLAY,
                    x = x + slot.x - 4,
                    y = y + slot.y - 4,
                    width = CommandPostStorageWidget.SLOT_SIZE,
                    height = CommandPostStorageWidget.SLOT_SIZE,
                    alpha = 0.55F
                )
                SlotFrameStyle.COMPACT -> {
                    context.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xAA8E8E8E.toInt())
                    context.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xCC4A4A4A.toInt())
                }
            }
        }
    }

    private fun drawModeControls(context: DrawContext, localMouseX: Int, localMouseY: Int) {
        CommandPostModeDrawer.drawDrawerLabel(context, textRenderer, x, y, roleFamilyFilter, stateFilter, rosterSort)
        modeButtons.entries.firstOrNull { (_, button) -> button.isHovered }?.let { (mode, _) ->
            hoveredTooltip = HoverTooltip("mode-${mode.name}", listOf(Text.literal(mode.tooltip)))
        }
        if (filterButton?.isHovered == true) {
            hoveredTooltip = HoverTooltip("filter", listOf(Text.literal("Filters"), Text.literal("${roleFamilyFilter.label} / ${stateFilter.label}")))
        }
        if (optionsButton?.isHovered == true) {
            hoveredTooltip = HoverTooltip("sort", listOf(Text.literal("Options"), Text.literal("Sort: ${rosterSort.label}")))
        }
        if (sourceToggleButton?.visible == true && sourceToggleButton?.isHovered == true) {
            hoveredTooltip = HoverTooltip("source-toggle", listOf(Text.literal(if (sourceType == CrewSourceType.PC) "Show Party" else "Show PC")))
        }
    }

    private fun drawFilterDrawer(context: DrawContext, localMouseX: Int, localMouseY: Int) {
        drawerHits = when (drawerMode) {
            CommandPostDrawerMode.CLOSED -> emptyList()
            CommandPostDrawerMode.FILTERS -> CommandPostFilterDrawer.drawFilters(
                context = context,
                textRenderer = textRenderer,
                originX = x,
                originY = y,
                localMouseX = localMouseX,
                localMouseY = localMouseY,
                query = sourceQuery,
                searchActive = sourceSearchActive,
                role = roleFamilyFilter,
                state = stateFilter,
                assignment = assignmentFilter,
                availability = availabilityFilter,
                assigned = assignedFilter,
                fit = ::fit
            )
            CommandPostDrawerMode.OPTIONS -> CommandPostFilterDrawer.drawOptions(
                context = context,
                textRenderer = textRenderer,
                originX = x,
                originY = y,
                localMouseX = localMouseX,
                localMouseY = localMouseY,
                sort = rosterSort,
                fit = ::fit
            )
        }
        drawerHits.firstOrNull { hit -> CommandPostFilterDrawer.contains(localMouseX, localMouseY, hit) }?.let { hit ->
            hoveredTooltip = HoverTooltip("drawer-${hit.action.name}", drawerTooltip(hit.action))
        }
    }

    private fun drawJobsPanel(context: DrawContext, localMouseX: Int, localMouseY: Int) {
        drawOperationalFrame(context, "JOBS")
        val views = moduleViews()
        val members = CommandPostCrewSnapshotCache.get(handler.routerPos)?.members.orEmpty()
        drawSmallText(context, "Tag cards", JOBS_MODULE_LEFT, JOBS_MODULE_TOP - 10, 0xFFEAF4F5.toInt(), true)
        drawSmallText(context, "Augments", JOBS_UPGRADE_LEFT, JOBS_UPGRADE_TOP - 10, 0xFFEAF4F5.toInt(), true)
        drawSmallText(context, "Bag", CENTER_INV_LEFT, JOBS_PLAYER_TOP - 9, 0xFFEAF4F5.toInt(), true)
        drawSlotFrames(context, 0 until RouterBlockEntity.MODULE_SLOT_COUNT, SlotFrameStyle.COBBLEMON)
        drawSlotFrames(context, RouterScreenHandler.UPGRADE_SCREEN_SLOT_START until RouterScreenHandler.STORAGE_SCREEN_SLOT_START, SlotFrameStyle.COMPACT)
        drawSlotFrames(context, RouterScreenHandler.COMMAND_SLOT_COUNT until handler.slots.size, SlotFrameStyle.COMPACT)
        if (views.isEmpty()) {
            drawSmallText(context, "Drop tags here", JOBS_MODULE_LEFT + 2, JOBS_MODULE_TOP + 60, 0xFFB8C3C7.toInt(), false)
        }
        views.forEach { view ->
            val slot = handler.slots.getOrNull(view.moduleIndex) ?: return@forEach
            val active = handler.moduleActive(view.moduleIndex)
            val assigned = handler.moduleAssigned(view.moduleIndex)
            val roleMembers = members.filter { it.tagTypeId == view.tagType.id }
            val blocked = roleMembers.count { it.isBlocked() || it.isFainted || it.isMissing }
            val rowColor = when {
                blocked > 0 -> 0xFFFF7777.toInt()
                active -> 0xFF6FEA8A.toInt()
                assigned -> 0xFFFFD166.toInt()
                else -> 0xFFEAF4F5.toInt()
            }
            val badgeLeft = slot.x + 12
            val badgeTop = slot.y + 12
            context.fill(x + badgeLeft, y + badgeTop, x + badgeLeft + 5, y + badgeTop + 5, rowColor)
            if (contains(localMouseX, localMouseY, slot.x - 4, slot.y - 4, 25, 25)) {
                hoveredTooltip = HoverTooltip("job-${view.moduleIndex}", listOf(
                    Text.literal(TagTypePresentation.roleLabel(view.tagType)),
                    Text.literal(TagTypePresentation.familyOf(view.tagType).label),
                    Text.literal(causeChip(active, assigned, blocked))
                ))
            }
        }
        panelHits = emptyList()
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
        views.take(4).forEachIndexed { row, view ->
            val top = CommandPostStorageWidget.Y + 18 + row * 24
            val selected = selectedModuleIndex == view.moduleIndex
            context.fill(x + CommandPostStorageWidget.X + 8, y + top - 2, x + CommandPostStorageWidget.X + 164, y + top + 20, if (selected) 0x77415661 else if (contains(localMouseX, localMouseY, CommandPostStorageWidget.X + 8, top - 2, 156, 20)) 0x5522343A else 0x3318242A)
            drawSmallText(context, TagTypePresentation.roleLabel(view.tagType), CommandPostStorageWidget.X + 13, top, 0xFFEAF4F5.toInt(), true)
            drawSmallText(context, "${humanValue(view.spec.filter.matchMode.name)} ${if (view.spec.filter.whitelist) "Allow" else "Deny"}", CommandPostStorageWidget.X + 13, top + 9, 0xFFB8C3C7.toInt(), false)
            val signalLeft = CommandPostStorageWidget.X + 91
            val targetLeft = CommandPostStorageWidget.X + 115
            val runLeft = CommandPostStorageWidget.X + 139
            drawMiniChip(context, signalLeft, top + 5, compactValue(view.spec.settings.redstoneMode.id), localMouseX, localMouseY)
            drawMiniChip(context, targetLeft, top + 5, compactValue(view.spec.settings.targetStrategy.id), localMouseX, localMouseY)
            drawMiniChip(context, runLeft, top + 5, if (view.spec.settings.terminateAfterSuccess) "1" else "Loop", localMouseX, localMouseY)
            hits += PanelHit(CommandPostStorageWidget.X + 8, top - 2, 80, 20) {
                selectedModuleIndex = view.moduleIndex
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
        drawPolicyContextSheet(context, localMouseX, localMouseY, views.firstOrNull { it.moduleIndex == selectedModuleIndex } ?: views.firstOrNull(), hits)
        panelHits = hits
        slotHits = emptyList()
    }

    private fun drawPolicyContextSheet(context: DrawContext, localMouseX: Int, localMouseY: Int, view: ModuleView?, hits: MutableList<PanelHit>) {
        val top = CommandPostStorageWidget.Y + 118
        context.fill(x + CommandPostStorageWidget.X + 8, y + top, x + CommandPostStorageWidget.X + 164, y + top + 31, 0xAA18242A.toInt())
        if (view == null) {
            drawSmallText(context, "No policy", CommandPostStorageWidget.X + 13, top + 11, 0xFF8FA0A8.toInt(), false)
            return
        }
        drawSmallText(context, fit(TagTypePresentation.roleLabel(view.tagType), 70), CommandPostStorageWidget.X + 13, top + 4, 0xFFEAF4F5.toInt(), true)
        drawSmallText(context, TagTypePresentation.bindingLabel(view.tagType), CommandPostStorageWidget.X + 13, top + 15, 0xFFB8C3C7.toInt(), false)
        val editLeft = CommandPostStorageWidget.X + 110
        drawMiniChip(context, editLeft, top + 10, "Edit", localMouseX, localMouseY)
        hits += PanelHit(editLeft, top + 10, 20, 10) {
            client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.ACTION_OPEN_POLICY_ROW_BASE + view.rowIndex)
            true
        }
    }

    private fun causeChip(active: Boolean, assigned: Boolean, blocked: Int): String = when {
        blocked > 0 -> "Blocked"
        active -> "Working"
        assigned -> "Ready"
        else -> "Needs pal"
    }

    private fun drawLogisticsPanel(context: DrawContext) {
        drawOperationalFrame(context, "LOGISTICS")
        val storageSlots = handler.slots.drop(RouterScreenHandler.STORAGE_SCREEN_SLOT_START).take(RouterBlockEntity.STORAGE_SLOT_COUNT)
        val usedSlots = storageSlots.count { it.hasStack() }
        val itemCount = storageSlots.sumOf { it.stack.count }
        val views = moduleViews()
        val moduleCount = views.size
        val logisticsViews = views.filter { TagTypePresentation.familyOf(it.tagType) == com.cobblepalsworld.tag.TagRoleFamily.Logistics }
        val logisticsCount = logisticsViews.size
        val starved = logisticsViews.count { !handler.moduleAssigned(it.moduleIndex) }
        val backedUp = if (usedSlots >= RouterBlockEntity.STORAGE_SLOT_COUNT * 2 / 3) logisticsViews.count { it.tagType == TagType.PULLER || it.tagType == TagType.VACUUM } else 0
        val pressure = when {
            usedSlots >= RouterBlockEntity.STORAGE_SLOT_COUNT -> "Full"
            usedSlots >= RouterBlockEntity.STORAGE_SLOT_COUNT * 2 / 3 -> "High"
            usedSlots > 0 -> "Flowing"
            else -> "Empty"
        }
        drawSmallText(context, "Buffer", CENTER_INV_LEFT, LOGISTICS_BUFFER_TOP - 9, 0xFFEAF4F5.toInt(), true)
        drawSmallText(context, "$usedSlots/${RouterBlockEntity.STORAGE_SLOT_COUNT}  $itemCount items  $pressure", CENTER_INV_LEFT + 46, LOGISTICS_BUFFER_TOP - 9, if (pressure == "Full") 0xFFFF7777.toInt() else 0xFFBFE7C4.toInt(), false)
        drawSlotFrames(context, RouterScreenHandler.STORAGE_SCREEN_SLOT_START until RouterScreenHandler.COMMAND_SLOT_COUNT, SlotFrameStyle.COMPACT)
        drawSmallText(context, "Bag", CENTER_INV_LEFT, LOGISTICS_PLAYER_TOP - 9, 0xFFEAF4F5.toInt(), true)
        drawSmallText(context, "Routes $logisticsCount  Starved $starved  Backlog $backedUp", CENTER_INV_LEFT + 26, LOGISTICS_PLAYER_TOP - 9, if (starved > 0 || backedUp > 0) 0xFFFFD166.toInt() else 0xFFBFE7C4.toInt(), false)
        drawSlotFrames(context, RouterScreenHandler.COMMAND_SLOT_COUNT until handler.slots.size, SlotFrameStyle.COMPACT)
        drawSmallText(context, if (usedSlots == 0) "Senders wait for buffer" else if (pressure == "Full") "Pullers may stall" else "Items can move", CENTER_INV_LEFT, LOGISTICS_HOTBAR_TOP + 19, 0xFFB8C3C7.toInt(), false)
        panelHits = emptyList()
        slotHits = emptyList()
    }

    private fun drawOperationalFrame(context: DrawContext, title: String) {
        val background = when (commandMode) {
            CommandPostMode.JOBS -> CommandPostStorageWidget.JOBS_BACKGROUND
            CommandPostMode.POLICY -> CommandPostStorageWidget.POLICY_BACKGROUND
            CommandPostMode.LOGISTICS -> CommandPostStorageWidget.LOGISTICS_BACKGROUND
            CommandPostMode.SOURCE -> null
        }
        if (background == null) {
            CommandPostStorageWidget.drawFrame(context, x, y)
        } else {
            CommandPostStorageWidget.drawFrame(context, x, y, background)
        }
        CommandPostStorageWidget.drawTitle(context, textRenderer, x, y, title)
    }

    private fun drawMiniChip(context: DrawContext, left: Int, top: Int, value: String, localMouseX: Int, localMouseY: Int) {
        val hovered = contains(localMouseX, localMouseY, left, top, 20, 10)
        context.fill(x + left, y + top, x + left + 20, y + top + 10, if (hovered) 0xCC8E8E8E.toInt() else 0xCC575757.toInt())
        drawSmallText(context, fit(value, 18), left + 2, top + 2, 0xFFEAF4F5.toInt(), false)
    }

    private fun handleDrawerHit(action: CommandPostFilterDrawer.Action) {
        when (action) {
            CommandPostFilterDrawer.Action.SEARCH -> {
                drawerMode = CommandPostDrawerMode.FILTERS
                searchField?.visible = true
                searchField?.setFocused(true)
                sourceSearchActive = true
            }
            CommandPostFilterDrawer.Action.ROLE -> {
                sourceSearchActive = false
                roleFamilyFilter = roleFamilyFilter.next()
                pastureScrollIndex = 0
            }
            CommandPostFilterDrawer.Action.STATE -> {
                sourceSearchActive = false
                stateFilter = stateFilter.next()
                pastureScrollIndex = 0
            }
            CommandPostFilterDrawer.Action.ASSIGNMENT -> {
                sourceSearchActive = false
                assignmentFilter = assignmentFilter.next()
                pastureScrollIndex = 0
            }
            CommandPostFilterDrawer.Action.AVAILABILITY -> {
                sourceSearchActive = false
                availabilityFilter = availabilityFilter.next()
                pastureScrollIndex = 0
            }
            CommandPostFilterDrawer.Action.ASSIGNED -> {
                sourceSearchActive = false
                assignedFilter = assignedFilter.next()
                pastureScrollIndex = 0
            }
            CommandPostFilterDrawer.Action.SORT -> {
                sourceSearchActive = false
                rosterSort = rosterSort.next()
                pastureScrollIndex = 0
            }
        }
    }

    private fun drawerTooltip(action: CommandPostFilterDrawer.Action): List<Text> = when (action) {
        CommandPostFilterDrawer.Action.SEARCH -> listOf(Text.literal("Search: ${sourceQuery.ifBlank { "all" }}"))
        CommandPostFilterDrawer.Action.ROLE -> listOf(Text.literal("Role family: ${roleFamilyFilter.label}"))
        CommandPostFilterDrawer.Action.STATE -> listOf(Text.literal("State: ${stateFilter.label}"))
        CommandPostFilterDrawer.Action.ASSIGNMENT -> listOf(Text.literal("Assignment: ${assignmentFilter.label}"))
        CommandPostFilterDrawer.Action.AVAILABILITY -> listOf(Text.literal("Availability: ${availabilityFilter.label}"))
        CommandPostFilterDrawer.Action.ASSIGNED -> listOf(Text.literal("Source: ${assignedFilter.label}"))
        CommandPostFilterDrawer.Action.SORT -> listOf(Text.literal("Sort: ${rosterSort.label}"))
    }

    private inline fun <reified T : Enum<T>> T.next(): T {
        val values = enumValues<T>()
        return values[(ordinal + 1) % values.size]
    }

    private fun drawExitButton(context: DrawContext, localMouseX: Int, localMouseY: Int) {
    }

    private fun renderPokemon(
        context: DrawContext,
        view: CommandPostPokemonRenderView,
        centerX: Int,
        topY: Int,
        delta: Float,
        modelScale: Float,
        matrixScale: Float,
        stateKey: String,
        animate: Boolean,
        clipHalfWidth: Int,
        clipHeight: Int
    ) {
        val renderable = renderablePokemon(view) ?: return
        val state = renderStates.getOrPut(stateKey) { FloatingState() }
        context.enableScissor(centerX - clipHalfWidth, topY, centerX + clipHalfWidth, topY + clipHeight)
        context.matrices.push()
        context.matrices.translate(centerX.toDouble(), topY.toDouble(), 0.0)
        context.matrices.scale(matrixScale, matrixScale, 1F)
        drawProfilePokemon(
            renderablePokemon = renderable,
            matrixStack = context.matrices,
            rotation = Quaternionf().rotateXYZ(Math.toRadians(13.0).toFloat(), Math.toRadians(35.0).toFloat(), 0F),
            state = state,
            partialTicks = if (animate && !view.isFainted) delta else 0F,
            scale = modelScale
        )
        context.matrices.pop()
        context.disableScissor()
    }

    private fun renderablePokemon(view: CommandPostPokemonRenderView): RenderablePokemon? {
        val signature = buildString {
            append(view.speciesIdentifier)
            append('|')
            append(view.aspects.sorted().joinToString(","))
            append('|')
            append(view.heldItemId)
        }
        renderableCache[view.pokemonId]?.takeIf { it.signature == signature }?.let { return it.renderable }
        val identifier = runCatching { Identifier.of(view.speciesIdentifier) }.getOrNull()
        val species = identifier?.let(PokemonSpecies::getByIdentifier)
            ?: PokemonSpecies.getByName(view.species.substringAfter(':'))
            ?: return null
        return RenderablePokemon(species, view.aspects, heldStack(view.heldItemId) ?: ItemStack.EMPTY).also {
            renderableCache[view.pokemonId] = RenderableCacheEntry(signature, it)
        }
    }

    private fun heldStack(itemId: String): ItemStack? {
        if (itemId.isBlank()) return null
        heldItemCache[itemId]?.let { return it?.copy() }
        val identifier = runCatching { Identifier.of(itemId) }.getOrNull() ?: return null
        val item = Registries.ITEM.get(identifier)
        if (item == Items.AIR) return null
        return ItemStack(item).also { heldItemCache[itemId] = it.copy() }
    }

    private fun currentSourceBox(sources: List<CrewSourceSnapshot>): CrewSourceBoxSnapshot? {
        val source = sources.firstOrNull { it.sourceType == sourceType } ?: return null
        if (sourceType == CrewSourceType.PARTY) return source.boxes.firstOrNull()
        val boxCount = source.boxCount
        if (boxCount <= 0) return null
        sourceBoxIndex = sourceBoxIndex.coerceIn(0, boxCount - 1)
        return source.boxes.firstOrNull { it.boxIndex == sourceBoxIndex } ?: source.boxes.firstOrNull()
    }

    private fun primeHoveredPokemon(box: CrewSourceBoxSnapshot?, members: List<CommandPostCrewMemberSnapshot>, localMouseX: Int, localMouseY: Int) {
        if (commandMode == CommandPostMode.SOURCE) {
            box?.slots.orEmpty().forEachIndexed { index, rawSlot ->
                val pokemon = rawSlot.pokemon?.takeIf(::sourceMatchesFilters) ?: return@forEachIndexed
                val left: Int
                val top: Int
                val size: Int
                if (sourceType == CrewSourceType.PARTY) {
                    val bounds = CommandPostPartySlot.bounds(index) ?: return@forEachIndexed
                    left = bounds.left
                    top = bounds.top
                    size = bounds.size
                } else {
                    val gridIndex = rawSlot.slotIndex
                    if (gridIndex !in 0 until CommandPostStorageWidget.BOX_SLOT_COUNT) return@forEachIndexed
                    left = CommandPostStorageWidget.slotLeft(gridIndex)
                    top = CommandPostStorageWidget.slotTop(gridIndex)
                    size = CommandPostStorageWidget.SLOT_SIZE
                }
                if (contains(localMouseX, localMouseY, left, top, size, size)) {
                    hoveredSource = pokemon
                    return
                }
            }
        }

        if (!CommandPostPastureWidget.listContains(localMouseX, localMouseY)) return
        members.drop(pastureScrollIndex).take(CommandPostPastureWidget.VISIBLE_ROWS).forEachIndexed { row, member ->
            val bounds = CommandPostPastureWidget.rowBounds(row)
            if (contains(localMouseX, localMouseY, bounds.left, bounds.top, CommandPostPastureWidget.SLOT_WIDTH, CommandPostPastureWidget.SLOT_HEIGHT)) {
                hoveredCrew = member
                return
            }
        }
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
        CobblePalsNetworking.sendCrewSourceRefresh(handler.routerPos, sourceType, sourceBoxIndex, sourceQuery)
    }

    private fun filteredMembers(members: List<CommandPostCrewMemberSnapshot>): List<CommandPostCrewMemberSnapshot> {
        val filtered = members.filter { member ->
            memberMatchesQuery(member) &&
                memberMatchesRole(member) &&
                memberMatchesState(member) &&
                memberMatchesAssignment(member) &&
                memberMatchesAvailability(member) &&
                assignedFilter != CommandPostAssignedFilter.UNASSIGNED
        }
        return when (rosterSort) {
            CommandPostRosterSort.STATUS -> filtered.sortedWith(compareBy<CommandPostCrewMemberSnapshot> { it.sortRank() }.thenBy { it.displayName })
            CommandPostRosterSort.ROLE -> filtered.sortedWith(compareBy<CommandPostCrewMemberSnapshot> { it.tagTypeId ?: "" }.thenBy { it.displayName })
            CommandPostRosterSort.FAMILY -> filtered.sortedWith(compareBy<CommandPostCrewMemberSnapshot> { familyLabel(it.tagTypeId) }.thenBy { it.displayName })
            CommandPostRosterSort.ASSIGNMENT -> filtered.sortedWith(compareBy<CommandPostCrewMemberSnapshot> { it.assignmentLabel() }.thenBy { it.displayName })
            CommandPostRosterSort.SOURCE -> filtered.sortedWith(compareBy<CommandPostCrewMemberSnapshot> { it.sourceType }.thenBy { it.boxIndex }.thenBy { it.slotIndex })
        }
    }

    private fun sourceMatchesFilters(pokemon: CrewSourcePokemonSnapshot): Boolean {
        return sourceMatchesRole(pokemon) && sourceMatchesState(pokemon) && sourceMatchesAvailability(pokemon) && sourceMatchesAssigned(pokemon)
    }

    private fun sourceMatchesRole(pokemon: CrewSourcePokemonSnapshot): Boolean {
        val family = pokemon.tagTypeId?.let(TagType::fromId)?.let(TagTypePresentation::familyOf)
        return roleFamilyFilter.family == null || family == roleFamilyFilter.family
    }

    private fun sourceMatchesState(pokemon: CrewSourcePokemonSnapshot): Boolean = when (stateFilter) {
        CommandPostStateFilter.ALL -> true
        CommandPostStateFilter.READY -> pokemon.isAvailable && !pokemon.isFainted
        CommandPostStateFilter.ACTIVE -> pokemon.workStatus.contains("work", ignoreCase = true) || pokemon.workStatus.contains("active", ignoreCase = true)
        CommandPostStateFilter.BLOCKED -> !pokemon.isAvailable && !pokemon.isFainted && !pokemon.isCrewMember
        CommandPostStateFilter.WAITING -> pokemon.workStatus.contains("await", ignoreCase = true) || pokemon.workStatus.contains("cooldown", ignoreCase = true)
        CommandPostStateFilter.STANDBY -> pokemon.workStatus.contains("standby", ignoreCase = true)
        CommandPostStateFilter.FAINTED -> pokemon.isFainted
        CommandPostStateFilter.MISSING -> false
        CommandPostStateFilter.NO_ROLE -> pokemon.tagTypeId == null
        CommandPostStateFilter.CARGO -> pokemon.cargoSummary.isNotBlank()
    }

    private fun sourceMatchesAvailability(pokemon: CrewSourcePokemonSnapshot): Boolean = when (availabilityFilter) {
        CommandPostAvailabilityFilter.ALL -> true
        CommandPostAvailabilityFilter.AVAILABLE -> pokemon.isAvailable
        CommandPostAvailabilityFilter.UNAVAILABLE -> !pokemon.isAvailable
    }

    private fun sourceMatchesAssigned(pokemon: CrewSourcePokemonSnapshot): Boolean = when (assignedFilter) {
        CommandPostAssignedFilter.ALL -> true
        CommandPostAssignedFilter.ASSIGNED -> pokemon.isCrewMember
        CommandPostAssignedFilter.UNASSIGNED -> !pokemon.isCrewMember
    }

    private fun memberMatchesQuery(member: CommandPostCrewMemberSnapshot): Boolean {
        val query = sourceQuery.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) return true
        return member.displayName.lowercase(Locale.ROOT).contains(query) ||
            member.species.lowercase(Locale.ROOT).contains(query) ||
            member.sourceLabel().lowercase(Locale.ROOT).contains(query) ||
            member.statusLabel().lowercase(Locale.ROOT).contains(query) ||
            member.assignmentLabel().lowercase(Locale.ROOT).contains(query) ||
            member.tagTypeId?.lowercase(Locale.ROOT)?.contains(query) == true
    }

    private fun memberMatchesRole(member: CommandPostCrewMemberSnapshot): Boolean {
        val family = member.tagTypeId?.let(TagType::fromId)?.let(TagTypePresentation::familyOf)
        return roleFamilyFilter.family == null || family == roleFamilyFilter.family
    }

    private fun memberMatchesState(member: CommandPostCrewMemberSnapshot): Boolean = when (stateFilter) {
        CommandPostStateFilter.ALL -> true
        CommandPostStateFilter.READY -> member.isReady()
        CommandPostStateFilter.ACTIVE -> member.isActive()
        CommandPostStateFilter.BLOCKED -> member.isBlocked()
        CommandPostStateFilter.WAITING -> member.statusReason()?.kind == WorkerStatusKind.WAITING
        CommandPostStateFilter.STANDBY -> member.statusReason()?.kind == WorkerStatusKind.STANDBY
        CommandPostStateFilter.FAINTED -> member.isFainted
        CommandPostStateFilter.MISSING -> member.isMissing
        CommandPostStateFilter.NO_ROLE -> member.tagTypeId == null
        CommandPostStateFilter.CARGO -> member.carriedItemCount > 0
    }

    private fun memberMatchesAssignment(member: CommandPostCrewMemberSnapshot): Boolean = when (assignmentFilter) {
        CommandPostAssignmentFilter.ALL -> true
        CommandPostAssignmentFilter.GENERAL -> member.assignmentMode() == WorkerAssignmentMode.GENERAL && member.allowFallback
        CommandPostAssignmentFilter.PREFERRED -> member.assignmentMode() == WorkerAssignmentMode.PREFERRED
        CommandPostAssignmentFilter.RESERVED -> member.assignmentMode() == WorkerAssignmentMode.RESERVED
        CommandPostAssignmentFilter.STRICT -> !member.allowFallback
    }

    private fun memberMatchesAvailability(member: CommandPostCrewMemberSnapshot): Boolean = when (availabilityFilter) {
        CommandPostAvailabilityFilter.ALL -> true
        CommandPostAvailabilityFilter.AVAILABLE -> !member.isMissing && !member.isFainted && !member.isBlocked() && member.tagTypeId != null
        CommandPostAvailabilityFilter.UNAVAILABLE -> member.isMissing || member.isFainted || member.isBlocked() || member.tagTypeId == null
    }

    private fun familyLabel(tagId: String?): String {
        return tagId?.let(TagType::fromId)?.let(TagTypePresentation::familyOf)?.label ?: "None"
    }

    private fun detailLines(preview: CommandPostPokemonRenderView?): List<CommandPostInfoPanel.DetailLine> {
        if (preview == null) return emptyList()
        val gender = when {
            "male" in preview.aspects -> " M"
            "female" in preview.aspects -> " F"
            else -> ""
        }
        val lines = mutableListOf(CommandPostInfoPanel.DetailLine("Lv.${preview.level} ${friendlySpecies(preview.species)}$gender"))
        when (preview) {
            is SourcePokemonRenderView -> {
                val statusColor = if (preview.source.isAvailable) 0xFFBFE7C4.toInt() else 0xFFFFD166.toInt()
                lines += CommandPostInfoPanel.DetailLine(preview.source.statusLabel(), statusColor, preview.source.isAvailable)
                lines += CommandPostInfoPanel.DetailLine(preview.source.sourceLabel(), 0xFFB8C3C7.toInt())
                lines += preview.source.tagTypeId?.let { tagRoleLine(it) } ?: CommandPostInfoPanel.DetailLine("Role: none", 0xFF8FA0A8.toInt())
                lines += CommandPostInfoPanel.DetailLine(if (preview.source.isCrewMember) "Assigned" else "Unassigned", if (preview.source.isCrewMember) 0xFFFFD166.toInt() else 0xFFBFE7C4.toInt())
                lines += CommandPostInfoPanel.DetailLine(preview.source.cargoSummary.ifBlank { if (preview.source.isFainted) "Fainted" else "No cargo" }, if (preview.source.isFainted) 0xFFFF7777.toInt() else 0xFF8FA0A8.toInt())
            }
            is CrewPokemonRenderView -> {
                val member = preview.member
                val statusColor = if (member.isBlocked() || member.isFainted || member.isMissing) 0xFFFF7777.toInt() else 0xFFBFE7C4.toInt()
                lines += CommandPostInfoPanel.DetailLine(member.statusLabel(), statusColor, member.isReady())
                lines += CommandPostInfoPanel.DetailLine(member.sourceLabel(), 0xFFB8C3C7.toInt())
                lines += member.tagTypeId?.let { tagRoleLine(it) } ?: CommandPostInfoPanel.DetailLine("Role: none", 0xFF8FA0A8.toInt())
                lines += CommandPostInfoPanel.DetailLine(member.assignmentLabel(), 0xFFEAF4F5.toInt())
                lines += CommandPostInfoPanel.DetailLine(member.cargoSummary.ifBlank { member.statusDetailOrFallback() }, if (member.carriedItemCount > 0) 0xFFFFD166.toInt() else 0xFF8FA0A8.toInt())
            }
            else -> {
                lines += CommandPostInfoPanel.DetailLine(if (preview.isFainted) "Fainted" else "Ready")
            }
        }
        return lines
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
        for (slotIndex in handler.slots.indices) {
            setSlotPosition(slotIndex, HIDDEN_SLOT_X, HIDDEN_SLOT_Y)
        }
        when (commandMode) {
            CommandPostMode.SOURCE -> return
            CommandPostMode.JOBS -> {
                positionModuleSlots(JOBS_MODULE_LEFT, JOBS_MODULE_TOP)
                positionUpgradeSlots(JOBS_UPGRADE_LEFT, JOBS_UPGRADE_TOP)
                positionPlayerInventory(CENTER_INV_LEFT, JOBS_PLAYER_TOP, JOBS_HOTBAR_TOP)
            }
            CommandPostMode.POLICY -> return
            CommandPostMode.LOGISTICS -> {
                positionStorageSlots(CENTER_INV_LEFT, LOGISTICS_BUFFER_TOP)
                positionPlayerInventory(CENTER_INV_LEFT, LOGISTICS_PLAYER_TOP, LOGISTICS_HOTBAR_TOP)
            }
        }
    }

    private fun positionModuleSlots(left: Int, top: Int) {
        for (row in 0 until RouterScreenHandler.MODULE_ROWS) {
            for (col in 0 until RouterScreenHandler.MODULE_COLUMNS) {
                val slotIndex = row * RouterScreenHandler.MODULE_COLUMNS + col
                setSlotPosition(slotIndex, left + col * 18, top + row * 18)
            }
        }
    }

    private fun positionUpgradeSlots(left: Int, top: Int) {
        for (index in 0 until RouterBlockEntity.UPGRADE_SLOT_COUNT) {
            setSlotPosition(RouterScreenHandler.UPGRADE_SCREEN_SLOT_START + index, left + index * 18, top)
        }
    }

    private fun positionStorageSlots(left: Int, top: Int) {
        for (row in 0 until RouterScreenHandler.STORAGE_ROWS) {
            for (col in 0 until RouterScreenHandler.STORAGE_COLUMNS) {
                val slotIndex = RouterScreenHandler.STORAGE_SCREEN_SLOT_START + row * RouterScreenHandler.STORAGE_COLUMNS + col
                setSlotPosition(slotIndex, left + col * 18, top + row * 18)
            }
        }
    }

    private fun positionPlayerInventory(left: Int, top: Int, hotbarTop: Int) {
        val start = RouterScreenHandler.COMMAND_SLOT_COUNT
        for (row in 0..2) {
            for (col in 0..8) {
                setSlotPosition(start + row * 9 + col, left + col * 18, top + row * 18)
            }
        }
        for (col in 0..8) {
            setSlotPosition(start + 27 + col, left + col * 18, hotbarTop)
        }
    }

    private fun setSlotPosition(slotIndex: Int, slotX: Int, slotY: Int) {
        val slot = handler.slots.getOrNull(slotIndex) ?: return
        slot.x = slotX
        slot.y = slotY
    }

    private fun hoveredModuleSlot(localMouseX: Int, localMouseY: Int): Int? {
        for (index in 0 until RouterBlockEntity.MODULE_SLOT_COUNT) {
            val slot = handler.slots.getOrNull(index) ?: continue
            if (slot.x == HIDDEN_SLOT_X && slot.y == HIDDEN_SLOT_Y) continue
            if (contains(localMouseX, localMouseY, slot.x, slot.y, 18, 18)) return index
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

    private fun crewHitContains(mouseX: Int, mouseY: Int, hit: CommandPostPastureScrollList.CrewHit): Boolean {
        return CommandPostPastureSlot.contains(mouseX, mouseY, CommandPostPastureSlot.Hit(hit.member, hit.left, hit.top, hit.action))
    }

    companion object {
        private const val SOURCE_BUTTON_LEFT = 242
        private const val SOURCE_BUTTON_TOP = 186
        private const val SOURCE_BUTTON_SIZE = 8

        private const val CENTER_INV_LEFT = 91
        private const val JOBS_MODULE_LEFT = 101
        private const val JOBS_MODULE_TOP = 42
        private const val JOBS_UPGRADE_LEFT = 165
        private const val JOBS_UPGRADE_TOP = 56
        private const val JOBS_PLAYER_TOP = 101
        private const val JOBS_HOTBAR_TOP = 159
        private const val LOGISTICS_BUFFER_TOP = 39
        private const val LOGISTICS_PLAYER_TOP = 104
        private const val LOGISTICS_HOTBAR_TOP = 162
        private const val HIDDEN_SLOT_X = -10_000
        private const val HIDDEN_SLOT_Y = -10_000

        private val SOURCE_BUTTON_TEXTURE = CommandPostPcShell.cobblemon("textures/gui/pc/pc_icon_filter.png")
    }
}
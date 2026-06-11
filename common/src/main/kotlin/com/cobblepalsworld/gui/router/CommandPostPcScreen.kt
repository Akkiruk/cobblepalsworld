/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblepalsworld.gui.router

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblepalsworld.gui.crew.CommandPostCrewSnapshotCache
import com.cobblepalsworld.gui.crew.CrewSourceSnapshotCache
import com.cobblepalsworld.gui.crew.CrewSourceType
import com.cobblepalsworld.gui.router.panel.CommandPostPanel
import com.cobblepalsworld.gui.router.panel.CommandPostPanelContext
import com.cobblepalsworld.gui.router.panel.JobsPanel
import com.cobblepalsworld.gui.router.panel.LogisticsPanel
import com.cobblepalsworld.gui.router.panel.PolicyPanel
import com.cobblepalsworld.gui.router.panel.SourcePanel
import com.cobblepalsworld.networking.CobblePalsNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
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
import org.joml.Quaternionf
import org.lwjgl.glfw.GLFW
import java.util.UUID

class CommandPostPcScreen(
    handler: RouterScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<RouterScreenHandler>(handler, inventory, title) {
    private data class RenderableCacheEntry(val signature: String, val renderable: RenderablePokemon)

    private var commandMode = CommandPostMode.JOBS
    private var nextCrewRefreshAtMs = 0L
    private var nextSourceRefreshAtMs = 0L
    internal var pointerOffsetY = 0
        private set
    private var pointerAscending = true

    private val panelContext = CommandPostPanelContext(this, handler) { commandMode }
    private val panels: Map<CommandPostMode, CommandPostPanel> = mapOf(
        CommandPostMode.SOURCE to SourcePanel(panelContext),
        CommandPostMode.JOBS to JobsPanel(panelContext),
        CommandPostMode.POLICY to PolicyPanel(panelContext),
        CommandPostMode.LOGISTICS to LogisticsPanel(panelContext)
    )

    private val modeButtons = mutableMapOf<CommandPostMode, CommandPostTextureButtonWidget>()
    private var filterButton: CommandPostTextureButtonWidget? = null
    private var optionsButton: CommandPostTextureButtonWidget? = null
    private var sourceToggleButton: CommandPostTextureButtonWidget? = null
    private var previousBoxButton: CommandPostTextureButtonWidget? = null
    private var nextBoxButton: CommandPostTextureButtonWidget? = null
    private var pastureListWidget: CommandPostPastureListWidget? = null

    private val renderStates = linkedMapOf<String, FloatingState>()
    private val renderableCache = linkedMapOf<UUID, RenderableCacheEntry>()
    private val heldItemCache = linkedMapOf<String, ItemStack>()

    internal val panelOriginX: Int get() = x
    internal val panelOriginY: Int get() = y
    internal val panelTextRenderer: TextRenderer get() = textRenderer
    internal val panelClient: MinecraftClient? get() = client

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
            modeButtons[mode] = addDrawableChild(
                CommandPostTextureButtonWidget(
                    x = x + CommandPostModeDrawer.MODE_LEFT + index * (CommandPostModeDrawer.MODE_SIZE + CommandPostModeDrawer.MODE_GAP),
                    y = y + CommandPostModeDrawer.MODE_TOP,
                    width = CommandPostModeDrawer.MODE_SIZE,
                    height = CommandPostModeDrawer.MODE_SIZE,
                    texture = CommandPostModeDrawer.BUTTON_TEXTURE,
                    highlighted = { commandMode == mode }
                ) {
                    val previousMode = commandMode
                    commandMode = mode
                    panelContext.selectedPokemonId = null
                    panelContext.searchField?.setFocused(false)
                    panelContext.sourceSearchActive = false
                    (panels[CommandPostMode.POLICY] as? PolicyPanel)?.onShown(previousMode)
                    panels[mode]?.onShown(previousMode)
                }
            )
        }
        filterButton = addDrawableChild(
            CommandPostTextureButtonWidget(x + CommandPostModeDrawer.FILTER_LEFT, y + CommandPostModeDrawer.TOOL_TOP, CommandPostModeDrawer.TOOL_SIZE, CommandPostModeDrawer.TOOL_SIZE, CommandPostModeDrawer.FILTER_TEXTURE, scaled = true, highlighted = { panelContext.drawerMode == CommandPostDrawerMode.FILTERS }) {
                panelContext.drawerMode = if (panelContext.drawerMode == CommandPostDrawerMode.FILTERS) CommandPostDrawerMode.CLOSED else CommandPostDrawerMode.FILTERS
                if (panelContext.drawerMode != CommandPostDrawerMode.FILTERS) panelContext.searchField?.setFocused(false)
            }
        )
        optionsButton = addDrawableChild(
            CommandPostTextureButtonWidget(x + CommandPostModeDrawer.OPTIONS_LEFT, y + CommandPostModeDrawer.TOOL_TOP, CommandPostModeDrawer.TOOL_SIZE, CommandPostModeDrawer.TOOL_SIZE, CommandPostModeDrawer.OPTIONS_TEXTURE, scaled = true, highlighted = { panelContext.drawerMode == CommandPostDrawerMode.OPTIONS }) {
                panelContext.drawerMode = if (panelContext.drawerMode == CommandPostDrawerMode.OPTIONS) CommandPostDrawerMode.CLOSED else CommandPostDrawerMode.OPTIONS
                if (panelContext.drawerMode != CommandPostDrawerMode.FILTERS) panelContext.searchField?.setFocused(false)
            }
        )
        addDrawableChild(CommandPostTextureButtonWidget(x + CommandPostPcShell.EXIT_LEFT, y + CommandPostPcShell.EXIT_TOP, CommandPostPcShell.EXIT_WIDTH, CommandPostPcShell.EXIT_HEIGHT, CommandPostPcShell.BACK_BUTTON) { close() })
        sourceToggleButton = addDrawableChild(
            CommandPostTextureButtonWidget(x + SOURCE_BUTTON_LEFT, y + SOURCE_BUTTON_TOP, SOURCE_BUTTON_SIZE, SOURCE_BUTTON_SIZE, SOURCE_BUTTON_TEXTURE, scaled = true) {
                panelContext.sourceType = if (panelContext.sourceType == CrewSourceType.PC) CrewSourceType.PARTY else CrewSourceType.PC
                panelContext.selectedPokemonId = null
                panelContext.requestSourceRefresh()
            }
        )
        previousBoxButton = addDrawableChild(CommandPostTextureButtonWidget(x + CommandPostStorageWidget.PREV_LEFT, y + CommandPostStorageWidget.NAV_TOP, CommandPostStorageWidget.NAV_SIZE, CommandPostStorageWidget.NAV_SIZE, CommandPostStorageWidget.NAV_PREVIOUS_TEXTURE, scaled = true) { panelContext.changeBox(-1) })
        nextBoxButton = addDrawableChild(CommandPostTextureButtonWidget(x + CommandPostStorageWidget.NEXT_LEFT, y + CommandPostStorageWidget.NAV_TOP, CommandPostStorageWidget.NAV_SIZE, CommandPostStorageWidget.NAV_SIZE, CommandPostStorageWidget.NAV_NEXT_TEXTURE, scaled = true) { panelContext.changeBox(1) })
        pastureListWidget = addDrawableChild(createPastureListWidget())
        panelContext.searchField = addDrawableChild(createSearchField())
        panelContext.applySlotLayout()
        panelContext.requestCrewRefresh()
        if (commandMode == CommandPostMode.SOURCE) panelContext.requestSourceRefresh()
        nextCrewRefreshAtMs = System.currentTimeMillis() + 1_000L
        nextSourceRefreshAtMs = System.currentTimeMillis() + 3_000L
    }

    private fun createSearchField(): TextFieldWidget = TextFieldWidget(
        textRenderer,
        x + CommandPostFilterDrawer.SEARCH_LEFT + 3,
        y + CommandPostFilterDrawer.SEARCH_TOP + 1,
        CommandPostFilterDrawer.SEARCH_WIDTH - 6,
        CommandPostFilterDrawer.SEARCH_HEIGHT + 2,
        Text.translatable("gui.cobblepalsworld.search")
    ).apply {
        setMaxLength(40)
        setDrawsBackground(false)
        setText(panelContext.sourceQuery)
        setChangedListener { value ->
            panelContext.sourceQuery = value
            if (commandMode == CommandPostMode.SOURCE) panelContext.requestSourceRefresh()
        }
        visible = panelContext.drawerMode == CommandPostDrawerMode.FILTERS
    }

    private fun createPastureListWidget(): CommandPostPastureListWidget = CommandPostPastureListWidget(
        x = x + CommandPostPastureWidget.LIST_LEFT,
        y = y + CommandPostPastureWidget.LIST_TOP - 4,
        originX = { x },
        originY = { y },
        membersProvider = { panelContext.filteredMembers(CommandPostCrewSnapshotCache.get(handler.routerPos)?.members.orEmpty()) },
        selectedPokemonId = { panelContext.selectedPokemonId },
        scrollIndexProvider = { panelContext.pastureScrollIndex },
        setScrollIndex = { panelContext.pastureScrollIndex = it },
        onHover = { member ->
            panelContext.hoveredCrew = member
            if (member != null) panelContext.hoveredTooltip = CommandPostPanelContext.HoverTooltip("crew-${member.pokemonId}", panelContext.crewTooltip(member))
        },
        onSelect = { member ->
            panelContext.selectedPokemonId = member.pokemonId
            playForPanel(CobblemonSounds.PC_CLICK)
        },
        onRecall = { member ->
            panelContext.selectedPokemonId = member.pokemonId
            CobblePalsNetworking.sendRemoveCrewPokemon(handler.routerPos, member.pokemonId)
            playForPanel(CobblemonSounds.PC_DROP)
            panelContext.requestCrewRefresh()
            panelContext.requestSourceRefresh()
        },
        onMode = { member ->
            panelContext.selectedPokemonId = member.pokemonId
            CobblePalsNetworking.sendCycleCrewMode(handler.routerPos, member.pokemonId)
            playForPanel(CobblemonSounds.PC_CLICK)
            panelContext.requestCrewRefresh()
        },
        heldStack = panelContext::heldStack,
        fit = panelContext::fit,
        renderPokemon = { context, view, centerX, topY, renderDelta, modelScale, matrixScale, animate ->
            renderPokemonForPanel(context, view, centerX, topY, renderDelta, modelScale, matrixScale, "pasture-row-${view.pokemonId}", animate, 16, 32)
        }
    )

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val now = System.currentTimeMillis()
        if (now >= nextCrewRefreshAtMs) {
            panelContext.requestCrewRefresh()
            nextCrewRefreshAtMs = now + 1_000L
        }
        if (commandMode == CommandPostMode.SOURCE && now >= nextSourceRefreshAtMs) {
            panelContext.requestSourceRefresh()
            nextSourceRefreshAtMs = now + 4_000L
        }
        pointerOffsetY = if (pointerAscending) pointerOffsetY + 1 else pointerOffsetY - 1
        if (pointerOffsetY >= 3) pointerAscending = false
        if (pointerOffsetY <= 0) pointerAscending = true
        panelContext.resetFrameState()
        sourceToggleButton?.visible = commandMode == CommandPostMode.SOURCE
        previousBoxButton?.visible = commandMode == CommandPostMode.SOURCE && panelContext.sourceType == CrewSourceType.PC
        nextBoxButton?.visible = commandMode == CommandPostMode.SOURCE && panelContext.sourceType == CrewSourceType.PC
        pastureListWidget?.visible = true
        renderBackground(context, mouseX, mouseY, delta)
        super.render(context, mouseX, mouseY, delta)
        val members = panelContext.filteredMembers(CommandPostCrewSnapshotCache.get(handler.routerPos)?.members.orEmpty())
        panelContext.drawPastureChrome(context, members, mouseX - x, mouseY - y)
        drawMouseoverTooltip(context, mouseX, mouseY)
        panelContext.hoveredTooltip?.let { context.drawTooltip(textRenderer, it.lines, mouseX, mouseY) }
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        val localMouseX = mouseX - x
        val localMouseY = mouseY - y
        panelContext.applySlotLayout()
        val sources = CrewSourceSnapshotCache.get(handler.routerPos)
        val crew = CommandPostCrewSnapshotCache.get(handler.routerPos)
        val currentBox = panelContext.currentSourceBox(sources)
        val allMembers = crew?.members.orEmpty()
        val members = panelContext.filteredMembers(allMembers)
        panelContext.primeHoveredPokemon(currentBox, members, localMouseX, localMouseY)
        val preview = panelContext.previewPokemon(currentBox, allMembers)

        drawPortrait(context, preview, delta)
        CommandPostPcShell.drawBase(context, x, y)
        drawModeControls(context, localMouseX, localMouseY)
        panels[commandMode]?.render(context, localMouseX, localMouseY, delta)
        panelContext.drawFilterDrawer(context, localMouseX, localMouseY)
        panelContext.drawPasturePanel(context, members, localMouseX, localMouseY, delta)
        CommandPostInfoPanel.draw(context, textRenderer, x, y, preview, panelContext.detailLines(preview), panelContext::heldStack, panelContext::fit)
    }

    override fun drawForeground(context: DrawContext, mouseX: Int, mouseY: Int) {}

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val localMouseX = (mouseX - x).toInt()
        val localMouseY = (mouseY - y).toInt()
        panelContext.drawerHits.firstOrNull { hit -> CommandPostFilterDrawer.contains(localMouseX, localMouseY, hit) }?.let { hit ->
            panelContext.handleDrawerHit(hit.action)
            playForPanel(CobblemonSounds.PC_CLICK)
            return true
        }
        if (commandMode == CommandPostMode.POLICY && panels[commandMode]?.mouseClicked(mouseX, mouseY, button) == true) return true
        if (CommandPostPastureWidget.recallContains(localMouseX, localMouseY)) {
            val members = CommandPostCrewSnapshotCache.get(handler.routerPos)?.members.orEmpty()
            members.forEach { CobblePalsNetworking.sendRemoveCrewPokemon(handler.routerPos, it.pokemonId) }
            playForPanel(CobblemonSounds.PC_RELEASE)
            panelContext.requestCrewRefresh()
            panelContext.requestSourceRefresh()
            return true
        }
        if (commandMode != CommandPostMode.POLICY && panels[commandMode]?.mouseClicked(mouseX, mouseY, button) == true) return true
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (panels[commandMode]?.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount) == true) return true
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (panelContext.searchField?.isFocused == true && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            panelContext.searchField?.setFocused(false)
            panelContext.sourceSearchActive = false
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun close() {
        clearScreenCaches()
        super.close()
    }

    override fun removed() {
        clearRenderCaches()
        super.removed()
    }

    private fun drawModeControls(context: DrawContext, localMouseX: Int, localMouseY: Int) {
        CommandPostModeDrawer.drawDrawerLabel(context, textRenderer, x, y, panelContext.roleFamilyFilter, panelContext.stateFilter, panelContext.rosterSort)
        modeButtons.entries.firstOrNull { (_, button) -> button.isHovered }?.let { (mode, _) ->
            panelContext.hoveredTooltip = CommandPostPanelContext.HoverTooltip("mode-${mode.name}", listOf(Text.literal(mode.tooltip)))
        }
        if (filterButton?.isHovered == true) {
            panelContext.hoveredTooltip = CommandPostPanelContext.HoverTooltip("filter", listOf(Text.translatable("gui.cobblepalsworld.filters"), Text.literal("${panelContext.roleFamilyFilter.label} / ${panelContext.stateFilter.label}")))
        }
        if (optionsButton?.isHovered == true) {
            panelContext.hoveredTooltip = CommandPostPanelContext.HoverTooltip("sort", listOf(Text.translatable("gui.cobblepalsworld.options"), Text.translatable("gui.cobblepalsworld.sort", panelContext.rosterSort.label)))
        }
        if (sourceToggleButton?.visible == true && sourceToggleButton?.isHovered == true) {
            panelContext.hoveredTooltip = CommandPostPanelContext.HoverTooltip("source-toggle", listOf(Text.translatable(if (panelContext.sourceType == CrewSourceType.PC) "gui.cobblepalsworld.show_party" else "gui.cobblepalsworld.show_pc")))
        }
    }

    private fun drawPortrait(context: DrawContext, preview: CommandPostPokemonRenderView?, delta: Float) {
        CommandPostPcShell.drawPortraitPanel(context, x, y)
        if (preview != null) renderPokemonForPanel(context, preview, x + 39, y + 26, delta, 7.0F, 3.2F, "portrait-${preview.pokemonId}", true, 36, 68)
    }

    internal fun renderPokemonForPanel(context: DrawContext, view: CommandPostPokemonRenderView, centerX: Int, topY: Int, delta: Float, modelScale: Float, matrixScale: Float, stateKey: String, animate: Boolean, clipHalfWidth: Int, clipHeight: Int) {
        val renderable = renderablePokemon(view) ?: return
        val state = renderStates.remove(stateKey) ?: FloatingState()
        renderStates[stateKey] = state
        trimCache(renderStates, MAX_RENDER_STATE_CACHE_ENTRIES)
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
            append(view.speciesIdentifier); append('|'); append(view.aspects.sorted().joinToString(",")); append('|'); append(view.heldItemId)
        }
        renderableCache.remove(view.pokemonId)?.takeIf { it.signature == signature }?.let { cached ->
            renderableCache[view.pokemonId] = cached
            return cached.renderable
        }
        val identifier = runCatching { Identifier.of(view.speciesIdentifier) }.getOrNull()
        val species = identifier?.let(PokemonSpecies::getByIdentifier) ?: PokemonSpecies.getByName(view.species.substringAfter(':')) ?: return null
        return RenderablePokemon(species, view.aspects, heldStackForPanel(view.heldItemId) ?: ItemStack.EMPTY).also {
            renderableCache[view.pokemonId] = RenderableCacheEntry(signature, it)
            trimCache(renderableCache, MAX_RENDERABLE_CACHE_ENTRIES)
        }
    }

    internal fun heldStackForPanel(itemId: String): ItemStack? {
        if (itemId.isBlank()) return null
        heldItemCache.remove(itemId)?.let { cached ->
            heldItemCache[itemId] = cached
            return cached.copy()
        }
        val identifier = runCatching { Identifier.of(itemId) }.getOrNull() ?: return null
        val item = Registries.ITEM.get(identifier)
        if (item == Items.AIR) return null
        return ItemStack(item).also {
            heldItemCache[itemId] = it.copy()
            trimCache(heldItemCache, MAX_HELD_ITEM_CACHE_ENTRIES)
        }
    }

    internal fun playForPanel(sound: SoundEvent) {
        MinecraftClient.getInstance().soundManager.play(PositionedSoundInstance.master(sound, 1.0F))
    }

    internal fun clickButtonForPanel(id: Int) {
        client?.interactionManager?.clickButton(handler.syncId, id)
    }

    private fun clearScreenCaches() {
        clearRenderCaches()
        CrewSourceSnapshotCache.clear(handler.routerPos)
        CommandPostCrewSnapshotCache.clear(handler.routerPos)
    }

    private fun clearRenderCaches() {
        renderStates.clear()
        renderableCache.clear()
        heldItemCache.clear()
    }

    private fun <K, V> trimCache(cache: MutableMap<K, V>, maxEntries: Int) {
        while (cache.size > maxEntries) cache.remove(cache.keys.firstOrNull() ?: return)
    }

    companion object {
        private const val SOURCE_BUTTON_LEFT = 242
        private const val SOURCE_BUTTON_TOP = 186
        private const val SOURCE_BUTTON_SIZE = 8
        private const val MAX_RENDER_STATE_CACHE_ENTRIES = 48
        private const val MAX_RENDERABLE_CACHE_ENTRIES = 72
        private const val MAX_HELD_ITEM_CACHE_ENTRIES = 64
        private val SOURCE_BUTTON_TEXTURE = CommandPostPcShell.cobblemon("textures/gui/pc/pc_icon_filter.png")
    }
}

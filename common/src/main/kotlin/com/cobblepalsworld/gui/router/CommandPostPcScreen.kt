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
import com.cobblemon.mod.common.client.render.renderScaledGuiItemIcon
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
import kotlin.math.max
import kotlin.math.min

class CommandPostPcScreen(
    handler: RouterScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<RouterScreenHandler>(handler, inventory, title) {
    private data class SlotHit(val slot: CrewSourceSlotSnapshot, val left: Int, val top: Int)
    private data class CrewHit(val member: CommandPostCrewMemberSnapshot, val left: Int, val top: Int)
    private data class HoverTooltip(val id: String, val lines: List<Text>)
    private data class TextureButton(val id: String, val left: Int, val top: Int, val width: Int, val height: Int, val texture: Identifier)

    private interface RenderPokemonView {
        val pokemonId: UUID
        val displayName: String
        val species: String
        val speciesIdentifier: String
        val aspects: Set<String>
        val heldItemId: String
        val level: Int
        val isFainted: Boolean
    }

    private data class SourcePokemonView(val source: CrewSourcePokemonSnapshot) : RenderPokemonView {
        override val pokemonId: UUID get() = source.pokemonId
        override val displayName: String get() = source.displayName
        override val species: String get() = source.species
        override val speciesIdentifier: String get() = source.speciesIdentifier
        override val aspects: Set<String> get() = source.aspects
        override val heldItemId: String get() = source.heldItemId
        override val level: Int get() = source.level
        override val isFainted: Boolean get() = source.isFainted
    }

    private data class CrewPokemonView(val member: CommandPostCrewMemberSnapshot) : RenderPokemonView {
        override val pokemonId: UUID get() = member.pokemonId
        override val displayName: String get() = member.displayName
        override val species: String get() = member.species
        override val speciesIdentifier: String get() = member.speciesIdentifier
        override val aspects: Set<String> get() = member.aspects
        override val heldItemId: String get() = member.heldItemId
        override val level: Int get() = member.level
        override val isFainted: Boolean get() = member.isFainted
    }

    private var sourceType = CrewSourceType.PC
    private var sourceBoxIndex = 0
    private var pastureScrollIndex = 0
    private var selectedPokemonId: UUID? = null
    private var nextCrewRefreshAtMs = 0L
    private var nextSourceRefreshAtMs = 0L
    private var pointerOffsetY = 0
    private var pointerAscending = true

    private var hoveredSource: CrewSourcePokemonSnapshot? = null
    private var hoveredCrew: CommandPostCrewMemberSnapshot? = null
    private var hoveredTooltip: HoverTooltip? = null
    private var slotHits: List<SlotHit> = emptyList()
    private var crewHits: List<CrewHit> = emptyList()

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
        val preview = previewPokemon(currentBox, crew?.members.orEmpty())

        drawPortrait(context, preview, delta)
        drawBase(context)
        drawSourceModeButton(context, localMouseX, localMouseY)
        drawStorageScreen(context, currentBox, localMouseX, localMouseY, delta)
        drawPasturePanel(context, crew?.members.orEmpty(), localMouseX, localMouseY, delta)
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

        if (sourceType == CrewSourceType.PC && contains(localMouseX, localMouseY, CommandPostStorageWidget.PREV_LEFT, CommandPostStorageWidget.NAV_TOP, CommandPostStorageWidget.NAV_SIZE, CommandPostStorageWidget.NAV_SIZE)) {
            changeBox(-1)
            return true
        }
        if (sourceType == CrewSourceType.PC && contains(localMouseX, localMouseY, CommandPostStorageWidget.NEXT_LEFT, CommandPostStorageWidget.NAV_TOP, CommandPostStorageWidget.NAV_SIZE, CommandPostStorageWidget.NAV_SIZE)) {
            changeBox(1)
            return true
        }
        if (contains(localMouseX, localMouseY, SOURCE_BUTTON_LEFT, SOURCE_BUTTON_TOP, SOURCE_BUTTON_SIZE, SOURCE_BUTTON_SIZE)) {
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

        slotHits.firstOrNull { hit -> contains(localMouseX, localMouseY, hit.left, hit.top, CommandPostStorageWidget.SLOT_SIZE, CommandPostStorageWidget.SLOT_SIZE) }?.slot?.pokemon?.let { pokemon ->
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
        if (sourceType == CrewSourceType.PC && CommandPostStorageWidget.contains(localMouseX, localMouseY)) {
            changeBox(if (verticalAmount > 0) -1 else 1)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    private fun drawBase(context: DrawContext) {
        CommandPostPcShell.drawBase(context, x, y)
    }

    private fun drawPortrait(context: DrawContext, preview: RenderPokemonView?, delta: Float) {
        CommandPostPcShell.drawPortraitPanel(context, x, y)
        if (preview != null) {
            renderPokemon(context, preview, x + 39, y + 26, delta, 7.0F, 3.2F)
        }
    }

    private fun drawInfoBox(context: DrawContext, preview: RenderPokemonView?) {
        CommandPostPcShell.drawInfoBox(context, x, y)
        drawSmallText(context, "COMMAND POST", 17, 131, 0xFFEFE7D7.toInt(), true)

        if (preview == null) {
            drawSmallText(context, "No Pokemon", 18, 145, 0xFFB8C3C7.toInt(), false)
            drawSmallText(context, "Hover a slot", 18, 156, 0xFF8FA0A8.toInt(), false)
            return
        }

        drawSmallText(context, fit(preview.displayName, 58), 15, 145, 0xFFFFFFFF.toInt(), true)
        drawSmallText(context, "Lv.${preview.level} ${friendlySpecies(preview.species)}", 15, 156, 0xFFDDE7EA.toInt(), false)
        val gender = when {
            "male" in preview.aspects -> "M"
            "female" in preview.aspects -> "F"
            else -> ""
        }
        if (gender.isNotEmpty()) {
            drawSmallText(context, gender, 63, 156, if (gender == "M") 0xFF32CBFF.toInt() else 0xFFFC5454.toInt(), true)
        }
        val status = when {
            preview.isFainted -> "Fainted"
            selectedPokemonId == preview.pokemonId -> "Selected"
            else -> "Ready"
        }
        drawSmallText(context, status, 15, 169, if (preview.isFainted) 0xFFFF7777.toInt() else 0xFFBFE7C4.toInt(), false)
        heldStack(preview.heldItemId)?.let { stack ->
            renderScaledGuiItemIcon(itemStack = stack, x = x + 55.0, y = y + 176.0, scale = 0.5, matrixStack = context.matrices)
        }
    }

    private fun drawStorageScreen(context: DrawContext, box: CrewSourceBoxSnapshot?, localMouseX: Int, localMouseY: Int, delta: Float) {
        val title = if (sourceType == CrewSourceType.PARTY) "Party" else box?.label ?: "Box ${sourceBoxIndex + 1}"
        CommandPostStorageWidget.drawTitle(context, textRenderer, x, y, title)

        if (sourceType == CrewSourceType.PC) {
            CommandPostStorageWidget.drawNavigation(context, x, y, localMouseX, localMouseY)
        }

        CommandPostStorageWidget.drawFrame(context, x, y)

        val hits = mutableListOf<SlotHit>()
        val slots = box?.slots.orEmpty()
        slots.forEachIndexed { index, slot ->
            val gridIndex = if (sourceType == CrewSourceType.PARTY) index else slot.slotIndex
            if (gridIndex !in 0 until CommandPostStorageWidget.BOX_SLOT_COUNT) return@forEachIndexed
            val left = CommandPostStorageWidget.slotLeft(gridIndex)
            val top = CommandPostStorageWidget.slotTop(gridIndex)
            hits += SlotHit(slot, left, top)
            drawSourceSlot(context, slot, left, top, localMouseX, localMouseY, delta)
        }
        slotHits = hits
    }

    private fun drawSourceSlot(context: DrawContext, slot: CrewSourceSlotSnapshot, left: Int, top: Int, localMouseX: Int, localMouseY: Int, delta: Float) {
        val pokemon = slot.pokemon ?: return
        val hovered = contains(localMouseX, localMouseY, left, top, CommandPostStorageWidget.SLOT_SIZE, CommandPostStorageWidget.SLOT_SIZE)
        if (hovered) {
            hoveredSource = pokemon
            hoveredTooltip = HoverTooltip("source-${pokemon.pokemonId}", sourceTooltip(pokemon))
        }

        val selected = selectedPokemonId == pokemon.pokemonId || hovered
        renderPokemon(context, SourcePokemonView(pokemon), x + left + CommandPostStorageWidget.SLOT_SIZE / 2, y + top + 1, delta, 4.5F, 2.5F)

        context.matrices.push()
        context.matrices.translate(0.0, 0.0, 140.0)
        drawSmallTextAbsolute(context, "Lv.${pokemon.level}", x + left + 1, y + top + 1, 0xFFFFFFFF.toInt(), true)
        if ("male" in pokemon.aspects || "female" in pokemon.aspects) {
            blitk(
                matrixStack = context.matrices,
                texture = if ("male" in pokemon.aspects) CommandPostPcShell.GENDER_MALE else CommandPostPcShell.GENDER_FEMALE,
                x = (x + left + 21) / CommandPostPcShell.TEXTURE_SCALE,
                y = (y + top + 1) / CommandPostPcShell.TEXTURE_SCALE,
                width = 6,
                height = 8,
                scale = CommandPostPcShell.TEXTURE_SCALE
            )
        }
        heldStack(pokemon.heldItemId)?.let { stack ->
            renderScaledGuiItemIcon(itemStack = stack, x = x + left + 16.0, y = y + top + 16.0, scale = 0.5, matrixStack = context.matrices)
        }
        context.matrices.pop()

        context.matrices.push()
        context.matrices.translate(0.0, 0.0, 500.0)
        if (pokemon.isCrewMember || !pokemon.isAvailable) {
            blitk(matrixStack = context.matrices, texture = CommandPostPcShell.SLOT_OVERLAY, x = x + left, y = y + top, width = CommandPostStorageWidget.SLOT_SIZE, height = CommandPostStorageWidget.SLOT_SIZE, alpha = if (pokemon.isCrewMember) 1.0F else 0.72F)
        }
        if (pokemon.isCrewMember) {
            blitk(matrixStack = context.matrices, texture = CommandPostPastureWidget.SLOT_PASTURE_ICON, x = (x + left + 7.5) / CommandPostPcShell.TEXTURE_SCALE, y = (y + top + 7.5) / CommandPostPcShell.TEXTURE_SCALE, width = 20, height = 20, scale = CommandPostPcShell.TEXTURE_SCALE)
        } else if (hovered && pokemon.isAvailable) {
            blitk(matrixStack = context.matrices, texture = CommandPostPcShell.SLOT_OVERLAY, x = x + left, y = y + top, width = CommandPostStorageWidget.SLOT_SIZE, height = CommandPostStorageWidget.SLOT_SIZE)
            blitk(matrixStack = context.matrices, texture = CommandPostPastureWidget.SLOT_MOVE_ICON, x = (x + left + 7.5) / CommandPostPcShell.TEXTURE_SCALE, y = (y + top + 7.5) / CommandPostPcShell.TEXTURE_SCALE, width = 20, height = 20, scale = CommandPostPcShell.TEXTURE_SCALE)
        }
        if (selected) {
            blitk(matrixStack = context.matrices, texture = CommandPostPcShell.POINTER, x = (x + left + 10) / CommandPostPcShell.TEXTURE_SCALE, y = ((y + top - 3) / CommandPostPcShell.TEXTURE_SCALE) - pointerOffsetY, width = 11, height = 8, scale = CommandPostPcShell.TEXTURE_SCALE)
        }
        context.matrices.pop()
    }

    private fun drawPasturePanel(context: DrawContext, members: List<CommandPostCrewMemberSnapshot>, localMouseX: Int, localMouseY: Int, delta: Float) {
        val maxScroll = CommandPostPastureWidget.maxScroll(members.size)
        pastureScrollIndex = pastureScrollIndex.coerceIn(0, maxScroll)
        val visibleMembers = members.drop(pastureScrollIndex).take(CommandPostPastureWidget.VISIBLE_ROWS)
        val hits = mutableListOf<CrewHit>()
        val maxWorkers = CommandPostCrewSnapshotCache.get(handler.routerPos)?.maxWorkers ?: 0

        CommandPostPastureWidget.drawPanel(context, textRenderer, x, y)
        context.enableScissor(x + CommandPostPastureWidget.LIST_LEFT, y + CommandPostPastureWidget.LIST_TOP - 1, x + CommandPostPastureWidget.LIST_LEFT + CommandPostPastureWidget.LIST_WIDTH, y + CommandPostPastureWidget.LIST_TOP + CommandPostPastureWidget.LIST_HEIGHT)
        visibleMembers.forEachIndexed { index, member ->
            val row = CommandPostPastureWidget.rowBounds(index)
            hits += CrewHit(member, row.left, row.top)
            drawPastureRow(context, member, row.left, row.top, localMouseX, localMouseY, delta)
        }
        context.disableScissor()
        crewHits = hits

        CommandPostPastureWidget.drawScrollOverlay(context, x, y)
    CommandPostPastureWidget.drawControls(context, textRenderer, x, y, members.size, maxWorkers, localMouseX, localMouseY)
        if (CommandPostPastureWidget.recallContains(localMouseX, localMouseY)) {
            hoveredTooltip = HoverTooltip("recall", listOf(Text.literal("Recall all Command Post Pokemon")))
        }
    }

    private fun drawPastureRow(context: DrawContext, member: CommandPostCrewMemberSnapshot, left: Int, top: Int, localMouseX: Int, localMouseY: Int, delta: Float) {
        val hovered = contains(localMouseX, localMouseY, left, top, CommandPostPastureWidget.SLOT_WIDTH, CommandPostPastureWidget.SLOT_HEIGHT)
        if (hovered) {
            hoveredCrew = member
            hoveredTooltip = HoverTooltip("crew-${member.pokemonId}", crewTooltip(member))
        }

        CommandPostPastureWidget.drawRowFrame(context, x, y, left, top, member.isActive(), hovered)

        renderPokemon(context, CrewPokemonView(member), x + left + 23, y + top - 1, delta, 4.5F, 2.5F)
        heldStack(member.heldItemId)?.let { stack ->
            renderScaledGuiItemIcon(itemStack = stack, x = x + left + 23.5, y = y + top + 13.0, scale = 0.5, matrixStack = context.matrices)
        }
        drawSmallTextAbsolute(context, "Lv.${member.level}", x + left + 44, y + top + 17, 0xFFFFFFFF.toInt(), true)
        drawSmallTextAbsolute(context, fit(member.displayName, 50), x + left + 11, y + top + 24, 0xFFEAF4F5.toInt(), false)

        CommandPostPastureWidget.drawSlotIcon(context, x, y, CommandPostPastureWidget.SLOT_MOVE_ICON, left + 2, top + 11, hovered = contains(localMouseX, localMouseY, left + 2, top + 11, CommandPostPastureWidget.SLOT_ICON_SIZE, CommandPostPastureWidget.SLOT_ICON_SIZE))
        CommandPostPastureWidget.drawSlotIcon(context, x, y, CommandPostPastureWidget.SLOT_DEFEND_ICON, left + 44, top + 3, hovered = contains(localMouseX, localMouseY, left + 44, top + 3, CommandPostPastureWidget.SLOT_ICON_SIZE, CommandPostPastureWidget.SLOT_ICON_SIZE))

        if (member.isBlocked()) {
            context.fill(x + left + 1, y + top + 1, x + left + 4, y + top + 4, 0xFFFF5555.toInt())
        } else if (member.isReady()) {
            context.fill(x + left + 1, y + top + 1, x + left + 4, y + top + 4, 0xFF74E08A.toInt())
        }
    }

    private fun drawRouterSlots(context: DrawContext) {
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

    private fun renderPokemon(context: DrawContext, view: RenderPokemonView, centerX: Int, topY: Int, delta: Float, modelScale: Float, matrixScale: Float) {
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

    private fun renderablePokemon(view: RenderPokemonView): RenderablePokemon? {
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
        val boxCount = source.boxes.size
        if (boxCount <= 0) return null
        sourceBoxIndex = sourceBoxIndex.coerceIn(0, boxCount - 1)
        return source.boxes.getOrNull(sourceBoxIndex)
    }

    private fun previewPokemon(box: CrewSourceBoxSnapshot?, members: List<CommandPostCrewMemberSnapshot>): RenderPokemonView? {
        hoveredSource?.let { return SourcePokemonView(it) }
        hoveredCrew?.let { return CrewPokemonView(it) }
        selectedPokemonId?.let { selected ->
            box?.slots?.firstNotNullOfOrNull { it.pokemon?.takeIf { pokemon -> pokemon.pokemonId == selected } }?.let { return SourcePokemonView(it) }
            members.firstOrNull { it.pokemonId == selected }?.let { return CrewPokemonView(it) }
        }
        members.firstOrNull()?.let { return CrewPokemonView(it) }
        box?.slots?.firstNotNullOfOrNull { it.pokemon }?.let { return SourcePokemonView(it) }
        return null
    }

    private fun changeBox(delta: Int) {
        val source = CrewSourceSnapshotCache.get(handler.routerPos).firstOrNull { it.sourceType == CrewSourceType.PC } ?: return
        if (source.boxes.isEmpty()) return
        sourceBoxIndex = Math.floorMod(sourceBoxIndex + delta, source.boxes.size)
        selectedPokemonId = null
        play(CobblemonSounds.PC_CLICK)
    }

    private fun requestCrewRefresh() {
        CobblePalsNetworking.sendCommandPostCrewRefresh(handler.routerPos)
    }

    private fun requestSourceRefresh() {
        CobblePalsNetworking.sendCrewSourceRefresh(handler.routerPos)
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
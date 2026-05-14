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
import java.util.Locale
import java.util.UUID
import kotlin.math.floor
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
        backgroundWidth = BASE_WIDTH
        backgroundHeight = BASE_HEIGHT
        playerInventoryTitleY = 10_000
        titleY = 10_000
    }

    override fun init() {
        backgroundWidth = BASE_WIDTH
        backgroundHeight = BASE_HEIGHT
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

        if (contains(localMouseX, localMouseY, EXIT_LEFT, EXIT_TOP, EXIT_WIDTH, EXIT_HEIGHT)) {
            play(CobblemonSounds.PC_CLICK)
            close()
            return true
        }

        if (sourceType == CrewSourceType.PC && contains(localMouseX, localMouseY, PREV_LEFT, NAV_TOP, NAV_SIZE, NAV_SIZE)) {
            changeBox(-1)
            return true
        }
        if (sourceType == CrewSourceType.PC && contains(localMouseX, localMouseY, NEXT_LEFT, NAV_TOP, NAV_SIZE, NAV_SIZE)) {
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

        if (contains(localMouseX, localMouseY, PASTURE_RECALL_LEFT, PASTURE_RECALL_TOP, PASTURE_RECALL_WIDTH, PASTURE_RECALL_HEIGHT)) {
            val members = CommandPostCrewSnapshotCache.get(handler.routerPos)?.members.orEmpty()
            members.forEach { CobblePalsNetworking.sendRemoveCrewPokemon(handler.routerPos, it.pokemonId) }
            play(CobblemonSounds.PC_RELEASE)
            requestCrewRefresh()
            requestSourceRefresh()
            return true
        }

        crewHits.firstOrNull { hit -> contains(localMouseX, localMouseY, hit.left + 2, hit.top + 11, SLOT_ICON_SIZE, SLOT_ICON_SIZE) }?.let { hit ->
            selectedPokemonId = hit.member.pokemonId
            CobblePalsNetworking.sendRemoveCrewPokemon(handler.routerPos, hit.member.pokemonId)
            play(CobblemonSounds.PC_DROP)
            requestCrewRefresh()
            requestSourceRefresh()
            return true
        }

        crewHits.firstOrNull { hit -> contains(localMouseX, localMouseY, hit.left + 44, hit.top + 3, SLOT_ICON_SIZE, SLOT_ICON_SIZE) }?.let { hit ->
            selectedPokemonId = hit.member.pokemonId
            CobblePalsNetworking.sendCycleCrewMode(handler.routerPos, hit.member.pokemonId)
            play(CobblemonSounds.PC_CLICK)
            requestCrewRefresh()
            return true
        }

        crewHits.firstOrNull { hit -> contains(localMouseX, localMouseY, hit.left, hit.top, PASTURE_SLOT_WIDTH, PASTURE_SLOT_HEIGHT) }?.let { hit ->
            selectedPokemonId = hit.member.pokemonId
            play(CobblemonSounds.PC_CLICK)
            return true
        }

        slotHits.firstOrNull { hit -> contains(localMouseX, localMouseY, hit.left, hit.top, STORAGE_SLOT_SIZE, STORAGE_SLOT_SIZE) }?.slot?.pokemon?.let { pokemon ->
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
        if (contains(localMouseX, localMouseY, PASTURE_LIST_LEFT, PASTURE_LIST_TOP - 4, PASTURE_LIST_WIDTH, PASTURE_LIST_HEIGHT)) {
            val members = CommandPostCrewSnapshotCache.get(handler.routerPos)?.members.orEmpty()
            val maxScroll = max(0, members.size - VISIBLE_PASTURE_ROWS)
            pastureScrollIndex = (pastureScrollIndex - verticalAmount.toInt()).coerceIn(0, maxScroll)
            return true
        }
        if (sourceType == CrewSourceType.PC && contains(localMouseX, localMouseY, STORAGE_X, STORAGE_Y, SCREEN_WIDTH, SCREEN_HEIGHT)) {
            changeBox(if (verticalAmount > 0) -1 else 1)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    private fun drawBase(context: DrawContext) {
        blitk(matrixStack = context.matrices, texture = PC_BASE, x = x, y = y, width = BASE_WIDTH, height = BASE_HEIGHT)
    }

    private fun drawPortrait(context: DrawContext, preview: RenderPokemonView?, delta: Float) {
        blitk(matrixStack = context.matrices, texture = PORTRAIT_BACKGROUND, x = x + 6, y = y + 27, width = PORTRAIT_SIZE, height = PORTRAIT_SIZE)
        if (preview != null) {
            renderPokemon(context, preview, x + 39, y + 26, delta, 7.0F, 3.2F)
        }
    }

    private fun drawInfoBox(context: DrawContext, preview: RenderPokemonView?) {
        blitk(matrixStack = context.matrices, texture = INFO_BOX, x = x + 9, y = y + 128, width = INFO_BOX_WIDTH, height = INFO_BOX_HEIGHT)
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
        drawSmallText(context, title.uppercase(Locale.ROOT), 151 - textRenderer.getWidth(title) / 4, 14, 0xFFFFFFFF.toInt(), true)

        if (sourceType == CrewSourceType.PC) {
            drawTextureButton(context, TextureButton("previous", PREV_LEFT, NAV_TOP, NAV_SIZE, NAV_SIZE, NAV_PREVIOUS), localMouseX, localMouseY, scaled = true)
            drawTextureButton(context, TextureButton("next", NEXT_LEFT, NAV_TOP, NAV_SIZE, NAV_SIZE, NAV_NEXT), localMouseX, localMouseY, scaled = true)
        }

        blitk(matrixStack = context.matrices, texture = SCREEN_GLOW, x = x + STORAGE_X - 17, y = y + STORAGE_Y - 17, width = 208, height = 189, alpha = 0.82F)
        blitk(matrixStack = context.matrices, texture = SCREEN_GRID, x = x + STORAGE_X + 7, y = y + STORAGE_Y + 11, width = 160, height = 133)
        blitk(matrixStack = context.matrices, texture = SCREEN_OVERLAY, x = x + STORAGE_X, y = y + STORAGE_Y, width = SCREEN_WIDTH, height = SCREEN_HEIGHT)

        val hits = mutableListOf<SlotHit>()
        val slots = box?.slots.orEmpty()
        slots.forEachIndexed { index, slot ->
            val gridIndex = if (sourceType == CrewSourceType.PARTY) index else slot.slotIndex
            if (gridIndex !in 0 until BOX_SLOT_COUNT) return@forEachIndexed
            val col = gridIndex % BOX_COLUMNS
            val row = gridIndex / BOX_COLUMNS
            val left = STORAGE_X + BOX_SLOT_START_X + col * (STORAGE_SLOT_SIZE + BOX_SLOT_PADDING)
            val top = STORAGE_Y + BOX_SLOT_START_Y + row * (STORAGE_SLOT_SIZE + BOX_SLOT_PADDING)
            hits += SlotHit(slot, left, top)
            drawSourceSlot(context, slot, left, top, localMouseX, localMouseY, delta)
        }
        slotHits = hits
    }

    private fun drawSourceSlot(context: DrawContext, slot: CrewSourceSlotSnapshot, left: Int, top: Int, localMouseX: Int, localMouseY: Int, delta: Float) {
        val pokemon = slot.pokemon ?: return
        val hovered = contains(localMouseX, localMouseY, left, top, STORAGE_SLOT_SIZE, STORAGE_SLOT_SIZE)
        if (hovered) {
            hoveredSource = pokemon
            hoveredTooltip = HoverTooltip("source-${pokemon.pokemonId}", sourceTooltip(pokemon))
        }

        val selected = selectedPokemonId == pokemon.pokemonId || hovered
        renderPokemon(context, SourcePokemonView(pokemon), x + left + STORAGE_SLOT_SIZE / 2, y + top + 1, delta, 4.5F, 2.5F)

        context.matrices.push()
        context.matrices.translate(0.0, 0.0, 140.0)
        drawSmallTextAbsolute(context, "Lv.${pokemon.level}", x + left + 1, y + top + 1, 0xFFFFFFFF.toInt(), true)
        if ("male" in pokemon.aspects || "female" in pokemon.aspects) {
            blitk(
                matrixStack = context.matrices,
                texture = if ("male" in pokemon.aspects) GENDER_MALE else GENDER_FEMALE,
                x = (x + left + 21) / TEXTURE_SCALE,
                y = (y + top + 1) / TEXTURE_SCALE,
                width = 6,
                height = 8,
                scale = TEXTURE_SCALE
            )
        }
        heldStack(pokemon.heldItemId)?.let { stack ->
            renderScaledGuiItemIcon(itemStack = stack, x = x + left + 16.0, y = y + top + 16.0, scale = 0.5, matrixStack = context.matrices)
        }
        context.matrices.pop()

        context.matrices.push()
        context.matrices.translate(0.0, 0.0, 500.0)
        if (pokemon.isCrewMember || !pokemon.isAvailable) {
            blitk(matrixStack = context.matrices, texture = SLOT_OVERLAY, x = x + left, y = y + top, width = STORAGE_SLOT_SIZE, height = STORAGE_SLOT_SIZE, alpha = if (pokemon.isCrewMember) 1.0F else 0.72F)
        }
        if (pokemon.isCrewMember) {
            blitk(matrixStack = context.matrices, texture = SLOT_PASTURE_ICON, x = (x + left + 7.5) / TEXTURE_SCALE, y = (y + top + 7.5) / TEXTURE_SCALE, width = 20, height = 20, scale = TEXTURE_SCALE)
        } else if (hovered && pokemon.isAvailable) {
            blitk(matrixStack = context.matrices, texture = SLOT_OVERLAY, x = x + left, y = y + top, width = STORAGE_SLOT_SIZE, height = STORAGE_SLOT_SIZE)
            blitk(matrixStack = context.matrices, texture = SLOT_MOVE_ICON, x = (x + left + 7.5) / TEXTURE_SCALE, y = (y + top + 7.5) / TEXTURE_SCALE, width = 20, height = 20, scale = TEXTURE_SCALE)
        }
        if (selected) {
            blitk(matrixStack = context.matrices, texture = POINTER, x = (x + left + 10) / TEXTURE_SCALE, y = ((y + top - 3) / TEXTURE_SCALE) - pointerOffsetY, width = 11, height = 8, scale = TEXTURE_SCALE)
        }
        context.matrices.pop()
    }

    private fun drawPasturePanel(context: DrawContext, members: List<CommandPostCrewMemberSnapshot>, localMouseX: Int, localMouseY: Int, delta: Float) {
        blitk(matrixStack = context.matrices, texture = PASTURE_PANEL, x = x + PASTURE_X, y = y + PASTURE_Y, width = RIGHT_PANEL_WIDTH, height = RIGHT_PANEL_HEIGHT)
        drawSmallText(context, "PASTURE", PASTURE_X + 25, PASTURE_Y + 4, 0xFFEFE7D7.toInt(), true)

        val maxScroll = max(0, members.size - VISIBLE_PASTURE_ROWS)
        pastureScrollIndex = pastureScrollIndex.coerceIn(0, maxScroll)
        val visibleMembers = members.drop(pastureScrollIndex).take(VISIBLE_PASTURE_ROWS)
        val hits = mutableListOf<CrewHit>()

        context.enableScissor(x + PASTURE_LIST_LEFT, y + PASTURE_LIST_TOP - 1, x + PASTURE_LIST_LEFT + PASTURE_LIST_WIDTH, y + PASTURE_LIST_TOP + PASTURE_LIST_HEIGHT)
        visibleMembers.forEachIndexed { index, member ->
            val rowLeft = PASTURE_LIST_LEFT - 4
            val rowTop = PASTURE_LIST_TOP + index * (PASTURE_SLOT_HEIGHT + PASTURE_SLOT_SPACING)
            hits += CrewHit(member, rowLeft, rowTop)
            drawPastureRow(context, member, rowLeft, rowTop, localMouseX, localMouseY, delta)
        }
        context.disableScissor()
        crewHits = hits

        blitk(matrixStack = context.matrices, texture = PASTURE_SCROLL_OVERLAY, x = x + PASTURE_LIST_LEFT, y = y + PASTURE_LIST_TOP - 13, width = PASTURE_LIST_WIDTH, height = 131)
        val maxWorkers = CommandPostCrewSnapshotCache.get(handler.routerPos)?.maxWorkers ?: 0
        drawSmallText(context, "${members.size}/$maxWorkers", PASTURE_X + 36, PASTURE_Y + 24, 0xFFFFFFFF.toInt(), true)
        drawTextureButton(context, TextureButton("recall", PASTURE_RECALL_LEFT, PASTURE_RECALL_TOP, PASTURE_RECALL_WIDTH, PASTURE_RECALL_HEIGHT, PASTURE_BUTTON), localMouseX, localMouseY, scaled = false)
        drawSmallText(context, "Recall", PASTURE_RECALL_LEFT + 22, PASTURE_RECALL_TOP + 5, 0xFFFFFFFF.toInt(), true)
        if (contains(localMouseX, localMouseY, PASTURE_RECALL_LEFT, PASTURE_RECALL_TOP, PASTURE_RECALL_WIDTH, PASTURE_RECALL_HEIGHT)) {
            hoveredTooltip = HoverTooltip("recall", listOf(Text.literal("Recall all Command Post Pokemon")))
        }
    }

    private fun drawPastureRow(context: DrawContext, member: CommandPostCrewMemberSnapshot, left: Int, top: Int, localMouseX: Int, localMouseY: Int, delta: Float) {
        val hovered = contains(localMouseX, localMouseY, left, top, PASTURE_SLOT_WIDTH, PASTURE_SLOT_HEIGHT)
        if (hovered) {
            hoveredCrew = member
            hoveredTooltip = HoverTooltip("crew-${member.pokemonId}", crewTooltip(member))
        }

        blitk(
            matrixStack = context.matrices,
            texture = if (member.isActive()) PASTURE_SLOT_OWNER else PASTURE_SLOT,
            x = x + left,
            y = y + top,
            width = PASTURE_SLOT_WIDTH,
            height = PASTURE_SLOT_HEIGHT,
            vOffset = if (hovered) PASTURE_SLOT_HEIGHT else 0,
            textureHeight = PASTURE_SLOT_HEIGHT * 2
        )

        renderPokemon(context, CrewPokemonView(member), x + left + 23, y + top - 1, delta, 4.5F, 2.5F)
        heldStack(member.heldItemId)?.let { stack ->
            renderScaledGuiItemIcon(itemStack = stack, x = x + left + 23.5, y = y + top + 13.0, scale = 0.5, matrixStack = context.matrices)
        }
        drawSmallTextAbsolute(context, "Lv.${member.level}", x + left + 44, y + top + 17, 0xFFFFFFFF.toInt(), true)
        drawSmallTextAbsolute(context, fit(member.displayName, 50), x + left + 11, y + top + 24, 0xFFEAF4F5.toInt(), false)

        drawScaledSlotIcon(context, SLOT_MOVE_ICON, left + 2, top + 11, hovered = contains(localMouseX, localMouseY, left + 2, top + 11, SLOT_ICON_SIZE, SLOT_ICON_SIZE))
        drawScaledSlotIcon(context, SLOT_DEFEND_ICON, left + 44, top + 3, hovered = contains(localMouseX, localMouseY, left + 44, top + 3, SLOT_ICON_SIZE, SLOT_ICON_SIZE))

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
                blitk(matrixStack = context.matrices, texture = SLOT_OVERLAY, x = x + left - 4, y = y + top - 4, width = STORAGE_SLOT_SIZE, height = STORAGE_SLOT_SIZE, alpha = 0.55F)
            }
        }
        for (index in 0 until RouterBlockEntity.UPGRADE_SLOT_COUNT) {
            val left = UPGRADE_SLOT_LEFT + (index % 3) * 18
            val top = UPGRADE_SLOT_TOP + (index / 3) * 18
            blitk(matrixStack = context.matrices, texture = SLOT_OVERLAY, x = x + left - 4, y = y + top - 4, width = STORAGE_SLOT_SIZE, height = STORAGE_SLOT_SIZE, alpha = 0.38F)
        }
    }

    private fun drawSourceModeButton(context: DrawContext, localMouseX: Int, localMouseY: Int) {
        drawTextureButton(context, TextureButton("source", SOURCE_BUTTON_LEFT, SOURCE_BUTTON_TOP, SOURCE_BUTTON_SIZE, SOURCE_BUTTON_SIZE, SOURCE_BUTTON_TEXTURE), localMouseX, localMouseY, scaled = true)
        if (contains(localMouseX, localMouseY, SOURCE_BUTTON_LEFT, SOURCE_BUTTON_TOP, SOURCE_BUTTON_SIZE, SOURCE_BUTTON_SIZE)) {
            hoveredTooltip = HoverTooltip("source-toggle", listOf(Text.literal(if (sourceType == CrewSourceType.PC) "Show Party" else "Show PC")))
        }
    }

    private fun drawExitButton(context: DrawContext, localMouseX: Int, localMouseY: Int) {
        val hovered = contains(localMouseX, localMouseY, EXIT_LEFT, EXIT_TOP, EXIT_WIDTH, EXIT_HEIGHT)
        blitk(matrixStack = context.matrices, texture = BACK_BUTTON, x = x + EXIT_LEFT, y = y + EXIT_TOP, width = EXIT_WIDTH, height = EXIT_HEIGHT, vOffset = if (hovered) EXIT_HEIGHT else 0, textureHeight = EXIT_HEIGHT * 2)
        blitk(matrixStack = context.matrices, texture = BACK_BUTTON_ICON, x = (x + EXIT_LEFT + 7) / TEXTURE_SCALE, y = (y + EXIT_TOP + 4) / TEXTURE_SCALE, width = 21, height = 11, scale = TEXTURE_SCALE)
    }

    private fun drawTextureButton(context: DrawContext, button: TextureButton, localMouseX: Int, localMouseY: Int, scaled: Boolean) {
        val hovered = contains(localMouseX, localMouseY, button.left, button.top, button.width, button.height)
        if (scaled) {
            blitk(
                matrixStack = context.matrices,
                texture = button.texture,
                x = (x + button.left) / TEXTURE_SCALE,
                y = (y + button.top) / TEXTURE_SCALE,
                width = floor(button.width / TEXTURE_SCALE).toInt(),
                height = floor(button.height / TEXTURE_SCALE).toInt(),
                vOffset = if (hovered) floor(button.height / TEXTURE_SCALE).toInt() else 0,
                textureHeight = floor(button.height / TEXTURE_SCALE).toInt() * 2,
                scale = TEXTURE_SCALE
            )
        } else {
            blitk(matrixStack = context.matrices, texture = button.texture, x = x + button.left, y = y + button.top, width = button.width, height = button.height, vOffset = if (hovered) button.height else 0, textureHeight = button.height * 2)
        }
    }

    private fun drawScaledSlotIcon(context: DrawContext, texture: Identifier, localX: Int, localY: Int, hovered: Boolean) {
        blitk(
            matrixStack = context.matrices,
            texture = texture,
            x = (x + localX) / TEXTURE_SCALE,
            y = (y + localY) / TEXTURE_SCALE,
            width = 14,
            height = 14,
            vOffset = if (hovered) 14 else 0,
            textureHeight = 28,
            scale = TEXTURE_SCALE
        )
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
        context.matrices.push()
        context.matrices.translate(absoluteX.toDouble(), absoluteY.toDouble(), 650.0)
        context.matrices.scale(TEXTURE_SCALE, TEXTURE_SCALE, 1F)
        context.drawText(textRenderer, Text.literal(value), 0, 0, color, shadow)
        context.matrices.pop()
    }

    private fun friendlySpecies(species: String): String = species.substringAfter(':').replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

    private fun fit(value: String, maxWidth: Int): String {
        if (textRenderer.getWidth(value) * TEXTURE_SCALE <= maxWidth) return value
        var result = value
        while (result.length > 3 && textRenderer.getWidth("$result...") * TEXTURE_SCALE > maxWidth) {
            result = result.dropLast(1)
        }
        return "$result..."
    }

    private fun contains(mouseX: Int, mouseY: Int, left: Int, top: Int, width: Int, height: Int): Boolean {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height
    }

    companion object {
        private const val BASE_WIDTH = 349
        private const val BASE_HEIGHT = 205
        private const val RIGHT_PANEL_WIDTH = 82
        private const val RIGHT_PANEL_HEIGHT = 169
        private const val PORTRAIT_SIZE = 66
        private const val INFO_BOX_WIDTH = 63
        private const val INFO_BOX_HEIGHT = 69
        private const val TEXTURE_SCALE = 0.5F

        private const val STORAGE_X = 85
        private const val STORAGE_Y = 27
        private const val SCREEN_WIDTH = 174
        private const val SCREEN_HEIGHT = 155
        private const val BOX_COLUMNS = 6
        private const val BOX_SLOT_COUNT = 30
        private const val BOX_SLOT_START_X = 7
        private const val BOX_SLOT_START_Y = 11
        private const val BOX_SLOT_PADDING = 2
        private const val STORAGE_SLOT_SIZE = 25

        private const val PASTURE_X = 267
        private const val PASTURE_Y = 8
        private const val PASTURE_LIST_LEFT = PASTURE_X + 6
        private const val PASTURE_LIST_TOP = PASTURE_Y + 31
        private const val PASTURE_LIST_WIDTH = 70
        private const val PASTURE_LIST_HEIGHT = 120
        private const val PASTURE_SLOT_WIDTH = 62
        private const val PASTURE_SLOT_HEIGHT = 29
        private const val PASTURE_SLOT_SPACING = 3
        private const val VISIBLE_PASTURE_ROWS = 4
        private const val PASTURE_RECALL_LEFT = PASTURE_X + 6
        private const val PASTURE_RECALL_TOP = PASTURE_Y + 153
        private const val PASTURE_RECALL_WIDTH = 70
        private const val PASTURE_RECALL_HEIGHT = 17

        private const val PREV_LEFT = 117
        private const val NEXT_LEFT = 220
        private const val NAV_TOP = 16
        private const val NAV_SIZE = 7
        private const val SOURCE_BUTTON_LEFT = 242
        private const val SOURCE_BUTTON_TOP = 186
        private const val SOURCE_BUTTON_SIZE = 8
        private const val EXIT_LEFT = 320
        private const val EXIT_TOP = 186
        private const val EXIT_WIDTH = 26
        private const val EXIT_HEIGHT = 13
        private const val SLOT_ICON_SIZE = 7

        private const val MODULE_SLOT_LEFT = 14
        private const val MODULE_SLOT_TOP = 139
        private const val UPGRADE_SLOT_LEFT = 8
        private const val UPGRADE_SLOT_TOP = 99
        private const val HIDDEN_SLOT_X = -10_000
        private const val HIDDEN_SLOT_Y = -10_000

        private val PC_BASE = cobblemon("textures/gui/pc/pc_base.png")
        private val PORTRAIT_BACKGROUND = cobblemon("textures/gui/pc/portrait_background.png")
        private val INFO_BOX = cobblemon("textures/gui/pc/info_box.png")
        private val SCREEN_GRID = cobblemon("textures/gui/pc/pc_screen_grid.png")
        private val SCREEN_OVERLAY = cobblemon("textures/gui/pc/pc_screen_overlay.png")
        private val SCREEN_GLOW = cobblemon("textures/gui/pc/pc_screen_glow.png")
        private val SLOT_OVERLAY = cobblemon("textures/gui/pc/pc_slot_overlay.png")
        private val POINTER = cobblemon("textures/gui/pc/pc_pointer.png")
        private val SLOT_PASTURE_ICON = cobblemon("textures/gui/pasture/pc_slot_icon_pasture.png")
        private val SLOT_MOVE_ICON = cobblemon("textures/gui/pasture/pc_slot_icon_move.png")
        private val SLOT_DEFEND_ICON = cobblemon("textures/gui/pasture/pasture_slot_icon_defend.png")
        private val PASTURE_PANEL = cobblemon("textures/gui/pasture/pasture_panel.png")
        private val PASTURE_SCROLL_OVERLAY = cobblemon("textures/gui/pasture/pasture_scroll_overlay.png")
        private val PASTURE_SLOT = cobblemon("textures/gui/pasture/pasture_slot.png")
        private val PASTURE_SLOT_OWNER = cobblemon("textures/gui/pasture/pasture_slot_owner.png")
        private val PASTURE_BUTTON = cobblemon("textures/gui/pasture/pasture_button.png")
        private val NAV_PREVIOUS = cobblemon("textures/gui/pc/pc_arrow_previous.png")
        private val NAV_NEXT = cobblemon("textures/gui/pc/pc_arrow_next.png")
        private val SOURCE_BUTTON_TEXTURE = cobblemon("textures/gui/pc/pc_icon_filter.png")
        private val BACK_BUTTON = cobblemon("textures/gui/common/back_button.png")
        private val BACK_BUTTON_ICON = cobblemon("textures/gui/common/back_button_icon.png")
        private val GENDER_MALE = cobblemon("textures/gui/pc/gender_icon_male.png")
        private val GENDER_FEMALE = cobblemon("textures/gui/pc/gender_icon_female.png")

        private fun cobblemon(path: String): Identifier = Identifier.of("cobblemon", path)
    }
}
package com.cobblepalsworld.gui.pasture

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblepalsworld.networking.CobblePalsNetworking
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.joml.Quaternionf
import org.joml.Vector3f

class PastureManagerScreen(private val snapshot: PastureSnapshot) : Screen(Text.literal("CobblePals Manager")) {

    companion object {
        private const val PANEL_WIDTH = 280
        private const val PANEL_HEIGHT = 200
        private const val ENTRY_HEIGHT = 38
        private const val HEADER_HEIGHT = 30
        private const val SCROLL_BAR_WIDTH = 5
        private const val PORTRAIT_SIZE = 28
        private const val HOME_BTN_WIDTH = 12
        private const val HOME_BTN_HEIGHT = 10

        // Cobblemon dark teal/green palette
        private const val BG_OUTER = 0xE0102428      // very dark teal border
        private const val BG_INNER = 0xE0182E34       // dark teal body
        private const val BG_HEADER = 0xE0143038       // slightly lighter header
        private const val ROW_EVEN = 0x50102020        // subtle dark row
        private const val ROW_ODD = 0x50183030         // slightly lighter row
        private const val ROW_HOVER = 0x60206040       // highlight on hover
        private const val DIVIDER = 0x40308888         // subtle column dividers
        private const val ACCENT = 0xFF4ADE80.toInt()  // green accent
        private const val ACCENT_DIM = 0xFF2D8E56.toInt()
        private const val TEXT_BRIGHT = 0xE8E8F0
        private const val TEXT_DIM = 0x78909C
        private const val TEXT_FAINTED = 0xFF5555
        private const val AUGMENT_COLOR = 0x9B8AFF
        private const val BINDING_COLOR = 0x7DC4FF
        private const val ITEMS_COLOR = 0xFFB74D
        private const val TAG_COLOR = 0x4ADE80
        private const val NO_TAG_COLOR = 0x4A5568

        private val PORTRAIT_BG = cobblemonResource("textures/gui/pc/portrait_background.png")
    }

    private var scrollOffset = 0
    private val visibleEntries get() = (PANEL_HEIGHT - HEADER_HEIGHT) / ENTRY_HEIGHT
    private var panelLeft = 0
    private var panelTop = 0
    private val portraitStates = mutableMapOf<Int, FloatingState>()

    override fun init() {
        super.init()
        panelLeft = (width - PANEL_WIDTH) / 2
        panelTop = (height - PANEL_HEIGHT) / 2
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Darken world behind the screen
        context.fill(0, 0, width, height, 0xA0000000.toInt())

        val pL = panelLeft
        val pT = panelTop

        // Outer border (1px)
        context.fill(pL - 2, pT - 2, pL + PANEL_WIDTH + 2, pT + PANEL_HEIGHT + 2, BG_OUTER.toInt())
        // Inner panel
        context.fill(pL, pT, pL + PANEL_WIDTH, pT + PANEL_HEIGHT, BG_INNER.toInt())
        // Header bar
        context.fill(pL, pT, pL + PANEL_WIDTH, pT + HEADER_HEIGHT, BG_HEADER.toInt())
        // Header bottom divider
        context.fill(pL, pT + HEADER_HEIGHT - 1, pL + PANEL_WIDTH, pT + HEADER_HEIGHT, DIVIDER.toInt())

        // Title
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = Text.literal("CobblePals Manager").styled { it.withBold(true) } as MutableText,
            x = pL + PANEL_WIDTH / 2,
            y = pT + 4,
            centered = true,
            shadow = true,
            colour = ACCENT
        )

        // Subtitle info
        val pos = snapshot.pasturePos
        val workerCount = snapshot.pals.count { it.tagTypeId != null }
        val subText = "${snapshot.ownerName}'s Pasture  \u2022  ${pos.x}, ${pos.y}, ${pos.z}  \u2022  $workerCount/${snapshot.maxWorkers} working"
        drawScaledText(
            context = context,
            text = Text.literal(subText) as MutableText,
            x = pL + PANEL_WIDTH / 2,
            y = pT + 17,
            scale = 0.85F,
            centered = true,
            shadow = true,
            colour = TEXT_DIM
        )

        // Close button (X) top right
        val closeX = pL + PANEL_WIDTH - 12
        val closeY = pT + 3
        val closeHovered = mouseX >= closeX && mouseX < closeX + 10 && mouseY >= closeY && mouseY < closeY + 10
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = Text.literal("X").styled { it.withBold(true) } as MutableText,
            x = closeX + 5,
            y = closeY + 1,
            centered = true,
            shadow = true,
            colour = if (closeHovered) 0xFF6666 else TEXT_DIM
        )

        if (snapshot.pals.isEmpty()) {
            drawScaledText(
                context = context,
                text = Text.literal("No Pokemon in this pasture") as MutableText,
                x = pL + PANEL_WIDTH / 2,
                y = pT + HEADER_HEIGHT + 30,
                centered = true,
                shadow = true,
                colour = TEXT_DIM
            )
            return
        }

        // Scrollable area with scissor clipping
        val listTop = pT + HEADER_HEIGHT
        val listBottom = pT + PANEL_HEIGHT
        context.enableScissor(pL, listTop, pL + PANEL_WIDTH, listBottom)

        val endIdx = minOf(scrollOffset + visibleEntries + 1, snapshot.pals.size)
        for (i in scrollOffset until endIdx) {
            val pal = snapshot.pals[i]
            val ey = listTop + (i - scrollOffset) * ENTRY_HEIGHT

            if (ey + ENTRY_HEIGHT < listTop || ey > listBottom) continue

            // Row background
            val rowHovered = mouseX >= pL && mouseX < pL + PANEL_WIDTH - SCROLL_BAR_WIDTH - 2
                    && mouseY >= ey && mouseY < ey + ENTRY_HEIGHT && mouseY >= listTop && mouseY < listBottom
            val rowBg = when {
                rowHovered -> ROW_HOVER.toInt()
                i % 2 == 0 -> ROW_EVEN.toInt()
                else -> ROW_ODD.toInt()
            }
            context.fill(pL, ey, pL + PANEL_WIDTH - SCROLL_BAR_WIDTH - 2, ey + ENTRY_HEIGHT, rowBg)

            // Bottom row divider
            context.fill(pL + 4, ey + ENTRY_HEIGHT - 1, pL + PANEL_WIDTH - SCROLL_BAR_WIDTH - 6, ey + ENTRY_HEIGHT, DIVIDER.toInt())

            // ── Column 1: Portrait + Name (0..100) ──
            val portraitX = pL + 5
            val portraitY = ey + 5

            // Portrait background
            context.fill(portraitX, portraitY, portraitX + PORTRAIT_SIZE, portraitY + PORTRAIT_SIZE, 0x60102020)
            context.fill(portraitX, portraitY, portraitX + PORTRAIT_SIZE, portraitY + 1, DIVIDER.toInt())
            context.fill(portraitX, portraitY, portraitX + 1, portraitY + PORTRAIT_SIZE, DIVIDER.toInt())
            context.fill(portraitX + PORTRAIT_SIZE - 1, portraitY, portraitX + PORTRAIT_SIZE, portraitY + PORTRAIT_SIZE, DIVIDER.toInt())
            context.fill(portraitX, portraitY + PORTRAIT_SIZE - 1, portraitX + PORTRAIT_SIZE, portraitY + PORTRAIT_SIZE, DIVIDER.toInt())

            // Render Pokemon 3D portrait
            renderPokemonPortrait(context, pal, i, portraitX, portraitY, delta)

            // Name + Level
            val nameX = pL + 38
            val nameColor = if (pal.isFainted) TEXT_FAINTED else TEXT_BRIGHT
            drawScaledText(
                context = context,
                text = Text.literal(pal.displayName).styled { it.withBold(true) } as MutableText,
                x = nameX,
                y = ey + 6,
                scale = 0.9F,
                shadow = true,
                colour = nameColor
            )
            drawScaledText(
                context = context,
                text = Text.literal("Lv.${pal.level}") as MutableText,
                x = nameX,
                y = ey + 17,
                scale = 0.75F,
                shadow = true,
                colour = TEXT_DIM
            )
            if (pal.isFainted) {
                drawScaledText(
                    context = context,
                    text = Text.literal("FAINTED").styled { it.withBold(true) } as MutableText,
                    x = nameX,
                    y = ey + 26,
                    scale = 0.65F,
                    shadow = true,
                    colour = TEXT_FAINTED
                )
            }

            // ── Column divider ──
            context.fill(pL + 98, ey + 3, pL + 99, ey + ENTRY_HEIGHT - 4, DIVIDER.toInt())

            // ── Column 2: Tag + Filter + Augment (100..190) ──
            val tagX = pL + 104
            if (pal.tagTypeId != null) {
                drawScaledText(
                    context = context,
                    text = Text.literal(pal.tagTypeId.uppercase()).styled { it.withBold(true) } as MutableText,
                    x = tagX,
                    y = ey + 5,
                    scale = 0.85F,
                    shadow = true,
                    colour = TAG_COLOR
                )
            } else {
                drawScaledText(
                    context = context,
                    text = Text.literal("No Tag") as MutableText,
                    x = tagX,
                    y = ey + 5,
                    scale = 0.85F,
                    shadow = true,
                    colour = NO_TAG_COLOR
                )
            }

            // Filter summary
            if (pal.filterSummary.isNotEmpty() && pal.filterSummary != "No filter") {
                drawScaledText(
                    context = context,
                    text = Text.literal("\u25B8 ${pal.filterSummary}") as MutableText,
                    x = tagX,
                    y = ey + 16,
                    scale = 0.7F,
                    shadow = true,
                    colour = TEXT_DIM
                )
            }

            // Augment summary
            if (pal.augmentSummary.isNotEmpty()) {
                drawScaledText(
                    context = context,
                    text = Text.literal("\u2726 ${pal.augmentSummary}") as MutableText,
                    x = tagX,
                    y = ey + 26,
                    scale = 0.7F,
                    shadow = true,
                    colour = AUGMENT_COLOR
                )
            }

            // ── Column divider ──
            context.fill(pL + 192, ey + 3, pL + 193, ey + ENTRY_HEIGHT - 4, DIVIDER.toInt())

            // ── Column 3: Binding + Items (190..end) ──
            val detailX = pL + 198
            if (pal.boundPos != null) {
                val bp = pal.boundPos
                drawScaledText(
                    context = context,
                    text = Text.literal("\u2316 ${bp.x}, ${bp.y}, ${bp.z}") as MutableText,
                    x = detailX,
                    y = ey + 5,
                    scale = 0.75F,
                    shadow = true,
                    colour = BINDING_COLOR
                )
            }

            if (pal.carriedItemDescs.isNotEmpty()) {
                val itemLine = pal.carriedItemDescs.take(2).joinToString(", ")
                val truncated = if (itemLine.length > 14) itemLine.take(13) + "\u2026" else itemLine
                drawScaledText(
                    context = context,
                    text = Text.literal(truncated) as MutableText,
                    x = detailX,
                    y = if (pal.boundPos != null) ey + 16 else ey + 5,
                    scale = 0.7F,
                    shadow = true,
                    colour = ITEMS_COLOR
                )
                if (pal.carriedItemDescs.size > 2) {
                    drawScaledText(
                        context = context,
                        text = Text.literal("+${pal.carriedItemDescs.size - 2} more") as MutableText,
                        x = detailX,
                        y = if (pal.boundPos != null) ey + 26 else ey + 16,
                        scale = 0.65F,
                        shadow = true,
                        colour = TEXT_DIM
                    )
                }
            }

            // ── Home button (bottom-right of row) ──
            val homeBtnX = pL + PANEL_WIDTH - SCROLL_BAR_WIDTH - HOME_BTN_WIDTH - 8
            val homeBtnY = ey + ENTRY_HEIGHT - HOME_BTN_HEIGHT - 3
            val homeHovered = mouseX >= homeBtnX && mouseX < homeBtnX + HOME_BTN_WIDTH
                    && mouseY >= homeBtnY && mouseY < homeBtnY + HOME_BTN_HEIGHT
                    && mouseY >= listTop && mouseY < listBottom
            val homeBg = if (homeHovered) 0xC0306050 else 0x80203030
            context.fill(homeBtnX, homeBtnY, homeBtnX + HOME_BTN_WIDTH, homeBtnY + HOME_BTN_HEIGHT, homeBg.toInt())
            drawScaledText(
                context = context,
                text = Text.literal("\u2302") as MutableText,
                x = homeBtnX + HOME_BTN_WIDTH / 2,
                y = homeBtnY + 1,
                scale = 0.8F,
                centered = true,
                shadow = true,
                colour = if (homeHovered) ACCENT else TEXT_DIM
            )
        }

        context.disableScissor()

        // ── Scroll bar ──
        if (snapshot.pals.size > visibleEntries) {
            val barX = pL + PANEL_WIDTH - SCROLL_BAR_WIDTH - 1
            val barTop = listTop + 2
            val barBottom = listBottom - 2
            val barH = barBottom - barTop

            // Track
            context.fill(barX, barTop, barX + SCROLL_BAR_WIDTH, barBottom, 0x30FFFFFF)

            // Thumb
            val maxScroll = maxOf(1, snapshot.pals.size - visibleEntries)
            val thumbH = maxOf(12, barH * visibleEntries / snapshot.pals.size)
            val thumbY = barTop + (barH - thumbH) * scrollOffset / maxScroll
            context.fill(barX, thumbY, barX + SCROLL_BAR_WIDTH, thumbY + thumbH, ACCENT_DIM)
        }
    }

    private fun renderPokemonPortrait(context: DrawContext, pal: PalSnapshot, index: Int, px: Int, py: Int, delta: Float) {
        val state = portraitStates.getOrPut(index) { FloatingState() }
        try {
            val speciesId = Identifier.of(pal.species)
            val matrixStack = context.matrices
            matrixStack.push()
            matrixStack.translate(
                (px + PORTRAIT_SIZE / 2).toDouble(),
                (py + PORTRAIT_SIZE / 2).toDouble(),
                0.0
            )
            matrixStack.scale(1.8F, 1.8F, 1.0F)

            drawProfilePokemon(
                species = speciesId,
                matrixStack = matrixStack,
                rotation = Quaternionf().rotateXYZ(
                    Math.toRadians(13.0).toFloat(),
                    Math.toRadians(35.0).toFloat(),
                    0F
                ),
                state = state,
                partialTicks = delta,
                scale = 3.0F
            )
            matrixStack.pop()
        } catch (_: Exception) {
            // Species not found or render issue — draw a fallback "?" icon
            drawScaledText(
                context = context,
                text = Text.literal("?").styled { it.withBold(true) } as MutableText,
                x = px + PORTRAIT_SIZE / 2,
                y = py + PORTRAIT_SIZE / 2 - 4,
                centered = true,
                shadow = true,
                colour = TEXT_DIM
            )
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Close button
        val closeX = panelLeft + PANEL_WIDTH - 12
        val closeY = panelTop + 3
        if (button == 0 && mouseX >= closeX && mouseX < closeX + 10 && mouseY >= closeY && mouseY < closeY + 10) {
            close()
            return true
        }

        // Home buttons per row
        if (button == 0) {
            val listTop = panelTop + HEADER_HEIGHT
            val listBottom = panelTop + PANEL_HEIGHT
            val endIdx = minOf(scrollOffset + visibleEntries + 1, snapshot.pals.size)
            for (i in scrollOffset until endIdx) {
                val ey = listTop + (i - scrollOffset) * ENTRY_HEIGHT
                val homeBtnX = panelLeft + PANEL_WIDTH - SCROLL_BAR_WIDTH - HOME_BTN_WIDTH - 8
                val homeBtnY = ey + ENTRY_HEIGHT - HOME_BTN_HEIGHT - 3
                if (mouseX >= homeBtnX && mouseX < homeBtnX + HOME_BTN_WIDTH
                    && mouseY >= homeBtnY && mouseY < homeBtnY + HOME_BTN_HEIGHT
                    && mouseY >= listTop && mouseY < listBottom
                ) {
                    CobblePalsNetworking.sendTeleportHome(snapshot.pals[i].pokemonId)
                    return true
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val maxScroll = maxOf(0, snapshot.pals.size - visibleEntries)
        scrollOffset = (scrollOffset - verticalAmount.toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun shouldPause() = false
}

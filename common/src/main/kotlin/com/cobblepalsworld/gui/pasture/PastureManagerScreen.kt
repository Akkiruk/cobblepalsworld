package com.cobblepalsworld.gui.pasture

import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.gui.UiGlyph
import com.cobblepalsworld.gui.UiIconButtons
import com.cobblepalsworld.networking.CobblePalsNetworking
import com.cobblepalsworld.tag.TagRegistry
import com.cobblepalsworld.tag.TagType
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import org.joml.Quaternionf
import java.util.Locale
import java.util.UUID

class PastureManagerScreen(private var snapshot: PastureSnapshot) : Screen(Text.literal("CobblePals Manager")) {

    companion object {
        private const val PANEL_WIDTH = 284
        private const val PANEL_HEIGHT = 208
        private const val ENTRY_HEIGHT = 40
        private const val HEADER_HEIGHT = 34
        private const val SCROLL_BAR_WIDTH = 5
        private const val PORTRAIT_SIZE = 28
        private const val HOME_BTN_WIDTH = 12
        private const val HOME_BTN_HEIGHT = 10
        private const val REFRESH_INTERVAL_TICKS = 20

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
    }

    private var scrollOffset = 0
    private val visibleEntries get() = maxOf(1, (PANEL_HEIGHT - HEADER_HEIGHT) / ENTRY_HEIGHT)
    private var panelLeft = 0
    private var panelTop = 0
    private var refreshTicks = 0
    private val portraitStates = mutableMapOf<UUID, FloatingState>()

    override fun init() {
        super.init()
        panelLeft = (width - PANEL_WIDTH) / 2
        panelTop = (height - PANEL_HEIGHT) / 2
    }

    override fun tick() {
        super.tick()
        refreshTicks++
        if (refreshTicks >= REFRESH_INTERVAL_TICKS) {
            refreshTicks = 0
            CobblePalsNetworking.sendOpenRequest(snapshot.pasturePos)
        }
    }

    fun appliesTo(pasturePos: BlockPos): Boolean = snapshot.pasturePos == pasturePos

    fun updateSnapshot(snapshot: PastureSnapshot) {
        val samePasture = this.snapshot.pasturePos == snapshot.pasturePos
        this.snapshot = snapshot
        if (!samePasture) {
            scrollOffset = 0
            portraitStates.clear()
        }

        val maxScroll = maxOf(0, snapshot.pals.size - visibleEntries)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)
        portraitStates.keys.retainAll(snapshot.pals.mapTo(mutableSetOf()) { it.pokemonId })
        refreshTicks = 0
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

        val pos = snapshot.pasturePos
        val assignedCount = snapshot.pals.count { it.tagTypeId != null }
        val activeCount = snapshot.pals.count { it.isActive() }
        val cargoCount = snapshot.pals.count { it.hasCargo() }
        val ecoCount = snapshot.pals.count { it.isEcoMode }
        val subText = "${snapshot.ownerName}'s Pasture  \u2022  ${pos.x}, ${pos.y}, ${pos.z}"
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

        val statusText = buildString {
            append("Assigned ")
            append(assignedCount)
            append("/")
            append(snapshot.maxWorkers)
            append("  \u2022  Active ")
            append(activeCount)
            append("  \u2022  Cargo ")
            append(cargoCount)
            if (ecoCount > 0) {
                append("  \u2022  Eco ")
                append(ecoCount)
            }
        }
        drawScaledText(
            context = context,
            text = Text.literal(statusText) as MutableText,
            x = pL + PANEL_WIDTH / 2,
            y = pT + 25,
            scale = 0.72F,
            centered = true,
            shadow = true,
            colour = TEXT_DIM
        )

        // Close button (X) top right
        val closeX = pL + PANEL_WIDTH - 12
        val closeY = pT + 3
        val closeHovered = mouseX >= closeX && mouseX < closeX + 10 && mouseY >= closeY && mouseY < closeY + 10
        UiIconButtons.draw(context, closeX, closeY, 10, 10, UiGlyph.Close, 0xFFFF6B6B.toInt(), closeHovered, closeHovered)

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
            if (pal.isActive()) {
                context.fill(pL, ey, pL + 3, ey + ENTRY_HEIGHT, statusColor(pal))
            }

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
            renderPokemonPortrait(context, pal, portraitX, portraitY, delta)

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
                text = Text.literal("Lv.${pal.level}  \u2022  ${speciesLabel(pal)}") as MutableText,
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
            } else if (pal.isManagedByCommandPost) {
                drawScaledText(
                    context = context,
                    text = Text.literal("Command Post linked") as MutableText,
                    x = nameX,
                    y = ey + 26,
                    scale = 0.65F,
                    shadow = true,
                    colour = BINDING_COLOR
                )
            }

            // ── Column divider ──
            context.fill(pL + 98, ey + 3, pL + 99, ey + ENTRY_HEIGHT - 4, DIVIDER.toInt())

            // ── Column 2: Tag + Filter + Augment (100..190) ──
            val tagX = pL + 104
            tagStack(pal)?.let { stack: ItemStack ->
                context.drawItem(stack, tagX, ey + 4)
            }
            val tagTextX = tagX + 18
            if (pal.tagTypeId != null) {
                drawScaledText(
                    context = context,
                    text = Text.literal(pal.tagTypeId.uppercase()).styled { it.withBold(true) } as MutableText,
                    x = tagTextX,
                    y = ey + 5,
                    scale = 0.85F,
                    shadow = true,
                    colour = TAG_COLOR
                )
            } else {
                drawScaledText(
                    context = context,
                    text = Text.literal("No Tag") as MutableText,
                    x = tagTextX,
                    y = ey + 5,
                    scale = 0.85F,
                    shadow = true,
                    colour = NO_TAG_COLOR
                )
            }

            drawStatusChip(context, pal, tagX, ey + 17)

            val summaryLine = compactSummary(pal)
            if (summaryLine.isNotEmpty()) {
                drawScaledText(
                    context = context,
                    text = Text.literal(summaryLine) as MutableText,
                    x = tagX,
                    y = ey + 28,
                    scale = 0.68F,
                    shadow = true,
                    colour = if (pal.augmentSummary.isNotEmpty()) AUGMENT_COLOR else TEXT_DIM
                )
            }

            // ── Column divider ──
            context.fill(pL + 192, ey + 3, pL + 193, ey + ENTRY_HEIGHT - 4, DIVIDER.toInt())

            // ── Column 3: Binding + Items (190..end) ──
            val detailX = pL + 198
            drawScaledText(
                context = context,
                text = Text.literal(targetLine(pal)) as MutableText,
                x = detailX,
                y = ey + 5,
                scale = 0.72F,
                shadow = true,
                colour = BINDING_COLOR
            )

            val cargoStack = cargoStack(pal)
            cargoStack?.let { stack: ItemStack ->
                context.drawItem(stack, detailX, ey + 15)
            }
            drawScaledText(
                context = context,
                text = Text.literal(cargoLine(pal)) as MutableText,
                x = detailX + if (cargoStack != null) 18 else 0,
                y = ey + 18,
                scale = 0.7F,
                shadow = true,
                colour = if (pal.hasCargo()) ITEMS_COLOR else TEXT_DIM
            )

            val timingLine = timingLine(pal)
            if (timingLine.isNotEmpty()) {
                drawScaledText(
                    context = context,
                    text = Text.literal(timingLine) as MutableText,
                    x = detailX,
                    y = ey + 29,
                    scale = 0.65F,
                    shadow = true,
                    colour = TEXT_DIM
                )
            }

            // ── Home button (bottom-right of row) ──
            val homeBtnX = pL + PANEL_WIDTH - SCROLL_BAR_WIDTH - HOME_BTN_WIDTH - 8
            val homeBtnY = ey + ENTRY_HEIGHT - HOME_BTN_HEIGHT - 3
            val homeHovered = mouseX >= homeBtnX && mouseX < homeBtnX + HOME_BTN_WIDTH
                    && mouseY >= homeBtnY && mouseY < homeBtnY + HOME_BTN_HEIGHT
                    && mouseY >= listTop && mouseY < listBottom
            val homeBg = if (homeHovered) 0xC0306050 else 0x80203030
            context.fill(homeBtnX - 1, homeBtnY - 1, homeBtnX + HOME_BTN_WIDTH + 1, homeBtnY + HOME_BTN_HEIGHT + 1, homeBg.toInt())
            UiIconButtons.draw(context, homeBtnX, homeBtnY, HOME_BTN_WIDTH, HOME_BTN_HEIGHT, UiGlyph.Home, ACCENT, homeHovered, homeHovered)
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

        hoveredTooltip(mouseX, mouseY)?.let { tooltip ->
            context.drawTooltip(textRenderer, tooltip, mouseX, mouseY)
        }
    }

    private fun renderPokemonPortrait(context: DrawContext, pal: PalSnapshot, px: Int, py: Int, delta: Float) {
        val state = portraitStates.getOrPut(pal.pokemonId) { FloatingState() }
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
                    CobblePalsNetworking.sendTeleportHome(snapshot.pasturePos, snapshot.pals[i].pokemonId)
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

    private fun hoveredTooltip(mouseX: Int, mouseY: Int): List<Text>? {
        val closeX = panelLeft + PANEL_WIDTH - 12
        val closeY = panelTop + 3
        if (UiIconButtons.contains(mouseX, mouseY, closeX, closeY, 10, 10)) {
            return listOf(Text.literal("Close manager"))
        }

        val listTop = panelTop + HEADER_HEIGHT
        val listBottom = panelTop + PANEL_HEIGHT
        val endIdx = minOf(scrollOffset + visibleEntries + 1, snapshot.pals.size)
        for (i in scrollOffset until endIdx) {
            val ey = listTop + (i - scrollOffset) * ENTRY_HEIGHT
            val homeBtnX = panelLeft + PANEL_WIDTH - SCROLL_BAR_WIDTH - HOME_BTN_WIDTH - 8
            val homeBtnY = ey + ENTRY_HEIGHT - HOME_BTN_HEIGHT - 3
            if (mouseY >= listTop && mouseY < listBottom && UiIconButtons.contains(mouseX, mouseY, homeBtnX, homeBtnY, HOME_BTN_WIDTH, HOME_BTN_HEIGHT)) {
                return listOf(
                    Text.literal("Send ${snapshot.pals[i].displayName} home"),
                    Text.literal("Returns this pal to its pasture anchor.")
                )
            }

            val rowRight = panelLeft + PANEL_WIDTH - SCROLL_BAR_WIDTH - 2
            if (mouseX >= panelLeft && mouseX < rowRight && mouseY >= ey && mouseY < ey + ENTRY_HEIGHT) {
                val pal = snapshot.pals[i]
                return buildList {
                    add(Text.literal("${pal.displayName} \u2022 ${pal.statusLabel()}"))
                    add(Text.literal("Species: ${speciesLabel(pal)} \u2022 Lv.${pal.level}"))
                    add(Text.literal("Tag: ${pal.tagTypeId?.uppercase() ?: "none"}"))
                    pal.boundPos?.let { add(Text.literal("Binding: ${formatPos(it)}")) }
                    pal.activeTargetPos?.let { add(Text.literal("Target: ${formatPos(it)}")) }
                    if (pal.hasCargo()) {
                        add(Text.literal("Cargo: ${pal.carriedItemCount} items across ${pal.carriedSlotCount} slots"))
                    }
                    if (pal.isManagedByCommandPost) {
                        add(Text.literal("Managed by linked Command Post"))
                    }
                    if (pal.filterSummary != "No filter") {
                        add(Text.literal("Filter: ${pal.filterSummary}"))
                    }
                    if (pal.augmentSummary.isNotBlank()) {
                        add(Text.literal("Augments: ${pal.augmentSummary}"))
                    }
                }
            }
        }

        return null
    }

    private fun statusColor(pal: PalSnapshot): Int = when {
        pal.isFainted -> TEXT_FAINTED
        pal.tagTypeId == null -> NO_TAG_COLOR
        pal.workerPhase() == WorkerPhase.WORKING -> ACCENT
        pal.workerPhase() == WorkerPhase.NAVIGATING -> BINDING_COLOR
        pal.workerPhase() == WorkerPhase.ARRIVING -> ITEMS_COLOR
        pal.workerPhase() == WorkerPhase.DEPOSITING -> 0xF59E0B.toInt()
        pal.isEcoMode -> 0x8AA3AD
        pal.cooldownTicksRemaining > 0 || pal.searchDelayTicksRemaining > 0 -> 0xD8C060.toInt()
        else -> TEXT_DIM
    }

    private fun drawStatusChip(context: DrawContext, pal: PalSnapshot, x: Int, y: Int) {
        val label = pal.statusLabel()
        val color = statusColor(pal)
        val chipWidth = textRenderer.getWidth(label) + 10
        context.fill(x, y, x + chipWidth, y + 10, withAlpha(color, 0x55))
        drawScaledText(
            context = context,
            text = Text.literal(label) as MutableText,
            x = x + 4,
            y = y + 1,
            scale = 0.65F,
            shadow = false,
            colour = color
        )
    }

    private fun compactSummary(pal: PalSnapshot): String {
        val parts = buildList {
            if (pal.filterSummary != "No filter") add(pal.filterSummary)
            if (pal.augmentSummary.isNotBlank()) add(pal.augmentSummary)
        }
        return truncate(parts.joinToString(" \u2022 "), 28)
    }

    private fun targetLine(pal: PalSnapshot): String = when {
        pal.activeTargetPos != null && pal.isActive() -> "Target ${formatPos(pal.activeTargetPos)}"
        pal.boundPos != null -> "Bind ${formatPos(pal.boundPos)}"
        pal.tagTypeId != null -> "No binding"
        else -> "No work assigned"
    }

    private fun cargoLine(pal: PalSnapshot): String {
        if (!pal.hasCargo()) return "No cargo"
        val summary = if (pal.carriedItemDescs.isNotEmpty()) pal.carriedItemDescs.first() else "${pal.carriedItemCount} items"
        return truncate(summary, 18)
    }

    private fun timingLine(pal: PalSnapshot): String {
        val parts = mutableListOf<String>()
        if (pal.cooldownTicksRemaining > 0) {
            parts += "Ready ${formatTicks(pal.cooldownTicksRemaining)}"
        } else if (pal.searchDelayTicksRemaining > 0) {
            parts += "Scan ${formatTicks(pal.searchDelayTicksRemaining)}"
        }
        if (pal.isEcoMode) parts += "Eco"
        if (pal.isManagedByCommandPost && parts.isEmpty()) parts += "Command Post"
        return truncate(parts.joinToString(" \u2022 "), 22)
    }

    private fun tagStack(pal: PalSnapshot): ItemStack? {
        val typeId = pal.tagTypeId ?: return null
        val tagType = TagType.fromId(typeId) ?: return null
        val item = TagRegistry.getItem(tagType) ?: return null
        return ItemStack(item)
    }

    private fun cargoStack(pal: PalSnapshot): ItemStack? {
        val itemId = pal.primaryCarriedItemId ?: return null
        val identifier = runCatching { Identifier.of(itemId) }.getOrNull() ?: return null
        val item = Registries.ITEM.get(identifier)
        if (item == Items.AIR) return null
        return ItemStack(item)
    }

    private fun speciesLabel(pal: PalSnapshot): String {
        val rawName = runCatching { Identifier.of(pal.species).path }.getOrElse { pal.species.substringAfterLast(':') }
        return rawName
            .split('_')
            .joinToString(" ") { part -> part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } }
    }

    private fun formatPos(pos: BlockPos): String = "${pos.x}, ${pos.y}, ${pos.z}"

    private fun formatTicks(ticks: Int): String {
        val seconds = ticks / 20.0
        return String.format(Locale.ROOT, "%.1fs", seconds)
    }

    private fun truncate(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        return value.take(maxChars - 1) + "\u2026"
    }

    private fun withAlpha(color: Int, alpha: Int): Int = ((alpha and 0xFF) shl 24) or (color and 0x00FFFFFF)
}

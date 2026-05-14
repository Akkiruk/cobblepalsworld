package com.cobblepalsworld.gui.pasture

import com.cobblepalsworld.gui.CobblePalsUiTheme
import com.cobblepalsworld.gui.UiGlyph
import com.cobblepalsworld.gui.UiIconButtons
import com.cobblepalsworld.networking.CobblePalsNetworking
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

class PastureManagerScreen(private var snapshot: PastureSnapshot) : Screen(Text.literal("Pasture Overview")) {

    companion object {
        private const val PANEL_WIDTH = 276
        private const val PANEL_HEIGHT = 170
        private const val HEADER_HEIGHT = 34
        private const val REFRESH_INTERVAL_TICKS = 20
    }

    private var panelLeft = 0
    private var panelTop = 0
    private var refreshTicks = 0

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
            CobblePalsNetworking.sendSnapshotRefresh(snapshot.pasturePos)
        }
    }

    fun appliesTo(pasturePos: BlockPos): Boolean = snapshot.pasturePos == pasturePos

    fun updateSnapshot(snapshot: PastureSnapshot) {
        this.snapshot = snapshot
        refreshTicks = 0
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, 0xA0000000.toInt())
        CobblePalsUiTheme.drawRootFrame(context, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, HEADER_HEIGHT)

        val managedCount = snapshot.pals.count { it.isManagedByCommandPost }
        val blockedCount = snapshot.pals.count { it.isBlocked() }
        val activeCount = snapshot.pals.count { it.isActive() }
        val faintedCount = snapshot.pals.count { it.isFainted }
        val standbyCount = snapshot.pals.count { it.isStandby() }
        val readyCount = snapshot.pals.count { it.isReady() }
        val taggedCount = snapshot.pals.count { it.tagTypeId != null }

        context.drawText(textRenderer, Text.literal("PASTURE OVERVIEW"), panelLeft + 12, panelTop + 8, CobblePalsUiTheme.HEADER_TEXT, false)
        context.drawText(textRenderer, Text.literal(fit("${snapshot.ownerName} • ${formatPos(snapshot.pasturePos)}", 218)), panelLeft + 12, panelTop + 20, CobblePalsUiTheme.SUBTITLE_TEXT, false)

        drawMetric(context, panelLeft + 14, panelTop + 50, "Tagged", taggedCount, CobblePalsUiTheme.ACCENT_WORK)
        drawMetric(context, panelLeft + 78, panelTop + 50, "Active", activeCount, CobblePalsUiTheme.ACCENT_CREW)
        drawMetric(context, panelLeft + 142, panelTop + 50, "Ready", readyCount, CobblePalsUiTheme.ACCENT_BUFFER)
        drawMetric(context, panelLeft + 206, panelTop + 50, "Max", snapshot.maxWorkers, CobblePalsUiTheme.TEXT_MUTED)

        CobblePalsUiTheme.drawPlainPanel(context, panelLeft + 14, panelTop + 84, 248, 64, if (blockedCount > 0 || faintedCount > 0) CobblePalsUiTheme.ACCENT_DANGER else CobblePalsUiTheme.ACCENT_CREW)
        context.drawText(textRenderer, Text.literal("Command Post"), panelLeft + 24, panelTop + 94, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("$managedCount managed • $standbyCount standby"), panelLeft + 24, panelTop + 108, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(if (blockedCount > 0) "$blockedCount blocked workers need review" else "No blocked workers"), panelLeft + 24, panelTop + 124, if (blockedCount > 0) CobblePalsUiTheme.ACCENT_DANGER else CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(if (faintedCount > 0) "$faintedCount fainted pals unavailable" else "No fainted pals"), panelLeft + 24, panelTop + 138, if (faintedCount > 0) CobblePalsUiTheme.ACCENT_DANGER else CobblePalsUiTheme.TEXT_FAINT, false)

        val closeLeft = panelLeft + PANEL_WIDTH - 24
        val closeTop = panelTop + 10
        val hovered = mouseX in closeLeft until closeLeft + 12 && mouseY in closeTop until closeTop + 12
        UiIconButtons.draw(context, closeLeft, closeTop, 12, 12, UiGlyph.Close, CobblePalsUiTheme.ACCENT_DANGER, hovered, true)

        hoveredTooltip(mouseX, mouseY)?.let { tooltip ->
            context.drawTooltip(textRenderer, tooltip, mouseX, mouseY)
        }
    }

    private fun drawMetric(context: DrawContext, left: Int, top: Int, label: String, value: Int, accent: Int) {
        context.fill(left, top, left + 56, top + 24, 0xFF0D141A.toInt())
        context.fill(left, top, left + 3, top + 24, accent)
        context.drawText(textRenderer, Text.literal(label), left + 7, top + 4, CobblePalsUiTheme.TEXT_FAINT, false)
        val valueText = Text.literal(value.toString())
        context.drawText(textRenderer, valueText, left + 49 - textRenderer.getWidth(valueText), top + 14, CobblePalsUiTheme.TEXT_PRIMARY, false)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val closeLeft = panelLeft + PANEL_WIDTH - 24
        val closeTop = panelTop + 10
        if (button == 0 && mouseX >= closeLeft && mouseX < closeLeft + 12 && mouseY >= closeTop && mouseY < closeTop + 12) {
            close()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun shouldPause(): Boolean = false

    private fun hoveredTooltip(mouseX: Int, mouseY: Int): List<Text>? {
        val closeLeft = panelLeft + PANEL_WIDTH - 24
        val closeTop = panelTop + 10
        if (UiIconButtons.contains(mouseX, mouseY, closeLeft, closeTop, 12, 12)) {
            return listOf(Text.literal("Close overview"))
        }
        return null
    }

    private fun formatPos(pos: BlockPos): String = "${pos.x}, ${pos.y}, ${pos.z}"

    private fun fit(value: String, maxWidth: Int): String {
        if (textRenderer.getWidth(value) <= maxWidth) return value
        var clipped = value
        while (clipped.isNotEmpty() && textRenderer.getWidth("$clipped...") > maxWidth) clipped = clipped.dropLast(1)
        return if (clipped.isEmpty()) "..." else "$clipped..."
    }
}
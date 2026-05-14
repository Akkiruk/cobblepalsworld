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
        private const val PANEL_WIDTH = 252
        private const val PANEL_HEIGHT = 176
        private const val HEADER_HEIGHT = 28
        private const val SUMMARY_PANEL_LEFT = 12
        private const val SUMMARY_PANEL_TOP = 44
        private const val SUMMARY_PANEL_WIDTH = 228
        private const val SUMMARY_PANEL_HEIGHT = 50
        private const val ALERT_PANEL_LEFT = 12
        private const val ALERT_PANEL_TOP = 100
        private const val ALERT_PANEL_WIDTH = 228
        private const val ALERT_PANEL_HEIGHT = 60
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
        CobblePalsUiTheme.drawPanel(context, panelLeft, panelTop, SUMMARY_PANEL_LEFT, SUMMARY_PANEL_TOP, SUMMARY_PANEL_WIDTH, SUMMARY_PANEL_HEIGHT, CobblePalsUiTheme.jobsPanel)
        CobblePalsUiTheme.drawPanel(context, panelLeft, panelTop, ALERT_PANEL_LEFT, ALERT_PANEL_TOP, ALERT_PANEL_WIDTH, ALERT_PANEL_HEIGHT, CobblePalsUiTheme.statusPanel)
        CobblePalsUiTheme.drawStripedFill(context, panelLeft, panelTop, SUMMARY_PANEL_LEFT + 5, SUMMARY_PANEL_TOP + 18, SUMMARY_PANEL_WIDTH - 10, SUMMARY_PANEL_HEIGHT - 24, 0x1E6CD1C9, 6)
        CobblePalsUiTheme.drawStripedFill(context, panelLeft, panelTop, ALERT_PANEL_LEFT + 5, ALERT_PANEL_TOP + 18, ALERT_PANEL_WIDTH - 10, ALERT_PANEL_HEIGHT - 24, 0x1EE3B16B, 6)

        val managedCount = snapshot.pals.count { it.isManagedByCommandPost }
        val blockedCount = snapshot.pals.count { it.isBlocked() }
        val activeCount = snapshot.pals.count { it.isActive() }
        val faintedCount = snapshot.pals.count { it.isFainted }
        val standbyCount = snapshot.pals.count { it.isStandby() }
        val readyCount = snapshot.pals.count { it.isReady() }
        val taggedCount = snapshot.pals.count { it.tagTypeId != null }

        context.drawText(textRenderer, Text.literal("PASTURE OVERVIEW"), panelLeft + 12, panelTop + 8, CobblePalsUiTheme.HEADER_TEXT, false)
        context.drawText(textRenderer, Text.literal("Crew edits live in the linked Command Post."), panelLeft + 12, panelTop + 20, CobblePalsUiTheme.SUBTITLE_TEXT, false)
        context.drawText(textRenderer, Text.literal("SUMMARY"), panelLeft + 16, panelTop + 48, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("LOCAL STATE"), panelLeft + 16, panelTop + 104, CobblePalsUiTheme.TEXT_PRIMARY, false)

        context.drawText(textRenderer, Text.literal("Owner: ${snapshot.ownerName}"), panelLeft + 18, panelTop + 64, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal("Pasture: ${formatPos(snapshot.pasturePos)}"), panelLeft + 18, panelTop + 76, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal("Tagged $taggedCount • Active $activeCount/$${snapshot.maxWorkers}".replace("$${snapshot.maxWorkers}", snapshot.maxWorkers.toString())), panelLeft + 124, panelTop + 64, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("Managed $managedCount • Ready $readyCount • Standby $standbyCount"), panelLeft + 124, panelTop + 76, CobblePalsUiTheme.TEXT_FAINT, false)

        context.drawText(textRenderer, Text.literal(if (managedCount > 0) "$managedCount pals are governed by a Command Post." else "No pals are currently governed by a Command Post."), panelLeft + 18, panelTop + 120, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(if (blockedCount > 0) "$blockedCount workers are blocked and need Command Post review." else "No blocked workers right now."), panelLeft + 18, panelTop + 132, if (blockedCount > 0) 0xFFE07B67.toInt() else CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(if (faintedCount > 0) "$faintedCount pals are fainted and unavailable." else "No fainted pals right now."), panelLeft + 18, panelTop + 144, if (faintedCount > 0) 0xFFE07B67.toInt() else CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal("Use this surface for presence, alerts, and refresh only."), panelLeft + 18, panelTop + 156, CobblePalsUiTheme.TEXT_FAINT, false)

        val closeLeft = panelLeft + PANEL_WIDTH - 22
        val closeTop = panelTop + 8
        UiIconButtons.draw(context, closeLeft, closeTop, 10, 10, UiGlyph.Close, 0xFFFF8A6B.toInt(), mouseX in closeLeft until closeLeft + 10 && mouseY in closeTop until closeTop + 10, true)

        hoveredTooltip(mouseX, mouseY)?.let { tooltip ->
            context.drawTooltip(textRenderer, tooltip, mouseX, mouseY)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val closeLeft = panelLeft + PANEL_WIDTH - 22
        val closeTop = panelTop + 8
        if (button == 0 && mouseX >= closeLeft && mouseX < closeLeft + 10 && mouseY >= closeTop && mouseY < closeTop + 10) {
            close()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun shouldPause(): Boolean = false

    private fun hoveredTooltip(mouseX: Int, mouseY: Int): List<Text>? {
        val closeLeft = panelLeft + PANEL_WIDTH - 22
        val closeTop = panelTop + 8
        if (UiIconButtons.contains(mouseX, mouseY, closeLeft, closeTop, 10, 10)) {
            return listOf(
                Text.literal("Close overview"),
                Text.literal("Return to the world without changing crew state.")
            )
        }

        if (mouseX in (panelLeft + SUMMARY_PANEL_LEFT) until (panelLeft + SUMMARY_PANEL_LEFT + SUMMARY_PANEL_WIDTH) && mouseY in (panelTop + SUMMARY_PANEL_TOP) until (panelTop + SUMMARY_PANEL_TOP + SUMMARY_PANEL_HEIGHT)) {
            return listOf(
                Text.literal("Pasture summary"),
                Text.literal("Shows presence, Command Post coverage, and worker capacity."),
                Text.literal("Deep crew management now belongs in the Command Post.")
            )
        }

        if (mouseX in (panelLeft + ALERT_PANEL_LEFT) until (panelLeft + ALERT_PANEL_LEFT + ALERT_PANEL_WIDTH) && mouseY in (panelTop + ALERT_PANEL_TOP) until (panelTop + ALERT_PANEL_TOP + ALERT_PANEL_HEIGHT)) {
            return listOf(
                Text.literal("Local state"),
                Text.literal("Focuses on quick blocked and fainted alerts."),
                Text.literal("Use it as a lightweight presence check, not a second admin console.")
            )
        }

        return null
    }

    private fun formatPos(pos: BlockPos): String = "${pos.x}, ${pos.y}, ${pos.z}"
}

package com.cobblepalsworld.gui.router

import com.cobblepalsworld.router.RouterBlockEntity
import com.cobblepalsworld.tag.TagItem
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text

class RouterScreen(
    handler: RouterScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<RouterScreenHandler>(handler, inventory, title) {

    private data class HoverTooltip(val id: String, val lines: List<Text>)

    companion object {
        private const val HEADER_HEIGHT = 22
        private const val JOB_PANEL_LEFT = 10
        private const val JOB_PANEL_TOP = 26
        private const val JOB_PANEL_WIDTH = 90
        private const val JOB_PANEL_HEIGHT = 84
        private const val STATUS_PANEL_LEFT = 108
        private const val STATUS_PANEL_TOP = 26
        private const val STATUS_PANEL_WIDTH = 112
        private const val STATUS_PANEL_HEIGHT = 62
        private const val BOOST_PANEL_LEFT = 108
        private const val BOOST_PANEL_TOP = 92
        private const val BOOST_PANEL_WIDTH = 112
        private const val BOOST_PANEL_HEIGHT = 34
        private const val HINT_BAR_TOP = 132
        private const val STORAGE_PANEL_LEFT = 10
        private const val STORAGE_PANEL_TOP = 146
        private const val STORAGE_PANEL_WIDTH = 210
        private const val STORAGE_PANEL_HEIGHT = 74
        private const val PLAYER_PANEL_LEFT = 10
        private const val PLAYER_PANEL_TOP = 224
        private const val PLAYER_PANEL_WIDTH = 210
        private const val PLAYER_PANEL_HEIGHT = 84

        private const val FRAME_OUTER = 0xFF090C10.toInt()
        private const val FRAME_MID = 0xFF111820.toInt()
        private const val FRAME_INNER = 0xFF151D26.toInt()
        private const val HEADER_BAR = 0xFF2F241C.toInt()
        private const val HEADER_GLOW = 0xFFC38A46.toInt()
        private const val HEADER_TEXT = 0xFFF6E7CF.toInt()
        private const val SUBTITLE_TEXT = 0xFFD4BC98.toInt()
        private const val JOB_PANEL_BODY = 0xFF13262C.toInt()
        private const val JOB_PANEL_HEADER = 0xFF1E4D51.toInt()
        private const val JOB_PANEL_ACCENT = 0xFF69D0C9.toInt()
        private const val STATUS_PANEL_BODY = 0xFF211B18.toInt()
        private const val STATUS_PANEL_HEADER = 0xFF523C26.toInt()
        private const val STATUS_PANEL_ACCENT = 0xFFE3B16B.toInt()
        private const val BOOST_PANEL_BODY = 0xFF1E1B22.toInt()
        private const val BOOST_PANEL_HEADER = 0xFF4E3755.toInt()
        private const val BOOST_PANEL_ACCENT = 0xFFC59BFF.toInt()
        private const val INVENTORY_PANEL_BODY = 0xFF171C22.toInt()
        private const val INVENTORY_PANEL_HEADER = 0xFF273340.toInt()
        private const val INVENTORY_PANEL_ACCENT = 0xFF89B6DE.toInt()
        private const val PANEL_BORDER = 0xFF0C1218.toInt()
        private const val PANEL_LINE = 0xFF05080B.toInt()
        private const val TEXT_PRIMARY = 0xFFEAF0F2.toInt()
        private const val TEXT_MUTED = 0xFFA9B6BD.toInt()
        private const val TEXT_FAINT = 0xFF7F929D.toInt()
        private const val JOB_SLOT_OUTER = 0xFF0A1619.toInt()
        private const val JOB_SLOT_MID = 0xFF17353A.toInt()
        private const val JOB_SLOT_INNER = 0xFF0F2228.toInt()
        private const val JOB_SLOT_GLOW = 0xFF5ECBC5.toInt()
        private const val BOOST_SLOT_OUTER = 0xFF120D14.toInt()
        private const val BOOST_SLOT_MID = 0xFF30213B.toInt()
        private const val BOOST_SLOT_INNER = 0xFF1E1524.toInt()
        private const val BOOST_SLOT_GLOW = 0xFFC59BFF.toInt()
        private const val INVENTORY_SLOT_OUTER = 0xFF0D1115.toInt()
        private const val INVENTORY_SLOT_MID = 0xFF232D37.toInt()
        private const val INVENTORY_SLOT_INNER = 0xFF171E26.toInt()
        private const val INVENTORY_SLOT_GLOW = 0xFF6B7D8B.toInt()

        private const val STATUS_CARD_WIDTH = 44
        private const val STATUS_CARD_HEIGHT = 16
        private const val TOOLTIP_DELAY_MS = 3000L
    }

    private var hoveredTooltipId: String? = null
    private var hoveredTooltipSinceMs: Long = 0L

    override fun init() {
        backgroundWidth = RouterScreenHandler.BACKGROUND_WIDTH
        backgroundHeight = RouterScreenHandler.BACKGROUND_HEIGHT
        super.init()
        titleX = 12
        titleY = 8
        playerInventoryTitleX = RouterScreenHandler.PLAYER_INV_X
        playerInventoryTitleY = 228
    }

    private fun drawPanel(
        context: DrawContext,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        bodyColor: Int,
        borderColor: Int,
        headerColor: Int,
        accentColor: Int
    ) {
        context.fill(x + left - 2, y + top - 2, x + left + width + 2, y + top + height + 2, PANEL_LINE)
        context.fill(x + left - 1, y + top - 1, x + left + width + 1, y + top + height + 1, borderColor)
        context.fill(x + left, y + top, x + left + width, y + top + height, bodyColor)
        context.fill(x + left, y + top, x + left + width, y + top + 12, headerColor)
        context.fill(x + left, y + top + 12, x + left + width, y + top + 13, accentColor)
        context.fill(x + left + 4, y + top + 4, x + left + 18, y + top + 5, accentColor)
        context.fill(x + left + width - 18, y + top + 4, x + left + width - 4, y + top + 5, accentColor)
    }

    private fun drawSlotWell(context: DrawContext, left: Int, top: Int, outer: Int, mid: Int, inner: Int, accent: Int) {
        context.fill(x + left - 1, y + top - 1, x + left + 17, y + top + 17, outer)
        context.fill(x + left, y + top, x + left + 16, y + top + 16, mid)
        context.fill(x + left + 1, y + top + 1, x + left + 15, y + top + 15, inner)
        context.fill(x + left + 2, y + top + 2, x + left + 14, y + top + 3, accent)
        context.fill(x + left + 2, y + top + 2, x + left + 3, y + top + 14, accent)
    }

    private fun drawStatusCard(
        context: DrawContext,
        left: Int,
        top: Int,
        width: Int,
        label: String,
        value: Int,
        accent: Int,
        valueColor: Int
    ) {
        context.fill(x + left - 1, y + top - 1, x + left + width + 1, y + top + 17, PANEL_LINE)
        context.fill(x + left, y + top, x + left + width, y + top + 16, 0xFF171114.toInt())
        context.fill(x + left, y + top, x + left + 3, y + top + 16, accent)
        val labelText = Text.literal(label)
        val valueText = Text.literal(value.toString())
        context.drawText(textRenderer, labelText, left + 8, top + 4, TEXT_MUTED, false)
        context.drawText(textRenderer, valueText, left + width - textRenderer.getWidth(valueText) - 6, top + 4, valueColor, false)
    }

    private fun drawStripedFill(context: DrawContext, left: Int, top: Int, width: Int, height: Int, color: Int, step: Int) {
        var line = 0
        while (line < height) {
            context.fill(x + left, y + top + line, x + left + width, y + top + line + 1, color)
            line += step
        }
    }

    private fun drawHeaderChip(
        context: DrawContext,
        text: Text,
        left: Int,
        top: Int,
        accent: Int,
        body: Int,
        textColor: Int
    ) {
        val width = textRenderer.getWidth(text) + 16
        context.fill(x + left, y + top, x + left + width, y + top + 13, body)
        context.fill(x + left, y + top, x + left + 3, y + top + 13, accent)
        context.drawText(textRenderer, text, left + 7, top + 3, textColor, false)
    }

    private fun contains(localMouseX: Int, localMouseY: Int, left: Int, top: Int, width: Int, height: Int): Boolean {
        return localMouseX in left until (left + width) && localMouseY in top until (top + height)
    }

    private fun hoveredTooltip(localMouseX: Int, localMouseY: Int, jobCardCount: Int, idleCount: Int, queuedCount: Int, controllerNativeCount: Int, hint: String): HoverTooltip? {
        val linkChipText = if (handler.linked) Text.literal("LINKED") else Text.literal("UNLINKED")
        val chipLeft = backgroundWidth - textRenderer.getWidth(linkChipText) - 24
        val chipWidth = textRenderer.getWidth(linkChipText) + 16

        return when {
            contains(localMouseX, localMouseY, chipLeft, 7, chipWidth, 13) -> HoverTooltip(
                id = "link-chip",
                lines = listOf(
                    Text.literal(if (handler.linked) "Pasture link active" else "Pasture link missing"),
                    Text.literal(if (handler.linked) "Sneak-use the block to relink it." else "Sneak-use near a pasture to link this post.")
                )
            )

            contains(localMouseX, localMouseY, JOB_PANEL_LEFT, JOB_PANEL_TOP, JOB_PANEL_WIDTH, JOB_PANEL_HEIGHT) -> HoverTooltip(
                id = "task-matrix",
                lines = listOf(
                    Text.literal("Task Matrix"),
                    Text.literal("Install tag cards here to create active job slots."),
                    Text.literal("Current cards: $jobCardCount")
                )
            )

            contains(localMouseX, localMouseY, STATUS_PANEL_LEFT, STATUS_PANEL_TOP, STATUS_PANEL_WIDTH, STATUS_PANEL_HEIGHT) -> HoverTooltip(
                id = "pasture-relay",
                lines = listOf(
                    Text.literal("Pasture Relay"),
                    Text.literal(if (handler.linked) "This post is linked to a pasture." else if (controllerNativeCount > 0) "Worker jobs need a pasture link, but controller-native jobs still run." else "This post is not linked yet."),
                    Text.literal("Hover the counters for details.")
                )
            )

            contains(localMouseX, localMouseY, BOOST_PANEL_LEFT, BOOST_PANEL_TOP, BOOST_PANEL_WIDTH, BOOST_PANEL_HEIGHT) -> HoverTooltip(
                id = "augment-rack",
                lines = listOf(
                    Text.literal("Augment Rack"),
                    Text.literal("Augments in these slots affect every installed tag.")
                )
            )

            contains(localMouseX, localMouseY, 116, 56, STATUS_CARD_WIDTH, STATUS_CARD_HEIGHT) -> HoverTooltip(
                id = "roster-card",
                lines = listOf(
                    Text.literal("Roster"),
                    Text.literal("Tethered pals in the linked pasture: ${handler.rosterCount}")
                )
            )

            contains(localMouseX, localMouseY, 166, 56, STATUS_CARD_WIDTH, STATUS_CARD_HEIGHT) -> HoverTooltip(
                id = "jobs-card",
                lines = listOf(
                    Text.literal("Jobs"),
                    Text.literal("Installed tag cards: $jobCardCount"),
                    Text.literal(if (queuedCount > 0) "$queuedCount worker-managed job slots are waiting for free pals." else "Every installed job is runnable right now.")
                )
            )

            contains(localMouseX, localMouseY, 116, 76, STATUS_CARD_WIDTH, STATUS_CARD_HEIGHT) -> HoverTooltip(
                id = "busy-card",
                lines = listOf(
                    Text.literal("Busy"),
                    Text.literal("Runnable job slots currently doing work: ${handler.activeCount}")
                )
            )

            contains(localMouseX, localMouseY, 166, 76, STATUS_CARD_WIDTH, STATUS_CARD_HEIGHT) -> HoverTooltip(
                id = "ready-card",
                lines = listOf(
                    Text.literal("Ready"),
                    Text.literal("Runnable job slots waiting for work: $idleCount")
                )
            )

            contains(localMouseX, localMouseY, 10, HINT_BAR_TOP, backgroundWidth - 20, 12) -> HoverTooltip(
                id = "hint-bar",
                lines = listOf(
                    Text.literal("Status hint"),
                    Text.literal(hint)
                )
            )

            contains(localMouseX, localMouseY, STORAGE_PANEL_LEFT, STORAGE_PANEL_TOP, STORAGE_PANEL_WIDTH, STORAGE_PANEL_HEIGHT) -> HoverTooltip(
                id = "buffer-storage",
                lines = listOf(
                    Text.literal("Buffer Storage"),
                    Text.literal("This built-in buffer is the default inventory for unbound module flows."),
                    Text.literal("Modules pull from it and return items here when that behavior fits the tag.")
                )
            )

            contains(localMouseX, localMouseY, PLAYER_PANEL_LEFT, PLAYER_PANEL_TOP, PLAYER_PANEL_WIDTH, PLAYER_PANEL_HEIGHT) -> HoverTooltip(
                id = "player-inventory",
                lines = listOf(Text.literal("Player inventory"))
            )

            else -> null
        }
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        context.fill(x - 2, y - 2, x + backgroundWidth + 2, y + backgroundHeight + 2, FRAME_OUTER)
        context.fill(x - 1, y - 1, x + backgroundWidth + 1, y + backgroundHeight + 1, FRAME_MID)
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, FRAME_INNER)
        context.fill(x + 4, y + 4, x + backgroundWidth - 4, y + backgroundHeight - 4, 0xFF11171D.toInt())
        context.fill(x + 4, y + 4, x + backgroundWidth - 4, y + 4 + HEADER_HEIGHT, HEADER_BAR)
        context.fill(x + 4, y + HEADER_HEIGHT + 4, x + backgroundWidth - 4, y + HEADER_HEIGHT + 5, HEADER_GLOW)
        context.fill(x + 10, y + 14, x + 72, y + 15, HEADER_GLOW)
        context.fill(x + backgroundWidth - 72, y + 14, x + backgroundWidth - 10, y + 15, HEADER_GLOW)

        drawPanel(context, JOB_PANEL_LEFT, JOB_PANEL_TOP, JOB_PANEL_WIDTH, JOB_PANEL_HEIGHT, JOB_PANEL_BODY, PANEL_BORDER, JOB_PANEL_HEADER, JOB_PANEL_ACCENT)
        drawPanel(context, STATUS_PANEL_LEFT, STATUS_PANEL_TOP, STATUS_PANEL_WIDTH, STATUS_PANEL_HEIGHT, STATUS_PANEL_BODY, PANEL_BORDER, STATUS_PANEL_HEADER, STATUS_PANEL_ACCENT)
        drawPanel(context, BOOST_PANEL_LEFT, BOOST_PANEL_TOP, BOOST_PANEL_WIDTH, BOOST_PANEL_HEIGHT, BOOST_PANEL_BODY, PANEL_BORDER, BOOST_PANEL_HEADER, BOOST_PANEL_ACCENT)
        drawPanel(context, STORAGE_PANEL_LEFT, STORAGE_PANEL_TOP, STORAGE_PANEL_WIDTH, STORAGE_PANEL_HEIGHT, INVENTORY_PANEL_BODY, PANEL_BORDER, INVENTORY_PANEL_HEADER, INVENTORY_PANEL_ACCENT)
        drawPanel(context, PLAYER_PANEL_LEFT, PLAYER_PANEL_TOP, PLAYER_PANEL_WIDTH, PLAYER_PANEL_HEIGHT, INVENTORY_PANEL_BODY, PANEL_BORDER, INVENTORY_PANEL_HEADER, INVENTORY_PANEL_ACCENT)

        context.fill(x + 10, y + HINT_BAR_TOP, x + backgroundWidth - 10, y + HINT_BAR_TOP + 12, 0xFF131D24.toInt())
        context.fill(x + 10, y + HINT_BAR_TOP, x + 32, y + HINT_BAR_TOP + 12, JOB_PANEL_HEADER)
        context.fill(x + 10, y + HINT_BAR_TOP + 11, x + backgroundWidth - 10, y + HINT_BAR_TOP + 12, HEADER_GLOW)

        drawStripedFill(context, JOB_PANEL_LEFT + 5, JOB_PANEL_TOP + 18, JOB_PANEL_WIDTH - 10, JOB_PANEL_HEIGHT - 24, 0x1E6CD1C9, 6)
        drawStripedFill(context, STATUS_PANEL_LEFT + 5, STATUS_PANEL_TOP + 18, STATUS_PANEL_WIDTH - 10, STATUS_PANEL_HEIGHT - 24, 0x1EE3B16B, 6)
        drawStripedFill(context, STORAGE_PANEL_LEFT + 5, STORAGE_PANEL_TOP + 18, STORAGE_PANEL_WIDTH - 10, STORAGE_PANEL_HEIGHT - 24, 0x124B5A69, 6)
        drawStripedFill(context, PLAYER_PANEL_LEFT + 5, PLAYER_PANEL_TOP + 18, PLAYER_PANEL_WIDTH - 10, PLAYER_PANEL_HEIGHT - 24, 0x124B5A69, 6)

        for (row in 0 until RouterScreenHandler.MODULE_ROWS) {
            for (col in 0 until RouterScreenHandler.MODULE_COLUMNS) {
                drawSlotWell(context, RouterScreenHandler.MODULE_START_X + col * 18, RouterScreenHandler.MODULE_START_Y + row * 18, JOB_SLOT_OUTER, JOB_SLOT_MID, JOB_SLOT_INNER, JOB_SLOT_GLOW)
            }
        }

        for (index in 0 until RouterBlockEntity.UPGRADE_SLOT_COUNT) {
            drawSlotWell(context, RouterScreenHandler.BOOST_START_X + index * 18, RouterScreenHandler.BOOST_START_Y, BOOST_SLOT_OUTER, BOOST_SLOT_MID, BOOST_SLOT_INNER, BOOST_SLOT_GLOW)
        }

        for (row in 0 until RouterScreenHandler.STORAGE_ROWS) {
            for (col in 0 until RouterScreenHandler.STORAGE_COLUMNS) {
                drawSlotWell(context, RouterScreenHandler.STORAGE_START_X + col * 18, RouterScreenHandler.STORAGE_START_Y + row * 18, INVENTORY_SLOT_OUTER, INVENTORY_SLOT_MID, INVENTORY_SLOT_INNER, INVENTORY_SLOT_GLOW)
            }
        }

        for (row in 0..2) {
            for (col in 0..8) {
                drawSlotWell(context, RouterScreenHandler.PLAYER_INV_X + col * 18, RouterScreenHandler.PLAYER_INV_Y + row * 18, INVENTORY_SLOT_OUTER, INVENTORY_SLOT_MID, INVENTORY_SLOT_INNER, INVENTORY_SLOT_GLOW)
            }
        }

        for (col in 0..8) {
            drawSlotWell(context, RouterScreenHandler.PLAYER_INV_X + col * 18, RouterScreenHandler.PLAYER_INV_Y + 58, INVENTORY_SLOT_OUTER, INVENTORY_SLOT_MID, INVENTORY_SLOT_INNER, INVENTORY_SLOT_GLOW)
        }
    }

    override fun drawForeground(context: DrawContext, mouseX: Int, mouseY: Int) {
        val moduleSlots = handler.slots.take(RouterBlockEntity.MODULE_SLOT_COUNT)
        val jobCardCount = moduleSlots.count { it.hasStack() }
        val controllerNativeCount = moduleSlots.count { slot ->
            val item = slot.stack.item as? TagItem
            item?.tagType?.controllerNative == true
        }
        val workerManagedCount = (jobCardCount - controllerNativeCount).coerceAtLeast(0)
        val idleCount = (handler.assignedCount - handler.activeCount).coerceAtLeast(0)
        val queuedCount = (jobCardCount - handler.assignedCount).coerceAtLeast(0)
        val hint = when {
            jobCardCount <= 0 -> "Insert tag cards in the jobs grid."
            !handler.linked && workerManagedCount > 0 && controllerNativeCount > 0 -> "Link a pasture to enable $workerManagedCount worker jobs. $controllerNativeCount controller jobs still run."
            !handler.linked && workerManagedCount > 0 -> "Sneak-use near a pasture to enable worker-managed jobs."
            !handler.linked -> "Controller-native jobs can run without a pasture link."
            handler.rosterCount <= 0 && workerManagedCount > 0 -> "The linked pasture has no active pals for worker-managed jobs."
            queuedCount > 0 -> "$queuedCount worker jobs are waiting for free pals."
            handler.activeCount <= 0 -> "Runnable job slots are ready and waiting for work."
            else -> "Sneak-use the block to relink it."
        }

        val linkChipText = if (handler.linked) Text.literal("LINKED") else Text.literal("UNLINKED")
        val chipTextColor = if (handler.linked) 0xFFD4FBDD.toInt() else 0xFFF6D1CE.toInt()
        val chipAccent = if (handler.linked) 0xFF2DBA62.toInt() else 0xFFD36D5C.toInt()
        val chipBody = if (handler.linked) 0xFF183126.toInt() else 0xFF341F1A.toInt()
        val chipLeft = backgroundWidth - textRenderer.getWidth(linkChipText) - 24

        context.drawText(textRenderer, title, titleX, titleY, HEADER_TEXT, false)
        drawHeaderChip(context, linkChipText, chipLeft, 7, chipAccent, chipBody, chipTextColor)

        context.drawText(textRenderer, Text.literal("TASK MATRIX"), 14, 30, TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("PASTURE RELAY"), 112, 30, TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("AUGMENT RACK"), 112, 96, TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("TIP"), 16, 135, HEADER_TEXT, false)
        context.drawText(textRenderer, Text.literal("BUFFER STORAGE"), 14, 150, TEXT_PRIMARY, false)

        drawStatusCard(context, 116, 56, 44, "Roster", handler.rosterCount, 0xFF4FC6C5.toInt(), 0xFFD9F3F1.toInt())
        drawStatusCard(context, 166, 56, 44, "Jobs", jobCardCount, 0xFFE3B16B.toInt(), 0xFFF4E1BA.toInt())
        drawStatusCard(context, 116, 76, 44, "Busy", handler.activeCount, 0xFF5FAEE8.toInt(), 0xFFD4EBFF.toInt())
        drawStatusCard(context, 166, 76, 44, "Ready", idleCount, 0xFF9AA7B3.toInt(), 0xFFE2E8EE.toInt())

        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, TEXT_MUTED, false)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)

        val moduleSlots = handler.slots.take(RouterBlockEntity.MODULE_SLOT_COUNT)
        val jobCardCount = moduleSlots.count { it.hasStack() }
        val controllerNativeCount = moduleSlots.count { slot ->
            val item = slot.stack.item as? TagItem
            item?.tagType?.controllerNative == true
        }
        val workerManagedCount = (jobCardCount - controllerNativeCount).coerceAtLeast(0)
        val idleCount = (handler.assignedCount - handler.activeCount).coerceAtLeast(0)
        val queuedCount = (jobCardCount - handler.assignedCount).coerceAtLeast(0)
        val hint = when {
            jobCardCount <= 0 -> "Insert tag cards in the jobs grid."
            !handler.linked && workerManagedCount > 0 && controllerNativeCount > 0 -> "Link a pasture to enable $workerManagedCount worker jobs. $controllerNativeCount controller jobs still run."
            !handler.linked && workerManagedCount > 0 -> "Sneak-use near a pasture to enable worker-managed jobs."
            !handler.linked -> "Controller-native jobs can run without a pasture link."
            handler.rosterCount <= 0 && workerManagedCount > 0 -> "The linked pasture has no active pals for worker-managed jobs."
            queuedCount > 0 -> "$queuedCount worker jobs are waiting for free pals."
            handler.activeCount <= 0 -> "Runnable job slots are ready and waiting for work."
            else -> "Sneak-use the block to relink it."
        }
        val hoveredTooltip = hoveredTooltip(mouseX - x, mouseY - y, jobCardCount, idleCount, queuedCount, controllerNativeCount, hint)
        val now = System.currentTimeMillis()

        if (hoveredTooltip?.id != hoveredTooltipId) {
            hoveredTooltipId = hoveredTooltip?.id
            hoveredTooltipSinceMs = if (hoveredTooltip == null) 0L else now
        }

        if (hoveredTooltip != null && now - hoveredTooltipSinceMs >= TOOLTIP_DELAY_MS) {
            context.drawTooltip(textRenderer, hoveredTooltip.lines, mouseX, mouseY)
        }
    }
}
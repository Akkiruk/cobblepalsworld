package com.cobblepalsworld.gui.filter

import com.cobblepalsworld.gui.CobblePalsUiTheme
import com.cobblepalsworld.gui.UiGlyph
import com.cobblepalsworld.gui.UiIconButtons
import com.cobblepalsworld.tag.TagTypePresentation
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text
import kotlin.math.roundToInt

class TagFilterScreen(
    handler: TagFilterScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<TagFilterScreenHandler>(handler, inventory, title) {

    private val screenTitle = Text.literal("ROLE POLICY")

    private data class IconActionButton(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val actionId: Int,
        val glyph: UiGlyph,
        val accent: Int,
        val active: Boolean,
        val tooltip: List<Text>
    )

    companion object {
        private const val BACKGROUND_WIDTH = 228
        private const val BACKGROUND_HEIGHT = 246
        private const val HEADER_HEIGHT = 30

        private const val FILTER_PANEL_LEFT = 10
        private const val FILTER_PANEL_TOP = 44
        private const val FILTER_PANEL_WIDTH = 98
        private const val FILTER_PANEL_HEIGHT = 92

        private const val SETTINGS_PANEL_LEFT = 116
        private const val SETTINGS_PANEL_TOP = 44
        private const val SETTINGS_PANEL_WIDTH = 102
        private const val SETTINGS_PANEL_HEIGHT = 92

        private const val SUMMARY_PANEL_LEFT = 10
        private const val SUMMARY_PANEL_TOP = 140
        private const val SUMMARY_PANEL_WIDTH = 208
        private const val SUMMARY_PANEL_HEIGHT = 34

        private const val PLAYER_PANEL_LEFT = 10
        private const val PLAYER_PANEL_TOP = 178
        private const val PLAYER_PANEL_WIDTH = 208
        private const val PLAYER_PANEL_HEIGHT = 58

        private const val FILTER_SLOT_X = 18
        private const val FILTER_SLOT_Y = 60
        private const val PLAYER_SLOT_X = 16
        private const val PLAYER_SLOT_Y = 188

        private const val ROW_LABEL_X = 142
        private const val ROW_VALUE_RIGHT = 212
        private const val ACTION_COLUMN_X = 122
        private const val ACTION_SIZE = 10
    }

    override fun init() {
        backgroundWidth = BACKGROUND_WIDTH
        backgroundHeight = BACKGROUND_HEIGHT
        super.init()
        titleX = 12
        titleY = 8
        playerInventoryTitleX = PLAYER_PANEL_LEFT + 6
        playerInventoryTitleY = PLAYER_PANEL_TOP + 4
        applySlotLayout()
    }

    private fun applySlotLayout() {
        for (row in 0..2) {
            for (col in 0..2) {
                val slotIndex = row * 3 + col
                setSlotPosition(slotIndex, FILTER_SLOT_X + col * 18, FILTER_SLOT_Y + row * 18)
            }
        }

        val playerStart = 9
        for (row in 0..2) {
            for (col in 0..8) {
                val slotIndex = playerStart + row * 9 + col
                setSlotPosition(slotIndex, PLAYER_SLOT_X + col * 18, PLAYER_SLOT_Y + row * 18)
            }
        }

        val hotbarStart = playerStart + 27
        for (col in 0..8) {
            setSlotPosition(hotbarStart + col, PLAYER_SLOT_X + col * 18, PLAYER_SLOT_Y + 58)
        }
    }

    private fun setSlotPosition(slotIndex: Int, slotX: Int, slotY: Int) {
        val slot = handler.slots.getOrNull(slotIndex) ?: return
        slot.x = slotX
        slot.y = slotY
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        CobblePalsUiTheme.drawRootFrame(context, x, y, backgroundWidth, backgroundHeight, HEADER_HEIGHT)

        CobblePalsUiTheme.drawPanel(context, x, y, FILTER_PANEL_LEFT, FILTER_PANEL_TOP, FILTER_PANEL_WIDTH, FILTER_PANEL_HEIGHT, CobblePalsUiTheme.jobsPanel)
        CobblePalsUiTheme.drawPanel(context, x, y, SETTINGS_PANEL_LEFT, SETTINGS_PANEL_TOP, SETTINGS_PANEL_WIDTH, SETTINGS_PANEL_HEIGHT, CobblePalsUiTheme.statusPanel)
        CobblePalsUiTheme.drawPanel(context, x, y, SUMMARY_PANEL_LEFT, SUMMARY_PANEL_TOP, SUMMARY_PANEL_WIDTH, SUMMARY_PANEL_HEIGHT, CobblePalsUiTheme.augmentPanel)
        CobblePalsUiTheme.drawPanel(context, x, y, PLAYER_PANEL_LEFT, PLAYER_PANEL_TOP, PLAYER_PANEL_WIDTH, PLAYER_PANEL_HEIGHT, CobblePalsUiTheme.inventoryPanel)

        CobblePalsUiTheme.drawStripedFill(context, x, y, FILTER_PANEL_LEFT + 5, FILTER_PANEL_TOP + 18, FILTER_PANEL_WIDTH - 10, FILTER_PANEL_HEIGHT - 24, 0x1E6CD1C9, 6)
        CobblePalsUiTheme.drawStripedFill(context, x, y, SETTINGS_PANEL_LEFT + 5, SETTINGS_PANEL_TOP + 18, SETTINGS_PANEL_WIDTH - 10, SETTINGS_PANEL_HEIGHT - 24, 0x1EE3B16B, 6)
        CobblePalsUiTheme.drawStripedFill(context, x, y, SUMMARY_PANEL_LEFT + 5, SUMMARY_PANEL_TOP + 18, SUMMARY_PANEL_WIDTH - 10, SUMMARY_PANEL_HEIGHT - 24, 0x12C59BFF, 6)
        CobblePalsUiTheme.drawStripedFill(context, x, y, PLAYER_PANEL_LEFT + 5, PLAYER_PANEL_TOP + 18, PLAYER_PANEL_WIDTH - 10, PLAYER_PANEL_HEIGHT - 24, 0x124B5A69, 6)

        for (row in 0..2) {
            for (col in 0..2) {
                CobblePalsUiTheme.drawSlotWell(context, x, y, FILTER_SLOT_X + col * 18, FILTER_SLOT_Y + row * 18, CobblePalsUiTheme.jobsSlot)
            }
        }

        for (row in 0..2) {
            for (col in 0..8) {
                CobblePalsUiTheme.drawSlotWell(context, x, y, PLAYER_SLOT_X + col * 18, PLAYER_SLOT_Y + row * 18, CobblePalsUiTheme.inventorySlot)
            }
        }
        for (col in 0..8) {
            CobblePalsUiTheme.drawSlotWell(context, x, y, PLAYER_SLOT_X + col * 18, PLAYER_SLOT_Y + 58, CobblePalsUiTheme.inventorySlot)
        }
    }

    override fun drawForeground(context: DrawContext, mouseX: Int, mouseY: Int) {
        val tagType = handler.tagType
        val roleLabel = tagType?.let { TagTypePresentation.roleLabel(it) } ?: "Role Policy"
        val familyLabel = tagType?.let { TagTypePresentation.familyOf(it).label } ?: "General"
        val chipText = if (handler.usesFilter) Text.literal("FILTERED") else Text.literal("NO FILTER")
        val chipStyle = if (handler.usesFilter) CobblePalsUiTheme.linkedStateChip else CobblePalsUiTheme.unlinkedStateChip
        val chipLeft = backgroundWidth - textRenderer.getWidth(chipText) - 22

        context.drawText(textRenderer, screenTitle, titleX, titleY, CobblePalsUiTheme.HEADER_TEXT, false)
        context.drawText(textRenderer, Text.literal("$roleLabel • $familyLabel"), 12, 20, CobblePalsUiTheme.SUBTITLE_TEXT, false)
        CobblePalsUiTheme.drawHeaderChip(context, textRenderer, x, y, chipText, chipLeft, 7, chipStyle)

        context.drawText(textRenderer, Text.literal("FILTER GRID"), 14, 48, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("BEHAVIOR"), 120, 48, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("SUMMARY"), 14, 144, CobblePalsUiTheme.TEXT_PRIMARY, false)

        if (handler.usesFilter) {
            context.drawText(textRenderer, Text.literal("${handler.filterItemCount} item slots populated"), 18, 118, CobblePalsUiTheme.TEXT_MUTED, false)
        } else {
            context.drawText(textRenderer, Text.literal("This role ignores item filters."), 18, 88, CobblePalsUiTheme.TEXT_MUTED, false)
            context.drawText(textRenderer, Text.literal("Use the policy controls only."), 18, 100, CobblePalsUiTheme.TEXT_FAINT, false)
        }

        var rowY = 62
        if (handler.usesFilter) {
            drawSettingRow(context, rowY, "Mode", if (handler.isWhitelist) "Allow" else "Block", if (handler.isWhitelist) 0xFF50C864.toInt() else 0xFFC85050.toInt())
            rowY += 14
            drawSettingRow(context, rowY, "NBT", if (handler.isMatchNbt) "Exact" else "Loose", if (handler.isMatchNbt) 0xFF50C864.toInt() else 0xFF909098.toInt())
            rowY += 14
            drawSettingRow(context, rowY, "Match", compactValue(handler.matchMode.name), 0xFFA0A0C8.toInt())
            rowY += 14
        }
        drawSettingRow(context, rowY, "Signal", compactValue(handler.redstoneMode.id), 0xFFC8B43C.toInt())

        if (tagType?.supportsTargetList == true) {
            rowY += 14
            drawSettingRow(context, rowY, "Target", compactValue(handler.targetStrategy.id), 0xFF8CD2FF.toInt())
            rowY += 14
            drawSettingRow(context, rowY, "Run", if (handler.terminateAfterSuccess) "One pass" else "Loop", if (handler.terminateAfterSuccess) 0xFFC8B43C.toInt() else 0xFF909098.toInt())
            rowY += 14
            drawSettingRow(context, rowY, "Reg", handler.regulatorAmount.toString(), 0xFF50C864.toInt())
        }

        iconButtons().forEach { button ->
            val hovered = UiIconButtons.contains(mouseX - x, mouseY - y, button.left, button.top, button.width, button.height)
            UiIconButtons.draw(context, button.left, button.top, button.width, button.height, button.glyph, button.accent, hovered, button.active)
        }

        context.drawText(textRenderer, Text.literal(summaryLineOne()), 18, 154, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(summaryLineTwo()), 18, 165, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(summaryLineThree()), 110, 154, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(editHint()), 110, 165, CobblePalsUiTheme.TEXT_FAINT, false)

        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, CobblePalsUiTheme.TEXT_MUTED, false)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val localMouseX = (mouseX - x).roundToInt()
            val localMouseY = (mouseY - y).roundToInt()
            for (iconButton in iconButtons()) {
                if (UiIconButtons.contains(localMouseX, localMouseY, iconButton.left, iconButton.top, iconButton.width, iconButton.height)) {
                    client?.interactionManager?.clickButton(handler.syncId, iconButton.actionId)
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)
        hoveredTooltip(mouseX - x, mouseY - y)?.let { tooltip ->
            context.drawTooltip(textRenderer, tooltip, mouseX, mouseY)
        }
    }

    private fun drawSettingRow(context: DrawContext, y: Int, label: String, value: String, color: Int) {
        val valueText = Text.literal(value)
        context.drawText(textRenderer, Text.literal(label), ROW_LABEL_X, y, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, valueText, ROW_VALUE_RIGHT - textRenderer.getWidth(valueText), y, color, false)
    }

    private fun iconButtons(): List<IconActionButton> {
        val buttons = mutableListOf<IconActionButton>()
        var buttonY = 60
        if (handler.usesFilter) {
            buttons += IconActionButton(
                left = ACTION_COLUMN_X,
                top = buttonY,
                width = ACTION_SIZE,
                height = ACTION_SIZE,
                actionId = 0,
                glyph = if (handler.isWhitelist) UiGlyph.Check else UiGlyph.Ban,
                accent = if (handler.isWhitelist) 0xFF50C864.toInt() else 0xFFC85050.toInt(),
                active = handler.isWhitelist,
                tooltip = listOf(
                    Text.literal("Filter mode"),
                    Text.literal(if (handler.isWhitelist) "Whitelist: only matching items pass." else "Blacklist: matching items are blocked."),
                    Text.literal("This changes the role's item rule, not the worker itself.")
                )
            )
            buttonY += 14
            buttons += IconActionButton(
                left = ACTION_COLUMN_X,
                top = buttonY,
                width = ACTION_SIZE,
                height = ACTION_SIZE,
                actionId = 1,
                glyph = UiGlyph.Data,
                accent = if (handler.isMatchNbt) 0xFF50C864.toInt() else 0xFF909098.toInt(),
                active = handler.isMatchNbt,
                tooltip = listOf(
                    Text.literal("NBT matching"),
                    Text.literal(if (handler.isMatchNbt) "Require exact item data." else "Compare items loosely."),
                    Text.literal("Use exact mode when item metadata matters.")
                )
            )
            buttonY += 14
            buttons += IconActionButton(
                left = ACTION_COLUMN_X,
                top = buttonY,
                width = ACTION_SIZE,
                height = ACTION_SIZE,
                actionId = 2,
                glyph = UiGlyph.Filter,
                accent = 0xFFA0A0C8.toInt(),
                active = true,
                tooltip = listOf(
                    Text.literal("Match rule"),
                    Text.literal("Current: ${humanValue(handler.matchMode.name)}"),
                    Text.literal("Any checks one matching filter group. All requires every active group.")
                )
            )
            buttonY += 14
        }
        buttons += IconActionButton(
            left = ACTION_COLUMN_X,
            top = buttonY,
            width = ACTION_SIZE,
            height = ACTION_SIZE,
            actionId = 4,
            glyph = UiGlyph.Bolt,
            accent = 0xFFC8B43C.toInt(),
            active = true,
            tooltip = listOf(
                Text.literal("Redstone control"),
                Text.literal("Current: ${humanValue(handler.redstoneMode.id)}"),
                Text.literal("This gates when the role is allowed to run.")
            )
        )

        if (handler.tagType?.supportsTargetList == true) {
            buttonY += 14
            buttons += IconActionButton(
                left = ACTION_COLUMN_X,
                top = buttonY,
                width = ACTION_SIZE,
                height = ACTION_SIZE,
                actionId = 3,
                glyph = UiGlyph.Target,
                accent = 0xFF8CD2FF.toInt(),
                active = true,
                tooltip = listOf(
                    Text.literal("Target order"),
                    Text.literal("Current: ${humanValue(handler.targetStrategy.id)}"),
                    Text.literal("Choose how this role walks through its target list.")
                )
            )
            buttonY += 14
            buttons += IconActionButton(
                left = ACTION_COLUMN_X,
                top = buttonY,
                width = ACTION_SIZE,
                height = ACTION_SIZE,
                actionId = 6,
                glyph = if (handler.terminateAfterSuccess) UiGlyph.Stop else UiGlyph.Cycle,
                accent = if (handler.terminateAfterSuccess) 0xFFC8B43C.toInt() else 0xFF909098.toInt(),
                active = handler.terminateAfterSuccess,
                tooltip = listOf(
                    Text.literal("Completion rule"),
                    Text.literal(if (handler.terminateAfterSuccess) "Stop after the first success." else "Loop until the target list is exhausted."),
                    Text.literal("Use one-pass mode when one success is enough.")
                )
            )
            buttonY += 14
            buttons += IconActionButton(
                left = ACTION_COLUMN_X,
                top = buttonY,
                width = ACTION_SIZE,
                height = ACTION_SIZE,
                actionId = 7,
                glyph = UiGlyph.Minus,
                accent = 0xFF50C864.toInt(),
                active = false,
                tooltip = listOf(
                    Text.literal("Lower regulator"),
                    Text.literal("Decrease the kept-item target."),
                    Text.literal("This changes how many items the role tries to maintain.")
                )
            )
            buttons += IconActionButton(
                left = ACTION_COLUMN_X + 14,
                top = buttonY,
                width = ACTION_SIZE,
                height = ACTION_SIZE,
                actionId = 8,
                glyph = UiGlyph.Plus,
                accent = 0xFF50C864.toInt(),
                active = false,
                tooltip = listOf(
                    Text.literal("Raise regulator"),
                    Text.literal("Increase the kept-item target."),
                    Text.literal("Higher values keep more stock on hand.")
                )
            )
        }

        return buttons
    }

    private fun hoveredTooltip(localMouseX: Int, localMouseY: Int): List<Text>? {
        return iconButtons().firstOrNull { button ->
            UiIconButtons.contains(localMouseX, localMouseY, button.left, button.top, button.width, button.height)
        }?.tooltip
    }

    private fun summaryLineOne(): String {
        if (!handler.usesFilter) return "This role ignores item filtering."
        val parts = buildList {
            if (handler.filterItemCount > 0) add("${handler.filterItemCount} item")
            if (handler.matchTagCount > 0) add("${handler.matchTagCount} tag")
            if (handler.matchModIdCount > 0) add("${handler.matchModIdCount} mod")
        }
        return if (parts.isEmpty()) {
            if (handler.isWhitelist) "Whitelist is empty right now." else "No filter restrictions yet."
        } else {
            "${if (handler.isWhitelist) "Allow" else "Block"} ${parts.joinToString(" • ")}"
        }
    }

    private fun summaryLineTwo(): String {
        val targetText = if (handler.tagType?.supportsTargetList == true) {
            "Targets ${compactValue(handler.targetStrategy.id)}"
        } else {
            "No target list"
        }
        return "$targetText • Signal ${compactValue(handler.redstoneMode.id)}"
    }

    private fun summaryLineThree(): String {
        return if (handler.extraTargetCount > 0) {
            "Extra targets ${handler.extraTargetCount}"
        } else if (handler.tagType?.supportsTargetList == true) {
            "Run ${if (handler.terminateAfterSuccess) "One pass" else "Loop"}"
        } else {
            "Context preserved for this role"
        }
    }

    private fun editHint(): String {
        return if (handler.usesFilter) "Click items to shape this rule." else "Tune behavior with the controls." 
    }

    private fun compactValue(value: String): String {
        val normalized = humanValue(value)
        return if (normalized.length <= 11) normalized else normalized.take(10) + "..."
    }

    private fun humanValue(value: String): String {
        return value.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase)
    }
}

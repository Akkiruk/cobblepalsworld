package com.cobblepalsworld.gui.filter

import com.cobblepalsworld.gui.CobblePalsUiTheme
import com.cobblepalsworld.gui.UiGlyph
import com.cobblepalsworld.gui.UiIconButtons
import com.cobblepalsworld.tag.TagTypePresentation
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text

class TagFilterScreen(
    handler: TagFilterScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<TagFilterScreenHandler>(handler, inventory, title) {

    private data class ChipActionButton(
        val id: String,
        val left: Int,
        val top: Int,
        val width: Int,
        val label: String,
        val value: String,
        val actionId: Int,
        val accent: Int,
        val active: Boolean,
        val tooltip: List<Text>
    )

    private data class IconActionButton(
        val id: String,
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
        private const val BACKGROUND_WIDTH = 260
        private const val BACKGROUND_HEIGHT = 272
        private const val HEADER_HEIGHT = 34
        private const val FILTER_LEFT = 10
        private const val FILTER_TOP = 48
        private const val FILTER_WIDTH = 112
        private const val FILTER_HEIGHT = 124
        private const val SETTINGS_LEFT = 130
        private const val SETTINGS_TOP = 48
        private const val SETTINGS_WIDTH = 120
        private const val SETTINGS_HEIGHT = 124
        private const val PLAYER_LEFT = 10
        private const val PLAYER_TOP = 178
        private const val PLAYER_WIDTH = 240
        private const val PLAYER_HEIGHT = 82
        private const val FILTER_SLOT_X = 22
        private const val FILTER_SLOT_Y = 72
        private const val PLAYER_SLOT_X = 49
        private const val PLAYER_SLOT_Y = 194
        private const val CHIP_HEIGHT = 13
    }

    override fun init() {
        backgroundWidth = BACKGROUND_WIDTH
        backgroundHeight = BACKGROUND_HEIGHT
        super.init()
        titleX = 12
        titleY = 8
        playerInventoryTitleX = PLAYER_SLOT_X
        playerInventoryTitleY = PLAYER_SLOT_Y - 12
        applySlotLayout()
    }

    private fun applySlotLayout() {
        for (row in 0..2) {
            for (col in 0..2) {
                setSlotPosition(row * 3 + col, FILTER_SLOT_X + col * 18, FILTER_SLOT_Y + row * 18)
            }
        }
        val playerStart = 9
        for (row in 0..2) {
            for (col in 0..8) {
                setSlotPosition(playerStart + row * 9 + col, PLAYER_SLOT_X + col * 18, PLAYER_SLOT_Y + row * 18)
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
        CobblePalsUiTheme.drawPanel(context, x, y, FILTER_LEFT, FILTER_TOP, FILTER_WIDTH, FILTER_HEIGHT, CobblePalsUiTheme.jobsPanel)
        CobblePalsUiTheme.drawPanel(context, x, y, SETTINGS_LEFT, SETTINGS_TOP, SETTINGS_WIDTH, SETTINGS_HEIGHT, CobblePalsUiTheme.statusPanel)
        CobblePalsUiTheme.drawPanel(context, x, y, PLAYER_LEFT, PLAYER_TOP, PLAYER_WIDTH, PLAYER_HEIGHT, CobblePalsUiTheme.inventoryPanel)
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
        val localMouseX = mouseX - x
        val localMouseY = mouseY - y
        val tagType = handler.tagType
        val roleLabel = tagType?.let { TagTypePresentation.roleLabel(it) } ?: "Role Policy"
        val familyLabel = tagType?.let { TagTypePresentation.familyOf(it).label } ?: "General"
        val chipText = if (handler.usesFilter) Text.literal("FILTER") else Text.literal("NO FILTER")
        val chipStyle = if (handler.usesFilter) CobblePalsUiTheme.linkedStateChip else CobblePalsUiTheme.unlinkedStateChip

        context.drawText(textRenderer, Text.literal("ROLE POLICY"), titleX, titleY, CobblePalsUiTheme.HEADER_TEXT, false)
        context.drawText(textRenderer, Text.literal(fit("$roleLabel • $familyLabel", 178)), 12, 20, CobblePalsUiTheme.SUBTITLE_TEXT, false)
        drawChipText(context, chipText, backgroundWidth - textRenderer.getWidth(chipText) - 22, 9, chipStyle)

        context.drawText(textRenderer, Text.literal("FILTER"), 14, 52, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("BEHAVIOR"), 134, 52, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("INVENTORY"), playerInventoryTitleX, playerInventoryTitleY, CobblePalsUiTheme.TEXT_MUTED, false)

        if (handler.usesFilter) {
            context.drawText(textRenderer, Text.literal(summaryLineOne()), 18, 134, CobblePalsUiTheme.TEXT_MUTED, false)
            context.drawText(textRenderer, Text.literal(fit(summaryLineTwo(), 96)), 18, 148, CobblePalsUiTheme.TEXT_FAINT, false)
            filterWarning()?.let { warning ->
                context.drawText(textRenderer, Text.literal(fit(warning, 96)), 18, 160, CobblePalsUiTheme.ACCENT_POLICY, false)
            }
        } else {
            context.drawText(textRenderer, Text.literal("Filter slots disabled"), 18, 92, CobblePalsUiTheme.TEXT_MUTED, false)
            context.drawText(textRenderer, Text.literal("Behavior still applies"), 18, 106, CobblePalsUiTheme.TEXT_FAINT, false)
        }

        chipButtons().forEach { button ->
            val hovered = UiIconButtons.contains(localMouseX, localMouseY, button.left, button.top, button.width, CHIP_HEIGHT)
            CobblePalsUiTheme.drawChip(context, textRenderer, button.left, button.top, button.label, button.value, button.accent, button.active, hovered)
        }
        iconButtons().forEach { button ->
            val hovered = UiIconButtons.contains(localMouseX, localMouseY, button.left, button.top, button.width, button.height)
            UiIconButtons.draw(context, button.left, button.top, button.width, button.height, button.glyph, button.accent, hovered, button.active)
        }

        context.drawText(textRenderer, Text.literal(fit(summaryLineThree(), 108)), 134, 154, CobblePalsUiTheme.TEXT_FAINT, false)
        context.drawText(textRenderer, Text.literal(fit(editHint(), 108)), 134, 166, CobblePalsUiTheme.TEXT_FAINT, false)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val localMouseX = (mouseX - x).toInt()
            val localMouseY = (mouseY - y).toInt()
            chipButtons().firstOrNull { UiIconButtons.contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { action ->
                client?.interactionManager?.clickButton(handler.syncId, action.actionId)
                return true
            }
            iconButtons().firstOrNull { UiIconButtons.contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { action ->
                client?.interactionManager?.clickButton(handler.syncId, action.actionId)
                return true
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

    private fun chipButtons(): List<ChipActionButton> {
        val buttons = mutableListOf<ChipActionButton>()
        var top = 72
        if (handler.usesFilter) {
            buttons += ChipActionButton("mode", 134, top, 92, "Mode", if (handler.isWhitelist) "Allow" else "Block", 0, if (handler.isWhitelist) CobblePalsUiTheme.ACCENT_CREW else CobblePalsUiTheme.ACCENT_DANGER, handler.isWhitelist, listOf(Text.literal("Filter mode"), Text.literal(if (handler.isWhitelist) "Only matching items pass." else "Matching items are blocked.")))
            top += 16
            buttons += ChipActionButton("nbt", 134, top, 92, "NBT", if (handler.isMatchNbt) "Exact" else "Loose", 1, if (handler.isMatchNbt) CobblePalsUiTheme.ACCENT_CREW else CobblePalsUiTheme.TEXT_FAINT, handler.isMatchNbt, listOf(Text.literal("NBT matching"), Text.literal(if (handler.isMatchNbt) "Require exact item data." else "Compare items loosely.")))
            top += 16
            buttons += ChipActionButton("match", 134, top, 92, "Match", compactValue(handler.matchMode.name), 2, CobblePalsUiTheme.ACCENT_POLICY, true, listOf(Text.literal("Match rule"), Text.literal(matchModeHelp())))
            top += 16
        }
        buttons += ChipActionButton("signal", 134, top, 92, "Signal", compactValue(handler.redstoneMode.id), 4, CobblePalsUiTheme.ACCENT_DANGER, true, listOf(Text.literal("Redstone control"), Text.literal(humanValue(handler.redstoneMode.id))))
        top += 16
        if (handler.tagType?.supportsTargetList == true) {
            buttons += ChipActionButton("target", 134, top, 92, "Target", compactValue(handler.targetStrategy.id), 3, CobblePalsUiTheme.ACCENT_BUFFER, true, listOf(Text.literal("Target order"), Text.literal(humanValue(handler.targetStrategy.id))))
            top += 16
            buttons += ChipActionButton("run", 134, top, 92, "Run", if (handler.terminateAfterSuccess) "One pass" else "Loop", 6, CobblePalsUiTheme.ACCENT_PURPLE, handler.terminateAfterSuccess, listOf(Text.literal("Completion rule")))
        }
        return buttons
    }

    private fun iconButtons(): List<IconActionButton> {
        if (handler.tagType?.supportsTargetList != true) return emptyList()
        return listOf(
            IconActionButton("reg-down", 226, 136, 10, 10, 7, UiGlyph.Minus, CobblePalsUiTheme.ACCENT_CREW, false, listOf(Text.literal("Lower regulator"))),
            IconActionButton("reg-up", 240, 136, 10, 10, 8, UiGlyph.Plus, CobblePalsUiTheme.ACCENT_CREW, false, listOf(Text.literal("Raise regulator")))
        )
    }

    private fun hoveredTooltip(localMouseX: Int, localMouseY: Int): List<Text>? {
        chipButtons().firstOrNull { UiIconButtons.contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { return it.tooltip }
        iconButtons().firstOrNull { UiIconButtons.contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { return it.tooltip }
        return null
    }

    private fun drawChipText(context: DrawContext, text: Text, left: Int, top: Int, style: com.cobblepalsworld.gui.UiChipStyle) {
        val width = textRenderer.getWidth(text) + 14
        context.fill(left, top, left + width, top + 13, style.bodyColor)
        context.fill(left, top, left + 3, top + 13, style.accentColor)
        context.drawText(textRenderer, text, left + 7, top + 3, style.textColor, false)
    }

    private fun summaryLineOne(): String {
        if (!handler.usesFilter) return "This role ignores filters"
        val parts = buildList {
            if (handler.filterItemCount > 0) add("${handler.filterItemCount} item")
            if (handler.matchTagCount > 0) add("${handler.matchTagCount} tag")
            if (handler.matchModIdCount > 0) add("${handler.matchModIdCount} mod")
        }
        return if (parts.isEmpty()) {
            if (handler.isWhitelist) "Whitelist empty" else "No restrictions"
        } else {
            "${if (handler.isWhitelist) "Allow" else "Block"} ${parts.joinToString(" • ")}"
        }
    }

    private fun summaryLineTwo(): String {
        return "${if (handler.isMatchNbt) "Exact" else "Loose"} • ${compactValue(handler.matchMode.name)}"
    }

    private fun filterWarning(): String? {
        if (!handler.usesFilter) return null
        if (handler.isWhitelist && handler.activeFilterGroupCount == 0) return "No items can pass"
        if (handler.matchMode == com.cobblepalsworld.tag.filter.FilterMatchMode.ALL && handler.activeFilterGroupCount > 1) return "Must match every group"
        return null
    }

    private fun matchModeHelp(): String {
        return when (handler.matchMode) {
            com.cobblepalsworld.tag.filter.FilterMatchMode.ANY -> "Any enabled filter group may match."
            com.cobblepalsworld.tag.filter.FilterMatchMode.ALL -> "Every enabled filter group must match the same item."
        }
    }

    private fun summaryLineThree(): String {
        return if (handler.tagType?.supportsTargetList == true) {
            "Keep ${handler.regulatorAmount} • Extra ${handler.extraTargetCount}"
        } else {
            "Standard target behavior"
        }
    }

    private fun editHint(): String = if (handler.usesFilter) "Click items into ghost slots" else "Tune behavior only"

    private fun compactValue(value: String): String {
        val normalized = humanValue(value)
        return if (normalized.length <= 10) normalized else normalized.take(9) + "..."
    }

    private fun humanValue(value: String): String {
        return value.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase)
    }

    private fun fit(value: String, maxWidth: Int): String {
        if (textRenderer.getWidth(value) <= maxWidth) return value
        var clipped = value
        while (clipped.isNotEmpty() && textRenderer.getWidth("$clipped...") > maxWidth) clipped = clipped.dropLast(1)
        return if (clipped.isEmpty()) "..." else "$clipped..."
    }
}
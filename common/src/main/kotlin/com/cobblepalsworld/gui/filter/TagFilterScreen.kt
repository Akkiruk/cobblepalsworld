package com.cobblepalsworld.gui.filter

import com.cobblepalsworld.gui.UiGlyph
import com.cobblepalsworld.gui.UiIconButtons
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.math.roundToInt

class TagFilterScreen(
    handler: TagFilterScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<TagFilterScreenHandler>(handler, inventory, title) {

    companion object {
        private val TEXTURE = Identifier.of("cobblepalsworld", "textures/gui/tag_filter.png")
        private const val ROW_BUTTON_X = 76
        private const val ROW_BUTTON_SIZE = 8
        private const val ROW_LABEL_X = 90
        private const val ROW_VALUE_RIGHT = 166
    }

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

    override fun init() {
        backgroundWidth = 176
        backgroundHeight = 166
        super.init()
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2
        titleY = 4
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight)
    }

    override fun drawForeground(context: DrawContext, mouseX: Int, mouseY: Int) {
        context.drawText(textRenderer, title, titleX, titleY, 0xB4B4C3, false)

        val localMouseX = mouseX - x
        val localMouseY = mouseY - y
        val buttons = iconButtons()
        val showFilterControls = handler.usesFilter
        val showTargetControls = handler.tagType?.supportsTargetList == true

        var rowY = 20
        if (showFilterControls) {
            drawSettingRow(context, rowY, "Mode", if (handler.isWhitelist) "Allow" else "Block", if (handler.isWhitelist) 0x50C864 else 0xC85050)
            rowY += 9
            drawSettingRow(context, rowY, "NBT", if (handler.isMatchNbt) "Exact" else "Loose", if (handler.isMatchNbt) 0x50C864 else 0x909098)
            rowY += 9
            drawSettingRow(context, rowY, "Filter", compactValue(handler.matchMode.name), 0xA0A0C8)
            rowY += 9
        }
        drawSettingRow(context, rowY, "Signal", compactValue(handler.redstoneMode.id), 0xC8B43C)

        if (showTargetControls) {
            rowY += 9
            drawSettingRow(context, rowY, "Target", compactValue(handler.targetStrategy.id), 0x8CD2FF)
            rowY += 9
            drawSettingRow(context, rowY, "Stop", if (handler.terminateAfterSuccess) "One" else "Loop", if (handler.terminateAfterSuccess) 0xC8B43C else 0x909098)
            rowY += 9
            drawRegulatorRow(context, rowY)
        }

        if (!showFilterControls) {
            context.fill(7, 18, 63, 73, 0xAA16161C.toInt())
            context.drawText(textRenderer, Text.literal("No item"), 16, 34, 0xB4B4C3, false)
            context.drawText(textRenderer, Text.literal("filter"), 21, 44, 0x8A8A95, false)
        }

        for (button in buttons) {
            val hovered = UiIconButtons.contains(localMouseX, localMouseY, button.left, button.top, button.width, button.height)
            UiIconButtons.draw(context, button.left, button.top, button.width, button.height, button.glyph, button.accent, hovered, button.active)
            if (hovered) {
                context.fill(72, button.top - 1, 168, button.top + button.height + 1, 0x18FFFFFF)
            }
        }

        val footer = if (showFilterControls) "Click items to set filter" else "This tag ignores item filters"
        context.drawText(textRenderer, Text.literal(footer), 5, 76, 0x505058, false)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val localMouseX = (mouseX - x).roundToInt()
            val localMouseY = (mouseY - y).roundToInt()
            for (iconButton in iconButtons()) {
                if (UiIconButtons.contains(localMouseX, localMouseY, iconButton.left, iconButton.top, iconButton.width, iconButton.height)) {
                    client!!.interactionManager!!.clickButton(handler.syncId, iconButton.actionId)
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)
        hoveredTooltip(mouseX - x, mouseY - y)?.let { tooltip ->
            context.drawTooltip(textRenderer, tooltip, mouseX, mouseY)
        }
    }

    private fun drawSettingRow(context: DrawContext, y: Int, label: String, value: String, color: Int) {
        val valueText = Text.literal(value)
        context.drawText(textRenderer, Text.literal(label), ROW_LABEL_X, y, 0x707078, false)
        context.drawText(textRenderer, valueText, ROW_VALUE_RIGHT - textRenderer.getWidth(valueText), y, color, false)
    }

    private fun drawRegulatorRow(context: DrawContext, y: Int) {
        val valueText = Text.literal(handler.regulatorAmount.toString())
        context.drawText(textRenderer, Text.literal("Reg"), ROW_LABEL_X + 20, y, 0x707078, false)
        context.drawText(textRenderer, valueText, ROW_VALUE_RIGHT - textRenderer.getWidth(valueText), y, 0x50C864, false)
    }

    private fun iconButtons(): List<IconActionButton> {
        val buttons = mutableListOf<IconActionButton>()
        var buttonY = 20
        if (handler.usesFilter) {
            buttons += IconActionButton(
                left = ROW_BUTTON_X,
                top = buttonY,
                width = ROW_BUTTON_SIZE,
                height = ROW_BUTTON_SIZE,
                actionId = 0,
                glyph = if (handler.isWhitelist) UiGlyph.Check else UiGlyph.Ban,
                accent = if (handler.isWhitelist) 0xFF50C864.toInt() else 0xFFC85050.toInt(),
                active = handler.isWhitelist,
                tooltip = listOf(
                    Text.literal("Filter mode"),
                    Text.literal(if (handler.isWhitelist) "Whitelist: only matching items pass." else "Blacklist: matching items are blocked.")
                )
            )
            buttonY += 9
            buttons += IconActionButton(
                left = ROW_BUTTON_X,
                top = buttonY,
                width = ROW_BUTTON_SIZE,
                height = ROW_BUTTON_SIZE,
                actionId = 1,
                glyph = UiGlyph.Data,
                accent = if (handler.isMatchNbt) 0xFF50C864.toInt() else 0xFF909098.toInt(),
                active = handler.isMatchNbt,
                tooltip = listOf(
                    Text.literal("NBT matching"),
                    Text.literal(if (handler.isMatchNbt) "Match exact item data." else "Ignore item data and compare loosely.")
                )
            )
            buttonY += 9
            buttons += IconActionButton(
                left = ROW_BUTTON_X,
                top = buttonY,
                width = ROW_BUTTON_SIZE,
                height = ROW_BUTTON_SIZE,
                actionId = 2,
                glyph = UiGlyph.Filter,
                accent = 0xFFA0A0C8.toInt(),
                active = true,
                tooltip = listOf(
                    Text.literal("Match rule"),
                    Text.literal("Current: ${humanValue(handler.matchMode.name)}")
                )
            )
            buttonY += 9
        }
        buttons += IconActionButton(
            left = ROW_BUTTON_X,
            top = buttonY,
            width = ROW_BUTTON_SIZE,
            height = ROW_BUTTON_SIZE,
            actionId = 4,
            glyph = UiGlyph.Bolt,
            accent = 0xFFC8B43C.toInt(),
            active = true,
            tooltip = listOf(
                Text.literal("Redstone control"),
                Text.literal("Current: ${humanValue(handler.redstoneMode.id)}")
            )
        )

        if (handler.tagType?.supportsTargetList == true) {
            buttonY += 9
            buttons += IconActionButton(
                left = ROW_BUTTON_X,
                top = buttonY,
                width = ROW_BUTTON_SIZE,
                height = ROW_BUTTON_SIZE,
                actionId = 3,
                glyph = UiGlyph.Target,
                accent = 0xFF8CD2FF.toInt(),
                active = true,
                tooltip = listOf(
                    Text.literal("Target order"),
                    Text.literal("Current: ${humanValue(handler.targetStrategy.id)}")
                )
            )
            buttonY += 9
            buttons += IconActionButton(
                left = ROW_BUTTON_X,
                top = buttonY,
                width = ROW_BUTTON_SIZE,
                height = ROW_BUTTON_SIZE,
                actionId = 6,
                glyph = if (handler.terminateAfterSuccess) UiGlyph.Stop else UiGlyph.Cycle,
                accent = if (handler.terminateAfterSuccess) 0xFFC8B43C.toInt() else 0xFF909098.toInt(),
                active = handler.terminateAfterSuccess,
                tooltip = listOf(
                    Text.literal("Completion rule"),
                    Text.literal(if (handler.terminateAfterSuccess) "Stop after the first success." else "Keep working until the target list is exhausted.")
                )
            )
            buttonY += 9
            buttons += IconActionButton(
                left = ROW_BUTTON_X,
                top = buttonY,
                width = ROW_BUTTON_SIZE,
                height = ROW_BUTTON_SIZE,
                actionId = 7,
                glyph = UiGlyph.Minus,
                accent = 0xFF50C864.toInt(),
                active = false,
                tooltip = listOf(
                    Text.literal("Lower regulator"),
                    Text.literal("Decrease the kept-item target.")
                )
            )
            buttons += IconActionButton(
                left = ROW_BUTTON_X + 10,
                top = buttonY,
                width = ROW_BUTTON_SIZE,
                height = ROW_BUTTON_SIZE,
                actionId = 8,
                glyph = UiGlyph.Plus,
                accent = 0xFF50C864.toInt(),
                active = false,
                tooltip = listOf(
                    Text.literal("Raise regulator"),
                    Text.literal("Increase the kept-item target.")
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

    private fun compactValue(value: String): String {
        val normalized = humanValue(value)
        return if (normalized.length <= 8) normalized else normalized.take(7) + "…"
    }

    private fun humanValue(value: String): String {
        return value.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase)
    }
}

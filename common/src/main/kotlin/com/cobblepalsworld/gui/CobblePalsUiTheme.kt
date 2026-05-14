package com.cobblepalsworld.gui

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text

data class UiPanelStyle(
    val bodyColor: Int,
    val borderColor: Int,
    val headerColor: Int,
    val accentColor: Int
)

data class UiSlotStyle(
    val outerColor: Int,
    val midColor: Int,
    val innerColor: Int,
    val accentColor: Int
)

data class UiChipStyle(
    val accentColor: Int,
    val bodyColor: Int,
    val textColor: Int
)

object CobblePalsUiTheme {
    const val ROOT_HEADER_HEIGHT = 34
    const val TOOLTIP_DELAY_MS = 120L

    const val HEADER_TEXT = 0xFFF4EBD8.toInt()
    const val SUBTITLE_TEXT = 0xFFB8C3C7.toInt()
    const val TEXT_PRIMARY = 0xFFE8EEF0.toInt()
    const val TEXT_MUTED = 0xFFAEB8BD.toInt()
    const val TEXT_FAINT = 0xFF75838A.toInt()

    const val ACCENT_WORK = 0xFF4FC6B8.toInt()
    const val ACCENT_POLICY = 0xFFE3B75E.toInt()
    const val ACCENT_BUFFER = 0xFF83AEE8.toInt()
    const val ACCENT_CREW = 0xFF8ED47F.toInt()
    const val ACCENT_DANGER = 0xFFE27263.toInt()
    const val ACCENT_PURPLE = 0xFFB99BFF.toInt()

    private const val ROOT_OUTER = 0xFF05080B.toInt()
    private const val ROOT_MID = 0xFF0C1217.toInt()
    private const val ROOT_BODY = 0xFF111820.toInt()
    private const val ROOT_HEADER = 0xFF17222A.toInt()
    private const val ROOT_LINE = 0xFF26323C.toInt()
    private const val PANEL_BODY = 0xFF151D24.toInt()
    private const val PANEL_ALT = 0xFF11171D.toInt()
    private const val PANEL_BORDER = 0xFF27333D.toInt()
    private const val PANEL_HEADER = 0xFF1B252D.toInt()
    private const val SLOT_OUTER = 0xFF070A0D.toInt()
    private const val SLOT_MID = 0xFF27313A.toInt()
    private const val SLOT_INNER = 0xFF10161C.toInt()

    val jobsPanel = UiPanelStyle(PANEL_BODY, PANEL_BORDER, PANEL_HEADER, ACCENT_WORK)
    val statusPanel = UiPanelStyle(PANEL_BODY, PANEL_BORDER, PANEL_HEADER, ACCENT_CREW)
    val augmentPanel = UiPanelStyle(PANEL_BODY, PANEL_BORDER, PANEL_HEADER, ACCENT_PURPLE)
    val inventoryPanel = UiPanelStyle(PANEL_ALT, PANEL_BORDER, PANEL_HEADER, ACCENT_BUFFER)

    val jobsSlot = UiSlotStyle(SLOT_OUTER, SLOT_MID, SLOT_INNER, ACCENT_WORK)
    val augmentSlot = UiSlotStyle(SLOT_OUTER, 0xFF332A42.toInt(), 0xFF17131D.toInt(), ACCENT_PURPLE)
    val inventorySlot = UiSlotStyle(SLOT_OUTER, 0xFF29323A.toInt(), SLOT_INNER, 0xFF687983.toInt())

    val linkedStateChip = UiChipStyle(ACCENT_CREW, 0xFF18281F.toInt(), 0xFFDDF5D9.toInt())
    val unlinkedStateChip = UiChipStyle(ACCENT_DANGER, 0xFF2B1A17.toInt(), 0xFFFFDCD6.toInt())

    fun drawRootFrame(context: DrawContext, left: Int, top: Int, width: Int, height: Int, headerHeight: Int = ROOT_HEADER_HEIGHT) {
        context.fill(left - 2, top - 2, left + width + 2, top + height + 2, ROOT_OUTER)
        context.fill(left - 1, top - 1, left + width + 1, top + height + 1, ROOT_MID)
        context.fill(left, top, left + width, top + height, ROOT_BODY)
        context.fill(left, top, left + width, top + headerHeight, ROOT_HEADER)
        context.fill(left, top + headerHeight, left + width, top + headerHeight + 1, ROOT_LINE)
    }

    fun drawPanel(
        context: DrawContext,
        rootLeft: Int,
        rootTop: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        style: UiPanelStyle
    ) {
        context.fill(rootLeft + left - 1, rootTop + top - 1, rootLeft + left + width + 1, rootTop + top + height + 1, ROOT_OUTER)
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + width, rootTop + top + height, style.borderColor)
        context.fill(rootLeft + left + 1, rootTop + top + 1, rootLeft + left + width - 1, rootTop + top + height - 1, style.bodyColor)
        context.fill(rootLeft + left + 1, rootTop + top + 1, rootLeft + left + width - 1, rootTop + top + 13, style.headerColor)
        context.fill(rootLeft + left + 1, rootTop + top + 13, rootLeft + left + width - 1, rootTop + top + 14, style.accentColor)
    }

    fun drawPlainPanel(context: DrawContext, left: Int, top: Int, width: Int, height: Int, accent: Int = ROOT_LINE) {
        context.fill(left - 1, top - 1, left + width + 1, top + height + 1, ROOT_OUTER)
        context.fill(left, top, left + width, top + height, PANEL_BORDER)
        context.fill(left + 1, top + 1, left + width - 1, top + height - 1, PANEL_BODY)
        context.fill(left + 1, top + 1, left + 4, top + height - 1, accent)
    }

    fun drawInsetPanel(context: DrawContext, left: Int, top: Int, width: Int, height: Int) {
        context.fill(left, top, left + width, top + height, 0xFF0B1116.toInt())
        context.fill(left + 1, top + 1, left + width - 1, top + height - 1, 0xFF121A21.toInt())
    }

    fun drawSlotWell(context: DrawContext, rootLeft: Int, rootTop: Int, left: Int, top: Int, style: UiSlotStyle) {
        context.fill(rootLeft + left - 1, rootTop + top - 1, rootLeft + left + 17, rootTop + top + 17, style.outerColor)
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + 16, rootTop + top + 16, style.midColor)
        context.fill(rootLeft + left + 1, rootTop + top + 1, rootLeft + left + 15, rootTop + top + 15, style.innerColor)
    }

    fun drawStatusCard(
        context: DrawContext,
        textRenderer: TextRenderer,
        rootLeft: Int,
        rootTop: Int,
        left: Int,
        top: Int,
        width: Int,
        label: String,
        value: Int,
        accent: Int,
        valueColor: Int
    ) {
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + width, rootTop + top + 19, 0xFF0D141A.toInt())
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + 3, rootTop + top + 19, accent)
        context.drawText(textRenderer, Text.literal(label), left + 7, top + 3, TEXT_FAINT, false)
        val valueText = Text.literal(value.toString())
        context.drawText(textRenderer, valueText, left + width - textRenderer.getWidth(valueText) - 6, top + 10, valueColor, false)
    }

    fun drawStripedFill(context: DrawContext, rootLeft: Int, rootTop: Int, left: Int, top: Int, width: Int, height: Int, color: Int, step: Int) {
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + width, rootTop + top + height, color and 0x22FFFFFF)
    }

    fun drawHeaderChip(
        context: DrawContext,
        textRenderer: TextRenderer,
        rootLeft: Int,
        rootTop: Int,
        text: Text,
        left: Int,
        top: Int,
        style: UiChipStyle
    ) {
        val width = textRenderer.getWidth(text) + 14
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + width, rootTop + top + 13, style.bodyColor)
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + 3, rootTop + top + 13, style.accentColor)
        context.drawText(textRenderer, text, rootLeft + left + 7, rootTop + top + 3, style.textColor, false)
    }

    fun drawChip(context: DrawContext, textRenderer: TextRenderer, left: Int, top: Int, label: String, value: String, accent: Int, active: Boolean, hovered: Boolean): Int {
        val width = textRenderer.getWidth(label) + textRenderer.getWidth(value) + 18
        val body = when {
            hovered -> 0xFF202B34.toInt()
            active -> 0xFF1A242C.toInt()
            else -> 0xFF10171D.toInt()
        }
        context.fill(left, top, left + width, top + 13, body)
        context.fill(left, top, left + 3, top + 13, accent)
        context.drawText(textRenderer, Text.literal(label), left + 6, top + 3, TEXT_FAINT, false)
        val valueText = Text.literal(value)
        context.drawText(textRenderer, valueText, left + width - textRenderer.getWidth(valueText) - 5, top + 3, TEXT_PRIMARY, false)
        return width
    }

    fun buttonBorderColor(hovered: Boolean): Int = if (hovered) 0xFFF1E7D1.toInt() else ROOT_OUTER

    fun buttonBodyColor(hovered: Boolean, active: Boolean): Int {
        return when {
            active -> 0xFF24313B.toInt()
            hovered -> 0xFF202B34.toInt()
            else -> 0xFF101820.toInt()
        }
    }

    fun buttonInnerColor(hovered: Boolean, active: Boolean): Int {
        return when {
            active -> 0xFF1B252D.toInt()
            hovered -> 0xFF18222A.toInt()
            else -> 0xFF0D141A.toInt()
        }
    }
}
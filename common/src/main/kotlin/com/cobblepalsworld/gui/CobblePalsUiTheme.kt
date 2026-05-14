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

    const val HEADER_TEXT = 0xFF3E2E1E.toInt()
    const val SUBTITLE_TEXT = 0xFF6F6250.toInt()
    const val TEXT_PRIMARY = 0xFF3B2C1F.toInt()
    const val TEXT_MUTED = 0xFF6E604F.toInt()
    const val TEXT_FAINT = 0xFF9A8C77.toInt()

    const val ACCENT_WORK = 0xFF3F9C86.toInt()
    const val ACCENT_POLICY = 0xFFD39A36.toInt()
    const val ACCENT_BUFFER = 0xFF5B88B7.toInt()
    const val ACCENT_CREW = 0xFF6DAE4C.toInt()
    const val ACCENT_DANGER = 0xFFC65A47.toInt()
    const val ACCENT_PURPLE = 0xFF8B70B7.toInt()

    private const val ROOT_OUTER = 0xFF3A2A1B.toInt()
    private const val ROOT_MID = 0xFF6E593B.toInt()
    private const val ROOT_BODY = 0xFFE8D7B9.toInt()
    private const val ROOT_HEADER = 0xFFF3E4C7.toInt()
    private const val ROOT_LINE = 0xFFA57D44.toInt()
    private const val PANEL_BODY = 0xFFF4E7CC.toInt()
    private const val PANEL_ALT = 0xFFE2CC9F.toInt()
    private const val PANEL_BORDER = 0xFF7C5D35.toInt()
    private const val PANEL_HEADER = 0xFFEACB85.toInt()
    private const val SLOT_OUTER = 0xFF3D2C1D.toInt()
    private const val SLOT_MID = 0xFF95734A.toInt()
    private const val SLOT_INNER = 0xFFD9BE89.toInt()

    val jobsPanel = UiPanelStyle(PANEL_BODY, PANEL_BORDER, PANEL_HEADER, ACCENT_WORK)
    val statusPanel = UiPanelStyle(PANEL_BODY, PANEL_BORDER, PANEL_HEADER, ACCENT_CREW)
    val augmentPanel = UiPanelStyle(PANEL_BODY, PANEL_BORDER, PANEL_HEADER, ACCENT_PURPLE)
    val inventoryPanel = UiPanelStyle(PANEL_ALT, PANEL_BORDER, PANEL_HEADER, ACCENT_BUFFER)

    val jobsSlot = UiSlotStyle(SLOT_OUTER, SLOT_MID, SLOT_INNER, ACCENT_WORK)
    val augmentSlot = UiSlotStyle(SLOT_OUTER, 0xFF8B6A92.toInt(), 0xFFE5D0EC.toInt(), ACCENT_PURPLE)
    val inventorySlot = UiSlotStyle(SLOT_OUTER, 0xFF8A7557.toInt(), SLOT_INNER, 0xFF7A684D.toInt())

    val linkedStateChip = UiChipStyle(ACCENT_CREW, 0xFFDCECCF.toInt(), 0xFF355A25.toInt())
    val unlinkedStateChip = UiChipStyle(ACCENT_DANGER, 0xFFF1C7B8.toInt(), 0xFF713528.toInt())

    fun drawRootFrame(context: DrawContext, left: Int, top: Int, width: Int, height: Int, headerHeight: Int = ROOT_HEADER_HEIGHT) {
        context.fill(left - 2, top - 2, left + width + 2, top + height + 2, ROOT_OUTER)
        context.fill(left - 1, top - 1, left + width + 1, top + height + 1, ROOT_MID)
        context.fill(left, top, left + width, top + height, ROOT_BODY)
        context.fill(left, top, left + width, top + headerHeight, ROOT_HEADER)
        context.fill(left + 2, top + 2, left + width - 2, top + 3, 0x66FFFFFF)
        context.fill(left + 2, top + headerHeight - 2, left + width - 2, top + headerHeight - 1, 0x55FFFFFF)
        context.fill(left, top + headerHeight, left + width, top + headerHeight + 2, ROOT_LINE)
        context.fill(left + width - 2, top + 2, left + width - 1, top + height - 2, 0x33000000)
        context.fill(left + 2, top + height - 2, left + width - 2, top + height - 1, 0x33000000)
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
        context.fill(rootLeft + left + 2, rootTop + top + 2, rootLeft + left + width - 2, rootTop + top + 13, style.headerColor)
        context.fill(rootLeft + left + 2, rootTop + top + 13, rootLeft + left + width - 2, rootTop + top + 15, style.accentColor)
        context.fill(rootLeft + left + 2, rootTop + top + 2, rootLeft + left + width - 2, rootTop + top + 3, 0x66FFFFFF)
    }

    fun drawPlainPanel(context: DrawContext, left: Int, top: Int, width: Int, height: Int, accent: Int = ROOT_LINE) {
        context.fill(left - 1, top - 1, left + width + 1, top + height + 1, ROOT_OUTER)
        context.fill(left, top, left + width, top + height, PANEL_BORDER)
        context.fill(left + 1, top + 1, left + width - 1, top + height - 1, PANEL_BODY)
        context.fill(left + 2, top + 2, left + width - 2, top + 3, 0x66FFFFFF)
        context.fill(left + 2, top + 2, left + 5, top + height - 2, accent)
    }

    fun drawInsetPanel(context: DrawContext, left: Int, top: Int, width: Int, height: Int) {
        context.fill(left, top, left + width, top + height, 0xFF6F5533.toInt())
        context.fill(left + 1, top + 1, left + width - 1, top + height - 1, 0xFFD8C092.toInt())
    }

    fun drawSlotWell(context: DrawContext, rootLeft: Int, rootTop: Int, left: Int, top: Int, style: UiSlotStyle) {
        context.fill(rootLeft + left - 1, rootTop + top - 1, rootLeft + left + 17, rootTop + top + 17, style.outerColor)
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + 16, rootTop + top + 16, style.midColor)
        context.fill(rootLeft + left + 1, rootTop + top + 1, rootLeft + left + 15, rootTop + top + 15, style.innerColor)
        context.fill(rootLeft + left + 2, rootTop + top + 2, rootLeft + left + 14, rootTop + top + 3, 0x55FFFFFF)
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
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + width, rootTop + top + 19, 0xFFD9BF89.toInt())
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
            hovered -> 0xFFF7EBD1.toInt()
            active -> 0xFFEBD39A.toInt()
            else -> 0xFFD9BF89.toInt()
        }
        context.fill(left - 1, top - 1, left + width + 1, top + 14, ROOT_OUTER)
        context.fill(left, top, left + width, top + 13, body)
        context.fill(left, top, left + 3, top + 13, accent)
        context.fill(left + 2, top + 1, left + width - 2, top + 2, 0x66FFFFFF)
        context.drawText(textRenderer, Text.literal(label), left + 6, top + 3, TEXT_FAINT, false)
        val valueText = Text.literal(value)
        context.drawText(textRenderer, valueText, left + width - textRenderer.getWidth(valueText) - 5, top + 3, TEXT_PRIMARY, false)
        return width
    }

    fun buttonBorderColor(hovered: Boolean): Int = if (hovered) 0xFFF9F1D9.toInt() else ROOT_OUTER

    fun buttonBodyColor(hovered: Boolean, active: Boolean): Int {
        return when {
            active -> 0xFFEBD39A.toInt()
            hovered -> 0xFFF7EBD1.toInt()
            else -> 0xFFD7B979.toInt()
        }
    }

    fun buttonInnerColor(hovered: Boolean, active: Boolean): Int {
        return when {
            active -> 0xFFF0DCA7.toInt()
            hovered -> 0xFFFFF2D4.toInt()
            else -> 0xFFE4C987.toInt()
        }
    }
}
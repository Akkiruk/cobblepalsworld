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
    const val ROOT_HEADER_HEIGHT = 22
    const val TOOLTIP_DELAY_MS = 120L

    const val HEADER_TEXT = 0xFFF6E7CF.toInt()
    const val SUBTITLE_TEXT = 0xFFD4BC98.toInt()
    const val TEXT_PRIMARY = 0xFFEAF0F2.toInt()
    const val TEXT_MUTED = 0xFFA9B6BD.toInt()
    const val TEXT_FAINT = 0xFF7F929D.toInt()

    private const val ROOT_FRAME_OUTER = 0xFF090C10.toInt()
    private const val ROOT_FRAME_MID = 0xFF111820.toInt()
    private const val ROOT_FRAME_INNER = 0xFF151D26.toInt()
    private const val ROOT_BODY = 0xFF11171D.toInt()
    private const val ROOT_HEADER_BAR = 0xFF2F241C.toInt()
    private const val ROOT_HEADER_GLOW = 0xFFC38A46.toInt()
    private const val PANEL_LINE = 0xFF05080B.toInt()

    val jobsPanel = UiPanelStyle(
        bodyColor = 0xFF13262C.toInt(),
        borderColor = 0xFF0C1218.toInt(),
        headerColor = 0xFF1E4D51.toInt(),
        accentColor = 0xFF69D0C9.toInt()
    )

    val statusPanel = UiPanelStyle(
        bodyColor = 0xFF211B18.toInt(),
        borderColor = 0xFF0C1218.toInt(),
        headerColor = 0xFF523C26.toInt(),
        accentColor = 0xFFE3B16B.toInt()
    )

    val augmentPanel = UiPanelStyle(
        bodyColor = 0xFF1E1B22.toInt(),
        borderColor = 0xFF0C1218.toInt(),
        headerColor = 0xFF4E3755.toInt(),
        accentColor = 0xFFC59BFF.toInt()
    )

    val inventoryPanel = UiPanelStyle(
        bodyColor = 0xFF171C22.toInt(),
        borderColor = 0xFF0C1218.toInt(),
        headerColor = 0xFF273340.toInt(),
        accentColor = 0xFF89B6DE.toInt()
    )

    val jobsSlot = UiSlotStyle(
        outerColor = 0xFF0A1619.toInt(),
        midColor = 0xFF17353A.toInt(),
        innerColor = 0xFF0F2228.toInt(),
        accentColor = 0xFF5ECBC5.toInt()
    )

    val augmentSlot = UiSlotStyle(
        outerColor = 0xFF120D14.toInt(),
        midColor = 0xFF30213B.toInt(),
        innerColor = 0xFF1E1524.toInt(),
        accentColor = 0xFFC59BFF.toInt()
    )

    val inventorySlot = UiSlotStyle(
        outerColor = 0xFF0D1115.toInt(),
        midColor = 0xFF232D37.toInt(),
        innerColor = 0xFF171E26.toInt(),
        accentColor = 0xFF6B7D8B.toInt()
    )

    val linkedStateChip = UiChipStyle(
        accentColor = 0xFF2DBA62.toInt(),
        bodyColor = 0xFF183126.toInt(),
        textColor = 0xFFD4FBDD.toInt()
    )

    val unlinkedStateChip = UiChipStyle(
        accentColor = 0xFFD36D5C.toInt(),
        bodyColor = 0xFF341F1A.toInt(),
        textColor = 0xFFF6D1CE.toInt()
    )

    fun drawRootFrame(context: DrawContext, left: Int, top: Int, width: Int, height: Int, headerHeight: Int = ROOT_HEADER_HEIGHT) {
        context.fill(left - 2, top - 2, left + width + 2, top + height + 2, ROOT_FRAME_OUTER)
        context.fill(left - 1, top - 1, left + width + 1, top + height + 1, ROOT_FRAME_MID)
        context.fill(left, top, left + width, top + height, ROOT_FRAME_INNER)
        context.fill(left + 4, top + 4, left + width - 4, top + height - 4, ROOT_BODY)
        context.fill(left + 4, top + 4, left + width - 4, top + 4 + headerHeight, ROOT_HEADER_BAR)
        context.fill(left + 4, top + headerHeight + 4, left + width - 4, top + headerHeight + 5, ROOT_HEADER_GLOW)
        context.fill(left + 10, top + 14, left + 72, top + 15, ROOT_HEADER_GLOW)
        context.fill(left + width - 72, top + 14, left + width - 10, top + 15, ROOT_HEADER_GLOW)
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
        context.fill(rootLeft + left - 2, rootTop + top - 2, rootLeft + left + width + 2, rootTop + top + height + 2, PANEL_LINE)
        context.fill(rootLeft + left - 1, rootTop + top - 1, rootLeft + left + width + 1, rootTop + top + height + 1, style.borderColor)
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + width, rootTop + top + height, style.bodyColor)
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + width, rootTop + top + 12, style.headerColor)
        context.fill(rootLeft + left, rootTop + top + 12, rootLeft + left + width, rootTop + top + 13, style.accentColor)
        context.fill(rootLeft + left + 4, rootTop + top + 4, rootLeft + left + 18, rootTop + top + 5, style.accentColor)
        context.fill(rootLeft + left + width - 18, rootTop + top + 4, rootLeft + left + width - 4, rootTop + top + 5, style.accentColor)
    }

    fun drawSlotWell(context: DrawContext, rootLeft: Int, rootTop: Int, left: Int, top: Int, style: UiSlotStyle) {
        context.fill(rootLeft + left - 1, rootTop + top - 1, rootLeft + left + 17, rootTop + top + 17, style.outerColor)
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + 16, rootTop + top + 16, style.midColor)
        context.fill(rootLeft + left + 1, rootTop + top + 1, rootLeft + left + 15, rootTop + top + 15, style.innerColor)
        context.fill(rootLeft + left + 2, rootTop + top + 2, rootLeft + left + 14, rootTop + top + 3, style.accentColor)
        context.fill(rootLeft + left + 2, rootTop + top + 2, rootLeft + left + 3, rootTop + top + 14, style.accentColor)
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
        context.fill(rootLeft + left - 1, rootTop + top - 1, rootLeft + left + width + 1, rootTop + top + 17, PANEL_LINE)
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + width, rootTop + top + 16, 0xFF171114.toInt())
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + 3, rootTop + top + 16, accent)
        val labelText = Text.literal(label)
        val valueText = Text.literal(value.toString())
        context.drawText(textRenderer, labelText, left + 8, top + 4, TEXT_MUTED, false)
        context.drawText(textRenderer, valueText, left + width - textRenderer.getWidth(valueText) - 6, top + 4, valueColor, false)
    }

    fun drawStripedFill(context: DrawContext, rootLeft: Int, rootTop: Int, left: Int, top: Int, width: Int, height: Int, color: Int, step: Int) {
        var line = 0
        while (line < height) {
            context.fill(rootLeft + left, rootTop + top + line, rootLeft + left + width, rootTop + top + line + 1, color)
            line += step
        }
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
        val width = textRenderer.getWidth(text) + 16
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + width, rootTop + top + 13, style.bodyColor)
        context.fill(rootLeft + left, rootTop + top, rootLeft + left + 3, rootTop + top + 13, style.accentColor)
        context.drawText(textRenderer, text, left + 7, top + 3, style.textColor, false)
    }

    fun buttonBorderColor(hovered: Boolean): Int {
        return if (hovered) 0xFFF7EED4.toInt() else 0xFF06090C.toInt()
    }

    fun buttonBodyColor(hovered: Boolean, active: Boolean): Int {
        return when {
            active -> 0xFF223240.toInt()
            hovered -> 0xFF1A2530.toInt()
            else -> 0xFF121A22.toInt()
        }
    }

    fun buttonInnerColor(hovered: Boolean, active: Boolean): Int {
        return when {
            active -> 0xFF19242D.toInt()
            hovered -> 0xFF16212A.toInt()
            else -> 0xFF0D141A.toInt()
        }
    }
}
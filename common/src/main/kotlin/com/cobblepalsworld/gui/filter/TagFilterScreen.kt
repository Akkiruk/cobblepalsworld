package com.cobblepalsworld.gui.filter

import com.cobblepalsworld.gui.CobblemonUiChrome
import com.cobblepalsworld.tag.TagTypePresentation
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class TagFilterScreen(
    handler: TagFilterScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<TagFilterScreenHandler>(handler, inventory, title) {

    private data class PanelActionButton(
        val id: String,
        val left: Int,
        val top: Int,
        val width: Int,
        val label: String,
        val value: String,
        val actionId: Int,
        val active: Boolean,
        val tooltip: List<Text>
    )

    private data class TextureActionButton(
        val id: String,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val actionId: Int,
        val texture: Identifier,
        val scaled: Boolean,
        val tooltip: List<Text>
    )

    companion object {
        private const val BACKGROUND_WIDTH = CobblemonUiChrome.BASE_WIDTH
        private const val BACKGROUND_HEIGHT = CobblemonUiChrome.BASE_HEIGHT
        private const val FILTER_SLOT_X = 22
        private const val FILTER_SLOT_Y = 56
        private const val PLAYER_SLOT_X = 91
        private const val PLAYER_SLOT_Y = 91
        private const val PANEL_BUTTON_X = 274
        private const val PANEL_BUTTON_WIDTH = 70
        private const val PANEL_BUTTON_HEIGHT = 17
        private const val BACK_LEFT = 320
        private const val BACK_TOP = 186
    }

    override fun init() {
        backgroundWidth = BACKGROUND_WIDTH
        backgroundHeight = BACKGROUND_HEIGHT
        super.init()
        titleX = 10_000
        titleY = 10_000
        playerInventoryTitleX = 10_000
        playerInventoryTitleY = 10_000
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
        val localMouseX = mouseX - x
        val localMouseY = mouseY - y

        CobblemonUiChrome.drawPcBase(context, x, y)
        CobblemonUiChrome.drawPortraitPanel(context, x, y)
        CobblemonUiChrome.drawInfoBox(context, x, y)
        CobblemonUiChrome.drawStorageScreen(context, x, y)
        CobblemonUiChrome.drawPasturePanel(context, x, y)
        CobblemonUiChrome.drawBackButton(context, x, y, localMouseX, localMouseY, BACK_LEFT, BACK_TOP)

        if (handler.usesFilter) {
            for (row in 0..2) {
                for (col in 0..2) drawSlotFrame(context, FILTER_SLOT_X + col * 18, FILTER_SLOT_Y + row * 18, 0.62F)
            }
        }
        for (row in 0..2) {
            for (col in 0..8) drawSlotFrame(context, PLAYER_SLOT_X + col * 18, PLAYER_SLOT_Y + row * 18, 0.45F)
        }
        for (col in 0..8) drawSlotFrame(context, PLAYER_SLOT_X + col * 18, PLAYER_SLOT_Y + 58, 0.45F)
    }

    override fun drawForeground(context: DrawContext, mouseX: Int, mouseY: Int) {
        val localMouseX = mouseX - x
        val localMouseY = mouseY - y
        val tagType = handler.tagType
        val roleLabel = tagType?.let { TagTypePresentation.roleLabel(it) } ?: "Role Policy"
        val familyLabel = tagType?.let { TagTypePresentation.familyOf(it).label } ?: "General"

        text(context, "ROLE POLICY", 16, 16, CobblemonUiChrome.TEXT_LIGHT, true, 0.7F)
        text(context, fit(roleLabel, 112, 0.5F), 88, 17, CobblemonUiChrome.TEXT_LIGHT, true)
        text(context, if (handler.usesFilter) "FILTER" else "NO FILTER", 278, 16, CobblemonUiChrome.TEXT_LIGHT, true)
        text(context, familyLabel, 18, 35, CobblemonUiChrome.TEXT_DARK, false)
        text(context, "BAG", 94, 80, CobblemonUiChrome.TEXT_LIGHT, true)
        text(context, "BEHAVIOR", 276, 33, CobblemonUiChrome.TEXT_DARK, false)

        if (handler.usesFilter) {
            text(context, fit(summaryLineOne(), 112, 0.5F), 14, 136, CobblemonUiChrome.TEXT_DARK, false)
            text(context, fit(summaryLineTwo(), 112, 0.5F), 14, 149, CobblemonUiChrome.TEXT_MUTED, false)
            text(context, fit(filterWarning() ?: "Ready", 112, 0.5F), 14, 162, if (filterWarning() == null) CobblemonUiChrome.ACCENT_GREEN else CobblemonUiChrome.ACCENT_GOLD, false)
        } else {
            text(context, "Filter slots disabled", 14, 136, CobblemonUiChrome.TEXT_MUTED, false)
            text(context, "Behavior still applies", 14, 149, CobblemonUiChrome.TEXT_FAINT, false)
        }
        text(context, fit(summaryLineThree(), 112, 0.5F), 14, 175, CobblemonUiChrome.ACCENT_BLUE, false)

        panelButtons().forEach { button ->
            val hovered = CobblemonUiChrome.contains(localMouseX, localMouseY, button.left, button.top, button.width, PANEL_BUTTON_HEIGHT)
            CobblemonUiChrome.drawInfoButton(context, x, y, button.left, button.top, button.width, hovered, button.active)
            text(context, button.label, button.left + 5, button.top + 5, CobblemonUiChrome.TEXT_LIGHT, true)
            text(context, fit(button.value, 60, 0.5F), button.left + 5, button.top + 13, CobblemonUiChrome.TEXT_LIGHT, true)
        }
        textureButtons().forEach { button ->
            CobblemonUiChrome.drawButton(context, x, y, CobblemonUiChrome.TextureButton(button.left, button.top, button.width, button.height, button.texture, button.scaled), localMouseX, localMouseY)
        }
        text(context, fit(editHint(), 118, 0.5F), 276, 166, CobblemonUiChrome.TEXT_FAINT, false)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val localMouseX = (mouseX - x).toInt()
            val localMouseY = (mouseY - y).toInt()
            if (CobblemonUiChrome.contains(localMouseX, localMouseY, BACK_LEFT, BACK_TOP, 26, 13)) {
                CobblemonUiChrome.playClick()
                close()
                return true
            }
            panelButtons().firstOrNull { CobblemonUiChrome.contains(localMouseX, localMouseY, it.left, it.top, it.width, PANEL_BUTTON_HEIGHT) }?.let { action ->
                CobblemonUiChrome.playClick()
                client?.interactionManager?.clickButton(handler.syncId, action.actionId)
                return true
            }
            textureButtons().firstOrNull { CobblemonUiChrome.contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { action ->
                CobblemonUiChrome.playClick()
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

    private fun panelButtons(): List<PanelActionButton> {
        val buttons = mutableListOf<PanelActionButton>()
        var top = 48
        if (handler.usesFilter) {
            buttons += PanelActionButton("mode", PANEL_BUTTON_X, top, PANEL_BUTTON_WIDTH, "Mode", if (handler.isWhitelist) "Allow" else "Block", 0, handler.isWhitelist, listOf(Text.literal("Filter mode"), Text.literal(if (handler.isWhitelist) "Only matching items pass." else "Matching items are blocked.")))
            top += 20
            buttons += PanelActionButton("nbt", PANEL_BUTTON_X, top, PANEL_BUTTON_WIDTH, "NBT", if (handler.isMatchNbt) "Exact" else "Loose", 1, handler.isMatchNbt, listOf(Text.literal("NBT matching"), Text.literal(if (handler.isMatchNbt) "Require exact item data." else "Compare items loosely.")))
            top += 20
            buttons += PanelActionButton("match", PANEL_BUTTON_X, top, PANEL_BUTTON_WIDTH, "Match", compactValue(handler.matchMode.name), 2, true, listOf(Text.literal("Match rule"), Text.literal(matchModeHelp())))
            top += 20
        }
        buttons += PanelActionButton("signal", PANEL_BUTTON_X, top, PANEL_BUTTON_WIDTH, "Signal", compactValue(handler.redstoneMode.id), 4, handler.redstoneMode != com.cobblepalsworld.tag.RedstoneControlMode.ALWAYS, listOf(Text.literal("Redstone control"), Text.literal(humanValue(handler.redstoneMode.id))))
        top += 20
        if (handler.tagType?.supportsTargetList == true) {
            buttons += PanelActionButton("target", PANEL_BUTTON_X, top, PANEL_BUTTON_WIDTH, "Target", compactValue(handler.targetStrategy.id), 3, true, listOf(Text.literal("Target order"), Text.literal(humanValue(handler.targetStrategy.id))))
            top += 20
            buttons += PanelActionButton("run", PANEL_BUTTON_X, top, PANEL_BUTTON_WIDTH, "Run", if (handler.terminateAfterSuccess) "One pass" else "Loop", 6, handler.terminateAfterSuccess, listOf(Text.literal("Completion rule")))
        }
        return buttons
    }

    private fun textureButtons(): List<TextureActionButton> {
        if (handler.tagType?.supportsTargetList != true) return emptyList()
        return listOf(
            TextureActionButton("reg-down", 305, 153, 7, 7, 7, CobblemonUiChrome.NAV_PREVIOUS, true, listOf(Text.literal("Lower regulator"))),
            TextureActionButton("reg-up", 328, 153, 7, 7, 8, CobblemonUiChrome.NAV_NEXT, true, listOf(Text.literal("Raise regulator")))
        )
    }

    private fun hoveredTooltip(localMouseX: Int, localMouseY: Int): List<Text>? {
        panelButtons().firstOrNull { CobblemonUiChrome.contains(localMouseX, localMouseY, it.left, it.top, it.width, PANEL_BUTTON_HEIGHT) }?.let { return it.tooltip }
        textureButtons().firstOrNull { CobblemonUiChrome.contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { return it.tooltip }
        return null
    }

    private fun drawSlotFrame(context: DrawContext, localX: Int, localY: Int, alpha: Float) {
        CobblemonUiChrome.drawSlotFrame(context, x, y, localX, localY, alpha)
    }

    private fun text(context: DrawContext, value: String, localX: Int, localY: Int, color: Int, shadow: Boolean, scale: Float = CobblemonUiChrome.TEXTURE_SCALE) {
        CobblemonUiChrome.drawSmallText(context, textRenderer, value, x + localX, y + localY, color, shadow, scale)
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
            "${if (handler.isWhitelist) "Allow" else "Block"} ${parts.joinToString(", ")}".trim()
        }
    }

    private fun summaryLineTwo(): String {
        return "${if (handler.isMatchNbt) "Exact" else "Loose"} / ${compactValue(handler.matchMode.name)}"
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
            "Keep ${handler.regulatorAmount} / Extra ${handler.extraTargetCount}"
        } else {
            "Standard target behavior"
        }
    }

    private fun editHint(): String = if (handler.usesFilter) "Ghost slots set item rules" else "Behavior only"

    private fun compactValue(value: String): String {
        val normalized = humanValue(value)
        return if (normalized.length <= 10) normalized else normalized.take(9) + "..."
    }

    private fun humanValue(value: String): String {
        return value.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase)
    }

    private fun fit(value: String, maxWidth: Int, scale: Float): String {
        if (textRenderer.getWidth(value) * scale <= maxWidth) return value
        var clipped = value
        while (clipped.isNotEmpty() && textRenderer.getWidth("$clipped...") * scale > maxWidth) clipped = clipped.dropLast(1)
        return if (clipped.isEmpty()) "..." else "$clipped..."
    }
}
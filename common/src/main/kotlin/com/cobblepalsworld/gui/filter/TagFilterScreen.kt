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
        private const val FILTER_SLOT_X = 99
        private const val FILTER_SLOT_Y = 50
        private const val PLAYER_SLOT_X = 91
        private const val PLAYER_SLOT_Y = 108
        private const val HIDDEN_SLOT_X = -10_000
        private const val HIDDEN_SLOT_Y = -10_000
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
                if (handler.usesFilter) {
                    setSlotPosition(row * 3 + col, FILTER_SLOT_X + col * 18, FILTER_SLOT_Y + row * 18)
                } else {
                    setSlotPosition(row * 3 + col, HIDDEN_SLOT_X, HIDDEN_SLOT_Y)
                }
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
        CobblemonUiChrome.drawStorageScreen(context, x, y)
        CobblemonUiChrome.drawPasturePanel(context, x, y)
        CobblemonUiChrome.drawBackButton(context, x, y, localMouseX, localMouseY, BACK_LEFT, BACK_TOP)

        drawEditorSurface(context)
        if (handler.usesFilter) {
            for (row in 0..2) {
                for (col in 0..2) drawSlotWell(context, FILTER_SLOT_X + col * 18, FILTER_SLOT_Y + row * 18, true)
            }
        }
        for (row in 0..2) {
            for (col in 0..8) drawSlotWell(context, PLAYER_SLOT_X + col * 18, PLAYER_SLOT_Y + row * 18, false)
        }
        for (col in 0..8) drawSlotWell(context, PLAYER_SLOT_X + col * 18, PLAYER_SLOT_Y + 58, false)
    }

    override fun drawForeground(context: DrawContext, mouseX: Int, mouseY: Int) {
        val localMouseX = mouseX - x
        val localMouseY = mouseY - y
        val tagType = handler.tagType
        val roleLabel = tagType?.let { TagTypePresentation.roleLabel(it) } ?: "Role Policy"
        val familyLabel = tagType?.let { TagTypePresentation.familyOf(it).label } ?: "General"

        text(context, "ROLE POLICY", 108, 16, CobblemonUiChrome.TEXT_LIGHT, true, 0.7F)
        text(context, fit(roleLabel, 72, 0.5F), 161, 43, CobblemonUiChrome.TEXT_LIGHT, true)
        text(context, fit(familyLabel, 72, 0.5F), 161, 54, CobblemonUiChrome.TEXT_MUTED, false)
        text(context, if (handler.usesFilter) "FILTER" else "BEHAVIOR ONLY", 100, 40, CobblemonUiChrome.TEXT_LIGHT, true)
        text(context, "BAG", 94, 99, CobblemonUiChrome.TEXT_LIGHT, true)
        text(context, "BEHAVIOR", 276, 33, CobblemonUiChrome.TEXT_DARK, false)

        if (handler.usesFilter) {
            text(context, fit(summaryLineOne(), 78, 0.5F), 161, 68, CobblemonUiChrome.TEXT_LIGHT, false)
            text(context, fit(summaryLineTwo(), 78, 0.5F), 161, 79, CobblemonUiChrome.TEXT_MUTED, false)
            text(context, fit(filterWarning() ?: "Ready", 78, 0.5F), 161, 90, if (filterWarning() == null) CobblemonUiChrome.ACCENT_GREEN else CobblemonUiChrome.ACCENT_GOLD, false)
        } else {
            text(context, "No item filter", 161, 68, CobblemonUiChrome.TEXT_MUTED, false)
            text(context, "Behavior still applies", 161, 79, CobblemonUiChrome.TEXT_FAINT, false)
        }
        text(context, fit(summaryLineThree(), 78, 0.5F), 161, 101, CobblemonUiChrome.ACCENT_BLUE, false)

        panelButtons().forEach { button ->
            val hovered = CobblemonUiChrome.contains(localMouseX, localMouseY, button.left, button.top, button.width, PANEL_BUTTON_HEIGHT)
            CobblemonUiChrome.drawInfoButton(context, 0, 0, button.left, button.top, button.width, hovered, button.active)
            text(context, button.label, button.left + 5, button.top + 5, CobblemonUiChrome.TEXT_LIGHT, true)
            text(context, fit(button.value, 60, 0.5F), button.left + 5, button.top + 13, CobblemonUiChrome.TEXT_LIGHT, true)
        }
        textureButtons().forEach { button ->
            CobblemonUiChrome.drawButton(context, 0, 0, CobblemonUiChrome.TextureButton(button.left, button.top, button.width, button.height, button.texture, button.scaled), localMouseX, localMouseY)
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

    private fun drawEditorSurface(context: DrawContext) {
        context.fill(x + 96, y + 37, x + 246, y + 104, 0xB8202E35.toInt())
        context.fill(x + 96, y + 37, x + 246, y + 38, 0xFF657B86.toInt())
        context.fill(x + 96, y + 103, x + 246, y + 104, 0xFF162027.toInt())
        context.fill(x + 96, y + 37, x + 97, y + 104, 0xFF657B86.toInt())
        context.fill(x + 245, y + 37, x + 246, y + 104, 0xFF162027.toInt())
        context.fill(x + 160, y + 42, x + 241, y + 97, 0x6618242A)
    }

    private fun drawSlotWell(context: DrawContext, localX: Int, localY: Int, accent: Boolean) {
        val border = if (accent) 0xFF718A94.toInt() else 0xFF54626A.toInt()
        val fill = if (accent) 0xFF20343D.toInt() else 0xFF1D2B33.toInt()
        context.fill(x + localX - 1, y + localY - 1, x + localX + 17, y + localY + 17, border)
        context.fill(x + localX, y + localY, x + localX + 16, y + localY + 16, fill)
        context.fill(x + localX + 1, y + localY + 1, x + localX + 15, y + localY + 2, 0x553D5966)
    }

    private fun text(context: DrawContext, value: String, localX: Int, localY: Int, color: Int, shadow: Boolean, scale: Float = CobblemonUiChrome.TEXTURE_SCALE) {
        CobblemonUiChrome.drawSmallText(context, textRenderer, value, localX, localY, color, shadow, scale)
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
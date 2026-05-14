package com.cobblepalsworld.gui.assignment

import com.cobblepalsworld.behavior.state.WorkerStatusKind
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.gui.CobblePalsUiTheme
import com.cobblepalsworld.gui.UiGlyph
import com.cobblepalsworld.gui.UiIconButtons
import com.cobblepalsworld.pasture.WorkerAssignmentMode
import com.cobblepalsworld.tag.RedstoneControlMode
import com.cobblepalsworld.tag.TagItem
import com.cobblepalsworld.tag.TagSpec
import com.cobblepalsworld.tag.TagTypePresentation
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

class PokemonTagScreen(
    handler: PokemonTagScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<PokemonTagScreenHandler>(handler, inventory, title) {

    private val screenTitle = Text.literal("PAL DETAIL")

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

        private const val LOADOUT_PANEL_LEFT = 10
        private const val LOADOUT_PANEL_TOP = 44
        private const val LOADOUT_PANEL_WIDTH = 98
        private const val LOADOUT_PANEL_HEIGHT = 92

        private const val STATUS_PANEL_LEFT = 116
        private const val STATUS_PANEL_TOP = 44
        private const val STATUS_PANEL_WIDTH = 102
        private const val STATUS_PANEL_HEIGHT = 92

        private const val POLICY_PANEL_LEFT = 10
        private const val POLICY_PANEL_TOP = 140
        private const val POLICY_PANEL_WIDTH = 208
        private const val POLICY_PANEL_HEIGHT = 34

        private const val PLAYER_PANEL_LEFT = 10
        private const val PLAYER_PANEL_TOP = 178
        private const val PLAYER_PANEL_WIDTH = 208
        private const val PLAYER_PANEL_HEIGHT = 58

        private const val TAG_SLOT_X = 18
        private const val TAG_SLOT_Y = 60
        private const val AUGMENT_SLOT_X = 18
        private const val AUGMENT_SLOT_Y = 82
        private const val DISPLAY_SLOT_X = 50
        private const val DISPLAY_SLOT_Y = 60
        private const val PLAYER_SLOT_X = 16
        private const val PLAYER_SLOT_Y = 188

        private const val ACTION_BUTTON_WIDTH = 10
        private const val ACTION_BUTTON_HEIGHT = 10
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
        setSlotPosition(PokemonTagScreenHandler.TAG_SLOT, TAG_SLOT_X, TAG_SLOT_Y)

        for (index in 0 until PokemonTagScreenHandler.AUGMENT_SLOT_COUNT) {
            setSlotPosition(PokemonTagScreenHandler.AUGMENT_SLOT_START + index, AUGMENT_SLOT_X, AUGMENT_SLOT_Y + index * 18)
        }

        for (row in 0..2) {
            for (col in 0..2) {
                val slotIndex = PokemonTagScreenHandler.DISPLAY_SLOT_START + row * 3 + col
                setSlotPosition(slotIndex, DISPLAY_SLOT_X + col * 18, DISPLAY_SLOT_Y + row * 18)
            }
        }

        for (row in 0..2) {
            for (col in 0..8) {
                val slotIndex = PokemonTagScreenHandler.PLAYER_SLOT_START + row * 9 + col
                setSlotPosition(slotIndex, PLAYER_SLOT_X + col * 18, PLAYER_SLOT_Y + row * 18)
            }
        }

        val hotbarStart = PokemonTagScreenHandler.PLAYER_SLOT_START + 27
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

        CobblePalsUiTheme.drawPanel(context, x, y, LOADOUT_PANEL_LEFT, LOADOUT_PANEL_TOP, LOADOUT_PANEL_WIDTH, LOADOUT_PANEL_HEIGHT, CobblePalsUiTheme.jobsPanel)
        CobblePalsUiTheme.drawPanel(context, x, y, STATUS_PANEL_LEFT, STATUS_PANEL_TOP, STATUS_PANEL_WIDTH, STATUS_PANEL_HEIGHT, CobblePalsUiTheme.statusPanel)
        CobblePalsUiTheme.drawPanel(context, x, y, POLICY_PANEL_LEFT, POLICY_PANEL_TOP, POLICY_PANEL_WIDTH, POLICY_PANEL_HEIGHT, CobblePalsUiTheme.augmentPanel)
        CobblePalsUiTheme.drawPanel(context, x, y, PLAYER_PANEL_LEFT, PLAYER_PANEL_TOP, PLAYER_PANEL_WIDTH, PLAYER_PANEL_HEIGHT, CobblePalsUiTheme.inventoryPanel)

        CobblePalsUiTheme.drawStripedFill(context, x, y, LOADOUT_PANEL_LEFT + 5, LOADOUT_PANEL_TOP + 18, LOADOUT_PANEL_WIDTH - 10, LOADOUT_PANEL_HEIGHT - 24, 0x1E6CD1C9, 6)
        CobblePalsUiTheme.drawStripedFill(context, x, y, STATUS_PANEL_LEFT + 5, STATUS_PANEL_TOP + 18, STATUS_PANEL_WIDTH - 10, STATUS_PANEL_HEIGHT - 24, 0x1EE3B16B, 6)
        CobblePalsUiTheme.drawStripedFill(context, x, y, POLICY_PANEL_LEFT + 5, POLICY_PANEL_TOP + 18, POLICY_PANEL_WIDTH - 10, POLICY_PANEL_HEIGHT - 24, 0x12C59BFF, 6)
        CobblePalsUiTheme.drawStripedFill(context, x, y, PLAYER_PANEL_LEFT + 5, PLAYER_PANEL_TOP + 18, PLAYER_PANEL_WIDTH - 10, PLAYER_PANEL_HEIGHT - 24, 0x124B5A69, 6)

        CobblePalsUiTheme.drawSlotWell(context, x, y, TAG_SLOT_X, TAG_SLOT_Y, CobblePalsUiTheme.jobsSlot)
        for (index in 0 until PokemonTagScreenHandler.AUGMENT_SLOT_COUNT) {
            CobblePalsUiTheme.drawSlotWell(context, x, y, AUGMENT_SLOT_X, AUGMENT_SLOT_Y + index * 18, CobblePalsUiTheme.augmentSlot)
        }

        for (row in 0..2) {
            for (col in 0..2) {
                CobblePalsUiTheme.drawSlotWell(context, x, y, DISPLAY_SLOT_X + col * 18, DISPLAY_SLOT_Y + row * 18, CobblePalsUiTheme.inventorySlot)
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
        val statusReason = WorkerStatusReason.entries.getOrElse(handler.statusReasonOrdinal) { WorkerStatusReason.READY }
        val statusColor = statusColor(statusReason)
        val carryMax = handler.carryMax
        val carryCount = handler.carryCount
        val carryColor = when {
            carryMax <= 0 -> 0xFF505058.toInt()
            carryCount >= carryMax -> 0xFFC85050.toInt()
            carryCount > 0 -> 0xFF50C864.toInt()
            else -> 0xFF909098.toInt()
        }
        val assignmentMode = WorkerAssignmentMode.fromOrdinal(handler.assignmentModeOrdinal)
        val tagStack = handler.slots[PokemonTagScreenHandler.TAG_SLOT].stack
        val tagItem = tagStack.item as? TagItem
        val registries = client?.world?.registryManager
        val spec = if (tagItem != null && registries != null) TagItem.getSpec(tagStack, registries) else null
        val roleLabel = tagItem?.let { TagTypePresentation.roleLabel(it.tagType) } ?: "No role assigned"
        val familyLabel = tagItem?.let { TagTypePresentation.familyOf(it.tagType).label } ?: "Idle"

        val chipText = if (handler.isManagedByCommandPost) Text.literal("POST LINK") else Text.literal("LOCAL")
        val chipStyle = if (handler.isManagedByCommandPost) CobblePalsUiTheme.linkedStateChip else CobblePalsUiTheme.unlinkedStateChip
        val chipLeft = backgroundWidth - textRenderer.getWidth(chipText) - 22

        context.drawText(textRenderer, screenTitle, titleX, titleY, CobblePalsUiTheme.HEADER_TEXT, false)
        context.drawText(textRenderer, Text.literal(roleLabel), 12, 20, CobblePalsUiTheme.SUBTITLE_TEXT, false)
        CobblePalsUiTheme.drawHeaderChip(context, textRenderer, x, y, chipText, chipLeft, 7, chipStyle)

        context.drawText(textRenderer, Text.literal("LOADOUT"), 14, 48, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("STATUS"), 120, 48, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("POLICY"), 14, 144, CobblePalsUiTheme.TEXT_PRIMARY, false)

        context.drawText(textRenderer, Text.literal(familyLabel), 120, 62, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(statusReason.label), 120, 75, statusColor, false)
        context.drawText(textRenderer, Text.literal(if (handler.isEcoMode) "Eco mode active" else "Eco mode idle"), 120, 88, if (handler.isEcoMode) 0xFFD36D5C.toInt() else CobblePalsUiTheme.TEXT_FAINT, false)
        context.drawText(textRenderer, Text.literal(if (carryMax > 0) "Cargo $carryCount/$carryMax" else "Cargo empty"), 120, 101, carryColor, false)
        context.drawText(textRenderer, Text.literal(assignmentLabel(assignmentMode, handler.allowFallback)), 120, 114, assignmentColor(assignmentMode, handler.allowFallback), false)
        context.drawText(textRenderer, Text.literal(if (handler.allowFallback) "Fallback enabled" else "Fallback locked"), 120, 126, CobblePalsUiTheme.TEXT_MUTED, false)

        identityButtons().forEach { button ->
            val hovered = UiIconButtons.contains(mouseX - x, mouseY - y, button.left, button.top, button.width, button.height)
            UiIconButtons.draw(context, button.left, button.top, button.width, button.height, button.glyph, button.accent, hovered, button.active)
        }

        context.drawText(textRenderer, Text.literal(truncate(bindingSummary(tagItem, spec), 30)), 18, 155, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(truncate(filterSummary(tagItem, spec), 30)), 18, 166, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(truncate(settingsSummary(tagItem, spec), 30)), 110, 155, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(augmentSummary()), 110, 166, 0xFFC59BFF.toInt(), false)

        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, CobblePalsUiTheme.TEXT_MUTED, false)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val localMouseX = (mouseX - x).roundToInt()
            val localMouseY = (mouseY - y).roundToInt()
            for (iconButton in identityButtons()) {
                if (UiIconButtons.contains(localMouseX, localMouseY, iconButton.left, iconButton.top, iconButton.width, iconButton.height)) {
                    client?.interactionManager?.clickButton(handler.syncId, iconButton.actionId)
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val actionId = when (keyCode) {
            GLFW.GLFW_KEY_LEFT -> PokemonTagScreenHandler.ACTION_PREVIOUS_PAL
            GLFW.GLFW_KEY_RIGHT -> PokemonTagScreenHandler.ACTION_NEXT_PAL
            GLFW.GLFW_KEY_R -> if (hasAssignedRole()) PokemonTagScreenHandler.ACTION_OPEN_ROLE_POLICY else return super.keyPressed(keyCode, scanCode, modifiers)
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }

        client?.interactionManager?.clickButton(handler.syncId, actionId)
        return true
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)
        hoveredTooltip(mouseX - x, mouseY - y)?.let { tooltip ->
            context.drawTooltip(textRenderer, tooltip, mouseX, mouseY)
        }
    }

    private fun hasAssignedRole(): Boolean {
        return handler.slots[PokemonTagScreenHandler.TAG_SLOT].stack.item is TagItem
    }

    private fun identityButtons(): List<IconActionButton> {
        val assignmentMode = WorkerAssignmentMode.fromOrdinal(handler.assignmentModeOrdinal)
        val canEditPolicy = hasAssignedRole()
        return listOf(
            IconActionButton(
                left = 168,
                top = 7,
                width = ACTION_BUTTON_WIDTH,
                height = ACTION_BUTTON_HEIGHT,
                actionId = PokemonTagScreenHandler.ACTION_PREVIOUS_PAL,
                glyph = UiGlyph.Prev,
                accent = 0xFF8CD2FF.toInt(),
                active = false,
                tooltip = listOf(
                    Text.literal("Previous pal"),
                    Text.literal("Step to the previous pal in this pasture roster."),
                    Text.literal("Use this to inspect the crew without reopening the Command Post."),
                    Text.literal("Shortcut: Left Arrow")
                )
            ),
            IconActionButton(
                left = 182,
                top = 7,
                width = ACTION_BUTTON_WIDTH,
                height = ACTION_BUTTON_HEIGHT,
                actionId = PokemonTagScreenHandler.ACTION_NEXT_PAL,
                glyph = UiGlyph.Next,
                accent = 0xFF8CD2FF.toInt(),
                active = false,
                tooltip = listOf(
                    Text.literal("Next pal"),
                    Text.literal("Step to the next pal in this pasture roster."),
                    Text.literal("Use this to inspect the crew without reopening the Command Post."),
                    Text.literal("Shortcut: Right Arrow")
                )
            ),
            IconActionButton(
                left = 198,
                top = 146,
                width = ACTION_BUTTON_WIDTH,
                height = ACTION_BUTTON_HEIGHT,
                actionId = PokemonTagScreenHandler.ACTION_OPEN_ROLE_POLICY,
                glyph = UiGlyph.Filter,
                accent = if (canEditPolicy) 0xFFF1B85E.toInt() else 0xFF6B7D8B.toInt(),
                active = false,
                tooltip = listOf(
                    Text.literal("Role policy"),
                    Text.literal(
                        if (!canEditPolicy) {
                            "Assign a role card before opening policy."
                        } else if (handler.isManagedByCommandPost) {
                            "Open the real Command Post role card for this worker."
                        } else {
                            "Open this worker's local role card for editing."
                        }
                    ),
                    Text.literal("Shortcut: R")
                )
            ),
            IconActionButton(
                left = 184,
                top = 112,
                width = ACTION_BUTTON_WIDTH,
                height = ACTION_BUTTON_HEIGHT,
                actionId = PokemonTagScreenHandler.ACTION_CYCLE_ASSIGNMENT_MODE,
                glyph = UiGlyph.Cycle,
                accent = assignmentColor(assignmentMode, handler.allowFallback) or 0xFF000000.toInt(),
                active = assignmentMode != WorkerAssignmentMode.GENERAL,
                tooltip = listOf(
                    Text.literal("Crew mode"),
                    Text.literal("Current: ${assignmentLabel(assignmentMode, handler.allowFallback)}"),
                    Text.literal("General: normal worker pool."),
                    Text.literal("Preferred: this pal gets first call on the role."),
                    Text.literal("Reserved: keep this pal out of general labor.")
                )
            ),
            IconActionButton(
                left = 198,
                top = 112,
                width = ACTION_BUTTON_WIDTH,
                height = ACTION_BUTTON_HEIGHT,
                actionId = PokemonTagScreenHandler.ACTION_TOGGLE_FALLBACK,
                glyph = if (handler.allowFallback) UiGlyph.Check else UiGlyph.Ban,
                accent = if (handler.allowFallback) 0xFF50C864.toInt() else 0xFFC85050.toInt(),
                active = !handler.allowFallback,
                tooltip = listOf(
                    Text.literal("Fallback policy"),
                    Text.literal(if (handler.allowFallback) "General workers may cover this role." else "General workers will stand down while a preferred pal is available."),
                    Text.literal("Use this with Preferred mode to create a restricted worker.")
                )
            )
        )
    }

    private fun hoveredTooltip(localMouseX: Int, localMouseY: Int): List<Text>? {
        return identityButtons().firstOrNull { button ->
            UiIconButtons.contains(localMouseX, localMouseY, button.left, button.top, button.width, button.height)
        }?.tooltip
    }

    private fun statusColor(statusReason: WorkerStatusReason): Int = when (statusReason.kind) {
        WorkerStatusKind.ACTIVE -> when (statusReason) {
            WorkerStatusReason.DEPOSITING -> 0xFF50A0DC.toInt()
            WorkerStatusReason.ARRIVING -> 0xFFC8B43C.toInt()
            WorkerStatusReason.NAVIGATING -> 0xFF8CD2FF.toInt()
            else -> 0xFF50C864.toInt()
        }
        WorkerStatusKind.READY -> 0xFF50C864.toInt()
        WorkerStatusKind.WAITING -> 0xFFC8B43C.toInt()
        WorkerStatusKind.BLOCKED -> 0xFFC85050.toInt()
        WorkerStatusKind.STANDBY -> 0xFF9D7AFF.toInt()
    }

    private fun bindingSummary(tagItem: TagItem?, spec: TagSpec?): String {
        if (tagItem == null) return "No role card installed"
        val boundArea = spec?.boundArea
        val boundPos = spec?.boundPos
        return when {
            boundArea != null -> "Area ${boundArea.width()}x${boundArea.depth()} selected"
            boundPos != null -> "Target ${boundPos.x}, ${boundPos.y}, ${boundPos.z}"
            tagItem.tagType.supportsBinding -> "Target not configured yet"
            else -> "This role does not need a bound target"
        }
    }

    private fun filterSummary(tagItem: TagItem?, spec: TagSpec?): String {
        if (tagItem == null || spec == null) return "No active filter rules"
        if (!tagItem.tagType.usesFilter) return "This role ignores item filters"
        val filter = spec.filter
        if (filter.isEmpty()) {
            return if (filter.whitelist) "Whitelist is empty" else "No filter restrictions"
        }

        val parts = buildList {
            if (filter.items.isNotEmpty()) add("${filter.items.size} item")
            if (filter.matchTags.isNotEmpty()) add("${filter.matchTags.size} tag")
            if (filter.matchModIds.isNotEmpty()) add("${filter.matchModIds.size} mod")
        }
        val mode = if (filter.whitelist) "Allow" else "Block"
        return "$mode ${parts.joinToString(" • ")}"
    }

    private fun settingsSummary(tagItem: TagItem?, spec: TagSpec?): String {
        if (tagItem == null || spec == null) return "Policy unavailable"
        val settings = spec.settings
        return when {
            settings.redstoneMode != RedstoneControlMode.ALWAYS -> "Signal ${compactValue(settings.redstoneMode.id)}"
            tagItem.tagType.supportsTargetList -> {
                val loop = if (settings.terminateAfterSuccess) "One pass" else "Loop"
                "${compactValue(settings.targetStrategy.id)} • $loop"
            }
            else -> "Standard execution"
        }
    }

    private fun augmentSummary(): String {
        val augmentCount = (0 until PokemonTagScreenHandler.AUGMENT_SLOT_COUNT).count { index ->
            handler.slots[PokemonTagScreenHandler.AUGMENT_SLOT_START + index].hasStack()
        }
        return if (augmentCount > 0) {
            "$augmentCount augment${if (augmentCount == 1) "" else "s"} installed"
        } else {
            "No augments installed"
        }
    }

    private fun assignmentLabel(mode: WorkerAssignmentMode, allowFallback: Boolean): String = when {
        mode == WorkerAssignmentMode.RESERVED -> "Reserved"
        mode == WorkerAssignmentMode.PREFERRED && !allowFallback -> "Restricted"
        mode == WorkerAssignmentMode.PREFERRED -> "Preferred"
        !allowFallback -> "Strict"
        else -> "General"
    }

    private fun assignmentColor(mode: WorkerAssignmentMode, allowFallback: Boolean): Int = when {
        mode == WorkerAssignmentMode.RESERVED -> 0xFF9D7AFF.toInt()
        mode == WorkerAssignmentMode.PREFERRED && !allowFallback -> 0xFFE0A050.toInt()
        mode == WorkerAssignmentMode.PREFERRED -> 0xFF50C864.toInt()
        !allowFallback -> 0xFFC8B43C.toInt()
        else -> 0xFF909098.toInt()
    }

    private fun truncate(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        return value.take(maxChars - 1) + "..."
    }

    private fun compactValue(value: String): String =
        value.split('_').joinToString(" ") { token -> token.replaceFirstChar(Char::titlecase) }
}

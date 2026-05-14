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

class PokemonTagScreen(
    handler: PokemonTagScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<PokemonTagScreenHandler>(handler, inventory, title) {

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

    companion object {
        private const val BACKGROUND_WIDTH = 260
        private const val BACKGROUND_HEIGHT = 272
        private const val HEADER_HEIGHT = 34
        private const val LOADOUT_LEFT = 10
        private const val LOADOUT_TOP = 48
        private const val LOADOUT_WIDTH = 112
        private const val LOADOUT_HEIGHT = 124
        private const val STATUS_LEFT = 130
        private const val STATUS_TOP = 48
        private const val STATUS_WIDTH = 120
        private const val STATUS_HEIGHT = 124
        private const val PLAYER_LEFT = 10
        private const val PLAYER_TOP = 178
        private const val PLAYER_WIDTH = 240
        private const val PLAYER_HEIGHT = 82
        private const val TAG_SLOT_X = 22
        private const val TAG_SLOT_Y = 72
        private const val AUGMENT_SLOT_X = 22
        private const val AUGMENT_SLOT_Y = 100
        private const val DISPLAY_SLOT_X = 58
        private const val DISPLAY_SLOT_Y = 72
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
        setSlotPosition(PokemonTagScreenHandler.TAG_SLOT, TAG_SLOT_X, TAG_SLOT_Y)
        for (index in 0 until PokemonTagScreenHandler.AUGMENT_SLOT_COUNT) {
            setSlotPosition(PokemonTagScreenHandler.AUGMENT_SLOT_START + index, AUGMENT_SLOT_X, AUGMENT_SLOT_Y + index * 18)
        }
        for (row in 0..2) {
            for (col in 0..2) {
                setSlotPosition(PokemonTagScreenHandler.DISPLAY_SLOT_START + row * 3 + col, DISPLAY_SLOT_X + col * 18, DISPLAY_SLOT_Y + row * 18)
            }
        }
        for (row in 0..2) {
            for (col in 0..8) {
                setSlotPosition(PokemonTagScreenHandler.PLAYER_SLOT_START + row * 9 + col, PLAYER_SLOT_X + col * 18, PLAYER_SLOT_Y + row * 18)
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
        CobblePalsUiTheme.drawPanel(context, x, y, LOADOUT_LEFT, LOADOUT_TOP, LOADOUT_WIDTH, LOADOUT_HEIGHT, CobblePalsUiTheme.jobsPanel)
        CobblePalsUiTheme.drawPanel(context, x, y, STATUS_LEFT, STATUS_TOP, STATUS_WIDTH, STATUS_HEIGHT, CobblePalsUiTheme.statusPanel)
        CobblePalsUiTheme.drawPanel(context, x, y, PLAYER_LEFT, PLAYER_TOP, PLAYER_WIDTH, PLAYER_HEIGHT, CobblePalsUiTheme.inventoryPanel)

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
        val localMouseX = mouseX - x
        val localMouseY = mouseY - y
        val statusReason = WorkerStatusReason.entries.getOrElse(handler.statusReasonOrdinal) { WorkerStatusReason.READY }
        val statusColor = statusColor(statusReason)
        val assignmentMode = WorkerAssignmentMode.fromOrdinal(handler.assignmentModeOrdinal)
        val tagStack = handler.slots[PokemonTagScreenHandler.TAG_SLOT].stack
        val tagItem = tagStack.item as? TagItem
        val registries = client?.world?.registryManager
        val spec = if (tagItem != null && registries != null) TagItem.getSpec(tagStack, registries) else null
        val roleLabel = tagItem?.let { TagTypePresentation.roleLabel(it.tagType) } ?: "No role"
        val familyLabel = tagItem?.let { TagTypePresentation.familyOf(it.tagType).label } ?: "Idle"
        val chipText = if (handler.isManagedByCommandPost) Text.literal("POST") else Text.literal("LOCAL")
        val chipStyle = if (handler.isManagedByCommandPost) CobblePalsUiTheme.linkedStateChip else CobblePalsUiTheme.unlinkedStateChip

        context.drawText(textRenderer, Text.literal("PAL DETAIL"), titleX, titleY, CobblePalsUiTheme.HEADER_TEXT, false)
        context.drawText(textRenderer, Text.literal(fit(roleLabel, 174)), 12, 20, CobblePalsUiTheme.SUBTITLE_TEXT, false)
        drawChipText(context, chipText, backgroundWidth - textRenderer.getWidth(chipText) - 22, 9, chipStyle)

        context.drawText(textRenderer, Text.literal("LOADOUT"), 14, 52, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("STATE"), 134, 52, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("INVENTORY"), playerInventoryTitleX, playerInventoryTitleY, CobblePalsUiTheme.TEXT_MUTED, false)

        context.drawText(textRenderer, Text.literal(fit(familyLabel, 98)), 134, 72, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(fit(statusReason.label, 98)), 134, 86, statusColor, false)
        context.drawText(textRenderer, Text.literal(if (handler.isEcoMode) "Eco active" else "Eco idle"), 134, 100, if (handler.isEcoMode) CobblePalsUiTheme.ACCENT_DANGER else CobblePalsUiTheme.TEXT_FAINT, false)
        context.drawText(textRenderer, Text.literal(cargoSummary()), 134, 114, cargoColor(), false)
        context.drawText(textRenderer, Text.literal(assignmentLabel(assignmentMode, handler.allowFallback)), 134, 128, assignmentColor(assignmentMode, handler.allowFallback), false)
        context.drawText(textRenderer, Text.literal(if (handler.allowFallback) "Fallback on" else "Fallback off"), 134, 142, CobblePalsUiTheme.TEXT_FAINT, false)

        context.drawText(textRenderer, Text.literal(fit(bindingSummary(tagItem, spec), 96)), 18, 148, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(fit(augmentSummary(), 96)), 18, 160, CobblePalsUiTheme.ACCENT_PURPLE, false)
        context.drawText(textRenderer, Text.literal(fit(filterSummary(tagItem, spec), 104)), 134, 156, CobblePalsUiTheme.TEXT_FAINT, false)

        iconButtons().forEach { button ->
            val hovered = UiIconButtons.contains(localMouseX, localMouseY, button.left, button.top, button.width, button.height)
            UiIconButtons.draw(context, button.left, button.top, button.width, button.height, button.glyph, button.accent, hovered, button.active)
        }
        chipButtons().forEach { button ->
            val hovered = UiIconButtons.contains(localMouseX, localMouseY, button.left, button.top, button.width, CHIP_HEIGHT)
            CobblePalsUiTheme.drawChip(context, textRenderer, button.left, button.top, button.label, button.value, button.accent, button.active, hovered)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val localMouseX = (mouseX - x).toInt()
            val localMouseY = (mouseY - y).toInt()
            iconButtons().firstOrNull { UiIconButtons.contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { action ->
                client?.interactionManager?.clickButton(handler.syncId, action.actionId)
                return true
            }
            chipButtons().firstOrNull { UiIconButtons.contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { action ->
                client?.interactionManager?.clickButton(handler.syncId, action.actionId)
                return true
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

    private fun hasAssignedRole(): Boolean = handler.slots[PokemonTagScreenHandler.TAG_SLOT].stack.item is TagItem

    private fun iconButtons(): List<IconActionButton> {
        return listOf(
            IconActionButton("prev", 202, 10, 12, 12, PokemonTagScreenHandler.ACTION_PREVIOUS_PAL, UiGlyph.Prev, CobblePalsUiTheme.ACCENT_BUFFER, false, listOf(Text.literal("Previous pal"), Text.literal("Step through the pasture roster."))),
            IconActionButton("next", 218, 10, 12, 12, PokemonTagScreenHandler.ACTION_NEXT_PAL, UiGlyph.Next, CobblePalsUiTheme.ACCENT_BUFFER, false, listOf(Text.literal("Next pal"), Text.literal("Step through the pasture roster.")))
        )
    }

    private fun chipButtons(): List<ChipActionButton> {
        val assignmentMode = WorkerAssignmentMode.fromOrdinal(handler.assignmentModeOrdinal)
        val canEditPolicy = hasAssignedRole()
        return listOf(
            ChipActionButton("mode", 134, 154, 86, "Mode", assignmentLabel(assignmentMode, handler.allowFallback), PokemonTagScreenHandler.ACTION_CYCLE_ASSIGNMENT_MODE, assignmentColor(assignmentMode, handler.allowFallback), assignmentMode != WorkerAssignmentMode.GENERAL, listOf(Text.literal("Crew mode"), Text.literal("Current: ${assignmentLabel(assignmentMode, handler.allowFallback)}"))),
            ChipActionButton("fallback", 134, 170, 86, "Fallback", if (handler.allowFallback) "On" else "Off", PokemonTagScreenHandler.ACTION_TOGGLE_FALLBACK, if (handler.allowFallback) CobblePalsUiTheme.ACCENT_CREW else CobblePalsUiTheme.ACCENT_DANGER, !handler.allowFallback, listOf(Text.literal("Fallback"), Text.literal(if (handler.allowFallback) "General labor allowed." else "General labor locked."))),
            ChipActionButton("policy", 22, 154, 86, "Role", if (canEditPolicy) "Policy" else "None", PokemonTagScreenHandler.ACTION_OPEN_ROLE_POLICY, if (canEditPolicy) CobblePalsUiTheme.ACCENT_POLICY else CobblePalsUiTheme.TEXT_FAINT, canEditPolicy, listOf(Text.literal("Role policy"), Text.literal(if (canEditPolicy) "Open this role's policy." else "Assign a role first.")))
        )
    }

    private fun hoveredTooltip(localMouseX: Int, localMouseY: Int): List<Text>? {
        iconButtons().firstOrNull { UiIconButtons.contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { return it.tooltip }
        chipButtons().firstOrNull { UiIconButtons.contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { return it.tooltip }
        return null
    }

    private fun drawChipText(context: DrawContext, text: Text, left: Int, top: Int, style: com.cobblepalsworld.gui.UiChipStyle) {
        val width = textRenderer.getWidth(text) + 14
        context.fill(left, top, left + width, top + 13, style.bodyColor)
        context.fill(left, top, left + 3, top + 13, style.accentColor)
        context.drawText(textRenderer, text, left + 7, top + 3, style.textColor, false)
    }

    private fun statusColor(statusReason: WorkerStatusReason): Int = when (statusReason.kind) {
        WorkerStatusKind.ACTIVE -> when (statusReason) {
            WorkerStatusReason.DEPOSITING -> CobblePalsUiTheme.ACCENT_BUFFER
            WorkerStatusReason.ARRIVING -> CobblePalsUiTheme.ACCENT_POLICY
            WorkerStatusReason.NAVIGATING -> 0xFF8CD2FF.toInt()
            else -> CobblePalsUiTheme.ACCENT_WORK
        }
        WorkerStatusKind.READY -> CobblePalsUiTheme.ACCENT_CREW
        WorkerStatusKind.WAITING -> CobblePalsUiTheme.ACCENT_POLICY
        WorkerStatusKind.BLOCKED -> CobblePalsUiTheme.ACCENT_DANGER
        WorkerStatusKind.STANDBY -> CobblePalsUiTheme.ACCENT_PURPLE
    }

    private fun cargoSummary(): String {
        val carryMax = handler.carryMax
        return if (carryMax > 0) "Cargo ${handler.carryCount}/$carryMax" else "Cargo empty"
    }

    private fun cargoColor(): Int {
        val carryMax = handler.carryMax
        return when {
            carryMax <= 0 -> CobblePalsUiTheme.TEXT_FAINT
            handler.carryCount >= carryMax -> CobblePalsUiTheme.ACCENT_DANGER
            handler.carryCount > 0 -> CobblePalsUiTheme.ACCENT_CREW
            else -> CobblePalsUiTheme.TEXT_MUTED
        }
    }

    private fun bindingSummary(tagItem: TagItem?, spec: TagSpec?): String {
        if (tagItem == null) return "No role card"
        val boundArea = spec?.boundArea
        val boundPos = spec?.boundPos
        return when {
            boundArea != null -> "Area ${boundArea.width()}x${boundArea.depth()}"
            boundPos != null -> "Target ${boundPos.x}, ${boundPos.y}, ${boundPos.z}"
            tagItem.tagType.supportsBinding -> "Target missing"
            else -> "No target needed"
        }
    }

    private fun filterSummary(tagItem: TagItem?, spec: TagSpec?): String {
        if (tagItem == null || spec == null) return "No filter"
        if (!tagItem.tagType.usesFilter) return "No item filter"
        val filter = spec.filter
        if (filter.isEmpty()) return if (filter.whitelist) "Allow empty" else "No restrictions"
        val parts = buildList {
            if (filter.items.isNotEmpty()) add("${filter.items.size} item")
            if (filter.matchTags.isNotEmpty()) add("${filter.matchTags.size} tag")
            if (filter.matchModIds.isNotEmpty()) add("${filter.matchModIds.size} mod")
        }
        return "${if (filter.whitelist) "Allow" else "Block"} ${parts.joinToString(" • ")}"
    }

    private fun augmentSummary(): String {
        val augmentCount = (0 until PokemonTagScreenHandler.AUGMENT_SLOT_COUNT).count { index ->
            handler.slots[PokemonTagScreenHandler.AUGMENT_SLOT_START + index].hasStack()
        }
        return if (augmentCount > 0) "$augmentCount augment${if (augmentCount == 1) "" else "s"}" else "No augments"
    }

    private fun assignmentLabel(mode: WorkerAssignmentMode, allowFallback: Boolean): String = when {
        mode == WorkerAssignmentMode.RESERVED -> "Reserved"
        mode == WorkerAssignmentMode.PREFERRED && !allowFallback -> "Restricted"
        mode == WorkerAssignmentMode.PREFERRED -> "Preferred"
        !allowFallback -> "Strict"
        else -> "General"
    }

    private fun assignmentColor(mode: WorkerAssignmentMode, allowFallback: Boolean): Int = when {
        mode == WorkerAssignmentMode.RESERVED -> CobblePalsUiTheme.ACCENT_PURPLE
        mode == WorkerAssignmentMode.PREFERRED && !allowFallback -> CobblePalsUiTheme.ACCENT_POLICY
        mode == WorkerAssignmentMode.PREFERRED -> CobblePalsUiTheme.ACCENT_CREW
        !allowFallback -> CobblePalsUiTheme.ACCENT_POLICY
        else -> CobblePalsUiTheme.TEXT_MUTED
    }

    private fun fit(value: String, maxWidth: Int): String {
        if (textRenderer.getWidth(value) <= maxWidth) return value
        var clipped = value
        while (clipped.isNotEmpty() && textRenderer.getWidth("$clipped...") > maxWidth) clipped = clipped.dropLast(1)
        return if (clipped.isEmpty()) "..." else "$clipped..."
    }
}
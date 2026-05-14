package com.cobblepalsworld.gui.assignment

import com.cobblepalsworld.assignment.WorkerAssignmentMode
import com.cobblepalsworld.behavior.state.WorkerStatusKind
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.gui.CobblemonUiChrome
import com.cobblepalsworld.tag.RedstoneControlMode
import com.cobblepalsworld.tag.TagItem
import com.cobblepalsworld.tag.TagSettings
import com.cobblepalsworld.tag.TagSpec
import com.cobblepalsworld.tag.TagTypePresentation
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW

class PokemonTagScreen(
    handler: PokemonTagScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<PokemonTagScreenHandler>(handler, inventory, title) {

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

    companion object {
        private const val BACKGROUND_WIDTH = CobblemonUiChrome.BASE_WIDTH
        private const val BACKGROUND_HEIGHT = CobblemonUiChrome.BASE_HEIGHT
        private const val TAG_SLOT_X = 32
        private const val TAG_SLOT_Y = 52
        private const val AUGMENT_SLOT_X = 14
        private const val AUGMENT_SLOT_Y = 82
        private const val DISPLAY_SLOT_X = 94
        private const val DISPLAY_SLOT_Y = 54
        private const val PLAYER_SLOT_X = 91
        private const val PLAYER_SLOT_Y = 113
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
        setSlotPosition(PokemonTagScreenHandler.TAG_SLOT, TAG_SLOT_X, TAG_SLOT_Y)
        for (index in 0 until PokemonTagScreenHandler.AUGMENT_SLOT_COUNT) {
            setSlotPosition(PokemonTagScreenHandler.AUGMENT_SLOT_START + index, AUGMENT_SLOT_X + index * 18, AUGMENT_SLOT_Y)
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
        val localMouseX = mouseX - x
        val localMouseY = mouseY - y

        CobblemonUiChrome.drawPcBase(context, x, y)
        CobblemonUiChrome.drawPortraitPanel(context, x, y)
        CobblemonUiChrome.drawInfoBox(context, x, y)
        CobblemonUiChrome.drawStorageScreen(context, x, y)
        CobblemonUiChrome.drawPasturePanel(context, x, y)
        CobblemonUiChrome.drawBackButton(context, x, y, localMouseX, localMouseY, BACK_LEFT, BACK_TOP)

        drawSlotFrame(context, TAG_SLOT_X, TAG_SLOT_Y, 0.72F)
        for (index in 0 until PokemonTagScreenHandler.AUGMENT_SLOT_COUNT) {
            drawSlotFrame(context, AUGMENT_SLOT_X + index * 18, AUGMENT_SLOT_Y, 0.58F)
        }
        for (row in 0..2) {
            for (col in 0..2) drawSlotFrame(context, DISPLAY_SLOT_X + col * 18, DISPLAY_SLOT_Y + row * 18, 0.52F)
        }
        for (row in 0..2) {
            for (col in 0..8) drawSlotFrame(context, PLAYER_SLOT_X + col * 18, PLAYER_SLOT_Y + row * 18, 0.45F)
        }
        for (col in 0..8) drawSlotFrame(context, PLAYER_SLOT_X + col * 18, PLAYER_SLOT_Y + 58, 0.45F)
    }

    override fun drawForeground(context: DrawContext, mouseX: Int, mouseY: Int) {
        val localMouseX = mouseX - x
        val localMouseY = mouseY - y
        val statusReason = WorkerStatusReason.entries.getOrElse(handler.statusReasonOrdinal) { WorkerStatusReason.READY }
        val assignmentMode = WorkerAssignmentMode.fromOrdinal(handler.assignmentModeOrdinal)
        val tagStack = handler.slots[PokemonTagScreenHandler.TAG_SLOT].stack
        val tagItem = tagStack.item as? TagItem
        val registries = client?.world?.registryManager
        val spec = if (tagItem != null && registries != null) TagItem.getSpec(tagStack, registries) else null
        val roleLabel = tagItem?.let { TagTypePresentation.roleLabel(it.tagType) } ?: "No role"
        val familyLabel = tagItem?.let { TagTypePresentation.familyOf(it.tagType).label } ?: "Idle"

        text(context, "PAL DETAIL", 16, 16, CobblemonUiChrome.TEXT_LIGHT, true, 0.7F)
        text(context, fit(roleLabel, 112, 0.5F), 88, 17, CobblemonUiChrome.TEXT_LIGHT, true)
        text(context, if (handler.isManagedByCommandPost) "COMMAND POST" else "LOCAL PAL", 273, 16, CobblemonUiChrome.TEXT_LIGHT, true)
        text(context, "LOADOUT", 18, 35, CobblemonUiChrome.TEXT_DARK, false)
        text(context, "PAL PACK", 94, 43, CobblemonUiChrome.TEXT_LIGHT, true)
        text(context, "BAG", 94, 102, CobblemonUiChrome.TEXT_LIGHT, true)

        text(context, fit(bindingSummary(tagItem, spec), 112, 0.5F), 14, 136, CobblemonUiChrome.TEXT_DARK, false)
        text(context, fit(augmentSummary(), 112, 0.5F), 14, 149, CobblemonUiChrome.ACCENT_PURPLE, false)
        text(context, fit(filterSummary(tagItem, spec), 112, 0.5F), 14, 162, CobblemonUiChrome.TEXT_MUTED, false)
        text(context, fit(behaviorSummary(tagItem, spec), 112, 0.5F), 14, 175, CobblemonUiChrome.ACCENT_BLUE, false)

        text(context, fit(familyLabel, 118, 0.5F), 276, 33, CobblemonUiChrome.TEXT_DARK, false)
        text(context, fit(statusReason.label, 118, 0.5F), 276, 46, statusColor(statusReason), false)
        text(context, if (handler.isEcoMode) "Eco pacing active" else "Normal pacing", 276, 59, if (handler.isEcoMode) CobblemonUiChrome.ACCENT_GOLD else CobblemonUiChrome.TEXT_MUTED, false)

        textureButtons().forEach { button ->
            CobblemonUiChrome.drawButton(context, x, y, CobblemonUiChrome.TextureButton(button.left, button.top, button.width, button.height, button.texture, button.scaled), localMouseX, localMouseY)
        }
        panelButtons().forEach { button ->
            val hovered = CobblemonUiChrome.contains(localMouseX, localMouseY, button.left, button.top, button.width, PANEL_BUTTON_HEIGHT)
            CobblemonUiChrome.drawInfoButton(context, x, y, button.left, button.top, button.width, hovered, button.active)
            text(context, button.label, button.left + 5, button.top + 5, CobblemonUiChrome.TEXT_LIGHT, true)
            text(context, fit(button.value, 60, 0.5F), button.left + 5, button.top + 13, CobblemonUiChrome.TEXT_LIGHT, true)
        }
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
            textureButtons().firstOrNull { CobblemonUiChrome.contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { action ->
                CobblemonUiChrome.playClick()
                client?.interactionManager?.clickButton(handler.syncId, action.actionId)
                return true
            }
            panelButtons().firstOrNull { CobblemonUiChrome.contains(localMouseX, localMouseY, it.left, it.top, it.width, PANEL_BUTTON_HEIGHT) }?.let { action ->
                CobblemonUiChrome.playClick()
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
        CobblemonUiChrome.playClick()
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

    private fun textureButtons(): List<TextureActionButton> = listOf(
        TextureActionButton("prev", 286, 15, 7, 7, PokemonTagScreenHandler.ACTION_PREVIOUS_PAL, CobblemonUiChrome.NAV_PREVIOUS, true, listOf(Text.literal("Previous pal"))),
        TextureActionButton("next", 302, 15, 7, 7, PokemonTagScreenHandler.ACTION_NEXT_PAL, CobblemonUiChrome.NAV_NEXT, true, listOf(Text.literal("Next pal")))
    )

    private fun panelButtons(): List<PanelActionButton> {
        val assignmentMode = WorkerAssignmentMode.fromOrdinal(handler.assignmentModeOrdinal)
        val canEditPolicy = hasAssignedRole()
        return listOf(
            PanelActionButton("mode", PANEL_BUTTON_X, 75, PANEL_BUTTON_WIDTH, "Mode", assignmentLabel(assignmentMode, handler.allowFallback), PokemonTagScreenHandler.ACTION_CYCLE_ASSIGNMENT_MODE, assignmentMode != WorkerAssignmentMode.GENERAL, listOf(Text.literal("Crew mode"), Text.literal("Current: ${assignmentLabel(assignmentMode, handler.allowFallback)}"))),
            PanelActionButton("fallback", PANEL_BUTTON_X, 96, PANEL_BUTTON_WIDTH, "Fallback", if (handler.allowFallback) "On" else "Off", PokemonTagScreenHandler.ACTION_TOGGLE_FALLBACK, !handler.allowFallback, listOf(Text.literal("Fallback"), Text.literal(if (handler.allowFallback) "General labor allowed." else "General labor locked."))),
            PanelActionButton("policy", PANEL_BUTTON_X, 117, PANEL_BUTTON_WIDTH, "Role", if (canEditPolicy) "Policy" else "None", PokemonTagScreenHandler.ACTION_OPEN_ROLE_POLICY, canEditPolicy, listOf(Text.literal("Role policy"), Text.literal(if (canEditPolicy) "Open this role's policy." else "Assign a role first.")))
        )
    }

    private fun hoveredTooltip(localMouseX: Int, localMouseY: Int): List<Text>? {
        textureButtons().firstOrNull { CobblemonUiChrome.contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { return it.tooltip }
        panelButtons().firstOrNull { CobblemonUiChrome.contains(localMouseX, localMouseY, it.left, it.top, it.width, PANEL_BUTTON_HEIGHT) }?.let { return it.tooltip }
        return null
    }

    private fun drawSlotFrame(context: DrawContext, localX: Int, localY: Int, alpha: Float) {
        CobblemonUiChrome.drawSlotFrame(context, x, y, localX, localY, alpha)
    }

    private fun text(context: DrawContext, value: String, localX: Int, localY: Int, color: Int, shadow: Boolean, scale: Float = CobblemonUiChrome.TEXTURE_SCALE) {
        CobblemonUiChrome.drawSmallText(context, textRenderer, value, x + localX, y + localY, color, shadow, scale)
    }

    private fun statusColor(statusReason: WorkerStatusReason): Int = when (statusReason.kind) {
        WorkerStatusKind.ACTIVE -> when (statusReason) {
            WorkerStatusReason.DEPOSITING -> CobblemonUiChrome.ACCENT_BLUE
            WorkerStatusReason.ARRIVING -> CobblemonUiChrome.ACCENT_GOLD
            WorkerStatusReason.NAVIGATING -> CobblemonUiChrome.ACCENT_BLUE
            else -> CobblemonUiChrome.ACCENT_GREEN
        }
        WorkerStatusKind.READY -> CobblemonUiChrome.ACCENT_GREEN
        WorkerStatusKind.WAITING -> CobblemonUiChrome.ACCENT_GOLD
        WorkerStatusKind.BLOCKED -> CobblemonUiChrome.ACCENT_RED
        WorkerStatusKind.STANDBY -> CobblemonUiChrome.ACCENT_PURPLE
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
        return "${if (filter.whitelist) "Allow" else "Block"} ${parts.joinToString(", ")}".trim()
    }

    private fun behaviorSummary(tagItem: TagItem?, spec: TagSpec?): String {
        if (tagItem == null) return "No behavior"
        val settings = spec?.settings ?: TagSettings.EMPTY
        val parts = mutableListOf<String>()
        if (settings.redstoneMode != RedstoneControlMode.ALWAYS) parts += "Signal ${humanValue(settings.redstoneMode.id)}"
        if (tagItem.tagType.supportsTargetList) {
            parts += humanValue(settings.targetStrategy.id)
            parts += if (settings.terminateAfterSuccess) "One pass" else "Loop"
            if (settings.regulatorAmount != 64) parts += "Keep ${settings.regulatorAmount}"
        }
        return if (parts.isEmpty()) "Standard behavior" else parts.joinToString(" / ")
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

    private fun fit(value: String, maxWidth: Int, scale: Float): String {
        if (textRenderer.getWidth(value) * scale <= maxWidth) return value
        var clipped = value
        while (clipped.isNotEmpty() && textRenderer.getWidth("$clipped...") * scale > maxWidth) clipped = clipped.dropLast(1)
        return if (clipped.isEmpty()) "..." else "$clipped..."
    }

    private fun humanValue(value: String): String {
        return value.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase)
    }
}
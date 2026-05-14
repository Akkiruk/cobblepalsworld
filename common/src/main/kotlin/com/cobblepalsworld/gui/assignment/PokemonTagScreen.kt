package com.cobblepalsworld.gui.assignment

import com.cobblepalsworld.behavior.state.WorkerStatusKind
import com.cobblepalsworld.behavior.state.WorkerStatusReason
import com.cobblepalsworld.gui.UiGlyph
import com.cobblepalsworld.gui.UiIconButtons
import com.cobblepalsworld.pasture.WorkerAssignmentMode
import com.cobblepalsworld.tag.RedstoneControlMode
import com.cobblepalsworld.tag.TagItem
import com.cobblepalsworld.tag.TagType
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.math.roundToInt

class PokemonTagScreen(
    handler: PokemonTagScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<PokemonTagScreenHandler>(handler, inventory, title) {

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
        private val TEXTURE = Identifier.of("cobblepalsworld", "textures/gui/pokemon_tag.png")
    }

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

        val sx = 121
        var sy = 17

        if (handler.isManagedByCommandPost) {
            context.drawText(textRenderer, Text.literal("Managed"), sx, sy, 0xE0A050, false)
            context.drawText(textRenderer, Text.literal("by Post"), sx, sy + 9, 0xE0A050, false)
            sy += 20
        }

        val statusReason = WorkerStatusReason.entries.getOrElse(handler.statusReasonOrdinal) { WorkerStatusReason.READY }
        val statusColor = when (statusReason.kind) {
            WorkerStatusKind.ACTIVE -> when (statusReason) {
                WorkerStatusReason.DEPOSITING -> 0x50A0DC
                WorkerStatusReason.ARRIVING -> 0xC8B43C
                WorkerStatusReason.NAVIGATING -> 0x8CD2FF
                else -> 0x50C864
            }
            WorkerStatusKind.READY -> 0x50C864
            WorkerStatusKind.WAITING -> 0xC8B43C
            WorkerStatusKind.BLOCKED -> 0xC85050
            WorkerStatusKind.STANDBY -> 0x9D7AFF
        }
        context.fill(sx, sy + 1, sx + 5, sy + 6, 0xFF000000.toInt() or statusColor)
        context.drawText(textRenderer, Text.literal(statusReason.label), sx + 7, sy, statusColor, false)

        if (handler.isEcoMode) {
            context.fill(sx + 40, sy + 1, sx + 45, sy + 6, 0xFFC85050.toInt())
        }

        sy += 12

        val carryMax = handler.carryMax
        val carryCount = handler.carryCount
        val carryColor = when {
            carryMax <= 0 -> 0x505058
            carryCount >= carryMax -> 0xC85050
            carryCount > 0 -> 0x50C864
            else -> 0x909098
        }
        val carryLabel = if (carryMax > 0) "Carry: $carryCount/$carryMax" else "Carry: empty"
        context.drawText(textRenderer, Text.literal(carryLabel), sx, sy, carryColor, false)

        sy += 12

        val assignmentMode = WorkerAssignmentMode.fromOrdinal(handler.assignmentModeOrdinal)
        context.drawText(textRenderer, Text.literal(assignmentLabel(assignmentMode, handler.allowFallback)), sx, sy, assignmentColor(assignmentMode, handler.allowFallback), false)
        context.drawText(textRenderer, Text.literal(if (handler.allowFallback) "Fallback on" else "Fallback off"), sx + 16, sy + 9, 0x707078, false)
        identityButtons().forEach { button ->
            val hovered = UiIconButtons.contains(mouseX - x, mouseY - y, button.left, button.top, button.width, button.height)
            UiIconButtons.draw(context, button.left, button.top, button.width, button.height, button.glyph, button.accent, hovered, button.active)
        }

        sy += 22

        val tagStack = handler.slots[PokemonTagScreenHandler.TAG_SLOT].stack
        if (!tagStack.isEmpty && tagStack.item is TagItem) {
            val tagItem = tagStack.item as TagItem
            val registries = client?.world?.registryManager
            val spec = registries?.let { TagItem.getSpec(tagStack, it) }

            if (tagItem.tagType.supportsBinding) {
                val boundArea = spec?.boundArea
                val boundPos = spec?.boundPos
                if (boundArea != null) {
                    context.drawText(textRenderer, Text.literal("${boundArea.width()}x${boundArea.depth()}"), sx, sy, 0x50C864, false)
                    context.drawText(textRenderer, Text.literal("area"), sx, sy + 9, 0x50C864, false)
                } else if (boundPos != null) {
                    context.drawText(textRenderer, Text.literal("${boundPos.x},${boundPos.y}"), sx, sy, 0x50C864, false)
                    context.drawText(textRenderer, Text.literal(",${boundPos.z}"), sx, sy + 9, 0x50C864, false)
                } else {
                    val label = if (tagItem.tagType == TagType.BREAKER || tagItem.tagType == TagType.HARVESTER) "Unselected" else "Unbound"
                    context.drawText(textRenderer, Text.literal(label), sx, sy, 0x504848, false)
                }
                sy += 20
            } else {
                sy += 11
            }

            if (spec != null) {
                val filter = spec.filter
                val filterText = if (filter.items.isEmpty()) {
                    if (filter.whitelist) "Allow none" else "Allow all"
                } else {
                    "${if (filter.whitelist) "Allow" else "Block"} ${filter.items.size}"
                }
                context.drawText(textRenderer, Text.literal(filterText), sx, sy, 0x707078, false)

                val settings = spec.settings
                val settingsText = when {
                    settings.redstoneMode != RedstoneControlMode.ALWAYS -> compactValue(settings.redstoneMode.id)
                    tagItem.tagType.supportsTargetList -> compactValue(settings.targetStrategy.id)
                    else -> null
                }
                if (settingsText != null) {
                    context.drawText(textRenderer, Text.literal(settingsText), sx, sy + 9, 0x6060A0, false)
                }
            }
        }
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

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)
        hoveredTooltip(mouseX - x, mouseY - y)?.let { tooltip ->
            context.drawTooltip(textRenderer, tooltip, mouseX, mouseY)
        }
    }

    private fun identityButtons(): List<IconActionButton> {
        val assignmentMode = WorkerAssignmentMode.fromOrdinal(handler.assignmentModeOrdinal)
        return listOf(
            IconActionButton(
                left = 121,
                top = 42,
                width = 10,
                height = 10,
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
                left = 135,
                top = 42,
                width = 10,
                height = 10,
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

    private fun assignmentLabel(mode: WorkerAssignmentMode, allowFallback: Boolean): String = when {
        mode == WorkerAssignmentMode.RESERVED -> "Reserved"
        mode == WorkerAssignmentMode.PREFERRED && !allowFallback -> "Restricted"
        mode == WorkerAssignmentMode.PREFERRED -> "Preferred"
        !allowFallback -> "Strict"
        else -> "General"
    }

    private fun assignmentColor(mode: WorkerAssignmentMode, allowFallback: Boolean): Int = when {
        mode == WorkerAssignmentMode.RESERVED -> 0x9D7AFF
        mode == WorkerAssignmentMode.PREFERRED && !allowFallback -> 0xE0A050
        mode == WorkerAssignmentMode.PREFERRED -> 0x50C864
        !allowFallback -> 0xC8B43C
        else -> 0x909098
    }

    private fun compactValue(value: String): String =
        value.split('_').joinToString(" ") { token -> token.replaceFirstChar(Char::titlecase) }
}

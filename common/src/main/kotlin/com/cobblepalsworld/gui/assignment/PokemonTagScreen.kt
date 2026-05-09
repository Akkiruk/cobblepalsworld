package com.cobblepalsworld.gui.assignment

import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.tag.TagItem
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class PokemonTagScreen(
    handler: PokemonTagScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<PokemonTagScreenHandler>(handler, inventory, title) {

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

        // Status panel (right area: x=121..169, y=17..69)
        val sx = 121
        var sy = 17

        if (handler.isManagedByCommandPost) {
            context.drawText(textRenderer, Text.literal("Managed"), sx, sy, 0xE0A050, false)
            context.drawText(textRenderer, Text.literal("by Post"), sx, sy + 9, 0xE0A050, false)
            sy += 20
        }

        // Phase indicator
        val phase = WorkerPhase.entries.getOrElse(handler.workerPhase) { WorkerPhase.IDLE }
        val (phaseColor, phaseLabel) = when (phase) {
            WorkerPhase.IDLE -> 0x787890 to "Idle"
            WorkerPhase.NAVIGATING -> 0xC8B43C to "Moving"
            WorkerPhase.ARRIVING -> 0xC8B43C to "Arriving"
            WorkerPhase.WORKING -> 0x50C864 to "Working"
            WorkerPhase.DEPOSITING -> 0x50A0DC to "Storing"
        }
        context.fill(sx, sy + 1, sx + 5, sy + 6, 0xFF000000.toInt() or phaseColor)
        context.drawText(textRenderer, Text.literal(phaseLabel), sx + 7, sy, phaseColor, false)

        if (handler.isEcoMode) {
            context.fill(sx + 40, sy + 1, sx + 45, sy + 6, 0xFFC85050.toInt())
        }

        sy += 12

        // Carry count — always show with label
        val carryMax = handler.carryMax
        val carryCount = handler.carryCount
        val color = when {
            carryMax <= 0 -> 0x505058
            carryCount >= carryMax -> 0xC85050
            carryCount > 0 -> 0x50C864
            else -> 0x909098
        }
        val carryLabel = if (carryMax > 0) "Carry: $carryCount/$carryMax" else "Carry: empty"
        context.drawText(textRenderer, Text.literal(carryLabel), sx, sy, color, false)

        sy += 12

        // Tag-specific info
        val tagStack = handler.slots[PokemonTagScreenHandler.TAG_SLOT].stack
        if (!tagStack.isEmpty && tagStack.item is TagItem) {
            val tagItem = tagStack.item as TagItem

            if (tagItem.tagType.supportsBinding) {
                val boundPos = TagItem.getBoundPos(tagStack)
                if (boundPos != null) {
                    context.drawText(textRenderer, Text.literal("${boundPos.x},${boundPos.y}"), sx, sy, 0x50C864, false)
                    context.drawText(textRenderer, Text.literal(",${boundPos.z}"), sx, sy + 9, 0x50C864, false)
                } else {
                    context.drawText(textRenderer, Text.literal("Unbound"), sx, sy, 0x504848, false)
                }
                sy += 20
            } else {
                sy += 11
            }

            val registries = client!!.world?.registryManager
            if (registries != null) {
                val filter = TagItem.getFilter(tagStack, registries)
                val filterText = if (filter.items.isEmpty()) {
                    if (filter.whitelist) "No filter" else "All"
                } else {
                    "${filter.items.size} filter"
                }
                context.drawText(textRenderer, Text.literal(filterText), sx, sy, 0x707078, false)
            }
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)
    }
}

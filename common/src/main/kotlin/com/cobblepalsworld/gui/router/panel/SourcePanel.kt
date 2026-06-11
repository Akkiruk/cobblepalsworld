package com.cobblepalsworld.gui.router.panel

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblepalsworld.gui.crew.CrewSourceBoxSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceSnapshotCache
import com.cobblepalsworld.gui.crew.CrewSourceType
import com.cobblepalsworld.gui.router.CommandPostMode
import com.cobblepalsworld.gui.router.CommandPostPartySlot
import com.cobblepalsworld.gui.router.CommandPostPartyWidget
import com.cobblepalsworld.gui.router.CommandPostStorageSlot
import com.cobblepalsworld.gui.router.CommandPostStorageWidget
import com.cobblepalsworld.networking.CobblePalsNetworking
import net.minecraft.client.gui.DrawContext
import java.util.Locale

internal class SourcePanel(private val ctx: CommandPostPanelContext) : CommandPostPanel {
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        drawStorageScreen(context, ctx.currentSourceBox(CrewSourceSnapshotCache.get(ctx.handler.routerPos)), mouseX, mouseY, delta)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val localMouseX = (mouseX - ctx.originX).toInt()
        val localMouseY = (mouseY - ctx.originY).toInt()
        ctx.slotHits.firstOrNull { hit -> ctx.contains(localMouseX, localMouseY, hit.left, hit.top, hit.size, hit.size) }?.slot?.pokemon?.let { pokemon ->
            ctx.selectedPokemonId = pokemon.pokemonId
            if (pokemon.isCrewMember) {
                CobblePalsNetworking.sendRemoveCrewPokemon(ctx.handler.routerPos, pokemon.pokemonId)
                ctx.play(CobblemonSounds.PC_DROP)
                ctx.requestCrewRefresh()
                ctx.requestSourceRefresh()
                return true
            }
            if (pokemon.isAvailable) {
                CobblePalsNetworking.sendAssignCrewPokemon(ctx.handler.routerPos, pokemon.pokemonId)
                ctx.play(CobblemonSounds.PC_GRAB)
                ctx.requestCrewRefresh()
                ctx.requestSourceRefresh()
                return true
            }
            ctx.play(CobblemonSounds.PC_CLICK)
            return true
        }
        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val localMouseX = (mouseX - ctx.originX).toInt()
        val localMouseY = (mouseY - ctx.originY).toInt()
        if (ctx.sourceType == CrewSourceType.PC && CommandPostStorageWidget.contains(localMouseX, localMouseY)) {
            ctx.changeBox(if (verticalAmount > 0) -1 else 1)
            return true
        }
        return false
    }

    override fun onShown(previousMode: CommandPostMode?) {
        if (previousMode != CommandPostMode.SOURCE) {
            ctx.requestSourceRefresh()
        }
    }

    private fun drawStorageScreen(context: DrawContext, box: CrewSourceBoxSnapshot?, localMouseX: Int, localMouseY: Int, delta: Float) = with(ctx) {
                val title = if (sourceType == CrewSourceType.PARTY) "Party" else box?.label ?: "Box ${sourceBoxIndex + 1}"
                CommandPostStorageWidget.drawTitle(context, textRenderer, originX, originY, title)
        
                if (sourceType == CrewSourceType.PARTY) {
                    CommandPostPartyWidget.drawPanel(context, textRenderer, originX, originY)
                } else {
                    CommandPostStorageWidget.drawFrame(context, originX, originY)
                }
        
                val hits = mutableListOf<CommandPostStorageSlot.Hit>()
                val slots = box?.slots.orEmpty()
                slots.forEachIndexed { index, rawSlot ->
                    val slot = rawSlot.copy(pokemon = rawSlot.pokemon?.takeIf(::sourceMatchesFilters))
                    val left: Int
                    val top: Int
                    val size: Int
                    if (sourceType == CrewSourceType.PARTY) {
                        val bounds = CommandPostPartySlot.bounds(index) ?: return@forEachIndexed
                        left = bounds.left
                        top = bounds.top
                        size = bounds.size
                    } else {
                        val gridIndex = slot.slotIndex
                        if (gridIndex !in 0 until CommandPostStorageWidget.BOX_SLOT_COUNT) return@forEachIndexed
                        left = CommandPostStorageWidget.slotLeft(gridIndex)
                        top = CommandPostStorageWidget.slotTop(gridIndex)
                        size = CommandPostStorageWidget.SLOT_SIZE
                    }
                    hits += CommandPostStorageSlot.Hit(slot, left, top, size)
                    CommandPostStorageSlot.draw(context, textRenderer, originX, originY, slot, left, top, size, localMouseX, localMouseY, delta, selectedPokemonId, pointerOffsetY, ::heldStack) { view, centerX, topY, renderDelta, modelScale, matrixScale, animate ->
                        renderPokemonForPanel(
                            context = context,
                            view = view,
                            centerX = centerX,
                            topY = topY,
                            delta = renderDelta,
                            modelScale = modelScale,
                            matrixScale = matrixScale,
                            stateKey = "source-slot-${sourceType.name.lowercase(Locale.ROOT)}-${slot.slotIndex}",
                            animate = animate,
                            clipHalfWidth = 16,
                            clipHeight = 29
                        )
                    }?.let { hover ->
                        hoveredSource = hover.pokemon
                        hoveredTooltip = CommandPostPanelContext.HoverTooltip("source-${hover.pokemon.pokemonId}", sourceTooltip(hover.pokemon))
                    }
                }
                slotHits = hits
            
    }
}

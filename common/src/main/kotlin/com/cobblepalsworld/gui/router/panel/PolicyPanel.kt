package com.cobblepalsworld.gui.router.panel

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblepalsworld.gui.router.CommandPostMode
import com.cobblepalsworld.gui.router.CommandPostPcShell
import com.cobblepalsworld.gui.router.CommandPostStorageWidget
import com.cobblepalsworld.gui.router.RouterScreenHandler
import com.cobblepalsworld.tag.TagTypePresentation
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Formatting
import net.minecraft.text.Text

internal class PolicyPanel(private val ctx: CommandPostPanelContext) : CommandPostPanel {
    var scrollIndex = 0
    var selectedModuleIndex: Int? = null

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        drawPolicyPanel(context, mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val localMouseX = (mouseX - ctx.originX).toInt()
        val localMouseY = (mouseY - ctx.originY).toInt()
        ctx.panelHits.firstOrNull { hit -> ctx.contains(localMouseX, localMouseY, hit.left, hit.top, hit.width, hit.height) }?.let { hit ->
            if (hit.action()) {
                ctx.play(CobblemonSounds.PC_CLICK)
                return true
            }
        }
        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val localMouseX = (mouseX - ctx.originX).toInt()
        val localMouseY = (mouseY - ctx.originY).toInt()
        if (CommandPostStorageWidget.contains(localMouseX, localMouseY)) {
            val maxScroll = (ctx.moduleViews().size - CommandPostPanelContext.POLICY_VISIBLE_ROWS).coerceAtLeast(0)
            scrollIndex = (scrollIndex + if (verticalAmount > 0) -1 else 1).coerceIn(0, maxScroll)
            return true
        }
        return false
    }

    override fun onShown(previousMode: CommandPostMode?) {
        scrollIndex = 0
        selectedModuleIndex = null
    }

    private fun drawPolicyPanel(context: DrawContext, localMouseX: Int, localMouseY: Int) = with(ctx) {
                drawOperationalFrame(context, "POLICY")
                val views = moduleViews()
                if (views.isEmpty()) {
                    drawSmallText(context, "No role cards", CommandPostStorageWidget.X + 14, CommandPostStorageWidget.Y + 24, 0xFFB8C3C7.toInt(), false)
                    slotHits = emptyList()
                    panelHits = emptyList()
                    return
                }
                val policyIssues = modulePolicyIssues(views)
                val hits = mutableListOf<CommandPostPanelContext.PanelHit>()
                val maxScroll = (views.size - CommandPostPanelContext.POLICY_VISIBLE_ROWS).coerceAtLeast(0)
                this@PolicyPanel.scrollIndex = this@PolicyPanel.scrollIndex.coerceIn(0, maxScroll)
                if (maxScroll > 0) {
                    val firstRow = this@PolicyPanel.scrollIndex + 1
                    val lastRow = (this@PolicyPanel.scrollIndex + CommandPostPanelContext.POLICY_VISIBLE_ROWS).coerceAtMost(views.size)
                    val indicator = "$firstRow-$lastRow / ${views.size}"
                    val indicatorWidth = (textRenderer.getWidth(indicator) * CommandPostPcShell.TEXTURE_SCALE).toInt()
                    drawSmallText(context, indicator, CommandPostStorageWidget.X + 164 - indicatorWidth, CommandPostStorageWidget.Y + 7, 0xFF8FA0A8.toInt(), false)
                }
                views.drop(this@PolicyPanel.scrollIndex).take(CommandPostPanelContext.POLICY_VISIBLE_ROWS).forEachIndexed { row, view ->
                    val top = CommandPostStorageWidget.Y + 18 + row * 24
                    val selected = this@PolicyPanel.selectedModuleIndex == view.moduleIndex
                    context.fill(originX + CommandPostStorageWidget.X + 8, originY + top - 2, originX + CommandPostStorageWidget.X + 164, originY + top + 20, if (selected) 0x77415661 else if (contains(localMouseX, localMouseY, CommandPostStorageWidget.X + 8, top - 2, 156, 20)) 0x5522343A else 0x3318242A)
                    drawSmallText(context, TagTypePresentation.roleLabel(view.tagType), CommandPostStorageWidget.X + 13, top, 0xFFEAF4F5.toInt(), true)
                    drawSmallText(context, "${humanValue(view.spec.filter.matchMode.name)} ${if (view.spec.filter.whitelist) "Allow" else "Deny"}", CommandPostStorageWidget.X + 13, top + 9, 0xFFB8C3C7.toInt(), false)
                    val issues = policyIssues[view.moduleIndex].orEmpty()
                    issues.firstOrNull()?.let { issue ->
                        val dotLeft = CommandPostStorageWidget.X + 84
                        context.fill(originX + dotLeft, originY + top + 1, originX + dotLeft + 4, originY + top + 5, issueColor(issue.severity))
                        if (contains(localMouseX, localMouseY, dotLeft - 2, top - 1, 8, 8)) {
                            hoveredTooltip = CommandPostPanelContext.HoverTooltip("policy-issue-${view.moduleIndex}", buildList {
                                issues.forEach {
                                    add(Text.translatable(it.labelKey).formatted(issueFormatting(it.severity)))
                                    add(Text.translatable(it.detailKey).formatted(Formatting.GRAY))
                                }
                            })
                        }
                    }
                    val signalLeft = CommandPostStorageWidget.X + 91
                    val targetLeft = CommandPostStorageWidget.X + 115
                    val runLeft = CommandPostStorageWidget.X + 139
                    val supportsTargets = view.tagType.supportsTargetList
                    drawMiniChip(context, signalLeft, top + 5, compactValue(view.spec.settings.redstoneMode.id), localMouseX, localMouseY)
                    if (supportsTargets) {
                        drawMiniChip(context, targetLeft, top + 5, compactValue(view.spec.settings.targetStrategy.id), localMouseX, localMouseY)
                        drawMiniChip(context, runLeft, top + 5, if (view.spec.settings.terminateAfterSuccess) "1" else "Loop", localMouseX, localMouseY)
                    }
                    hits += CommandPostPanelContext.PanelHit(CommandPostStorageWidget.X + 8, top - 2, 80, 20) {
                        this@PolicyPanel.selectedModuleIndex = view.moduleIndex
                        true
                    }
                    hits += CommandPostPanelContext.PanelHit(signalLeft, top + 5, 20, 10) {
                        clickButton(RouterScreenHandler.policyQuickActionId(view.rowIndex, RouterScreenHandler.POLICY_ACTION_CYCLE_SIGNAL))
                        true
                    }
                    if (supportsTargets) {
                        hits += CommandPostPanelContext.PanelHit(targetLeft, top + 5, 20, 10) {
                            clickButton(RouterScreenHandler.policyQuickActionId(view.rowIndex, RouterScreenHandler.POLICY_ACTION_CYCLE_TARGET))
                            true
                        }
                        hits += CommandPostPanelContext.PanelHit(runLeft, top + 5, 20, 10) {
                            clickButton(RouterScreenHandler.policyQuickActionId(view.rowIndex, RouterScreenHandler.POLICY_ACTION_TOGGLE_RUN))
                            true
                        }
                    }
                }
                this@PolicyPanel.drawPolicyContextSheet(context, localMouseX, localMouseY, views.firstOrNull { it.moduleIndex == this@PolicyPanel.selectedModuleIndex } ?: views.firstOrNull(), hits)
                panelHits = hits
                slotHits = emptyList()
            
    }

    private fun drawPolicyContextSheet(context: DrawContext, localMouseX: Int, localMouseY: Int, view: CommandPostPanelContext.ModuleView?, hits: MutableList<CommandPostPanelContext.PanelHit>) = with(ctx) {
                val top = CommandPostStorageWidget.Y + 118
                context.fill(originX + CommandPostStorageWidget.X + 8, originY + top, originX + CommandPostStorageWidget.X + 164, originY + top + 31, 0xAA18242A.toInt())
                if (view == null) {
                    drawSmallText(context, "No policy", CommandPostStorageWidget.X + 13, top + 11, 0xFF8FA0A8.toInt(), false)
                    return
                }
                drawSmallText(context, fit(TagTypePresentation.roleLabel(view.tagType), 70), CommandPostStorageWidget.X + 13, top + 4, 0xFFEAF4F5.toInt(), true)
                drawSmallText(context, TagTypePresentation.bindingLabel(view.tagType), CommandPostStorageWidget.X + 13, top + 15, 0xFFB8C3C7.toInt(), false)
                val editLeft = CommandPostStorageWidget.X + 110
                drawMiniChip(context, editLeft, top + 10, "Edit", localMouseX, localMouseY)
                hits += CommandPostPanelContext.PanelHit(editLeft, top + 10, 20, 10) {
                    clickButton(RouterScreenHandler.ACTION_OPEN_POLICY_ROW_BASE + view.rowIndex)
                    true
                }
            
    }
}

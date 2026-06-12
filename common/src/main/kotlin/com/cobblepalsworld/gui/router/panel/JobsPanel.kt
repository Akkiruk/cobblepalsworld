package com.cobblepalsworld.gui.router.panel

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblepalsworld.gui.crew.CommandPostCrewSnapshotCache
import com.cobblepalsworld.gui.router.CommandPostStorageWidget
import com.cobblepalsworld.gui.router.RouterScreenHandler
import com.cobblepalsworld.tag.TagItem
import com.cobblepalsworld.tag.TagTypePresentation
import com.cobblepalsworld.tag.filter.TagPolicySeverity
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.Formatting

internal class JobsPanel(private val ctx: CommandPostPanelContext) : CommandPostPanel {
    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        drawJobsPanel(context, mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 1) return false
        val localMouseX = (mouseX - ctx.originX).toInt()
        val localMouseY = (mouseY - ctx.originY).toInt()
        ctx.hoveredModuleSlot(localMouseX, localMouseY)?.let { moduleIndex ->
            val slot = ctx.handler.slots.getOrNull(moduleIndex)
            if (slot?.stack?.item is TagItem) {
                ctx.clickButton(RouterScreenHandler.ACTION_EDIT_MODULE_BASE + moduleIndex)
                ctx.play(CobblemonSounds.PC_CLICK)
                return true
            }
        }
        return false
    }

    private fun drawJobsPanel(context: DrawContext, localMouseX: Int, localMouseY: Int) = with(ctx) {
                drawOperationalFrame(context, "JOBS")
                val views = moduleViews()
                val members = CommandPostCrewSnapshotCache.get(handler.routerPos)?.members.orEmpty()
                val policyIssues = modulePolicyIssues(views)
                drawSmallText(context, "Tag cards", CommandPostPanelContext.JOBS_MODULE_LEFT, CommandPostPanelContext.JOBS_MODULE_TOP - 10, 0xFFEAF4F5.toInt(), true)
                drawSmallText(context, "Augments", CommandPostPanelContext.JOBS_UPGRADE_LEFT, CommandPostPanelContext.JOBS_UPGRADE_TOP - 10, 0xFFEAF4F5.toInt(), true)
                drawSmallText(context, "Bag", CommandPostPanelContext.CENTER_INV_LEFT, CommandPostPanelContext.JOBS_PLAYER_TOP - 9, 0xFFEAF4F5.toInt(), true)
                drawTagCardWell(context, views.isEmpty())
                drawSlotFrames(context, RouterScreenHandler.UPGRADE_SCREEN_SLOT_START until RouterScreenHandler.STORAGE_SCREEN_SLOT_START, CommandPostPanelContext.SlotFrameStyle.COMPACT)
                drawSlotFrames(context, RouterScreenHandler.COMMAND_SLOT_COUNT until handler.slots.size, CommandPostPanelContext.SlotFrameStyle.COMPACT)
                views.forEach { view ->
                    val slot = handler.slots.getOrNull(view.moduleIndex) ?: return@forEach
                    val active = handler.moduleActive(view.moduleIndex)
                    val assigned = handler.moduleAssigned(view.moduleIndex)
                    val roleMembers = members.filter { it.tagTypeId == view.tagType.id }
                    val blocked = roleMembers.count { it.isBlocked() || it.isFainted || it.isMissing }
                    val issues = policyIssues[view.moduleIndex].orEmpty()
                    val worstIssue = issues.firstOrNull()
                    val rowColor = when {
                        worstIssue?.severity == TagPolicySeverity.BLOCKING || blocked > 0 -> 0xFFFF7777.toInt()
                        worstIssue?.severity == TagPolicySeverity.WARNING -> 0xFFFFD166.toInt()
                        active -> 0xFF6FEA8A.toInt()
                        assigned -> 0xFFFFD166.toInt()
                        else -> 0xFFEAF4F5.toInt()
                    }
                    val badgeLeft = slot.x + 12
                    val badgeTop = slot.y + 12
                    context.fill(originX + badgeLeft, originY + badgeTop, originX + badgeLeft + 5, originY + badgeTop + 5, rowColor)
                    if (contains(localMouseX, localMouseY, slot.x - 4, slot.y - 4, 25, 25)) {
                        hoveredTooltip = CommandPostPanelContext.HoverTooltip("job-${view.moduleIndex}", buildList {
                            add(Text.literal(TagTypePresentation.roleLabel(view.tagType)))
                            add(Text.literal(TagTypePresentation.familyOf(view.tagType).label))
                            add(Text.literal(causeChip(active, assigned, blocked)))
                            issues.forEach { issue ->
                                add(Text.translatable(issue.labelKey).formatted(issueFormatting(issue.severity)))
                                add(Text.translatable(issue.detailKey).formatted(Formatting.GRAY))
                            }
                        })
                    }
                }
                panelHits = emptyList()
                slotHits = emptyList()
            
    }
}

package com.cobblepalsworld.gui.router.panel

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblepalsworld.assignment.WorkerAssignmentMode
import com.cobblepalsworld.behavior.state.WorkerStatusKind
import com.cobblepalsworld.gui.CobblemonUiChrome
import com.cobblepalsworld.gui.crew.CommandPostCrewMemberSnapshot
import com.cobblepalsworld.gui.crew.CommandPostCrewSnapshotCache
import com.cobblepalsworld.gui.crew.CrewSourceBoxSnapshot
import com.cobblepalsworld.gui.crew.CrewSourcePokemonSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceSnapshotCache
import com.cobblepalsworld.gui.crew.CrewSourceType
import com.cobblepalsworld.gui.router.*
import com.cobblepalsworld.mastery.MasteryTier
import com.cobblepalsworld.networking.CobblePalsNetworking
import com.cobblepalsworld.router.RouterBlockEntity
import com.cobblepalsworld.tag.*
import com.cobblepalsworld.tag.filter.TagPolicyAnalyzer
import com.cobblepalsworld.tag.filter.TagPolicyIssue
import com.cobblepalsworld.tag.filter.TagPolicyLine
import com.cobblepalsworld.tag.filter.TagPolicySeverity
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.Locale
import java.util.UUID

internal class CommandPostPanelContext(
    private val screen: CommandPostPcScreen,
    val handler: RouterScreenHandler,
    private val currentMode: () -> CommandPostMode
) {
    data class HoverTooltip(val id: String, val lines: List<Text>)
    data class PanelHit(val left: Int, val top: Int, val width: Int, val height: Int, val action: () -> Boolean)
    data class ModuleView(val rowIndex: Int, val moduleIndex: Int, val stack: ItemStack, val tagType: TagType, val spec: TagSpec)
    internal enum class SlotFrameStyle { COBBLEMON, COMPACT }

    var sourceType = CrewSourceType.PC
    var sourceBoxIndex = 0
    var pastureScrollIndex = 0
    var rosterSort = CommandPostRosterSort.STATUS
    var drawerMode = CommandPostDrawerMode.CLOSED
    var sourceQuery = ""
    var sourceSearchActive = false
    var roleFamilyFilter = CommandPostRoleFamilyFilter.ALL
    var stateFilter = CommandPostStateFilter.ALL
    var assignmentFilter = CommandPostAssignmentFilter.ALL
    var availabilityFilter = CommandPostAvailabilityFilter.ALL
    var assignedFilter = CommandPostAssignedFilter.ALL
    var selectedPokemonId: UUID? = null

    var hoveredSource: CrewSourcePokemonSnapshot? = null
    var hoveredCrew: CommandPostCrewMemberSnapshot? = null
    var hoveredTooltip: HoverTooltip? = null
    var slotHits: List<CommandPostStorageSlot.Hit> = emptyList()
    var drawerHits: List<CommandPostFilterDrawer.Hit> = emptyList()
    var panelHits: List<PanelHit> = emptyList()
    var searchField: TextFieldWidget? = null

    val originX: Int get() = screen.panelOriginX
    val originY: Int get() = screen.panelOriginY
    internal val textRenderer get() = screen.panelTextRenderer
    internal val pointerOffsetY: Int get() = screen.pointerOffsetY

    internal fun renderPokemonForPanel(context: DrawContext, view: CommandPostPokemonRenderView, centerX: Int, topY: Int, delta: Float, modelScale: Float, matrixScale: Float, stateKey: String, animate: Boolean, clipHalfWidth: Int, clipHeight: Int) {
        screen.renderPokemonForPanel(context, view, centerX, topY, delta, modelScale, matrixScale, stateKey, animate, clipHalfWidth, clipHeight)
    }

    fun resetFrameState() {
        hoveredSource = null
        hoveredCrew = null
        hoveredTooltip = null
        drawerHits = emptyList()
        panelHits = emptyList()
        searchField?.visible = drawerMode == CommandPostDrawerMode.FILTERS
        sourceSearchActive = searchField?.isFocused == true
    }

    fun drawPasturePanel(context: DrawContext, members: List<CommandPostCrewMemberSnapshot>, localMouseX: Int, localMouseY: Int, delta: Float) {
        val maxScroll = CommandPostPastureWidget.maxScroll(members.size)
        pastureScrollIndex = pastureScrollIndex.coerceIn(0, maxScroll)
        CommandPostPastureWidget.drawPanel(context, screen.panelTextRenderer, originX, originY)
    }

    fun drawPastureChrome(context: DrawContext, members: List<CommandPostCrewMemberSnapshot>, localMouseX: Int, localMouseY: Int) {
        val maxWorkers = CommandPostCrewSnapshotCache.get(handler.routerPos)?.maxWorkers ?: 0
        CommandPostPastureWidget.drawScrollOverlay(context, originX, originY)
        CommandPostPastureWidget.drawControls(context, screen.panelTextRenderer, originX, originY, members.size, maxWorkers, localMouseX, localMouseY)
        if (CommandPostPastureWidget.recallContains(localMouseX, localMouseY)) {
            hoveredTooltip = HoverTooltip("recall", listOf(Text.translatable("gui.cobblepalsworld.recall_all")))
        }
    }

    fun drawFilterDrawer(context: DrawContext, localMouseX: Int, localMouseY: Int) {
        drawerHits = when (drawerMode) {
            CommandPostDrawerMode.CLOSED -> emptyList()
            CommandPostDrawerMode.FILTERS -> CommandPostFilterDrawer.drawFilters(
                context = context,
                textRenderer = screen.panelTextRenderer,
                originX = originX,
                originY = originY,
                localMouseX = localMouseX,
                localMouseY = localMouseY,
                query = sourceQuery,
                searchActive = sourceSearchActive,
                role = roleFamilyFilter,
                state = stateFilter,
                assignment = assignmentFilter,
                availability = availabilityFilter,
                assigned = assignedFilter,
                fit = ::fit
            )
            CommandPostDrawerMode.OPTIONS -> CommandPostFilterDrawer.drawOptions(
                context = context,
                textRenderer = screen.panelTextRenderer,
                originX = originX,
                originY = originY,
                localMouseX = localMouseX,
                localMouseY = localMouseY,
                sort = rosterSort,
                fit = ::fit
            )
        }
        drawerHits.firstOrNull { hit -> CommandPostFilterDrawer.contains(localMouseX, localMouseY, hit) }?.let { hit ->
            hoveredTooltip = HoverTooltip("drawer-${hit.action.name}", drawerTooltip(hit.action))
        }
    }

    internal fun drawOperationalFrame(context: DrawContext, title: String) {
        val background = when (currentMode()) {
            CommandPostMode.JOBS -> CommandPostStorageWidget.JOBS_BACKGROUND
            CommandPostMode.POLICY -> CommandPostStorageWidget.POLICY_BACKGROUND
            CommandPostMode.LOGISTICS -> CommandPostStorageWidget.LOGISTICS_BACKGROUND
            CommandPostMode.SOURCE -> null
        }
        if (background == null) {
            CommandPostStorageWidget.drawFrame(context, originX, originY)
        } else {
            CommandPostStorageWidget.drawFrame(context, originX, originY, background)
        }
        CommandPostStorageWidget.drawTitle(context, screen.panelTextRenderer, originX, originY, title)
    }

    internal fun drawSlotFrames(context: DrawContext, slotRange: IntRange, style: SlotFrameStyle) {
        slotRange.forEach { slotIndex ->
            val slot = handler.slots.getOrNull(slotIndex) ?: return@forEach
            if (slot.x == HIDDEN_SLOT_X && slot.y == HIDDEN_SLOT_Y) return@forEach
            when (style) {
                SlotFrameStyle.COBBLEMON -> blitk(
                    matrixStack = context.matrices,
                    texture = CommandPostPcShell.SLOT_OVERLAY,
                    x = originX + slot.x - 4,
                    y = originY + slot.y - 4,
                    width = CommandPostStorageWidget.SLOT_SIZE,
                    height = CommandPostStorageWidget.SLOT_SIZE,
                    alpha = 0.55F
                )
                SlotFrameStyle.COMPACT -> {
                    context.fill(originX + slot.x - 1, originY + slot.y - 1, originX + slot.x + 17, originY + slot.y + 17, 0xAA8E8E8E.toInt())
                    context.fill(originX + slot.x, originY + slot.y, originX + slot.x + 16, originY + slot.y + 16, 0xCC4A4A4A.toInt())
                }
            }
        }
    }

    internal fun drawTagCardWell(context: DrawContext, empty: Boolean) {
        val left = originX + JOBS_TAG_WELL_LEFT
        val top = originY + JOBS_TAG_WELL_TOP
        val right = left + JOBS_TAG_WELL_WIDTH
        val bottom = top + JOBS_TAG_WELL_HEIGHT
        context.fill(left, top, right, bottom, 0xE6223038.toInt())
        context.fill(left, top, right, top + 1, 0xFF6F8792.toInt())
        context.fill(left, bottom - 1, right, bottom, 0xFF172027.toInt())
        context.fill(left, top, left + 1, bottom, 0xFF6F8792.toInt())
        context.fill(right - 1, top, right, bottom, 0xFF172027.toInt())
        context.fill(left + 2, top + 2, right - 2, top + 11, 0x663D5360)

        (0 until RouterBlockEntity.MODULE_SLOT_COUNT).forEach { slotIndex ->
            val slot = handler.slots.getOrNull(slotIndex) ?: return@forEach
            if (slot.x == HIDDEN_SLOT_X && slot.y == HIDDEN_SLOT_Y) return@forEach
            context.fill(originX + slot.x - 1, originY + slot.y - 1, originX + slot.x + 17, originY + slot.y + 17, 0xFF54626A.toInt())
            context.fill(originX + slot.x, originY + slot.y, originX + slot.x + 16, originY + slot.y + 16, 0xFF1D2B33.toInt())
            context.fill(originX + slot.x + 1, originY + slot.y + 1, originX + slot.x + 15, originY + slot.y + 2, 0x553D5966)
        }

        if (empty) {
            val hint = "Drop tag cards"
            val hintWidth = (screen.panelTextRenderer.getWidth(hint) * CommandPostPcShell.TEXTURE_SCALE).toInt()
            drawSmallText(context, hint, JOBS_TAG_WELL_LEFT + (JOBS_TAG_WELL_WIDTH - hintWidth) / 2, JOBS_TAG_WELL_TOP + JOBS_TAG_WELL_HEIGHT - 10, 0xFFB8C3C7.toInt(), false)
        }
    }

    internal fun drawMiniChip(context: DrawContext, left: Int, top: Int, value: String, localMouseX: Int, localMouseY: Int) {
        val hovered = contains(localMouseX, localMouseY, left, top, 20, 10)
        context.fill(originX + left, originY + top, originX + left + 20, originY + top + 10, if (hovered) 0xCC8E8E8E.toInt() else 0xCC575757.toInt())
        drawSmallText(context, fit(value, 18), left + 2, top + 2, 0xFFEAF4F5.toInt(), false)
    }

    fun handleDrawerHit(action: CommandPostFilterDrawer.Action) {
        when (action) {
            CommandPostFilterDrawer.Action.SEARCH -> {
                drawerMode = CommandPostDrawerMode.FILTERS
                searchField?.visible = true
                searchField?.setFocused(true)
                sourceSearchActive = true
            }
            CommandPostFilterDrawer.Action.ROLE -> {
                sourceSearchActive = false
                roleFamilyFilter = roleFamilyFilter.next()
                pastureScrollIndex = 0
            }
            CommandPostFilterDrawer.Action.STATE -> {
                sourceSearchActive = false
                stateFilter = stateFilter.next()
                pastureScrollIndex = 0
            }
            CommandPostFilterDrawer.Action.ASSIGNMENT -> {
                sourceSearchActive = false
                assignmentFilter = assignmentFilter.next()
                pastureScrollIndex = 0
            }
            CommandPostFilterDrawer.Action.AVAILABILITY -> {
                sourceSearchActive = false
                availabilityFilter = availabilityFilter.next()
                pastureScrollIndex = 0
            }
            CommandPostFilterDrawer.Action.ASSIGNED -> {
                sourceSearchActive = false
                assignedFilter = assignedFilter.next()
                pastureScrollIndex = 0
            }
            CommandPostFilterDrawer.Action.SORT -> {
                sourceSearchActive = false
                rosterSort = rosterSort.next()
                pastureScrollIndex = 0
            }
        }
    }

    private fun drawerTooltip(action: CommandPostFilterDrawer.Action): List<Text> = when (action) {
        CommandPostFilterDrawer.Action.SEARCH -> listOf(Text.translatable("gui.cobblepalsworld.search_value", sourceQuery.ifBlank { "all" }))
        CommandPostFilterDrawer.Action.ROLE -> listOf(Text.translatable("gui.cobblepalsworld.role_family", roleFamilyFilter.label))
        CommandPostFilterDrawer.Action.STATE -> listOf(Text.translatable("gui.cobblepalsworld.state", stateFilter.label))
        CommandPostFilterDrawer.Action.ASSIGNMENT -> listOf(Text.translatable("gui.cobblepalsworld.assignment", assignmentFilter.label))
        CommandPostFilterDrawer.Action.AVAILABILITY -> listOf(Text.translatable("gui.cobblepalsworld.availability", availabilityFilter.label))
        CommandPostFilterDrawer.Action.ASSIGNED -> listOf(Text.translatable("gui.cobblepalsworld.source", assignedFilter.label))
        CommandPostFilterDrawer.Action.SORT -> listOf(Text.translatable("gui.cobblepalsworld.sort", rosterSort.label))
    }

    private fun <T : Enum<T>> T.next(): T {
        val values = declaringJavaClass.enumConstants
        return values[(ordinal + 1) % values.size]
    }

    fun currentSourceBox(sources: List<CrewSourceSnapshot>): CrewSourceBoxSnapshot? {
        val source = sources.firstOrNull { it.sourceType == sourceType } ?: return null
        if (sourceType == CrewSourceType.PARTY) return source.boxes.firstOrNull()
        val boxCount = source.boxCount
        if (boxCount <= 0) return null
        sourceBoxIndex = sourceBoxIndex.coerceIn(0, boxCount - 1)
        return source.boxes.firstOrNull { it.boxIndex == sourceBoxIndex } ?: source.boxes.firstOrNull()
    }

    fun primeHoveredPokemon(box: CrewSourceBoxSnapshot?, members: List<CommandPostCrewMemberSnapshot>, localMouseX: Int, localMouseY: Int) {
        if (currentMode() == CommandPostMode.SOURCE) {
            box?.slots.orEmpty().forEachIndexed { index, rawSlot ->
                val pokemon = rawSlot.pokemon?.takeIf(::sourceMatchesFilters) ?: return@forEachIndexed
                val left: Int
                val top: Int
                val size: Int
                if (sourceType == CrewSourceType.PARTY) {
                    val bounds = CommandPostPartySlot.bounds(index) ?: return@forEachIndexed
                    left = bounds.left
                    top = bounds.top
                    size = bounds.size
                } else {
                    val gridIndex = rawSlot.slotIndex
                    if (gridIndex !in 0 until CommandPostStorageWidget.BOX_SLOT_COUNT) return@forEachIndexed
                    left = CommandPostStorageWidget.slotLeft(gridIndex)
                    top = CommandPostStorageWidget.slotTop(gridIndex)
                    size = CommandPostStorageWidget.SLOT_SIZE
                }
                if (contains(localMouseX, localMouseY, left, top, size, size)) {
                    hoveredSource = pokemon
                    return
                }
            }
        }

        if (!CommandPostPastureWidget.listContains(localMouseX, localMouseY)) return
        members.drop(pastureScrollIndex).take(CommandPostPastureWidget.VISIBLE_ROWS).forEachIndexed { row, member ->
            val bounds = CommandPostPastureWidget.rowBounds(row)
            if (contains(localMouseX, localMouseY, bounds.left, bounds.top, CommandPostPastureWidget.SLOT_WIDTH, CommandPostPastureWidget.SLOT_HEIGHT)) {
                hoveredCrew = member
                return
            }
        }
    }

    fun previewPokemon(box: CrewSourceBoxSnapshot?, members: List<CommandPostCrewMemberSnapshot>): CommandPostPokemonRenderView? {
        hoveredSource?.let { return SourcePokemonRenderView(it) }
        hoveredCrew?.let { return CrewPokemonRenderView(it) }
        selectedPokemonId?.let { selected ->
            box?.slots?.firstNotNullOfOrNull { it.pokemon?.takeIf { pokemon -> pokemon.pokemonId == selected } }?.let { return SourcePokemonRenderView(it) }
            members.firstOrNull { it.pokemonId == selected }?.let { return CrewPokemonRenderView(it) }
        }
        members.firstOrNull()?.let { return CrewPokemonRenderView(it) }
        box?.slots?.firstNotNullOfOrNull { it.pokemon }?.let { return SourcePokemonRenderView(it) }
        return null
    }

    fun changeBox(delta: Int) {
        val source = CrewSourceSnapshotCache.get(handler.routerPos).firstOrNull { it.sourceType == CrewSourceType.PC } ?: return
        if (source.boxCount <= 0) return
        sourceBoxIndex = Math.floorMod(sourceBoxIndex + delta, source.boxCount)
        selectedPokemonId = null
        play(CobblemonSounds.PC_CLICK)
        requestSourceRefresh()
    }

    fun requestCrewRefresh() {
        CobblePalsNetworking.sendCommandPostCrewRefresh(handler.routerPos)
    }

    fun requestSourceRefresh() {
        if (currentMode() != CommandPostMode.SOURCE) return
        CobblePalsNetworking.sendCrewSourceRefresh(handler.routerPos, sourceType, sourceBoxIndex, sourceQuery)
    }

    fun filteredMembers(members: List<CommandPostCrewMemberSnapshot>): List<CommandPostCrewMemberSnapshot> {
        val filtered = members.filter { member ->
            memberMatchesQuery(member) &&
                memberMatchesRole(member) &&
                memberMatchesState(member) &&
                memberMatchesAssignment(member) &&
                memberMatchesAvailability(member) &&
                assignedFilter != CommandPostAssignedFilter.UNASSIGNED
        }
        return when (rosterSort) {
            CommandPostRosterSort.STATUS -> filtered.sortedWith(compareBy<CommandPostCrewMemberSnapshot> { it.sortRank() }.thenBy { it.displayName })
            CommandPostRosterSort.ROLE -> filtered.sortedWith(compareBy<CommandPostCrewMemberSnapshot> { it.tagTypeId ?: "" }.thenBy { it.displayName })
            CommandPostRosterSort.FAMILY -> filtered.sortedWith(compareBy<CommandPostCrewMemberSnapshot> { familyLabel(it.tagTypeId) }.thenBy { it.displayName })
            CommandPostRosterSort.ASSIGNMENT -> filtered.sortedWith(compareBy<CommandPostCrewMemberSnapshot> { it.assignmentLabel() }.thenBy { it.displayName })
            CommandPostRosterSort.SOURCE -> filtered.sortedWith(compareBy<CommandPostCrewMemberSnapshot> { it.sourceType }.thenBy { it.boxIndex }.thenBy { it.slotIndex })
        }
    }

    internal fun sourceMatchesFilters(pokemon: CrewSourcePokemonSnapshot): Boolean {
        return sourceMatchesRole(pokemon) && sourceMatchesState(pokemon) && sourceMatchesAvailability(pokemon) && sourceMatchesAssigned(pokemon)
    }

    private fun sourceMatchesRole(pokemon: CrewSourcePokemonSnapshot): Boolean {
        val family = pokemon.tagTypeId?.let(TagType::fromId)?.let(TagTypePresentation::familyOf)
        return roleFamilyFilter.family == null || family == roleFamilyFilter.family
    }

    private fun sourceMatchesState(pokemon: CrewSourcePokemonSnapshot): Boolean = when (stateFilter) {
        CommandPostStateFilter.ALL -> true
        CommandPostStateFilter.READY -> pokemon.isAvailable && !pokemon.isFainted
        CommandPostStateFilter.ACTIVE -> pokemon.workStatus.contains("work", ignoreCase = true) || pokemon.workStatus.contains("active", ignoreCase = true)
        CommandPostStateFilter.BLOCKED -> !pokemon.isAvailable && !pokemon.isFainted && !pokemon.isCrewMember
        CommandPostStateFilter.WAITING -> pokemon.workStatus.contains("await", ignoreCase = true) || pokemon.workStatus.contains("cooldown", ignoreCase = true)
        CommandPostStateFilter.STANDBY -> pokemon.workStatus.contains("standby", ignoreCase = true)
        CommandPostStateFilter.FAINTED -> pokemon.isFainted
        CommandPostStateFilter.MISSING -> false
        CommandPostStateFilter.NO_ROLE -> pokemon.tagTypeId == null
        CommandPostStateFilter.CARGO -> pokemon.cargoSummary.isNotBlank()
    }

    private fun sourceMatchesAvailability(pokemon: CrewSourcePokemonSnapshot): Boolean = when (availabilityFilter) {
        CommandPostAvailabilityFilter.ALL -> true
        CommandPostAvailabilityFilter.AVAILABLE -> pokemon.isAvailable
        CommandPostAvailabilityFilter.UNAVAILABLE -> !pokemon.isAvailable
    }

    private fun sourceMatchesAssigned(pokemon: CrewSourcePokemonSnapshot): Boolean = when (assignedFilter) {
        CommandPostAssignedFilter.ALL -> true
        CommandPostAssignedFilter.ASSIGNED -> pokemon.isCrewMember
        CommandPostAssignedFilter.UNASSIGNED -> !pokemon.isCrewMember
    }

    private fun memberMatchesQuery(member: CommandPostCrewMemberSnapshot): Boolean {
        val query = sourceQuery.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) return true
        return member.displayName.lowercase(Locale.ROOT).contains(query) ||
            member.species.lowercase(Locale.ROOT).contains(query) ||
            member.sourceLabel().lowercase(Locale.ROOT).contains(query) ||
            member.statusLabel().lowercase(Locale.ROOT).contains(query) ||
            member.assignmentLabel().lowercase(Locale.ROOT).contains(query) ||
            member.tagTypeId?.lowercase(Locale.ROOT)?.contains(query) == true
    }

    private fun memberMatchesRole(member: CommandPostCrewMemberSnapshot): Boolean {
        val family = member.tagTypeId?.let(TagType::fromId)?.let(TagTypePresentation::familyOf)
        return roleFamilyFilter.family == null || family == roleFamilyFilter.family
    }

    private fun memberMatchesState(member: CommandPostCrewMemberSnapshot): Boolean = when (stateFilter) {
        CommandPostStateFilter.ALL -> true
        CommandPostStateFilter.READY -> member.isReady()
        CommandPostStateFilter.ACTIVE -> member.isActive()
        CommandPostStateFilter.BLOCKED -> member.isBlocked()
        CommandPostStateFilter.WAITING -> member.statusReason()?.kind == WorkerStatusKind.WAITING
        CommandPostStateFilter.STANDBY -> member.statusReason()?.kind == WorkerStatusKind.STANDBY
        CommandPostStateFilter.FAINTED -> member.isFainted
        CommandPostStateFilter.MISSING -> member.isMissing
        CommandPostStateFilter.NO_ROLE -> member.tagTypeId == null
        CommandPostStateFilter.CARGO -> member.carriedItemCount > 0
    }

    private fun memberMatchesAssignment(member: CommandPostCrewMemberSnapshot): Boolean = when (assignmentFilter) {
        CommandPostAssignmentFilter.ALL -> true
        CommandPostAssignmentFilter.GENERAL -> member.assignmentMode() == WorkerAssignmentMode.GENERAL && member.allowFallback
        CommandPostAssignmentFilter.PREFERRED -> member.assignmentMode() == WorkerAssignmentMode.PREFERRED
        CommandPostAssignmentFilter.RESERVED -> member.assignmentMode() == WorkerAssignmentMode.RESERVED
        CommandPostAssignmentFilter.STRICT -> !member.allowFallback
    }

    private fun memberMatchesAvailability(member: CommandPostCrewMemberSnapshot): Boolean = when (availabilityFilter) {
        CommandPostAvailabilityFilter.ALL -> true
        CommandPostAvailabilityFilter.AVAILABLE -> !member.isMissing && !member.isFainted && !member.isBlocked() && member.tagTypeId != null
        CommandPostAvailabilityFilter.UNAVAILABLE -> member.isMissing || member.isFainted || member.isBlocked() || member.tagTypeId == null
    }

    private fun familyLabel(tagId: String?): String {
        return tagId?.let(TagType::fromId)?.let(TagTypePresentation::familyOf)?.label ?: "None"
    }

    fun detailLines(preview: CommandPostPokemonRenderView?): List<CommandPostInfoPanel.DetailLine> {
        if (preview == null) return emptyList()
        val gender = when {
            "male" in preview.aspects -> " M"
            "female" in preview.aspects -> " F"
            else -> ""
        }
        val lines = mutableListOf(CommandPostInfoPanel.DetailLine("Lv.${preview.level} ${friendlySpecies(preview.species)}$gender"))
        when (preview) {
            is SourcePokemonRenderView -> {
                val statusColor = if (preview.source.isAvailable) 0xFFBFE7C4.toInt() else 0xFFFFD166.toInt()
                lines += CommandPostInfoPanel.DetailLine(preview.source.statusLabel(), statusColor, preview.source.isAvailable)
                lines += CommandPostInfoPanel.DetailLine(preview.source.sourceLabel(), 0xFFB8C3C7.toInt())
                lines += preview.source.tagTypeId?.let { tagRoleLine(it) } ?: CommandPostInfoPanel.DetailLine("Role: none", 0xFF8FA0A8.toInt())
                lines += CommandPostInfoPanel.DetailLine(if (preview.source.isCrewMember) "Assigned" else "Unassigned", if (preview.source.isCrewMember) 0xFFFFD166.toInt() else 0xFFBFE7C4.toInt())
                lines += CommandPostInfoPanel.DetailLine(preview.source.cargoSummary.ifBlank { if (preview.source.isFainted) "Fainted" else "No cargo" }, if (preview.source.isFainted) 0xFFFF7777.toInt() else 0xFF8FA0A8.toInt())
            }
            is CrewPokemonRenderView -> {
                val member = preview.member
                val statusColor = if (member.isBlocked() || member.isFainted || member.isMissing) 0xFFFF7777.toInt() else 0xFFBFE7C4.toInt()
                lines += CommandPostInfoPanel.DetailLine(member.statusLabel(), statusColor, member.isReady())
                lines += CommandPostInfoPanel.DetailLine(member.sourceLabel(), 0xFFB8C3C7.toInt())
                lines += member.tagTypeId?.let { crewRoleLine(it, member) } ?: CommandPostInfoPanel.DetailLine(Text.translatable("gui.cobblepalsworld.role_none").string, 0xFF8FA0A8.toInt())
                lines += CommandPostInfoPanel.DetailLine(member.assignmentLabel(), 0xFFEAF4F5.toInt())
                lines += CommandPostInfoPanel.DetailLine(member.cargoSummary.ifBlank { Text.translatable(member.statusDetailKey()).string }, if (member.carriedItemCount > 0) 0xFFFFD166.toInt() else 0xFF8FA0A8.toInt())
            }
            else -> {
                lines += CommandPostInfoPanel.DetailLine(Text.translatable(if (preview.isFainted) "status.cobblepalsworld.label.fainted" else "status.cobblepalsworld.label.ready").string)
            }
        }
        return lines
    }

    private fun tagRoleLine(tagId: String): CommandPostInfoPanel.DetailLine {
        val tagType = TagType.fromId(tagId)
        val label = tagType?.let(TagTypePresentation::roleLabel) ?: tagId
        return CommandPostInfoPanel.DetailLine(label, 0xFFEAF4F5.toInt(), true)
    }

    private fun crewRoleLine(tagId: String, member: CommandPostCrewMemberSnapshot): CommandPostInfoPanel.DetailLine {
        val tagType = TagType.fromId(tagId) ?: return tagRoleLine(tagId)
        val roleLabel = TagTypePresentation.roleLabel(tagType)
        val tier = member.masteryTier()
        val label = if (tier == MasteryTier.NOVICE) roleLabel else "${tier.label} $roleLabel"
        return CommandPostInfoPanel.DetailLine(label, masteryColor(tier), true)
    }

    private fun masteryColor(tier: MasteryTier): Int = when (tier) {
        MasteryTier.NOVICE -> 0xFFEAF4F5.toInt()
        MasteryTier.APPRENTICE -> 0xFFFFFFFF.toInt()
        MasteryTier.ADEPT -> 0xFFBFE7C4.toInt()
        MasteryTier.EXPERT -> 0xFF9FE0EA.toInt()
        MasteryTier.MASTER -> 0xFFFFD166.toInt()
    }

    fun moduleViews(): List<ModuleView> {
        val registries = screen.panelClient?.world?.registryManager ?: return emptyList()
        return (0 until RouterBlockEntity.MODULE_SLOT_COUNT).mapNotNull { moduleIndex ->
            val stack = handler.slots.getOrNull(moduleIndex)?.stack ?: return@mapNotNull null
            val tagItem = stack.item as? TagItem ?: return@mapNotNull null
            val spec = TagItem.getSpec(stack, registries) ?: TagSpec(type = tagItem.tagType)
            ModuleView(rowIndex = 0, moduleIndex = moduleIndex, stack = stack, tagType = tagItem.tagType, spec = spec)
        }.mapIndexed { rowIndex, view -> view.copy(rowIndex = rowIndex) }
    }

    internal fun modulePolicyIssues(views: List<ModuleView>): Map<Int, List<TagPolicyIssue>> {
        if (views.isEmpty()) return emptyMap()
        return TagPolicyAnalyzer.issuesByModule(views.map { TagPolicyLine(it.moduleIndex, it.tagType, it.spec) })
    }

    internal fun issueFormatting(severity: TagPolicySeverity): Formatting = when (severity) {
        TagPolicySeverity.BLOCKING -> Formatting.RED
        TagPolicySeverity.WARNING -> Formatting.GOLD
        TagPolicySeverity.INFO -> Formatting.AQUA
    }

    internal fun issueColor(severity: TagPolicySeverity): Int = when (severity) {
        TagPolicySeverity.BLOCKING -> 0xFFFF7777.toInt()
        TagPolicySeverity.WARNING -> 0xFFFFD166.toInt()
        TagPolicySeverity.INFO -> 0xFF9FE0EA.toInt()
    }

    internal fun causeChip(active: Boolean, assigned: Boolean, blocked: Int): String = when {
        blocked > 0 -> "Blocked"
        active -> "Working"
        assigned -> "Ready"
        else -> "Needs pal"
    }

    fun applySlotLayout() {
        for (slotIndex in handler.slots.indices) {
            setSlotPosition(slotIndex, HIDDEN_SLOT_X, HIDDEN_SLOT_Y)
        }
        when (currentMode()) {
            CommandPostMode.SOURCE -> return
            CommandPostMode.JOBS -> {
                positionModuleSlots(JOBS_MODULE_LEFT, JOBS_MODULE_TOP)
                positionUpgradeSlots(JOBS_UPGRADE_LEFT, JOBS_UPGRADE_TOP)
                positionPlayerInventory(CENTER_INV_LEFT, JOBS_PLAYER_TOP, JOBS_HOTBAR_TOP)
            }
            CommandPostMode.POLICY -> return
            CommandPostMode.LOGISTICS -> {
                positionStorageSlots(CENTER_INV_LEFT, LOGISTICS_BUFFER_TOP)
                positionPlayerInventory(CENTER_INV_LEFT, LOGISTICS_PLAYER_TOP, LOGISTICS_HOTBAR_TOP)
            }
        }
    }

    private fun positionModuleSlots(left: Int, top: Int) {
        for (row in 0 until RouterScreenHandler.MODULE_ROWS) {
            for (col in 0 until RouterScreenHandler.MODULE_COLUMNS) {
                val slotIndex = row * RouterScreenHandler.MODULE_COLUMNS + col
                setSlotPosition(slotIndex, left + col * 18, top + row * 18)
            }
        }
    }

    private fun positionUpgradeSlots(left: Int, top: Int) {
        for (index in 0 until RouterBlockEntity.UPGRADE_SLOT_COUNT) {
            setSlotPosition(RouterScreenHandler.UPGRADE_SCREEN_SLOT_START + index, left + index * 18, top)
        }
    }

    private fun positionStorageSlots(left: Int, top: Int) {
        for (row in 0 until RouterScreenHandler.STORAGE_ROWS) {
            for (col in 0 until RouterScreenHandler.STORAGE_COLUMNS) {
                val slotIndex = RouterScreenHandler.STORAGE_SCREEN_SLOT_START + row * RouterScreenHandler.STORAGE_COLUMNS + col
                setSlotPosition(slotIndex, left + col * 18, top + row * 18)
            }
        }
    }

    private fun positionPlayerInventory(left: Int, top: Int, hotbarTop: Int) {
        val start = RouterScreenHandler.COMMAND_SLOT_COUNT
        for (row in 0..2) {
            for (col in 0..8) {
                setSlotPosition(start + row * 9 + col, left + col * 18, top + row * 18)
            }
        }
        for (col in 0..8) {
            setSlotPosition(start + 27 + col, left + col * 18, hotbarTop)
        }
    }

    private fun setSlotPosition(slotIndex: Int, slotX: Int, slotY: Int) {
        val slot = handler.slots.getOrNull(slotIndex) ?: return
        slot.x = slotX
        slot.y = slotY
    }

    fun hoveredModuleSlot(localMouseX: Int, localMouseY: Int): Int? {
        for (index in 0 until RouterBlockEntity.MODULE_SLOT_COUNT) {
            val slot = handler.slots.getOrNull(index) ?: continue
            if (slot.x == HIDDEN_SLOT_X && slot.y == HIDDEN_SLOT_Y) continue
            if (contains(localMouseX, localMouseY, slot.x, slot.y, 18, 18)) return index
        }
        return null
    }

    fun sourceTooltip(pokemon: CrewSourcePokemonSnapshot): List<Text> = buildList {
        add(Text.literal(pokemon.displayName))
        add(Text.translatable("gui.cobblepalsworld.level_species", pokemon.level, friendlySpecies(pokemon.species)))
        add(Text.literal(pokemon.sourceLabel()))
        add(Text.literal(pokemon.statusLabel()))
        if (pokemon.cargoSummary.isNotBlank()) add(Text.literal(pokemon.cargoSummary))
    }

    fun crewTooltip(member: CommandPostCrewMemberSnapshot): List<Text> = buildList {
        add(Text.literal(member.displayName))
        add(Text.translatable("gui.cobblepalsworld.level_species", member.level, friendlySpecies(member.species)))
        add(Text.literal(member.sourceLabel()))
        add(Text.literal(member.statusLabel()))
        add(Text.translatable(member.statusDetailKey()))
        member.tagTypeId?.let { tagId ->
            val role = TagType.fromId(tagId)?.let(TagTypePresentation::roleLabel) ?: tagId
            add(Text.literal(role))
            add(Text.literal("Mastery: ${member.masteryLabel()}").formatted(member.masteryTier().color))
        }
        add(Text.literal(member.assignmentLabel()))
    }

    fun heldStack(itemId: String): ItemStack? = screen.heldStackForPanel(itemId)
    fun play(sound: net.minecraft.sound.SoundEvent) = screen.playForPanel(sound)
    fun clickButton(id: Int) = screen.clickButtonForPanel(id)

    internal fun drawSmallText(context: DrawContext, value: String, localX: Int, localY: Int, color: Int, shadow: Boolean) {
        CobblemonUiChrome.drawSmallText(context, screen.panelTextRenderer, value, originX + localX, originY + localY, color, shadow)
    }

    fun friendlySpecies(species: String): String = species.substringAfter(':').replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

    internal fun humanValue(value: String): String = value
        .lowercase(Locale.ROOT)
        .split('_', '-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } }

    internal fun compactValue(value: String): String {
        val human = humanValue(value)
        return when {
            human.equals("terminate after success", ignoreCase = true) -> "Once"
            human.length <= 4 -> human
            human.contains(' ') -> human.split(' ').joinToString("") { it.firstOrNull()?.uppercaseChar()?.toString().orEmpty() }
            else -> human.take(4)
        }
    }

    fun fit(value: String, maxWidth: Int): String {
        if ((screen.panelTextRenderer.getWidth(value) * CommandPostPcShell.TEXTURE_SCALE).compareTo(maxWidth) <= 0) return value
        var result = value
        while (result.length > 3 && (screen.panelTextRenderer.getWidth("$result...") * CommandPostPcShell.TEXTURE_SCALE).compareTo(maxWidth) > 0) {
            result = result.dropLast(1)
        }
        return "$result..."
    }

    fun contains(mouseX: Int, mouseY: Int, left: Int, top: Int, width: Int, height: Int): Boolean {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height
    }

    companion object {
        internal const val CENTER_INV_LEFT = 91
        internal const val JOBS_TAG_WELL_LEFT = 97
        internal const val JOBS_TAG_WELL_TOP = 37
        internal const val JOBS_TAG_WELL_WIDTH = 60
        internal const val JOBS_TAG_WELL_HEIGHT = 61
        internal const val JOBS_MODULE_LEFT = 101
        internal const val JOBS_MODULE_TOP = 42
        internal const val JOBS_UPGRADE_LEFT = 165
        internal const val JOBS_UPGRADE_TOP = 56
        internal const val JOBS_PLAYER_TOP = 101
        internal const val JOBS_HOTBAR_TOP = 159
        internal const val LOGISTICS_BUFFER_TOP = 39
        internal const val LOGISTICS_PLAYER_TOP = 104
        internal const val LOGISTICS_HOTBAR_TOP = 162
        const val POLICY_VISIBLE_ROWS = 4
        const val HIDDEN_SLOT_X = -10_000
        const val HIDDEN_SLOT_Y = -10_000
    }
}

package com.cobblepalsworld.gui.router

import com.cobblepalsworld.gui.CobblePalsUiTheme
import com.cobblepalsworld.gui.UiGlyph
import com.cobblepalsworld.gui.UiIconButtons
import com.cobblepalsworld.gui.crew.CommandPostCrewMemberSnapshot
import com.cobblepalsworld.gui.crew.CommandPostCrewSnapshotCache
import com.cobblepalsworld.gui.crew.CrewSourcePokemonSnapshot
import com.cobblepalsworld.gui.crew.CrewSourceSnapshotCache
import com.cobblepalsworld.gui.crew.CrewSourceType
import com.cobblepalsworld.networking.CobblePalsNetworking
import com.cobblepalsworld.router.RouterBlockEntity
import com.cobblepalsworld.tag.TagItem
import com.cobblepalsworld.tag.TagRoleFamily
import com.cobblepalsworld.tag.TagSpec
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.TagTypePresentation
import com.cobblepalsworld.tag.filter.FilterMatchMode
import com.cobblepalsworld.tag.filter.TagPolicyAnalyzer
import com.cobblepalsworld.tag.filter.TagPolicyIssue
import com.cobblepalsworld.tag.filter.TagPolicyLine
import com.cobblepalsworld.tag.filter.TagPolicySeverity
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import java.util.Locale

class RouterScreen(
    handler: RouterScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<RouterScreenHandler>(handler, inventory, title) {

    private enum class CommandPostView(val label: String, val glyph: UiGlyph, val accent: Int) {
        Home("Home", UiGlyph.Home, CobblePalsUiTheme.ACCENT_WORK),
        Crew("Crew", UiGlyph.Cycle, CobblePalsUiTheme.ACCENT_CREW),
        Policy("Roles", UiGlyph.Filter, CobblePalsUiTheme.ACCENT_POLICY),
        Logistics("Buffer", UiGlyph.Data, CobblePalsUiTheme.ACCENT_BUFFER)
    }

    private enum class CrewSortMode(val label: String) {
        Need("Need"),
        Name("Name"),
        Role("Role")
    }

    private enum class CrewFocusFilter(val label: String) {
        All("All"),
        Active("Active"),
        Blocked("Blocked"),
        Fainted("Fainted"),
        Reserve("Reserve")
    }

    private enum class CrewModeFilter(val label: String) {
        All("All"),
        General("General"),
        Preferred("Preferred"),
        Restricted("Strict"),
        Reserved("Reserve")
    }

    private data class HoverTooltip(val id: String, val lines: List<Text>)

    private data class TabButton(
        val view: CommandPostView,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val tooltip: List<Text>
    )

    private data class TextChipButton(
        val id: String,
        val left: Int,
        val top: Int,
        val width: Int,
        val label: String,
        val value: String,
        val accentColor: Int,
        val active: Boolean,
        val tooltip: List<Text>
    )

    private data class IconButton(
        val id: String,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val glyph: UiGlyph,
        val accentColor: Int,
        val active: Boolean,
        val tooltip: List<Text>
    )

    private data class InstalledCommandCard(
        val moduleIndex: Int,
        val tagType: TagType,
        val configured: Boolean,
        val spec: TagSpec
    )

    private data class CommandAlert(
        val label: String,
        val detail: String,
        val accentColor: Int,
        val textColor: Int
    )

    private data class RoleSummary(
        val id: String,
        val moduleIndex: Int,
        val tagType: TagType,
        val label: String,
        val slotLabel: String,
        val family: TagRoleFamily,
        val installedCount: Int,
        val configuredCount: Int,
        val staffedCount: Int,
        val activeCount: Int,
        val targetLabel: String,
        val filterSummary: String,
        val matchSummary: String,
        val filterWhitelist: Boolean,
        val filterMatchNbt: Boolean,
        val filterMatchMode: FilterMatchMode,
        val redstoneLabel: String,
        val targetStrategyLabel: String,
        val runLabel: String,
        val regulatorAmount: Int,
        val extraTargetCount: Int,
        val issueCount: Int,
        val firstIssue: TagPolicyIssue?,
        val pressureLabel: String,
        val pressureColor: Int,
        val assignedNames: List<String>,
        val tooltipLines: List<Text>
    )

    private data class SourceRowSummary(
        val snapshot: CrewSourcePokemonSnapshot,
        val accentColor: Int,
        val tooltipLines: List<Text>
    )

    private data class NativeRosterRow(
        val source: CommandPostCrewMemberSnapshot,
        val accentColor: Int,
        val tooltipLines: List<Text>
    )

    private data class HomeState(
        val jobCardCount: Int,
        val idleCount: Int,
        val queuedCount: Int,
        val augmentCount: Int,
        val setupIssueCount: Int,
        val hasCrew: Boolean,
        val subtitle: String,
        val jobSummary: String,
        val crewSummary: String,
        val augmentSummary: String,
        val alert: CommandAlert,
        val roles: List<RoleSummary>,
        val familyCounts: List<Pair<TagRoleFamily, Int>>
    )

    companion object {
        private const val HEADER_HEIGHT = 34
        private const val TAB_TOP = 37
        private const val TAB_HEIGHT = 15
        private const val DECK_LEFT = 10
        private const val DECK_TOP = 58
        private const val DECK_WIDTH = 280
        private const val DECK_HEIGHT = 112
        private const val WORK_LEFT = 10
        private const val WORK_TOP = 178
        private const val WORK_WIDTH = 280
        private const val WORK_HEIGHT = 226
        private const val LIST_LEFT = 16
        private const val LIST_WIDTH = 142
        private const val DETAIL_LEFT = 166
        private const val DETAIL_WIDTH = 116
        private const val ROW_TOP = 228
        private const val POLICY_LIST_LEFT = 16
        private const val POLICY_LIST_TOP = 218
        private const val POLICY_LIST_WIDTH = 132
        private const val POLICY_ROW_HEIGHT = 22
        private const val POLICY_DETAIL_LEFT = 154
        private const val POLICY_DETAIL_WIDTH = 128
        private const val POLICY_DETAIL_TOP = 218
        private const val POLICY_ACTION_TOP = 300
        private const val CREW_INFO_TOP = 198
        private const val CREW_CHIP_TOP = 216
        private const val CREW_ROW_TOP = 254
        private const val SOURCE_LEFT = 154
        private const val SOURCE_WIDTH = 128
        private const val SOURCE_ROW_TOP = 254
        private const val SOURCE_DETAIL_TOP = 356
        private const val MAX_SOURCE_ROWS = 5
        private const val HOME_INV_LABEL_Y = 300
        private const val ROLE_ROW_HEIGHT = 17
        private const val CREW_ROW_HEIGHT = 20
        private const val MAX_ROLE_ROWS = 8
        private const val MAX_CREW_ROWS = 5
        private const val CHIP_HEIGHT = 13
        private const val HIDDEN_SLOT_X = -1000
        private const val HIDDEN_SLOT_Y = -1000
    }

    private var hoveredTooltipId: String? = null
    private var hoveredTooltipSinceMs: Long = 0L
    private var activeView = CommandPostView.Home
    private var nextCrewRefreshAtMs = 0L
    private var nextCrewSourceRefreshAtMs = 0L
    private var crewSortMode = CrewSortMode.Need
    private var crewFamilyFilter: TagRoleFamily? = null
    private var crewFocusFilter = CrewFocusFilter.All
    private var crewModeFilter = CrewModeFilter.All
    private var crewPageIndex = 0
    private var crewSourceType = CrewSourceType.PARTY
    private var crewSourcePageIndex = 0
    private var selectedSourcePokemonId: java.util.UUID? = null
    private var selectedPolicyRowIndex: Int? = null

    override fun init() {
        backgroundWidth = RouterScreenHandler.BACKGROUND_WIDTH
        backgroundHeight = RouterScreenHandler.BACKGROUND_HEIGHT
        super.init()
        titleX = 12
        titleY = 8
        playerInventoryTitleX = RouterScreenHandler.PLAYER_INV_X
        playerInventoryTitleY = RouterScreenHandler.PLAYER_INV_Y - 12
        applySlotLayout()
        requestCrewRefresh()
        requestCrewSourceRefresh()
        nextCrewRefreshAtMs = System.currentTimeMillis() + 1000L
        nextCrewSourceRefreshAtMs = System.currentTimeMillis() + 3000L
    }

    private fun contains(localMouseX: Int, localMouseY: Int, left: Int, top: Int, width: Int, height: Int): Boolean {
        return localMouseX in left until (left + width) && localMouseY in top until (top + height)
    }

    private fun switchView(view: CommandPostView) {
        if (activeView == view) return
        activeView = view
        crewPageIndex = 0
        crewSourcePageIndex = 0
        selectedSourcePokemonId = null
        if (view == CommandPostView.Crew) {
            requestCrewRefresh()
            requestCrewSourceRefresh()
        }
        applySlotLayout()
    }

    private fun requestCrewRefresh() {
        CobblePalsNetworking.sendCommandPostCrewRefresh(handler.routerPos)
    }

    private fun requestCrewSourceRefresh() {
        CobblePalsNetworking.sendCrewSourceRefresh(handler.routerPos)
    }

    private fun currentNativeCrewSnapshot() = CommandPostCrewSnapshotCache.get(handler.routerPos)

    private fun currentCrewSources() = CrewSourceSnapshotCache.get(handler.routerPos)

    private fun currentRegistries() = client?.world?.registryManager

    private fun buildInstalledCards(): List<InstalledCommandCard> {
        return handler.slots.take(RouterBlockEntity.MODULE_SLOT_COUNT).mapIndexedNotNull { moduleIndex, slot ->
            val stack = slot.stack
            val tagItem = stack.item as? TagItem ?: return@mapIndexedNotNull null
            val spec = currentRegistries()?.let { TagItem.getSpec(stack, it) } ?: TagSpec(type = tagItem.tagType)
            InstalledCommandCard(moduleIndex, tagItem.tagType, isConfigured(stack, tagItem), spec)
        }
    }

    private fun buildHomeState(): HomeState {
        val installedCards = buildInstalledCards()
        val jobCardCount = installedCards.size
        val hasCrew = handler.rosterCount > 0
        val idleCount = (handler.assignedCount - handler.activeCount).coerceAtLeast(0)
        val queuedCount = (jobCardCount - handler.assignedCount).coerceAtLeast(0)
        val augmentCount = handler.slots
            .subList(RouterScreenHandler.UPGRADE_SCREEN_SLOT_START, RouterScreenHandler.STORAGE_SCREEN_SLOT_START)
            .count { it.hasStack() }
        val familyCounts = installedCards
            .groupingBy { familyFor(it.tagType) }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<TagRoleFamily, Int>> { it.value }.thenBy { it.key.label })
            .map { it.key to it.value }
        val cardsByType = installedCards.groupBy { it.tagType }
        val policyLines = installedCards.map { card -> TagPolicyLine(card.moduleIndex, card.tagType, card.spec) }
        val issuesByModule = TagPolicyAnalyzer.issuesByModule(policyLines)

        val roles = installedCards
            .sortedBy { it.moduleIndex }
            .map { card ->
                val tagType = card.tagType
                val sameTypeCards = cardsByType[tagType].orEmpty().sortedBy { it.moduleIndex }
                val baseLabel = TagTypePresentation.roleLabel(tagType)
                val duplicateIndex = sameTypeCards.indexOfFirst { it.moduleIndex == card.moduleIndex }.takeIf { it >= 0 }?.plus(1) ?: 1
                val roleLabel = if (sameTypeCards.size > 1) "$baseLabel $duplicateIndex" else baseLabel
                val staffedNames = currentNativeCrewSnapshot()?.members?.filter { it.tagTypeId == tagType.id }?.map { it.displayName }.orEmpty()
                val activeCount = if (handler.moduleActive(card.moduleIndex)) 1 else 0
                val staffedCount = if (handler.moduleAssigned(card.moduleIndex)) 1 else 0
                val issues = issuesByModule[card.moduleIndex].orEmpty()
                val firstActionIssue = issues.firstOrNull { it.severity != TagPolicySeverity.INFO }
                val firstIssue = firstActionIssue ?: issues.firstOrNull()
                val configuredCount = if (card.configured) 1 else 0
                val filterSummary = summarizeFilter(tagType, card.spec)
                val pressureLabel = when {
                    firstActionIssue != null -> firstActionIssue.label
                    !hasCrew -> "needs crew"
                    staffedCount <= 0 && handler.rosterCount > 0 -> "needs pal"
                    activeCount > 0 -> "active $activeCount"
                    staffedCount > 0 -> "covered"
                    firstIssue != null -> firstIssue.label
                    else -> "standby"
                }
                val pressureColor = when {
                    firstActionIssue?.severity == TagPolicySeverity.BLOCKING -> CobblePalsUiTheme.ACCENT_DANGER
                    firstActionIssue?.severity == TagPolicySeverity.WARNING -> CobblePalsUiTheme.ACCENT_POLICY
                    !hasCrew -> CobblePalsUiTheme.ACCENT_DANGER
                    staffedCount <= 0 && handler.rosterCount > 0 -> 0xFFFF8A6B.toInt()
                    activeCount > 0 -> CobblePalsUiTheme.ACCENT_WORK
                    staffedCount > 0 -> CobblePalsUiTheme.ACCENT_CREW
                    firstIssue != null -> CobblePalsUiTheme.ACCENT_BUFFER
                    else -> CobblePalsUiTheme.TEXT_FAINT
                }
                val issueLines = issues.take(3).flatMap { issue ->
                    listOf(Text.literal("${issue.label}: ${issue.detail}"))
                }
                RoleSummary(
                    id = "module-${card.moduleIndex}",
                    moduleIndex = card.moduleIndex,
                    tagType = tagType,
                    label = roleLabel,
                    slotLabel = "S${card.moduleIndex + 1}",
                    family = familyFor(tagType),
                    installedCount = 1,
                    configuredCount = configuredCount,
                    staffedCount = staffedCount,
                    activeCount = activeCount,
                    targetLabel = summarizeBinding(tagType, card.spec),
                    filterSummary = filterSummary,
                    matchSummary = summarizeMatch(card.spec),
                    filterWhitelist = card.spec.filter.whitelist,
                    filterMatchNbt = card.spec.filter.matchNbt,
                    filterMatchMode = card.spec.filter.matchMode,
                    redstoneLabel = friendlyId(card.spec.settings.redstoneMode.id),
                    targetStrategyLabel = if (tagType.supportsTargetList) friendlyId(card.spec.settings.targetStrategy.id) else "Fixed",
                    runLabel = if (card.spec.settings.terminateAfterSuccess) "One pass" else "Loop",
                    regulatorAmount = card.spec.settings.regulatorAmount,
                    extraTargetCount = card.spec.settings.extraTargets.size,
                    issueCount = issues.size,
                    firstIssue = firstIssue,
                    pressureLabel = pressureLabel,
                    pressureColor = pressureColor,
                    assignedNames = staffedNames,
                    tooltipLines = listOf(
                        Text.literal("${roleLabel} • ${"S${card.moduleIndex + 1}"}"),
                        Text.literal("${staffedCount}/1 staffed • $pressureLabel"),
                        Text.literal("Target: ${summarizeBinding(tagType, card.spec)}"),
                        Text.literal("Filter: $filterSummary")
                    ) + issueLines
                )
            }
        val setupIssueCount = roles.count { role -> role.firstIssue?.severity?.let { it != TagPolicySeverity.INFO } == true }

        val subtitle = when {
            !hasCrew -> "Assign Party or PC Pokemon."
            jobCardCount <= 0 -> "Add role cards to begin."
            else -> "Native crew command."
        }
        val jobSummary = when {
            jobCardCount <= 0 -> "No roles installed"
            setupIssueCount > 0 -> "$setupIssueCount role${pluralize(setupIssueCount)} need setup"
            else -> "$jobCardCount roles ready"
        }
        val crewSummary = if (jobCardCount <= 0) {
            "No crew demand yet"
        } else {
            "$jobCardCount role${pluralize(jobCardCount)} • ${handler.activeCount} active"
        }
        val augmentSummary = if (augmentCount > 0) "$augmentCount installed" else "None"

        return HomeState(
            jobCardCount = jobCardCount,
            idleCount = idleCount,
            queuedCount = queuedCount,
            augmentCount = augmentCount,
            setupIssueCount = setupIssueCount,
            hasCrew = hasCrew,
            subtitle = subtitle,
            jobSummary = jobSummary,
            crewSummary = crewSummary,
            augmentSummary = augmentSummary,
            alert = buildAlert(jobCardCount, setupIssueCount, queuedCount),
            roles = roles,
            familyCounts = familyCounts
        )
    }

    private fun buildAlert(jobCardCount: Int, setupIssueCount: Int, queuedCount: Int): CommandAlert {
        return when {
            jobCardCount <= 0 -> CommandAlert("Empty", "Install role cards", CobblePalsUiTheme.TEXT_FAINT, CobblePalsUiTheme.TEXT_MUTED)
            setupIssueCount > 0 -> CommandAlert("Setup", "$setupIssueCount roles need targets", CobblePalsUiTheme.ACCENT_POLICY, 0xFFFFE3B2.toInt())
            handler.rosterCount <= 0 -> CommandAlert("No Crew", "Add Party or PC Pokemon", 0xFFCC8F62.toInt(), 0xFFF8DFC9.toInt())
            queuedCount > 0 -> CommandAlert("Queue", "$queuedCount roles await pals", CobblePalsUiTheme.ACCENT_BUFFER, 0xFFDCEBFF.toInt())
            handler.activeCount > 0 -> CommandAlert("Active", "Crew is working", CobblePalsUiTheme.ACCENT_WORK, 0xFFD7F7F0.toInt())
            else -> CommandAlert("Ready", "Crew is standing by", CobblePalsUiTheme.ACCENT_CREW, 0xFFE1F4D8.toInt())
        }
    }

    private fun filteredNativeRosterRows(): List<NativeRosterRow> {
        val rows = nativeRosterRows().filter { row ->
            val family = row.source.tagTypeId?.let(TagType::fromId)?.let(TagTypePresentation::familyOf)
            (crewFamilyFilter == null || family == crewFamilyFilter) && matchesFocusFilter(row) && matchesModeFilter(row)
        }
        return when (crewSortMode) {
            CrewSortMode.Need -> rows.sortedWith(compareBy<NativeRosterRow> { it.source.sortRank() }.thenBy { it.source.displayName.lowercase(Locale.ROOT) })
            CrewSortMode.Name -> rows.sortedBy { it.source.displayName.lowercase(Locale.ROOT) }
            CrewSortMode.Role -> rows.sortedWith(compareBy<NativeRosterRow> { it.source.tagTypeId?.let(TagType::fromId)?.let(TagTypePresentation::roleLabel) ?: "No role" }.thenBy { it.source.displayName.lowercase(Locale.ROOT) })
        }
    }

    private fun matchesFocusFilter(row: NativeRosterRow): Boolean {
        val source = row.source
        return when (crewFocusFilter) {
            CrewFocusFilter.All -> true
            CrewFocusFilter.Active -> source.isActive()
            CrewFocusFilter.Blocked -> source.isBlocked()
            CrewFocusFilter.Fainted -> source.isFainted
            CrewFocusFilter.Reserve -> source.assignmentLabel() == "Reserved"
        }
    }

    private fun matchesModeFilter(row: NativeRosterRow): Boolean {
        val assignmentLabel = row.source.assignmentLabel()
        return when (crewModeFilter) {
            CrewModeFilter.All -> true
            CrewModeFilter.General -> assignmentLabel == "General"
            CrewModeFilter.Preferred -> assignmentLabel == "Preferred"
            CrewModeFilter.Restricted -> assignmentLabel == "Restricted" || assignmentLabel == "Strict"
            CrewModeFilter.Reserved -> assignmentLabel == "Reserved"
        }
    }

    private fun visibleNativeRosterRows(): List<NativeRosterRow> {
        val filtered = filteredNativeRosterRows()
        crewPageIndex = crewPageIndex.coerceIn(0, crewPageCount(filtered) - 1)
        return filtered.drop(crewPageIndex * MAX_CREW_ROWS).take(MAX_CREW_ROWS)
    }

    private fun crewPageCount(rows: List<NativeRosterRow>): Int = maxOf(1, (rows.size + MAX_CREW_ROWS - 1) / MAX_CREW_ROWS)

    private fun sourceRows(): List<SourceRowSummary> {
        val source = currentCrewSources().firstOrNull { it.sourceType == crewSourceType } ?: return emptyList()
        return source.entries
            .sortedWith(compareBy<CrewSourcePokemonSnapshot> { if (it.isAvailable && !it.isFainted) 0 else 1 }.thenBy { it.displayName.lowercase(Locale.ROOT) })
            .map { snapshot ->
                SourceRowSummary(
                    snapshot = snapshot,
                    accentColor = sourceStatusColor(snapshot),
                    tooltipLines = listOf(
                        Text.literal(snapshot.displayName),
                        Text.literal("Lv.${snapshot.level} ${friendlyId(snapshot.species)}"),
                        Text.literal(snapshot.sourceLabel()),
                        Text.literal(snapshot.statusLabel())
                    )
                )
            }
    }

    private fun visibleSourceRows(): List<SourceRowSummary> {
        val rows = sourceRows()
        crewSourcePageIndex = crewSourcePageIndex.coerceIn(0, sourcePageCount(rows) - 1)
        return rows.drop(crewSourcePageIndex * MAX_SOURCE_ROWS).take(MAX_SOURCE_ROWS)
    }

    private fun sourcePageCount(rows: List<SourceRowSummary>): Int = maxOf(1, (rows.size + MAX_SOURCE_ROWS - 1) / MAX_SOURCE_ROWS)

    private fun selectedSourceRow(): SourceRowSummary? {
        val selectedId = selectedSourcePokemonId ?: return null
        return allSourceRows().firstOrNull { it.snapshot.pokemonId == selectedId }
    }

    private fun allSourceRows(): List<SourceRowSummary> {
        return currentCrewSources().flatMap { source ->
            source.entries.map { snapshot ->
                SourceRowSummary(
                    snapshot = snapshot,
                    accentColor = sourceStatusColor(snapshot),
                    tooltipLines = listOf(
                        Text.literal(snapshot.displayName),
                        Text.literal("Lv.${snapshot.level} ${friendlyId(snapshot.species)}"),
                        Text.literal(snapshot.sourceLabel()),
                        Text.literal(snapshot.statusLabel())
                    )
                )
            }
        }
    }

    private fun nativeRosterRows(): List<NativeRosterRow> {
        return currentNativeCrewSnapshot()?.members.orEmpty()
            .sortedWith(compareBy<CommandPostCrewMemberSnapshot> { it.sortRank() }.thenBy { it.displayName.lowercase(Locale.ROOT) })
            .map { snapshot ->
                NativeRosterRow(
                    source = snapshot,
                    accentColor = nativeStatusColor(snapshot),
                    tooltipLines = listOf(
                        Text.literal(snapshot.displayName),
                        Text.literal("Lv.${snapshot.level} ${friendlyId(snapshot.species)}"),
                        Text.literal(snapshot.sourceLabel()),
                        Text.literal(snapshot.statusLabel()),
                        Text.literal(snapshot.assignmentLabel()),
                        Text.literal(snapshot.cargoSummary)
                    )
                )
            }
    }

    private fun selectedPolicyRole(state: HomeState): RoleSummary? {
        return state.roles.getOrNull(selectedPolicyRowIndex ?: -1)
    }

    private fun selectPolicyForTag(tagTypeId: String?, state: HomeState) {
        selectedPolicyRowIndex = state.roles.indexOfFirst { it.tagType.id == tagTypeId }.takeIf { it >= 0 }
    }

    private fun sourceStatusColor(snapshot: CrewSourcePokemonSnapshot): Int {
        return when {
            snapshot.isFainted -> CobblePalsUiTheme.ACCENT_DANGER
            !snapshot.isAvailable -> CobblePalsUiTheme.TEXT_FAINT
            snapshot.isCrewMember && snapshot.tagTypeId != null -> CobblePalsUiTheme.ACCENT_WORK
            snapshot.isCrewMember -> CobblePalsUiTheme.ACCENT_CREW
            snapshot.sourceType == CrewSourceType.PARTY -> CobblePalsUiTheme.ACCENT_CREW
            else -> CobblePalsUiTheme.ACCENT_BUFFER
        }
    }

    private fun nativeStatusColor(snapshot: CommandPostCrewMemberSnapshot): Int {
        return when {
            snapshot.isMissing -> CobblePalsUiTheme.ACCENT_DANGER
            snapshot.isFainted -> CobblePalsUiTheme.ACCENT_DANGER
            snapshot.isBlocked() -> CobblePalsUiTheme.ACCENT_DANGER
            snapshot.isActive() -> CobblePalsUiTheme.ACCENT_WORK
            snapshot.tagTypeId != null -> CobblePalsUiTheme.ACCENT_CREW
            else -> CobblePalsUiTheme.TEXT_FAINT
        }
    }

    private fun setSlotPosition(slotIndex: Int, slotX: Int, slotY: Int) {
        val slot = handler.slots.getOrNull(slotIndex) ?: return
        slot.x = slotX
        slot.y = slotY
    }

    private fun hideSlot(slotIndex: Int) {
        setSlotPosition(slotIndex, HIDDEN_SLOT_X, HIDDEN_SLOT_Y)
    }

    private fun applySlotLayout() {
        for (row in 0 until RouterScreenHandler.MODULE_ROWS) {
            for (col in 0 until RouterScreenHandler.MODULE_COLUMNS) {
                val slotIndex = row * RouterScreenHandler.MODULE_COLUMNS + col
                setSlotPosition(slotIndex, RouterScreenHandler.MODULE_START_X + col * 18, RouterScreenHandler.MODULE_START_Y + row * 18)
            }
        }

        for (index in 0 until RouterBlockEntity.UPGRADE_SLOT_COUNT) {
            val slotIndex = RouterScreenHandler.UPGRADE_SCREEN_SLOT_START + index
            setSlotPosition(slotIndex, RouterScreenHandler.BOOST_START_X + index * 18, RouterScreenHandler.BOOST_START_Y)
        }

        val showLogistics = activeView == CommandPostView.Logistics
        val showPlayerInventory = activeView == CommandPostView.Home || showLogistics
        for (row in 0 until RouterScreenHandler.STORAGE_ROWS) {
            for (col in 0 until RouterScreenHandler.STORAGE_COLUMNS) {
                val slotIndex = RouterScreenHandler.STORAGE_SCREEN_SLOT_START + row * RouterScreenHandler.STORAGE_COLUMNS + col
                if (showLogistics) {
                    setSlotPosition(slotIndex, RouterScreenHandler.STORAGE_START_X + col * 18, RouterScreenHandler.STORAGE_START_Y + row * 18)
                } else {
                    hideSlot(slotIndex)
                }
            }
        }

        val playerStart = RouterScreenHandler.COMMAND_SLOT_COUNT
        for (row in 0..2) {
            for (col in 0..8) {
                val slotIndex = playerStart + row * 9 + col
                if (showPlayerInventory) {
                    setSlotPosition(slotIndex, RouterScreenHandler.PLAYER_INV_X + col * 18, RouterScreenHandler.PLAYER_INV_Y + row * 18)
                } else {
                    hideSlot(slotIndex)
                }
            }
        }

        val hotbarStart = playerStart + 27
        for (col in 0..8) {
            if (showPlayerInventory) {
                setSlotPosition(hotbarStart + col, RouterScreenHandler.PLAYER_INV_X + col * 18, RouterScreenHandler.PLAYER_INV_Y + 58)
            } else {
                hideSlot(hotbarStart + col)
            }
        }
    }

    private fun tabButtons(state: HomeState): List<TabButton> {
        return listOf(
            TabButton(CommandPostView.Home, 10, TAB_TOP, 62, TAB_HEIGHT, listOf(Text.literal("Home"), Text.literal(state.jobSummary), Text.literal(state.crewSummary))),
            TabButton(CommandPostView.Crew, 76, TAB_TOP, 62, TAB_HEIGHT, listOf(Text.literal("Crew"), Text.literal("${handler.rosterCount} crew members"))),
            TabButton(CommandPostView.Policy, 142, TAB_TOP, 70, TAB_HEIGHT, listOf(Text.literal("Roles"), Text.literal("${state.roles.size} installed role lines"))),
            TabButton(CommandPostView.Logistics, 216, TAB_TOP, 74, TAB_HEIGHT, listOf(Text.literal("Buffer"), Text.literal("Inventory and item flow")))
        )
    }

    private fun chipWidth(label: String, value: String): Int = textRenderer.getWidth(label) + textRenderer.getWidth(value) + 18

    private fun crewFilterButtons(): List<TextChipButton> {
        val familyValue = crewFamilyFilter?.label ?: "All"
        val pageCount = crewPageCount(filteredNativeRosterRows())
        return listOf(
            TextChipButton("crew-sort", 16, CREW_CHIP_TOP, chipWidth("Sort", crewSortMode.label), "Sort", crewSortMode.label, CobblePalsUiTheme.ACCENT_BUFFER, true, listOf(Text.literal("Sort crew"), Text.literal("Current: ${crewSortMode.label}"))),
            TextChipButton("crew-family", 86, CREW_CHIP_TOP, chipWidth("Family", familyValue), "Family", familyValue, CobblePalsUiTheme.ACCENT_WORK, crewFamilyFilter != null, listOf(Text.literal("Family filter"), Text.literal("Current: $familyValue"))),
            TextChipButton("crew-focus", 16, CREW_CHIP_TOP + 16, chipWidth("State", crewFocusFilter.label), "State", crewFocusFilter.label, CobblePalsUiTheme.ACCENT_POLICY, crewFocusFilter != CrewFocusFilter.All, listOf(Text.literal("State filter"), Text.literal("Current: ${crewFocusFilter.label}"))),
            TextChipButton("crew-mode", 86, CREW_CHIP_TOP + 16, chipWidth("Mode", crewModeFilter.label), "Mode", crewModeFilter.label, CobblePalsUiTheme.ACCENT_PURPLE, crewModeFilter != CrewModeFilter.All, listOf(Text.literal("Assignment filter"), Text.literal("Current: ${crewModeFilter.label}"))),
            TextChipButton("crew-page", 210, CREW_CHIP_TOP + 16, 48, "Page", "${crewPageIndex + 1}/$pageCount", CobblePalsUiTheme.TEXT_FAINT, false, listOf(Text.literal("Page ${crewPageIndex + 1} of $pageCount")))
        )
    }

    private fun crewPageButtons(): List<IconButton> {
        val pageCount = crewPageCount(filteredNativeRosterRows())
        return listOf(
            IconButton("crew-prev", 264, CREW_CHIP_TOP + 16, 10, 10, UiGlyph.Prev, CobblePalsUiTheme.ACCENT_BUFFER, crewPageIndex > 0, listOf(Text.literal("Previous page"))),
            IconButton("crew-next", 276, CREW_CHIP_TOP + 16, 10, 10, UiGlyph.Next, CobblePalsUiTheme.ACCENT_BUFFER, crewPageIndex < pageCount - 1, listOf(Text.literal("Next page")))
        )
    }

    private fun sourceTypeButtons(): List<TextChipButton> {
        val sources = currentCrewSources()
        fun count(type: CrewSourceType) = sources.firstOrNull { it.sourceType == type }?.entries?.size ?: 0
        return listOf(
            TextChipButton("source-party", SOURCE_LEFT, CREW_CHIP_TOP, 56, "Party", count(CrewSourceType.PARTY).toString(), CobblePalsUiTheme.ACCENT_CREW, crewSourceType == CrewSourceType.PARTY, listOf(Text.literal("Party Pokemon"))),
            TextChipButton("source-pc", SOURCE_LEFT + 60, CREW_CHIP_TOP, 52, "PC", count(CrewSourceType.PC).toString(), CobblePalsUiTheme.ACCENT_BUFFER, crewSourceType == CrewSourceType.PC, listOf(Text.literal("PC Pokemon")))
        )
    }

    private fun sourcePageButtons(): List<IconButton> {
        val pageCount = sourcePageCount(sourceRows())
        return listOf(
            IconButton("source-prev", SOURCE_LEFT + SOURCE_WIDTH - 22, CREW_CHIP_TOP + 16, 10, 10, UiGlyph.Prev, CobblePalsUiTheme.ACCENT_BUFFER, crewSourcePageIndex > 0, listOf(Text.literal("Previous source page"))),
            IconButton("source-next", SOURCE_LEFT + SOURCE_WIDTH - 10, CREW_CHIP_TOP + 16, 10, 10, UiGlyph.Next, CobblePalsUiTheme.ACCENT_BUFFER, crewSourcePageIndex < pageCount - 1, listOf(Text.literal("Next source page")))
        )
    }

    private fun sourceDetailButtons(selected: SourceRowSummary?): List<TextChipButton> {
        selected ?: return emptyList()
        val source = selected.snapshot
        val active = source.isCrewMember || source.isAvailable
        val value = if (source.isCrewMember) "Drop" else "Add"
        return listOf(
            TextChipButton(
                "source-crew-action",
            SOURCE_LEFT + 86,
                SOURCE_DETAIL_TOP + 27,
            36,
            "",
                value,
                if (source.isCrewMember) CobblePalsUiTheme.ACCENT_DANGER else CobblePalsUiTheme.ACCENT_CREW,
                active,
                listOf(Text.literal(if (source.isCrewMember) "Remove from crew" else "Add to crew"), Text.literal(source.statusLabel()))
            )
        )
    }

    private fun nativeRosterDetailButtons(selected: NativeRosterRow?): List<TextChipButton> {
        selected ?: return emptyList()
        return listOf(
            TextChipButton("native-return-action", LIST_LEFT + 94, SOURCE_DETAIL_TOP + 27, 38, "", "Home", CobblePalsUiTheme.ACCENT_PURPLE, !selected.source.isMissing, listOf(Text.literal("Return to Command Post"), Text.literal(selected.source.statusLabel())))
        )
    }

    private fun nativeActionButtons(selected: NativeRosterRow?): List<TextChipButton> {
        selected ?: return emptyList()
        val crew = selected.source
        return listOf(
            TextChipButton("native-mode-action", SOURCE_LEFT + 6, SOURCE_DETAIL_TOP + 8, 56, "Mode", crew.assignmentLabel(), CobblePalsUiTheme.ACCENT_BUFFER, true, listOf(Text.literal("Crew mode"), Text.literal("Current: ${crew.assignmentLabel()}"))),
            TextChipButton("native-fallback-action", SOURCE_LEFT + 66, SOURCE_DETAIL_TOP + 8, 56, "Fallback", if (crew.allowFallback) "On" else "Off", if (crew.allowFallback) CobblePalsUiTheme.ACCENT_CREW else CobblePalsUiTheme.ACCENT_DANGER, true, listOf(Text.literal("Fallback"), Text.literal(if (crew.allowFallback) "Enabled" else "Locked"))),
            TextChipButton("native-drop-action", SOURCE_LEFT + 82, SOURCE_DETAIL_TOP + 27, 40, "", "Drop", CobblePalsUiTheme.ACCENT_DANGER, true, listOf(Text.literal("Remove from crew"), Text.literal(crew.statusLabel())))
        )
    }

    private fun policyDetailButtons(policy: RoleSummary?): List<TextChipButton> {
        policy ?: return emptyList()
        val filterMode = if (policy.filterWhitelist) "Allow" else "Block"
        val nbtMode = if (policy.filterMatchNbt) "Exact" else "Loose"
        val matchMode = if (policy.filterMatchMode == FilterMatchMode.ALL) "All" else "Any"
        val buttons = mutableListOf<TextChipButton>()
        var top = POLICY_ACTION_TOP
        if (policy.tagType.usesFilter) {
            buttons += TextChipButton("policy-mode", POLICY_DETAIL_LEFT + 6, top, POLICY_DETAIL_WIDTH - 12, "Mode", filterMode, CobblePalsUiTheme.ACCENT_WORK, true, listOf(Text.literal("Filter mode")))
            top += 16
            buttons += TextChipButton("policy-nbt", POLICY_DETAIL_LEFT + 6, top, POLICY_DETAIL_WIDTH - 12, "NBT", nbtMode, CobblePalsUiTheme.ACCENT_BUFFER, true, listOf(Text.literal("NBT match")))
            top += 16
            buttons += TextChipButton("policy-match", POLICY_DETAIL_LEFT + 6, top, POLICY_DETAIL_WIDTH - 12, "Match", matchMode, CobblePalsUiTheme.ACCENT_POLICY, true, listOf(Text.literal("Match mode"), Text.literal(matchModeHelp(policy.filterMatchMode))))
            top += 16
        }
        buttons += TextChipButton("policy-signal", POLICY_DETAIL_LEFT + 6, top, POLICY_DETAIL_WIDTH - 12, "Signal", policy.redstoneLabel, CobblePalsUiTheme.ACCENT_DANGER, true, listOf(Text.literal("Redstone mode")))
        top += 16
        if (policy.tagType.supportsTargetList) {
            buttons += TextChipButton("policy-target", POLICY_DETAIL_LEFT + 6, top, POLICY_DETAIL_WIDTH - 12, "Target", policy.targetStrategyLabel, CobblePalsUiTheme.ACCENT_BUFFER, true, listOf(Text.literal("Target order")))
            top += 16
        }
        buttons += TextChipButton("policy-deep", POLICY_DETAIL_LEFT + 6, top, POLICY_DETAIL_WIDTH - 12, "Open", "Editor", CobblePalsUiTheme.ACCENT_POLICY, true, listOf(Text.literal("Full editor")))
        return buttons
    }

    private fun drawLocalHeaderChip(context: DrawContext, text: Text, left: Int, top: Int, style: com.cobblepalsworld.gui.UiChipStyle) {
        val width = textRenderer.getWidth(text) + 14
        context.fill(left, top, left + width, top + 13, style.bodyColor)
        context.fill(left, top, left + 3, top + 13, style.accentColor)
        context.drawText(textRenderer, text, left + 7, top + 3, style.textColor, false)
    }

    private fun drawHeader(context: DrawContext, state: HomeState) {
        val linkText = if (state.hasCrew) Text.literal("CREW") else Text.literal("EMPTY")
        val chipStyle = if (state.hasCrew) CobblePalsUiTheme.linkedStateChip else CobblePalsUiTheme.unlinkedStateChip
        context.drawText(textRenderer, Text.literal("COMMAND POST"), 12, 8, CobblePalsUiTheme.HEADER_TEXT, false)
        context.drawText(textRenderer, Text.literal(fit(state.subtitle, 194)), 12, 20, CobblePalsUiTheme.SUBTITLE_TEXT, false)
        drawLocalHeaderChip(context, linkText, backgroundWidth - textRenderer.getWidth(linkText) - 22, 9, chipStyle)
    }

    private fun drawTabs(context: DrawContext, localMouseX: Int, localMouseY: Int, state: HomeState) {
        tabButtons(state).forEach { tab ->
            val active = activeView == tab.view
            val hovered = contains(localMouseX, localMouseY, tab.left, tab.top, tab.width, tab.height)
            val body = when {
                active -> 0xFF202B34.toInt()
                hovered -> 0xFF1A242C.toInt()
                else -> 0xFF10171D.toInt()
            }
            context.fill(tab.left, tab.top, tab.left + tab.width, tab.top + tab.height, body)
            context.fill(tab.left, tab.top + tab.height - 2, tab.left + tab.width, tab.top + tab.height, if (active) tab.view.accent else 0xFF26323C.toInt())
            UiIconButtons.draw(context, tab.left + 4, tab.top + 3, 9, 9, tab.view.glyph, tab.view.accent, hovered, active)
            context.drawText(textRenderer, Text.literal(tab.view.label), tab.left + 17, tab.top + 4, if (active) CobblePalsUiTheme.TEXT_PRIMARY else CobblePalsUiTheme.TEXT_MUTED, false)
        }
    }

    private fun drawDeck(context: DrawContext, state: HomeState) {
        context.drawText(textRenderer, Text.literal("Role Cards"), 16, 62, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("Boosts"), 16, 148, CobblePalsUiTheme.TEXT_MUTED, false)
        CobblePalsUiTheme.drawStatusCard(context, textRenderer, 0, 0, 96, 76, 54, "Crew", handler.rosterCount, CobblePalsUiTheme.ACCENT_CREW, CobblePalsUiTheme.TEXT_PRIMARY)
        CobblePalsUiTheme.drawStatusCard(context, textRenderer, 0, 0, 154, 76, 54, "Roles", state.jobCardCount, CobblePalsUiTheme.ACCENT_POLICY, CobblePalsUiTheme.TEXT_PRIMARY)
        CobblePalsUiTheme.drawStatusCard(context, textRenderer, 0, 0, 212, 76, 54, "Active", handler.activeCount, CobblePalsUiTheme.ACCENT_WORK, CobblePalsUiTheme.TEXT_PRIMARY)
        drawAlert(context, state.alert, 96, 104, 170)
        context.drawText(textRenderer, Text.literal(fit(state.crewSummary, 160)), 100, 126, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal("Shared: ${state.augmentSummary}"), 180, 148, CobblePalsUiTheme.TEXT_FAINT, false)
    }

    private fun drawAlert(context: DrawContext, alert: CommandAlert, left: Int, top: Int, width: Int) {
        context.fill(left, top, left + width, top + 18, 0xFF0D141A.toInt())
        context.fill(left, top, left + 4, top + 18, alert.accentColor)
        context.drawText(textRenderer, Text.literal(alert.label), left + 8, top + 5, alert.accentColor, false)
        context.drawText(textRenderer, Text.literal(fit(alert.detail, width - 64)), left + 58, top + 5, alert.textColor, false)
    }

    private fun drawFixedChip(context: DrawContext, chip: TextChipButton, hovered: Boolean) {
        val body = when {
            hovered -> 0xFF202B34.toInt()
            chip.active -> 0xFF1A242C.toInt()
            else -> 0xFF10171D.toInt()
        }
        context.fill(chip.left, chip.top, chip.left + chip.width, chip.top + CHIP_HEIGHT, body)
        context.fill(chip.left, chip.top, chip.left + 3, chip.top + CHIP_HEIGHT, chip.accentColor)
        context.drawText(textRenderer, Text.literal(chip.label), chip.left + 6, chip.top + 3, CobblePalsUiTheme.TEXT_FAINT, false)
        val labelWidth = textRenderer.getWidth(chip.label)
        val valueText = Text.literal(fit(chip.value, (chip.width - labelWidth - 18).coerceAtLeast(12)))
        context.drawText(textRenderer, valueText, chip.left + chip.width - textRenderer.getWidth(valueText) - 5, chip.top + 3, CobblePalsUiTheme.TEXT_PRIMARY, false)
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        CobblePalsUiTheme.drawRootFrame(context, x, y, backgroundWidth, backgroundHeight, HEADER_HEIGHT)
        CobblePalsUiTheme.drawPanel(context, x, y, DECK_LEFT, DECK_TOP, DECK_WIDTH, DECK_HEIGHT, CobblePalsUiTheme.jobsPanel)
        CobblePalsUiTheme.drawPanel(context, x, y, WORK_LEFT, WORK_TOP, WORK_WIDTH, WORK_HEIGHT, if (activeView == CommandPostView.Logistics) CobblePalsUiTheme.inventoryPanel else CobblePalsUiTheme.statusPanel)

        for (row in 0 until RouterScreenHandler.MODULE_ROWS) {
            for (col in 0 until RouterScreenHandler.MODULE_COLUMNS) {
                CobblePalsUiTheme.drawSlotWell(context, x, y, RouterScreenHandler.MODULE_START_X + col * 18, RouterScreenHandler.MODULE_START_Y + row * 18, CobblePalsUiTheme.jobsSlot)
            }
        }
        for (index in 0 until RouterBlockEntity.UPGRADE_SLOT_COUNT) {
            CobblePalsUiTheme.drawSlotWell(context, x, y, RouterScreenHandler.BOOST_START_X + index * 18, RouterScreenHandler.BOOST_START_Y, CobblePalsUiTheme.augmentSlot)
        }

        if (activeView == CommandPostView.Logistics) {
            for (row in 0 until RouterScreenHandler.STORAGE_ROWS) {
                for (col in 0 until RouterScreenHandler.STORAGE_COLUMNS) {
                    CobblePalsUiTheme.drawSlotWell(context, x, y, RouterScreenHandler.STORAGE_START_X + col * 18, RouterScreenHandler.STORAGE_START_Y + row * 18, CobblePalsUiTheme.inventorySlot)
                }
            }
        }

        if (activeView == CommandPostView.Home || activeView == CommandPostView.Logistics) {
            for (row in 0..2) {
                for (col in 0..8) {
                    CobblePalsUiTheme.drawSlotWell(context, x, y, RouterScreenHandler.PLAYER_INV_X + col * 18, RouterScreenHandler.PLAYER_INV_Y + row * 18, CobblePalsUiTheme.inventorySlot)
                }
            }
            for (col in 0..8) {
                CobblePalsUiTheme.drawSlotWell(context, x, y, RouterScreenHandler.PLAYER_INV_X + col * 18, RouterScreenHandler.PLAYER_INV_Y + 58, CobblePalsUiTheme.inventorySlot)
            }
        }
    }

    override fun drawForeground(context: DrawContext, mouseX: Int, mouseY: Int) {
        val localMouseX = mouseX - x
        val localMouseY = mouseY - y
        val state = buildHomeState()
        drawHeader(context, state)
        drawTabs(context, localMouseX, localMouseY, state)
        drawDeck(context, state)

        context.drawText(textRenderer, Text.literal(activeView.label.uppercase(Locale.ROOT)), 16, 182, CobblePalsUiTheme.TEXT_PRIMARY, false)
        when (activeView) {
            CommandPostView.Home -> drawHomeView(context, state)
            CommandPostView.Crew -> drawCrewView(context, localMouseX, localMouseY, state)
            CommandPostView.Policy -> drawPolicyView(context, localMouseX, localMouseY, state)
            CommandPostView.Logistics -> drawLogisticsView(context)
        }
    }

    private fun drawHomeView(context: DrawContext, state: HomeState) {
        context.drawText(textRenderer, Text.literal(fit(state.jobSummary, 190)), 16, 196, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(fit(state.crewSummary, 190)), 16, 209, CobblePalsUiTheme.TEXT_FAINT, false)
        var chipLeft = 16
        state.familyCounts.take(4).forEach { (family, count) ->
            val width = CobblePalsUiTheme.drawChip(context, textRenderer, chipLeft, 226, family.label, count.toString(), familyAccent(family), true, false)
            chipLeft += width + 5
        }
        if (state.roles.isEmpty()) {
            context.drawText(textRenderer, Text.literal("No role cards installed."), 16, 258, CobblePalsUiTheme.TEXT_MUTED, false)
            context.drawText(textRenderer, Text.literal("Inventory"), RouterScreenHandler.PLAYER_INV_X, HOME_INV_LABEL_Y, CobblePalsUiTheme.TEXT_MUTED, false)
            return
        }
        context.drawText(textRenderer, Text.literal("Needs Attention"), 16, 252, CobblePalsUiTheme.TEXT_FAINT, false)
        state.roles.take(2).forEachIndexed { index, role ->
            drawRoleRow(context, role, 16, 268 + index * ROLE_ROW_HEIGHT, 266, false)
        }
        context.drawText(textRenderer, Text.literal("Inventory"), RouterScreenHandler.PLAYER_INV_X, HOME_INV_LABEL_Y, CobblePalsUiTheme.TEXT_MUTED, false)
    }

    private fun drawCrewView(context: DrawContext, localMouseX: Int, localMouseY: Int, state: HomeState) {
        val allNativeRows = nativeRosterRows()
        val filteredNativeRows = filteredNativeRosterRows()
        val visibleNativeRows = visibleNativeRosterRows()
        val selectedNative = allNativeRows.firstOrNull { it.source.pokemonId == selectedSourcePokemonId }
        val visibleSources = visibleSourceRows()
        val allSources = sourceRows()
        val selectedSource = if (selectedNative == null) selectedSourceRow() else null
        val rosterTotal = allNativeRows.size
        val rosterVisible = filteredNativeRows.size
        context.drawText(textRenderer, Text.literal(fit("Roster • $rosterVisible/$rosterTotal visible", 122)), 16, CREW_INFO_TOP, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(fit("Sources • ${allSources.size} ${crewSourceType.label}", 122)), SOURCE_LEFT, CREW_INFO_TOP, CobblePalsUiTheme.TEXT_MUTED, false)

        crewFilterButtons().forEach { chip ->
            if (chip.id == "crew-page") return@forEach
            val hovered = contains(localMouseX, localMouseY, chip.left, chip.top, chip.width, CHIP_HEIGHT)
            drawFixedChip(context, chip, hovered)
        }
        crewPageButtons().forEach { button ->
            val hovered = contains(localMouseX, localMouseY, button.left, button.top, button.width, button.height)
            UiIconButtons.draw(context, button.left, button.top, button.width, button.height, button.glyph, button.accentColor, hovered, button.active)
        }
        sourceTypeButtons().forEach { chip ->
            val hovered = contains(localMouseX, localMouseY, chip.left, chip.top, chip.width, CHIP_HEIGHT)
            drawFixedChip(context, chip, hovered)
        }
        sourcePageButtons().forEach { button ->
            val hovered = contains(localMouseX, localMouseY, button.left, button.top, button.width, button.height)
            UiIconButtons.draw(context, button.left, button.top, button.width, button.height, button.glyph, button.accentColor, hovered, button.active)
        }
        val pageText = Text.literal("${crewSourcePageIndex + 1}/${sourcePageCount(allSources)}")
        context.drawText(textRenderer, pageText, SOURCE_LEFT, CREW_CHIP_TOP + 18, CobblePalsUiTheme.TEXT_FAINT, false)

        if (visibleNativeRows.isNotEmpty()) {
            visibleNativeRows.forEachIndexed { index, row ->
                drawNativeRosterRow(context, row, LIST_LEFT, CREW_ROW_TOP + index * CREW_ROW_HEIGHT, LIST_WIDTH, row.source.pokemonId == selectedSourcePokemonId)
            }
        } else {
            context.drawText(textRenderer, Text.literal(if (allNativeRows.isEmpty()) "No crew assigned yet." else "No pals match filters."), LIST_LEFT, ROW_TOP, CobblePalsUiTheme.TEXT_MUTED, false)
        }
        if (visibleSources.isEmpty()) {
            context.drawText(textRenderer, Text.literal("No Pokemon found."), SOURCE_LEFT, SOURCE_ROW_TOP, CobblePalsUiTheme.TEXT_MUTED, false)
        } else {
            visibleSources.forEachIndexed { index, row ->
                drawSourceRow(context, row, SOURCE_LEFT, SOURCE_ROW_TOP + index * CREW_ROW_HEIGHT, SOURCE_WIDTH, row.snapshot.pokemonId == selectedSourcePokemonId)
            }
        }
        drawNativeRosterDetail(context, selectedNative)
        drawSourceDetail(context, selectedSource, selectedNative)
    }

    private fun drawNativeRosterDetail(context: DrawContext, selected: NativeRosterRow?) {
        CobblePalsUiTheme.drawPlainPanel(context, LIST_LEFT, SOURCE_DETAIL_TOP, LIST_WIDTH, 42, selected?.accentColor ?: CobblePalsUiTheme.TEXT_FAINT)
        if (selected == null) {
            context.drawText(textRenderer, Text.literal("Select crew"), LIST_LEFT + 8, SOURCE_DETAIL_TOP + 8, CobblePalsUiTheme.TEXT_PRIMARY, false)
            context.drawText(textRenderer, Text.literal("Native roster"), LIST_LEFT + 8, SOURCE_DETAIL_TOP + 21, CobblePalsUiTheme.TEXT_FAINT, false)
            return
        }
        val source = selected.source
        context.drawText(textRenderer, Text.literal(fit(source.displayName, LIST_WIDTH - 14)), LIST_LEFT + 8, SOURCE_DETAIL_TOP + 7, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(fit("Lv.${source.level} ${friendlyId(source.species)}", LIST_WIDTH - 14)), LIST_LEFT + 8, SOURCE_DETAIL_TOP + 19, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(fit("${source.statusLabel()} • ${source.sourceLabel()}", LIST_WIDTH - 58)), LIST_LEFT + 8, SOURCE_DETAIL_TOP + 31, selected.accentColor, false)
        nativeRosterDetailButtons(selected).forEach { chip ->
            drawFixedChip(context, chip, false)
        }
    }

    private fun drawPolicyView(context: DrawContext, localMouseX: Int, localMouseY: Int, state: HomeState) {
        if (state.roles.isEmpty()) {
            context.drawText(textRenderer, Text.literal("Install role cards to tune policy."), 16, 206, CobblePalsUiTheme.TEXT_MUTED, false)
            return
        }
        val selected = selectedPolicyRole(state)
        context.drawText(textRenderer, Text.literal("Role Cards"), POLICY_LIST_LEFT, 200, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal("Details"), POLICY_DETAIL_LEFT, 200, CobblePalsUiTheme.TEXT_MUTED, false)
        state.roles.take(MAX_ROLE_ROWS).forEachIndexed { index, role ->
            drawPolicyRoleRow(context, role, POLICY_LIST_LEFT, POLICY_LIST_TOP + index * POLICY_ROW_HEIGHT, POLICY_LIST_WIDTH, index == selectedPolicyRowIndex)
        }
        drawPolicyDetail(context, localMouseX, localMouseY, selected)
    }

    private fun drawLogisticsView(context: DrawContext) {
        context.drawText(textRenderer, Text.literal("Command buffer"), RouterScreenHandler.STORAGE_START_X, RouterScreenHandler.STORAGE_START_Y - 12, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, CobblePalsUiTheme.TEXT_MUTED, false)
    }

    private fun drawSourceDetail(context: DrawContext, selected: SourceRowSummary?, selectedNative: NativeRosterRow?) {
        CobblePalsUiTheme.drawPlainPanel(context, SOURCE_LEFT, SOURCE_DETAIL_TOP, SOURCE_WIDTH, 42, selected?.accentColor ?: selectedNative?.accentColor ?: CobblePalsUiTheme.TEXT_FAINT)
        if (selected == null && selectedNative != null) {
            nativeActionButtons(selectedNative).forEach { chip ->
                drawFixedChip(context, chip, false)
            }
            return
        }
        if (selected == null) {
            context.drawText(textRenderer, Text.literal("Select source"), SOURCE_LEFT + 8, SOURCE_DETAIL_TOP + 8, CobblePalsUiTheme.TEXT_PRIMARY, false)
            context.drawText(textRenderer, Text.literal("Party or PC"), SOURCE_LEFT + 8, SOURCE_DETAIL_TOP + 21, CobblePalsUiTheme.TEXT_FAINT, false)
            return
        }
        val source = selected.snapshot
        context.drawText(textRenderer, Text.literal(fit(source.displayName, SOURCE_WIDTH - 14)), SOURCE_LEFT + 8, SOURCE_DETAIL_TOP + 7, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(fit("Lv.${source.level} ${friendlyId(source.species)}", SOURCE_WIDTH - 14)), SOURCE_LEFT + 8, SOURCE_DETAIL_TOP + 19, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(fit(source.statusLabel(), 56)), SOURCE_LEFT + 8, SOURCE_DETAIL_TOP + 31, selected.accentColor, false)
        sourceDetailButtons(selected).forEach { chip ->
            drawFixedChip(context, chip, false)
        }
    }

    private fun drawPolicyDetail(context: DrawContext, localMouseX: Int, localMouseY: Int, selected: RoleSummary?) {
        CobblePalsUiTheme.drawPlainPanel(context, POLICY_DETAIL_LEFT, POLICY_DETAIL_TOP, POLICY_DETAIL_WIDTH, 74, selected?.pressureColor ?: CobblePalsUiTheme.TEXT_FAINT)
        if (selected == null) {
            context.drawText(textRenderer, Text.literal("Select"), POLICY_DETAIL_LEFT + 8, POLICY_DETAIL_TOP + 8, CobblePalsUiTheme.TEXT_PRIMARY, false)
            context.drawText(textRenderer, Text.literal("a role"), POLICY_DETAIL_LEFT + 8, POLICY_DETAIL_TOP + 20, CobblePalsUiTheme.TEXT_MUTED, false)
            return
        }
        context.drawText(textRenderer, Text.literal(fit(selected.label, POLICY_DETAIL_WIDTH - 14)), POLICY_DETAIL_LEFT + 8, POLICY_DETAIL_TOP + 8, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(fit(selected.pressureLabel, POLICY_DETAIL_WIDTH - 14)), POLICY_DETAIL_LEFT + 8, POLICY_DETAIL_TOP + 20, selected.pressureColor, false)
        context.drawText(textRenderer, Text.literal(fit(selected.targetLabel, POLICY_DETAIL_WIDTH - 14)), POLICY_DETAIL_LEFT + 8, POLICY_DETAIL_TOP + 38, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(fit(selected.filterSummary, POLICY_DETAIL_WIDTH - 14)), POLICY_DETAIL_LEFT + 8, POLICY_DETAIL_TOP + 50, CobblePalsUiTheme.TEXT_FAINT, false)
        selected.firstIssue?.let { issue ->
            context.drawText(textRenderer, Text.literal(fit(issue.detail, POLICY_DETAIL_WIDTH - 14)), POLICY_DETAIL_LEFT + 8, POLICY_DETAIL_TOP + 62, selected.pressureColor, false)
        }
        policyDetailButtons(selected).forEach { chip ->
            val hovered = contains(localMouseX, localMouseY, chip.left, chip.top, chip.width, CHIP_HEIGHT)
            drawFixedChip(context, chip, hovered)
        }
    }

    private fun drawPolicyRoleRow(context: DrawContext, role: RoleSummary, left: Int, top: Int, width: Int, active: Boolean) {
        val body = if (active) 0xFF202B34.toInt() else 0xFF10171D.toInt()
        context.fill(left, top, left + width, top + POLICY_ROW_HEIGHT - 2, body)
        context.fill(left, top, left + 3, top + POLICY_ROW_HEIGHT - 2, role.pressureColor)
        context.drawText(textRenderer, Text.literal(role.slotLabel), left + 7, top + 3, CobblePalsUiTheme.TEXT_FAINT, false)
        context.drawText(textRenderer, Text.literal(fit(role.label, 72)), left + 25, top + 3, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(fit(role.targetLabel, 64)), left + 7, top + 13, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(fit(role.pressureLabel, 54)), left + width - 58, top + 13, role.pressureColor, false)
    }

    private fun drawRoleRow(context: DrawContext, role: RoleSummary, left: Int, top: Int, width: Int, active: Boolean) {
        val body = if (active) 0xFF202B34.toInt() else 0xFF10171D.toInt()
        context.fill(left, top, left + width, top + ROLE_ROW_HEIGHT - 2, body)
        context.fill(left, top, left + 3, top + ROLE_ROW_HEIGHT - 2, role.pressureColor)
        val compact = width < 180
        context.drawText(textRenderer, Text.literal(role.slotLabel), left + 7, top + 4, CobblePalsUiTheme.TEXT_FAINT, false)
        context.drawText(textRenderer, Text.literal(fit(role.label, if (compact) 50 else 72)), left + 25, top + 4, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(fit(role.pressureLabel, if (compact) 42 else 58)), left + if (compact) 82 else 104, top + 4, role.pressureColor, false)
        val countText = Text.literal("${role.staffedCount}/${role.installedCount}")
        context.drawText(textRenderer, countText, left + width - textRenderer.getWidth(countText) - 5, top + 4, CobblePalsUiTheme.TEXT_MUTED, false)
    }

    private fun drawSourceRow(context: DrawContext, row: SourceRowSummary, left: Int, top: Int, width: Int, active: Boolean) {
        val source = row.snapshot
        val body = if (active) 0xFF202B34.toInt() else 0xFF10171D.toInt()
        context.fill(left, top, left + width, top + CREW_ROW_HEIGHT - 2, body)
        context.fill(left, top, left + 3, top + CREW_ROW_HEIGHT - 2, row.accentColor)
        context.drawText(textRenderer, Text.literal(fit(source.displayName, 68)), left + 7, top + 3, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("Lv.${source.level}"), left + width - 28, top + 3, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(fit(source.sourceLabel(), 62)), left + 7, top + 12, CobblePalsUiTheme.TEXT_FAINT, false)
        context.drawText(textRenderer, Text.literal(fit(source.statusLabel(), 48)), left + width - 52, top + 12, row.accentColor, false)
    }

    private fun drawNativeRosterRow(context: DrawContext, row: NativeRosterRow, left: Int, top: Int, width: Int, active: Boolean) {
        val source = row.source
        val body = if (active) 0xFF202B34.toInt() else 0xFF10171D.toInt()
        context.fill(left, top, left + width, top + CREW_ROW_HEIGHT - 2, body)
        context.fill(left, top, left + 3, top + CREW_ROW_HEIGHT - 2, row.accentColor)
        context.drawText(textRenderer, Text.literal(fit(source.displayName, 70)), left + 7, top + 3, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(fit(source.sourceType, 42)), left + width - 46, top + 3, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(fit("Lv.${source.level} ${friendlyId(source.species)}", 76)), left + 7, top + 12, CobblePalsUiTheme.TEXT_FAINT, false)
        context.drawText(textRenderer, Text.literal(fit(source.statusLabel(), 38)), left + width - 42, top + 12, row.accentColor, false)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val now = System.currentTimeMillis()
        if (now >= nextCrewRefreshAtMs) {
            requestCrewRefresh()
            nextCrewRefreshAtMs = now + 1000L
        }
        if (activeView == CommandPostView.Crew && now >= nextCrewSourceRefreshAtMs) {
            requestCrewSourceRefresh()
            nextCrewSourceRefreshAtMs = now + 4000L
        }
        renderBackground(context, mouseX, mouseY, delta)
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)

        val hoveredTooltip = hoveredTooltip(mouseX - x, mouseY - y, buildHomeState())
        if (hoveredTooltip?.id != hoveredTooltipId) {
            hoveredTooltipId = hoveredTooltip?.id
            hoveredTooltipSinceMs = if (hoveredTooltip == null) 0L else now
        }
        if (hoveredTooltip != null && now - hoveredTooltipSinceMs >= CobblePalsUiTheme.TOOLTIP_DELAY_MS) {
            context.drawTooltip(textRenderer, hoveredTooltip.lines, mouseX, mouseY)
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_P) {
            if (activeView == CommandPostView.Policy) {
                selectedPolicyRowIndex?.let { rowIndex ->
                    client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.ACTION_OPEN_POLICY_ROW_BASE + rowIndex)
                    return true
                }
            }
            val moduleIndex = hoveredModuleIndex() ?: return super.keyPressed(keyCode, scanCode, modifiers)
            val slot = handler.slots.getOrNull(moduleIndex) ?: return super.keyPressed(keyCode, scanCode, modifiers)
            if (slot.stack.item is TagItem) {
                client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.ACTION_EDIT_MODULE_BASE + moduleIndex)
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button)
        val localMouseX = (mouseX - x).toInt()
        val localMouseY = (mouseY - y).toInt()
        val state = buildHomeState()

        tabButtons(state).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { tab ->
            switchView(tab.view)
            return true
        }

        when (activeView) {
            CommandPostView.Home -> {
                state.roles.take(2).indices.firstOrNull { index -> contains(localMouseX, localMouseY, 16, 268 + index * ROLE_ROW_HEIGHT, 266, ROLE_ROW_HEIGHT - 2) }?.let { rowIndex ->
                    selectedPolicyRowIndex = rowIndex
                    switchView(CommandPostView.Policy)
                    return true
                }
            }

            CommandPostView.Crew -> {
                val selectedSource = selectedSourceRow()
                val selectedNative = nativeRosterRows().firstOrNull { it.source.pokemonId == selectedSourcePokemonId }
                nativeActionButtons(selectedNative).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { chip ->
                    val pokemonId = selectedNative?.source?.pokemonId ?: return true
                    when (chip.id) {
                        "native-mode-action" -> CobblePalsNetworking.sendCycleCrewMode(handler.routerPos, pokemonId)
                        "native-fallback-action" -> CobblePalsNetworking.sendToggleCrewFallback(handler.routerPos, pokemonId)
                        "native-drop-action" -> CobblePalsNetworking.sendRemoveCrewPokemon(handler.routerPos, pokemonId)
                    }
                    requestCrewRefresh()
                    requestCrewSourceRefresh()
                    return true
                }
                nativeRosterDetailButtons(selectedNative).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { chip ->
                    if (chip.id == "native-return-action" && chip.active) {
                        selectedNative?.source?.pokemonId?.let { pokemonId -> CobblePalsNetworking.sendReturnCrewPokemon(handler.routerPos, pokemonId) }
                        requestCrewSourceRefresh()
                        requestCrewRefresh()
                    }
                    return true
                }
                sourceDetailButtons(selectedSource).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { chip ->
                    if (chip.id == "source-crew-action" && selectedSource != null && chip.active) {
                        val pokemonId = selectedSource.snapshot.pokemonId
                        if (selectedSource.snapshot.isCrewMember) {
                            CobblePalsNetworking.sendRemoveCrewPokemon(handler.routerPos, pokemonId)
                        } else {
                            CobblePalsNetworking.sendAssignCrewPokemon(handler.routerPos, pokemonId)
                        }
                        requestCrewSourceRefresh()
                    }
                    return true
                }

                crewFilterButtons().firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { chip ->
                    when (chip.id) {
                        "crew-sort" -> crewSortMode = CrewSortMode.entries[(crewSortMode.ordinal + 1) % CrewSortMode.entries.size]
                        "crew-family" -> {
                            val options = listOf<TagRoleFamily?>(null) + TagRoleFamily.entries
                            val nextIndex = (options.indexOf(crewFamilyFilter) + 1).coerceAtLeast(0) % options.size
                            crewFamilyFilter = options[nextIndex]
                        }
                        "crew-focus" -> crewFocusFilter = CrewFocusFilter.entries[(crewFocusFilter.ordinal + 1) % CrewFocusFilter.entries.size]
                        "crew-mode" -> crewModeFilter = CrewModeFilter.entries[(crewModeFilter.ordinal + 1) % CrewModeFilter.entries.size]
                    }
                    crewPageIndex = 0
                    selectedSourcePokemonId = null
                    return true
                }

                sourceTypeButtons().firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { chip ->
                    crewSourceType = if (chip.id == "source-party") CrewSourceType.PARTY else CrewSourceType.PC
                    crewSourcePageIndex = 0
                    selectedSourcePokemonId = null
                    requestCrewSourceRefresh()
                    return true
                }

                sourcePageButtons().firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { pageButton ->
                    val pageCount = sourcePageCount(sourceRows())
                    when (pageButton.id) {
                        "source-prev" -> crewSourcePageIndex = (crewSourcePageIndex - 1).coerceAtLeast(0)
                        "source-next" -> crewSourcePageIndex = (crewSourcePageIndex + 1).coerceAtMost(pageCount - 1)
                    }
                    selectedSourcePokemonId = null
                    return true
                }

                crewPageButtons().firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { pageButton ->
                    val pageCount = crewPageCount(filteredNativeRosterRows())
                    when (pageButton.id) {
                        "crew-prev" -> crewPageIndex = (crewPageIndex - 1).coerceAtLeast(0)
                        "crew-next" -> crewPageIndex = (crewPageIndex + 1).coerceAtMost(pageCount - 1)
                    }
                    selectedSourcePokemonId = null
                    return true
                }

                val nativeRows = visibleNativeRosterRows()
                nativeRows.indices.firstOrNull { index -> contains(localMouseX, localMouseY, LIST_LEFT, CREW_ROW_TOP + index * CREW_ROW_HEIGHT, LIST_WIDTH, CREW_ROW_HEIGHT - 2) }?.let { rowIndex ->
                    selectedSourcePokemonId = nativeRows[rowIndex].source.pokemonId
                    return true
                }

                val sourceRows = visibleSourceRows()
                sourceRows.indices.firstOrNull { index -> contains(localMouseX, localMouseY, SOURCE_LEFT, SOURCE_ROW_TOP + index * CREW_ROW_HEIGHT, SOURCE_WIDTH, CREW_ROW_HEIGHT - 2) }?.let { rowIndex ->
                    selectedSourcePokemonId = sourceRows[rowIndex].snapshot.pokemonId
                    return true
                }
            }

            CommandPostView.Policy -> {
                val selected = selectedPolicyRole(state)
                policyDetailButtons(selected).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { chip ->
                    val rowIndex = selectedPolicyRowIndex ?: return true
                    when (chip.id) {
                        "policy-mode" -> client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.policyQuickActionId(rowIndex, RouterScreenHandler.POLICY_ACTION_TOGGLE_WHITELIST))
                        "policy-nbt" -> client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.policyQuickActionId(rowIndex, RouterScreenHandler.POLICY_ACTION_TOGGLE_NBT))
                        "policy-match" -> client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.policyQuickActionId(rowIndex, RouterScreenHandler.POLICY_ACTION_CYCLE_MATCH))
                        "policy-signal" -> client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.policyQuickActionId(rowIndex, RouterScreenHandler.POLICY_ACTION_CYCLE_SIGNAL))
                        "policy-target" -> client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.policyQuickActionId(rowIndex, RouterScreenHandler.POLICY_ACTION_CYCLE_TARGET))
                        "policy-run" -> client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.policyQuickActionId(rowIndex, RouterScreenHandler.POLICY_ACTION_TOGGLE_RUN))
                        "policy-reg" -> client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.policyQuickActionId(rowIndex, RouterScreenHandler.POLICY_ACTION_CYCLE_REGULATOR))
                        "policy-deep" -> client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.ACTION_OPEN_POLICY_ROW_BASE + rowIndex)
                    }
                    return true
                }

                state.roles.take(MAX_ROLE_ROWS).indices.firstOrNull { index -> contains(localMouseX, localMouseY, POLICY_LIST_LEFT, POLICY_LIST_TOP + index * POLICY_ROW_HEIGHT, POLICY_LIST_WIDTH, POLICY_ROW_HEIGHT - 2) }?.let { rowIndex ->
                    selectedPolicyRowIndex = rowIndex
                    return true
                }
            }

            CommandPostView.Logistics -> Unit
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun hoveredTooltip(localMouseX: Int, localMouseY: Int, state: HomeState): HoverTooltip? {
        tabButtons(state).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { tab ->
            return HoverTooltip("tab-${tab.view.name}", tab.tooltip)
        }
        when (activeView) {
            CommandPostView.Home -> {
                state.roles.take(2).indices.firstOrNull { index -> contains(localMouseX, localMouseY, 16, 268 + index * ROLE_ROW_HEIGHT, 266, ROLE_ROW_HEIGHT - 2) }?.let { index ->
                    return HoverTooltip("home-role-${state.roles[index].id}", state.roles[index].tooltipLines)
                }
            }
            CommandPostView.Crew -> {
                val selectedSource = selectedSourceRow()
                val selectedNative = nativeRosterRows().firstOrNull { it.source.pokemonId == selectedSourcePokemonId }
                nativeActionButtons(selectedNative).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { chip -> return HoverTooltip(chip.id, chip.tooltip) }
                nativeRosterDetailButtons(selectedNative).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { chip -> return HoverTooltip(chip.id, chip.tooltip) }
                sourceDetailButtons(selectedSource).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { chip -> return HoverTooltip(chip.id, chip.tooltip) }
                crewFilterButtons().firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { chip -> return HoverTooltip(chip.id, chip.tooltip) }
                crewPageButtons().firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { button -> return HoverTooltip(button.id, button.tooltip) }
                sourceTypeButtons().firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { chip -> return HoverTooltip(chip.id, chip.tooltip) }
                sourcePageButtons().firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { button -> return HoverTooltip(button.id, button.tooltip) }
                val nativeRows = visibleNativeRosterRows()
                nativeRows.indices.firstOrNull { index -> contains(localMouseX, localMouseY, LIST_LEFT, CREW_ROW_TOP + index * CREW_ROW_HEIGHT, LIST_WIDTH, CREW_ROW_HEIGHT - 2) }?.let { index ->
                    val row = nativeRows[index]
                    return HoverTooltip("native-crew-${row.source.pokemonId}", row.tooltipLines)
                }
                val visibleSources = visibleSourceRows()
                visibleSources.indices.firstOrNull { index -> contains(localMouseX, localMouseY, SOURCE_LEFT, SOURCE_ROW_TOP + index * CREW_ROW_HEIGHT, SOURCE_WIDTH, CREW_ROW_HEIGHT - 2) }?.let { index ->
                    val source = visibleSources[index]
                    return HoverTooltip("source-${source.snapshot.pokemonId}", source.tooltipLines)
                }
            }
            CommandPostView.Policy -> {
                val selected = selectedPolicyRole(state)
                policyDetailButtons(selected).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, CHIP_HEIGHT) }?.let { chip -> return HoverTooltip(chip.id, chip.tooltip) }
                state.roles.take(MAX_ROLE_ROWS).indices.firstOrNull { index -> contains(localMouseX, localMouseY, POLICY_LIST_LEFT, POLICY_LIST_TOP + index * POLICY_ROW_HEIGHT, POLICY_LIST_WIDTH, POLICY_ROW_HEIGHT - 2) }?.let { index ->
                    return HoverTooltip("policy-${state.roles[index].id}", state.roles[index].tooltipLines)
                }
            }
            CommandPostView.Logistics -> Unit
        }
        return null
    }

    private fun hoveredModuleIndex(): Int? {
        val client = client ?: return null
        val localMouseX = client.mouse.x.toInt() - x
        val localMouseY = client.mouse.y.toInt() - y
        for (row in 0 until RouterScreenHandler.MODULE_ROWS) {
            for (col in 0 until RouterScreenHandler.MODULE_COLUMNS) {
                val left = RouterScreenHandler.MODULE_START_X + col * 18
                val top = RouterScreenHandler.MODULE_START_Y + row * 18
                if (contains(localMouseX, localMouseY, left, top, 16, 16)) {
                    return row * RouterScreenHandler.MODULE_COLUMNS + col
                }
            }
        }
        return null
    }

    private fun isConfigured(stack: net.minecraft.item.ItemStack, tagItem: TagItem): Boolean {
        return !tagItem.tagType.supportsBinding || TagItem.getBoundPos(stack) != null || TagItem.getBoundArea(stack) != null
    }

    private fun familyFor(tagType: TagType): TagRoleFamily = TagTypePresentation.familyOf(tagType)

    private fun familyAccent(family: TagRoleFamily): Int {
        return when (family) {
            TagRoleFamily.Gathering -> CobblePalsUiTheme.ACCENT_WORK
            TagRoleFamily.Logistics -> CobblePalsUiTheme.ACCENT_BUFFER
            TagRoleFamily.Defense -> 0xFFFF8A6B.toInt()
            TagRoleFamily.Interaction -> CobblePalsUiTheme.ACCENT_POLICY
            TagRoleFamily.Care -> CobblePalsUiTheme.ACCENT_PURPLE
        }
    }

    private fun summarizeBinding(tagType: TagType, spec: TagSpec): String {
        return when {
            spec.boundArea != null -> "Area set"
            spec.boundPos != null -> TagTypePresentation.bindingLabel(tagType)
            tagType.supportsBinding -> "Needs target"
            else -> "No target"
        }
    }

    private fun summarizeFilter(tagType: TagType, spec: TagSpec): String {
        if (!tagType.usesFilter) return "Ignores filters"
        val filter = spec.filter
        if (filter.isEmpty()) return if (filter.whitelist) "Allow none" else "All items"
        val parts = mutableListOf<String>()
        if (filter.items.isNotEmpty()) parts += "${filter.items.size} items"
        if (filter.matchTags.isNotEmpty()) parts += "${filter.matchTags.size} tags"
        if (filter.matchModIds.isNotEmpty()) parts += "${filter.matchModIds.size} mods"
        return "${if (filter.whitelist) "Allow" else "Block"} ${parts.joinToString(", ")}"
    }

    private fun summarizeMatch(spec: TagSpec): String {
        val filter = spec.filter
        return "${if (filter.matchNbt) "Exact" else "Loose"} • ${if (filter.matchMode == FilterMatchMode.ALL) "All" else "Any"}"
    }

    private fun matchModeHelp(mode: FilterMatchMode): String {
        return when (mode) {
            FilterMatchMode.ANY -> "Any enabled filter group may match."
            FilterMatchMode.ALL -> "Every enabled filter group must match the same item."
        }
    }

    private fun friendlyId(value: String): String {
        return value.split('_').joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private fun pluralize(count: Int): String = if (count == 1) "" else "s"

    private fun fit(value: String, maxWidth: Int): String {
        if (textRenderer.getWidth(value) <= maxWidth) return value
        var clipped = value
        while (clipped.isNotEmpty() && textRenderer.getWidth("$clipped...") > maxWidth) {
            clipped = clipped.dropLast(1)
        }
        return if (clipped.isEmpty()) "..." else "$clipped..."
    }
}
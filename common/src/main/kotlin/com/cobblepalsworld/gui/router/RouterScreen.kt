package com.cobblepalsworld.gui.router

import com.cobblepalsworld.gui.CobblePalsUiTheme
import com.cobblepalsworld.gui.UiGlyph
import com.cobblepalsworld.gui.UiIconButtons
import com.cobblepalsworld.gui.pasture.PalSnapshot
import com.cobblepalsworld.gui.pasture.PastureSnapshot
import com.cobblepalsworld.gui.pasture.PastureSnapshotCache
import com.cobblepalsworld.networking.CobblePalsNetworking
import com.cobblepalsworld.router.RouterBlockEntity
import com.cobblepalsworld.tag.TagItem
import com.cobblepalsworld.tag.TagRoleFamily
import com.cobblepalsworld.tag.TagSpec
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.TagTypePresentation
import com.cobblepalsworld.tag.filter.FilterMatchMode
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

    private val screenTitle = Text.literal("COMMAND POST")

    private enum class CommandPostView {
        Home,
        Jobs,
        Crew,
        Policy,
        Logistics
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
        Restricted("Restricted"),
        Reserved("Reserved")
    }

    private data class HoverTooltip(val id: String, val lines: List<Text>)

    private data class SectionButton(
        val id: String,
        val view: CommandPostView,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val glyph: UiGlyph,
        val label: String,
        val accentColor: Int,
        val tooltip: List<Text>
    )

    private data class AccessCardButton(
        val id: String,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val title: String,
        val summary: String,
        val detail: String,
        val accentColor: Int,
        val targetView: CommandPostView,
        val tooltip: List<Text>
    )

    private data class TextChipButton(
        val id: String,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val label: String,
        val value: String,
        val accentColor: Int,
        val tooltip: List<Text>,
        val active: Boolean = false
    )

    private data class IconButton(
        val id: String,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val glyph: UiGlyph,
        val accentColor: Int,
        val tooltip: List<Text>,
        val active: Boolean = false
    )

    private data class InstalledCommandCard(
        val moduleIndex: Int,
        val tagType: TagType,
        val configured: Boolean,
        val spec: TagSpec
    )

    private data class CommandPostAlert(
        val label: String,
        val detail: String,
        val accentColor: Int,
        val bodyColor: Int,
        val textColor: Int
    )

    private data class JobFamilySummary(
        val family: TagRoleFamily,
        val count: Int
    )

    private data class JobRoleSummary(
        val id: String,
        val tagType: TagType,
        val label: String,
        val installedCount: Int,
        val staffedCount: Int,
        val activeCount: Int,
        val familyLabel: String,
        val visibleCause: String,
        val causeColor: Int,
        val assignedNames: List<String>,
        val tooltipLines: List<Text>
    )

    private data class PolicyRoleSummary(
        val rowIndex: Int,
        val id: String,
        val tagType: TagType,
        val label: String,
        val configuredCount: Int,
        val totalCount: Int,
        val targetLabel: String,
        val filterSummary: String,
        val matchSummary: String,
        val redstoneLabel: String,
        val targetStrategyLabel: String,
        val runLabel: String,
        val regulatorAmount: Int,
        val extraTargetCount: Int,
        val visibleCause: String,
        val causeColor: Int,
        val tooltipLines: List<Text>
    )

    private data class CrewRowSummary(
        val snapshotIndex: Int,
        val id: String,
        val name: String,
        val species: String,
        val level: Int,
        val roleLabel: String,
        val family: TagRoleFamily?,
        val tagTypeId: String?,
        val assignmentLabel: String,
        val statusLabel: String,
        val statusColor: Int,
        val cargoSummary: String,
        val pal: PalSnapshot,
        val tooltipLines: List<Text>
    )

    private data class CommandPostHomeState(
        val jobCardCount: Int,
        val idleCount: Int,
        val queuedCount: Int,
        val augmentCount: Int,
        val setupIssueCount: Int,
        val subtitle: String,
        val jobSummary: String,
        val crewSummary: String,
        val augmentSummary: String,
        val alert: CommandPostAlert,
        val families: List<JobFamilySummary>,
        val roles: List<JobRoleSummary>,
        val policyRoles: List<PolicyRoleSummary>
    )

    companion object {
        private const val HEADER_HEIGHT = 32
        private const val JOB_PANEL_LEFT = 10
        private const val JOB_PANEL_TOP = 44
        private const val JOB_PANEL_WIDTH = 96
        private const val JOB_PANEL_HEIGHT = 92
        private const val STATUS_PANEL_LEFT = 114
        private const val STATUS_PANEL_TOP = 44
        private const val STATUS_PANEL_WIDTH = 124
        private const val STATUS_PANEL_HEIGHT = 92
        private const val BOOST_PANEL_LEFT = 10
        private const val BOOST_PANEL_TOP = 140
        private const val BOOST_PANEL_WIDTH = 228
        private const val BOOST_PANEL_HEIGHT = 34
        private const val OPERATIONS_PANEL_LEFT = 10
        private const val OPERATIONS_PANEL_TOP = 178
        private const val OPERATIONS_PANEL_WIDTH = 228
        private const val OPERATIONS_PANEL_HEIGHT = 60
        private const val DETAIL_PANEL_LEFT = 10
        private const val DETAIL_PANEL_TOP = 242
        private const val DETAIL_PANEL_WIDTH = 228
        private const val DETAIL_PANEL_HEIGHT = 162
        private const val STORAGE_PANEL_LEFT = 10
        private const val STORAGE_PANEL_TOP = 242
        private const val STORAGE_PANEL_WIDTH = 228
        private const val STORAGE_PANEL_HEIGHT = 74
        private const val PLAYER_PANEL_LEFT = 10
        private const val PLAYER_PANEL_TOP = 320
        private const val PLAYER_PANEL_WIDTH = 228
        private const val PLAYER_PANEL_HEIGHT = 84

        private const val STATUS_CARD_WIDTH = 52
        private const val MAX_JOB_ROWS = 9
        private const val JOB_ROW_HEIGHT = 12
        private const val MAX_CREW_ROWS = 6
        private const val CREW_ROW_HEIGHT = 18
        private const val MAX_POLICY_ROWS = 9
        private const val POLICY_ROW_HEIGHT = 14
        private const val RAIL_TOP = 32
        private const val RAIL_HEIGHT = 11
        private const val HIDDEN_SLOT_X = -1000
        private const val HIDDEN_SLOT_Y = -1000
        private const val DRAWER_LEFT = 18
        private const val DRAWER_TOP = 256
        private const val DRAWER_WIDTH = 212
        private const val DRAWER_HEIGHT = 140
        private const val DETAIL_CONTENT_LEFT = 18
        private const val DETAIL_ROWS_TOP = 286
    }

    private var hoveredTooltipId: String? = null
    private var hoveredTooltipSinceMs: Long = 0L
    private var activeView = CommandPostView.Home
    private var nextCrewRefreshAtMs: Long = 0L
    private var crewSortMode = CrewSortMode.Need
    private var crewFamilyFilter: TagRoleFamily? = null
    private var crewFocusFilter = CrewFocusFilter.All
    private var crewModeFilter = CrewModeFilter.All
    private var crewPageIndex = 0
    private var selectedCrewSnapshotIndex: Int? = null
    private var selectedPolicyRowIndex: Int? = null
    private var selectedJobTagId: String? = null

    override fun init() {
        backgroundWidth = RouterScreenHandler.BACKGROUND_WIDTH
        backgroundHeight = RouterScreenHandler.BACKGROUND_HEIGHT
        super.init()
        titleX = 12
        titleY = 8
        playerInventoryTitleX = RouterScreenHandler.PLAYER_INV_X
        playerInventoryTitleY = 324
        applySlotLayout()
        requestCrewRefresh()
        nextCrewRefreshAtMs = System.currentTimeMillis() + 1000L
    }

    private fun fillLocal(context: DrawContext, left: Int, top: Int, right: Int, bottom: Int, color: Int) {
        context.fill(x + left, y + top, x + right, y + bottom, color)
    }

    private fun contains(localMouseX: Int, localMouseY: Int, left: Int, top: Int, width: Int, height: Int): Boolean {
        return localMouseX in left until (left + width) && localMouseY in top until (top + height)
    }

    private fun switchView(view: CommandPostView) {
        if (activeView == view) return
        activeView = view
        crewPageIndex = 0
        if (view == CommandPostView.Crew) {
            requestCrewRefresh()
        }
        applySlotLayout()
    }

    private fun requestCrewRefresh() {
        handler.linkedPasturePos?.let(CobblePalsNetworking::sendSnapshotRefresh)
    }

    private fun currentCrewSnapshot(): PastureSnapshot? {
        return PastureSnapshotCache.get(handler.linkedPasturePos)
    }

    private fun currentRegistries() = client?.world?.registryManager

    private fun buildInstalledCards(): List<InstalledCommandCard> {
        return handler.slots.take(RouterBlockEntity.MODULE_SLOT_COUNT).mapIndexedNotNull { moduleIndex, slot ->
            val stack = slot.stack
            val tagItem = stack.item as? TagItem ?: return@mapIndexedNotNull null
            val spec = currentRegistries()?.let { TagItem.getSpec(stack, it) } ?: TagSpec(type = tagItem.tagType)
            InstalledCommandCard(moduleIndex, tagItem.tagType, isConfigured(stack, tagItem), spec)
        }
    }

    private fun buildCrewRows(snapshot: PastureSnapshot?): List<CrewRowSummary> {
        if (snapshot == null) return emptyList()
        return snapshot.pals.mapIndexed { index, pal ->
            val tagType = pal.tagTypeId?.let(TagType::fromId)
            val roleLabel = tagType?.let(TagTypePresentation::roleLabel) ?: "No role"
            val cargoSummary = pal.carriedItemDescs.firstOrNull()?.let { "Cargo: $it" } ?: "Cargo: empty"
            CrewRowSummary(
                snapshotIndex = index,
                id = pal.pokemonId.toString(),
                name = pal.displayName,
                species = pal.species,
                level = pal.level,
                roleLabel = roleLabel,
                family = tagType?.let(TagTypePresentation::familyOf),
                tagTypeId = pal.tagTypeId,
                assignmentLabel = pal.assignmentLabel(),
                statusLabel = pal.statusLabel(),
                statusColor = crewStatusColor(pal),
                cargoSummary = cargoSummary,
                pal = pal,
                tooltipLines = buildList {
                    add(Text.literal(pal.displayName))
                    add(Text.literal("${roleLabel} • ${pal.statusLabel()}"))
                    add(Text.literal(pal.assignmentDetail()))
                    add(Text.literal(pal.statusDetailOrFallback()))
                    add(Text.literal(cargoSummary))
                    add(Text.literal(if (pal.isManagedByCommandPost) "Managed in Command Post" else "Local to pasture"))
                    add(Text.literal("Click to inspect this pal here."))
                }
            )
        }
    }

    private fun filteredCrewRows(snapshot: PastureSnapshot?): List<CrewRowSummary> {
        val rows = buildCrewRows(snapshot)
            .filter { row ->
                (crewFamilyFilter == null || row.family == crewFamilyFilter) &&
                    matchesFocusFilter(row) &&
                    matchesModeFilter(row)
            }

        return when (crewSortMode) {
            CrewSortMode.Need -> rows.sortedWith(compareBy<CrewRowSummary> { it.pal.sortRank() }.thenBy { it.name.lowercase(Locale.ROOT) })
            CrewSortMode.Name -> rows.sortedBy { it.name.lowercase(Locale.ROOT) }
            CrewSortMode.Role -> rows.sortedWith(compareBy<CrewRowSummary> { it.roleLabel.lowercase(Locale.ROOT) }.thenBy { it.name.lowercase(Locale.ROOT) })
        }
    }

    private fun matchesFocusFilter(row: CrewRowSummary): Boolean {
        return when (crewFocusFilter) {
            CrewFocusFilter.All -> true
            CrewFocusFilter.Active -> row.pal.isActive()
            CrewFocusFilter.Blocked -> row.pal.isBlocked()
            CrewFocusFilter.Fainted -> row.pal.isFainted
            CrewFocusFilter.Reserve -> row.assignmentLabel == "Reserved"
        }
    }

    private fun matchesModeFilter(row: CrewRowSummary): Boolean {
        return when (crewModeFilter) {
            CrewModeFilter.All -> true
            CrewModeFilter.General -> row.assignmentLabel == "General"
            CrewModeFilter.Preferred -> row.assignmentLabel == "Preferred"
            CrewModeFilter.Restricted -> row.assignmentLabel == "Restricted" || row.assignmentLabel == "Strict"
            CrewModeFilter.Reserved -> row.assignmentLabel == "Reserved"
        }
    }

    private fun visibleCrewRows(snapshot: PastureSnapshot?): List<CrewRowSummary> {
        val filtered = filteredCrewRows(snapshot)
        val pageCount = crewPageCount(filtered)
        crewPageIndex = crewPageIndex.coerceIn(0, pageCount - 1)
        return filtered.drop(crewPageIndex * MAX_CREW_ROWS).take(MAX_CREW_ROWS)
    }

    private fun crewPageCount(rows: List<CrewRowSummary>): Int {
        return maxOf(1, (rows.size + MAX_CREW_ROWS - 1) / MAX_CREW_ROWS)
    }

    private fun selectedCrewRow(snapshot: PastureSnapshot?): CrewRowSummary? {
        val filtered = filteredCrewRows(snapshot)
        val existing = filtered.firstOrNull { it.snapshotIndex == selectedCrewSnapshotIndex }
        if (existing != null) return existing
        val fallback = filtered.firstOrNull()
        selectedCrewSnapshotIndex = fallback?.snapshotIndex
        return fallback
    }

    private fun selectedJobRole(state: CommandPostHomeState): JobRoleSummary? {
        val existing = state.roles.firstOrNull { it.id == selectedJobTagId }
        if (existing != null) return existing
        val fallback = state.roles.firstOrNull()
        selectedJobTagId = fallback?.id
        return fallback
    }

    private fun selectedPolicyRole(state: CommandPostHomeState): PolicyRoleSummary? {
        val existing = state.policyRoles.firstOrNull { it.rowIndex == selectedPolicyRowIndex }
        if (existing != null) return existing
        val fallback = state.policyRoles.firstOrNull()
        selectedPolicyRowIndex = fallback?.rowIndex
        return fallback
    }

    private fun selectPolicyForTag(tagTypeId: String?, state: CommandPostHomeState) {
        selectedPolicyRowIndex = state.policyRoles.firstOrNull { it.id == tagTypeId }?.rowIndex
    }

    private fun crewStatusColor(pal: PalSnapshot): Int {
        return when {
            pal.isFainted -> 0xFFE07B67.toInt()
            pal.isBlocked() -> 0xFFE07B67.toInt()
            pal.isActive() -> 0xFF5ED6BE.toInt()
            pal.isReady() -> 0xFFA6D98C.toInt()
            pal.isWaiting() -> 0xFFF1B85E.toInt()
            pal.isStandby() -> 0xFF9D8BFF.toInt()
            pal.tagTypeId == null -> 0xFF8F98A5.toInt()
            else -> 0xFFD5DCE3.toInt()
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
                if (showLogistics) {
                    setSlotPosition(slotIndex, RouterScreenHandler.PLAYER_INV_X + col * 18, RouterScreenHandler.PLAYER_INV_Y + row * 18)
                } else {
                    hideSlot(slotIndex)
                }
            }
        }

        val hotbarStart = playerStart + 27
        for (col in 0..8) {
            val slotIndex = hotbarStart + col
            if (showLogistics) {
                setSlotPosition(slotIndex, RouterScreenHandler.PLAYER_INV_X + col * 18, RouterScreenHandler.PLAYER_INV_Y + 58)
            } else {
                hideSlot(slotIndex)
            }
        }
    }

    private fun buildHomeState(): CommandPostHomeState {
        val installedCards = buildInstalledCards()
        val snapshot = currentCrewSnapshot()

        val jobCardCount = installedCards.size
        val idleCount = (handler.assignedCount - handler.activeCount).coerceAtLeast(0)
        val queuedCount = (jobCardCount - handler.assignedCount).coerceAtLeast(0)
        val augmentSlots = handler.slots.subList(RouterScreenHandler.UPGRADE_SCREEN_SLOT_START, RouterScreenHandler.STORAGE_SCREEN_SLOT_START)
        val augmentCount = augmentSlots.count { it.hasStack() }
        val setupIssueCount = installedCards.count { !it.configured }

        val families = installedCards
            .groupingBy { familyFor(it.tagType) }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<TagRoleFamily, Int>> { it.value }.thenBy { it.key.label })
            .map { JobFamilySummary(it.key, it.value) }

        val roles = installedCards
            .groupBy { it.tagType }
            .entries
            .sortedWith(compareByDescending<Map.Entry<TagType, List<InstalledCommandCard>>> { it.value.size }.thenBy { TagTypePresentation.roleLabel(it.key) })
            .map { (tagType, cards) ->
                val staffedPals = snapshot?.pals?.filter { it.tagTypeId == tagType.id } ?: emptyList()
                val activeCount = staffedPals.count { it.isActive() }
                val blockedCount = staffedPals.count { it.isBlocked() }
                val missingTargets = cards.count { !it.configured }
                val visibleCause = when {
                    missingTargets > 0 -> "needs target"
                    !handler.linked -> "link pasture"
                    staffedPals.isEmpty() && handler.rosterCount > 0 -> "unstaffed"
                    blockedCount > 0 -> "blocked $blockedCount"
                    activeCount > 0 -> "working $activeCount"
                    staffedPals.isNotEmpty() -> "staffed ${staffedPals.size}"
                    else -> "standing by"
                }
                val causeColor = when {
                    missingTargets > 0 -> 0xFFF1B85E.toInt()
                    !handler.linked -> 0xFFE07B67.toInt()
                    staffedPals.isEmpty() && handler.rosterCount > 0 -> 0xFFFF8A6B.toInt()
                    blockedCount > 0 -> 0xFFE07B67.toInt()
                    activeCount > 0 -> 0xFF5ED6BE.toInt()
                    else -> 0xFFA6D98C.toInt()
                }
                JobRoleSummary(
                    id = tagType.id,
                    tagType = tagType,
                    label = TagTypePresentation.roleLabel(tagType),
                    installedCount = cards.size,
                    staffedCount = staffedPals.size,
                    activeCount = activeCount,
                    familyLabel = familyFor(tagType).label,
                    visibleCause = visibleCause,
                    causeColor = causeColor,
                    assignedNames = staffedPals.map { it.displayName },
                    tooltipLines = buildList {
                        add(Text.literal("${TagTypePresentation.roleLabel(tagType)} • ${cards.size} installed"))
                        add(Text.literal("Family: ${familyFor(tagType).label} • Staffed ${staffedPals.size}"))
                        add(Text.literal(if (missingTargets > 0) "$missingTargets installed card${pluralize(missingTargets)} still need setup." else "Installed cards are configured."))
                        if (staffedPals.isNotEmpty()) {
                            add(Text.literal("Crew: ${staffedPals.take(3).joinToString(", ") { it.displayName }}${if (staffedPals.size > 3) "..." else ""}"))
                        }
                        add(Text.literal("Pressure: $visibleCause"))
                    }
                )
            }

        val policyRoles = installedCards
            .groupBy { it.tagType }
            .entries
            .sortedWith(compareByDescending<Map.Entry<TagType, List<InstalledCommandCard>>> { it.value.size }.thenBy { TagTypePresentation.roleLabel(it.key) })
            .mapIndexed { rowIndex, entry ->
                val tagType = entry.key
                val cards = entry.value
                val primary = cards.first()
                val totalCount = cards.size
                val configuredCount = cards.count { it.configured }
                val missingCount = totalCount - configuredCount
                val filterSummary = summarizeFilter(tagType, primary.spec)
                val matchSummary = summarizeMatch(primary.spec)
                val targetLabel = summarizeBinding(tagType, primary.spec)
                val redstoneLabel = friendlyId(primary.spec.settings.redstoneMode.id)
                val targetStrategyLabel = if (tagType.supportsTargetList) friendlyId(primary.spec.settings.targetStrategy.id) else "Fixed"
                val runLabel = if (primary.spec.settings.terminateAfterSuccess) "One pass" else "Loop"
                val visibleCause = when {
                    missingCount > 0 -> "$missingCount need setup"
                    filterSummary == "No item rules yet." && tagType.usesFilter -> "filters empty"
                    else -> "quick tune"
                }
                val causeColor = when {
                    missingCount > 0 -> 0xFFF1B85E.toInt()
                    filterSummary == "No item rules yet." && tagType.usesFilter -> 0xFFE3B16B.toInt()
                    else -> 0xFF86B8FF.toInt()
                }
                PolicyRoleSummary(
                    rowIndex = rowIndex,
                    id = tagType.id,
                    tagType = tagType,
                    label = TagTypePresentation.roleLabel(tagType),
                    configuredCount = configuredCount,
                    totalCount = totalCount,
                    targetLabel = targetLabel,
                    filterSummary = filterSummary,
                    matchSummary = matchSummary,
                    redstoneLabel = redstoneLabel,
                    targetStrategyLabel = targetStrategyLabel,
                    runLabel = runLabel,
                    regulatorAmount = primary.spec.settings.regulatorAmount,
                    extraTargetCount = primary.spec.settings.extraTargets.size,
                    visibleCause = visibleCause,
                    causeColor = causeColor,
                    tooltipLines = buildList {
                        add(Text.literal("${TagTypePresentation.roleLabel(tagType)} • $configuredCount/$totalCount configured"))
                        add(Text.literal("Binding: $targetLabel"))
                        add(Text.literal("Filter: $filterSummary"))
                        add(Text.literal("Settings: $matchSummary • $redstoneLabel • $runLabel"))
                        add(Text.literal("Click to tune this role here."))
                    }
                )
            }

        val subtitle = when {
            !handler.linked -> "Link one pasture to activate this Command Post."
            jobCardCount <= 0 -> "Install tag cards to turn this post into a working crew hub."
            else -> "Manage one linked pasture through a single Command Post."
        }

        val jobSummary = when {
            jobCardCount <= 0 -> "Install up to 9 role cards in the jobs grid."
            setupIssueCount > 0 -> "$setupIssueCount installed role${pluralize(setupIssueCount, " still needs", "s still need")} target setup."
            else -> "Installed roles are configured and ready for crew coverage."
        }

        val crewSummary = when {
            jobCardCount <= 0 -> "No installed roles yet."
            else -> "${handler.assignedCount}/$jobCardCount staffed • ${handler.activeCount} active • $idleCount standing by"
        }

        val augmentSummary = if (augmentCount > 0) {
            "$augmentCount shared augment${pluralize(augmentCount)} currently boost every installed role."
        } else {
            "No shared augments installed yet."
        }

        return CommandPostHomeState(
            jobCardCount,
            idleCount,
            queuedCount,
            augmentCount,
            setupIssueCount,
            subtitle,
            jobSummary,
            crewSummary,
            augmentSummary,
            buildAlert(jobCardCount, setupIssueCount, queuedCount),
            families,
            roles,
            policyRoles
        )
    }

    private fun buildAlert(jobCardCount: Int, setupIssueCount: Int, queuedCount: Int): CommandPostAlert {
        return when {
            jobCardCount <= 0 -> CommandPostAlert("EMPTY", "Install tag cards to define this crew's jobs.", 0xFF6B7D8B.toInt(), 0xFF1D252C.toInt(), 0xFFE1E9EF.toInt())
            !handler.linked -> CommandPostAlert("UNLINKED", "Link a nearby pasture before installed roles can activate.", 0xFFE07B67.toInt(), 0xFF2E1B18.toInt(), 0xFFFFDDD8.toInt())
            setupIssueCount > 0 -> CommandPostAlert("SETUP", "$setupIssueCount installed role${pluralize(setupIssueCount)} still need a target or area.", 0xFFF1B85E.toInt(), 0xFF302419.toInt(), 0xFFFFE8C8.toInt())
            handler.rosterCount <= 0 -> CommandPostAlert("NO CREW", "The linked pasture has no active pals to assign.", 0xFFCC8F62.toInt(), 0xFF2B221B.toInt(), 0xFFF8DFC9.toInt())
            queuedCount > 0 -> CommandPostAlert("QUEUE", "$queuedCount installed role${pluralize(queuedCount)} are waiting for free pals.", 0xFF7FAEF7.toInt(), 0xFF182331.toInt(), 0xFFD8E8FF.toInt())
            handler.activeCount > 0 -> CommandPostAlert("ACTIVE", "The linked crew is already working from this Command Post.", 0xFF5ED6BE.toInt(), 0xFF162925.toInt(), 0xFFD7F7F0.toInt())
            else -> CommandPostAlert("READY", "Installed roles are configured and standing by for work.", 0xFFA6D98C.toInt(), 0xFF1D2A1A.toInt(), 0xFFE1F4D8.toInt())
        }
    }

    private fun isConfigured(stack: net.minecraft.item.ItemStack, tagItem: TagItem): Boolean {
        return !tagItem.tagType.supportsBinding || TagItem.getBoundPos(stack) != null || TagItem.getBoundArea(stack) != null
    }

    private fun familyFor(tagType: TagType): TagRoleFamily = TagTypePresentation.familyOf(tagType)

    private fun familyAccent(family: TagRoleFamily): Int {
        return when (family) {
            TagRoleFamily.Gathering -> 0xFF55D6BE.toInt()
            TagRoleFamily.Logistics -> 0xFF86B8FF.toInt()
            TagRoleFamily.Defense -> 0xFFFF8A6B.toInt()
            TagRoleFamily.Interaction -> 0xFFE7C36A.toInt()
            TagRoleFamily.Care -> 0xFFB494FF.toInt()
        }
    }

    private fun summarizeBinding(tagType: TagType, spec: TagSpec): String {
        return when {
            spec.boundArea != null -> "${TagTypePresentation.bindingLabel(tagType)} set"
            spec.boundPos != null -> TagTypePresentation.bindingLabel(tagType)
            tagType.supportsBinding -> "Needs binding"
            else -> "No binding"
        }
    }

    private fun summarizeFilter(tagType: TagType, spec: TagSpec): String {
        if (!tagType.usesFilter) return "This role ignores item filters."
        val filter = spec.filter
        if (filter.isEmpty()) return "No item rules yet."
        val parts = mutableListOf<String>()
        if (filter.items.isNotEmpty()) parts += "${filter.items.size} items"
        if (filter.matchTags.isNotEmpty()) parts += "${filter.matchTags.size} tags"
        if (filter.matchModIds.isNotEmpty()) parts += "${filter.matchModIds.size} mods"
        val mode = if (filter.whitelist) "Allow" else "Block"
        return "$mode ${parts.joinToString(", ")}"
    }

    private fun summarizeMatch(spec: TagSpec): String {
        val filter = spec.filter
        return "${if (filter.matchNbt) "Exact" else "Loose"} • ${if (filter.matchMode == FilterMatchMode.ALL) "All" else "Any"}"
    }

    private fun friendlyId(value: String): String {
        return value.split('_').joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private fun pluralize(count: Int, singularSuffix: String = "", pluralSuffix: String = "s"): String {
        return if (count == 1) singularSuffix else pluralSuffix
    }

    private fun truncate(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        return value.take(maxChars - 1) + "..."
    }

    private fun homeAccessCards(state: CommandPostHomeState): List<AccessCardButton> {
        return listOf(
            AccessCardButton("access-jobs", 18, 266, 102, 38, "Jobs", "${state.roles.size} role lines", "Read staffing and health from the work side.", 0xFF69D0C9.toInt(), CommandPostView.Jobs, listOf(Text.literal("Jobs view"), Text.literal("See role health, staffing, and which jobs need attention."), Text.literal(if (state.roles.isEmpty()) "No installed roles yet." else "${state.roles.size} installed role lines are ready."))),
            AccessCardButton("access-crew", 126, 266, 102, 38, "Crew", "${handler.rosterCount} linked pals", "Filter, page, and inspect the roster here.", 0xFF4FC6C5.toInt(), CommandPostView.Crew, listOf(Text.literal("Crew view"), Text.literal("Inspect one linked pasture roster without leaving the Command Post."), Text.literal(state.crewSummary))),
            AccessCardButton("access-policy", 18, 312, 102, 38, "Policy", "${state.policyRoles.size} role policies", "Tune role settings without losing shell context.", 0xFFF1B85E.toInt(), CommandPostView.Policy, listOf(Text.literal("Policy view"), Text.literal("Quick-tune role behavior in the Command Post shell."), Text.literal(if (state.setupIssueCount > 0) "${state.setupIssueCount} roles still need setup." else "Installed roles are configured."))),
            AccessCardButton("access-buffer", 126, 312, 102, 38, "Buffer", state.augmentSummary, "Keep item flow and storage pressure out of the landing page.", 0xFF86B8FF.toInt(), CommandPostView.Logistics, listOf(Text.literal("Buffer view"), Text.literal("Open the Command Buffer and player inventory view."), Text.literal(state.augmentSummary)))
        )
    }

    private fun sectionButtons(state: CommandPostHomeState): List<SectionButton> {
        return listOf(
            SectionButton("section-home", CommandPostView.Home, 10, RAIL_TOP, 40, RAIL_HEIGHT, UiGlyph.Home, "HOME", 0xFF69D0C9.toInt(), listOf(Text.literal("Home view"), Text.literal("Keep the landing surface calm and jobs-first."), Text.literal(state.crewSummary))),
            SectionButton("section-jobs", CommandPostView.Jobs, 54, RAIL_TOP, 40, RAIL_HEIGHT, UiGlyph.Action, "JOBS", 0xFF5ED6BE.toInt(), listOf(Text.literal("Jobs view"), Text.literal("Understand the crew from the work side."), Text.literal(if (state.roles.isEmpty()) "Install role cards to populate this board." else "${state.roles.size} role lines are active in this post."))),
            SectionButton("section-crew", CommandPostView.Crew, 98, RAIL_TOP, 40, RAIL_HEIGHT, UiGlyph.Cycle, "CREW", 0xFF4FC6C5.toInt(), listOf(Text.literal("Crew view"), Text.literal("Inspect, sort, and retune linked pals here."), Text.literal(if (currentCrewSnapshot() != null) "Live roster snapshot available." else "Waiting for linked pasture telemetry."))),
            SectionButton("section-policy", CommandPostView.Policy, 142, RAIL_TOP, 44, RAIL_HEIGHT, UiGlyph.Filter, "POLICY", 0xFFF1B85E.toInt(), listOf(Text.literal("Policy view"), Text.literal("Quick-tune role behavior without leaving the shell."), Text.literal(if (state.setupIssueCount > 0) "${state.setupIssueCount} installed roles still need setup." else "Installed roles are configured."))),
            SectionButton("section-buffer", CommandPostView.Logistics, 190, RAIL_TOP, 48, RAIL_HEIGHT, UiGlyph.Data, "BUFFER", 0xFF86B8FF.toInt(), listOf(Text.literal("Buffer view"), Text.literal("Keep storage and item flow separate from job health."), Text.literal(state.augmentSummary)))
        )
    }

    private fun chipWidth(label: String, value: String): Int {
        return textRenderer.getWidth(label) + textRenderer.getWidth(value) + 18
    }

    private fun crewFilterButtons(snapshot: PastureSnapshot?): List<TextChipButton> {
        val filtered = filteredCrewRows(snapshot)
        val pageCount = crewPageCount(filtered)
        val familyValue = crewFamilyFilter?.label ?: "All"
        return listOf(
            TextChipButton("crew-sort", DETAIL_CONTENT_LEFT, 258, chipWidth("Sort", crewSortMode.label), 11, "Sort", crewSortMode.label, 0xFF89B6DE.toInt(), listOf(Text.literal("Crew sort"), Text.literal("Current: ${crewSortMode.label}"), Text.literal("Cycle between operational need, name, and role order.")), active = true),
            TextChipButton("crew-family", 90, 258, chipWidth("Family", familyValue), 11, "Family", familyValue, 0xFF69D0C9.toInt(), listOf(Text.literal("Family filter"), Text.literal("Current: $familyValue"), Text.literal("Cycle through role families or show all pals.")), active = crewFamilyFilter != null),
            TextChipButton("crew-focus", DETAIL_CONTENT_LEFT, 271, chipWidth("State", crewFocusFilter.label), 11, "State", crewFocusFilter.label, 0xFFE3B16B.toInt(), listOf(Text.literal("State filter"), Text.literal("Current: ${crewFocusFilter.label}"), Text.literal("Focus on active, blocked, fainted, or reserve cases.")), active = crewFocusFilter != CrewFocusFilter.All),
            TextChipButton("crew-mode", 90, 271, chipWidth("Mode", crewModeFilter.label), 11, "Mode", crewModeFilter.label, 0xFFB494FF.toInt(), listOf(Text.literal("Assignment filter"), Text.literal("Current: ${crewModeFilter.label}"), Text.literal("Focus on general, preferred, restricted, or reserved workers.")), active = crewModeFilter != CrewModeFilter.All),
            TextChipButton("crew-page", 172, 271, 42, 11, "Page", "${crewPageIndex + 1}/$pageCount", 0xFF6B7D8B.toInt(), listOf(Text.literal("Crew page"), Text.literal("Page ${crewPageIndex + 1} of $pageCount"), Text.literal("Use the arrow buttons to move through filtered roster pages.")))
        )
    }

    private fun crewPageButtons(snapshot: PastureSnapshot?): List<IconButton> {
        val filtered = filteredCrewRows(snapshot)
        val pageCount = crewPageCount(filtered)
        return listOf(
            IconButton("crew-page-prev", 216, 271, 10, 10, UiGlyph.Prev, 0xFF89B6DE.toInt(), listOf(Text.literal("Previous crew page"), Text.literal("Move to the previous filtered roster page."), Text.literal("Page ${crewPageIndex + 1} of $pageCount")), active = crewPageIndex > 0),
            IconButton("crew-page-next", 228, 271, 10, 10, UiGlyph.Next, 0xFF89B6DE.toInt(), listOf(Text.literal("Next crew page"), Text.literal("Move to the next filtered roster page."), Text.literal("Page ${crewPageIndex + 1} of $pageCount")), active = crewPageIndex < pageCount - 1)
        )
    }

    private fun crewDrawerIconButtons(snapshot: PastureSnapshot?): List<IconButton> {
        val selected = selectedCrewRow(snapshot) ?: return emptyList()
        val filtered = filteredCrewRows(snapshot)
        val currentIndex = filtered.indexOfFirst { it.snapshotIndex == selected.snapshotIndex }
        val hasNeighbors = filtered.size > 1
        return listOf(
            IconButton("crew-drawer-prev", DRAWER_LEFT + DRAWER_WIDTH - 34, DRAWER_TOP + 4, 10, 10, UiGlyph.Prev, 0xFF8CD2FF.toInt(), listOf(Text.literal("Previous pal"), Text.literal("Move to the previous visible crew result."), Text.literal("Keeps the Command Post open while you step through workers.")), active = hasNeighbors && currentIndex > 0),
            IconButton("crew-drawer-next", DRAWER_LEFT + DRAWER_WIDTH - 22, DRAWER_TOP + 4, 10, 10, UiGlyph.Next, 0xFF8CD2FF.toInt(), listOf(Text.literal("Next pal"), Text.literal("Move to the next visible crew result."), Text.literal("Keeps the Command Post open while you step through workers.")), active = hasNeighbors && currentIndex >= 0 && currentIndex < filtered.lastIndex),
            IconButton("crew-drawer-close", DRAWER_LEFT + DRAWER_WIDTH - 10, DRAWER_TOP + 4, 10, 10, UiGlyph.Close, 0xFFFF8A6B.toInt(), listOf(Text.literal("Close pal detail"), Text.literal("Return to the filtered crew list without leaving the Command Post."), Text.literal("Background context stays intact.")), active = true)
        )
    }

    private fun crewDrawerButtons(snapshot: PastureSnapshot?): List<TextChipButton> {
        val selected = selectedCrewRow(snapshot) ?: return emptyList()
        val pal = selected.pal
        return listOf(
            TextChipButton("crew-drawer-mode", DRAWER_LEFT + 8, DRAWER_TOP + 112, chipWidth("Mode", pal.assignmentLabel()), 11, "Mode", pal.assignmentLabel(), 0xFF8CD2FF.toInt(), listOf(Text.literal("Assignment mode"), Text.literal("Current: ${pal.assignmentLabel()}"), Text.literal("Cycle this pal between general, preferred, and reserved duty.")), active = pal.tagTypeId != null),
            TextChipButton("crew-drawer-fallback", DRAWER_LEFT + 108, DRAWER_TOP + 112, chipWidth("Fallback", if (pal.allowFallback) "On" else "Off"), 11, "Fallback", if (pal.allowFallback) "On" else "Off", if (pal.allowFallback) 0xFFA6D98C.toInt() else 0xFFE07B67.toInt(), listOf(Text.literal("Fallback behavior"), Text.literal(if (pal.allowFallback) "Fallback is enabled for this pal." else "Fallback is locked off for this pal."), Text.literal("Toggle whether it can fall back into general labor.")), active = pal.tagTypeId != null),
            TextChipButton("crew-drawer-policy", DRAWER_LEFT + 8, DRAWER_TOP + 125, chipWidth("Role", if (pal.tagTypeId != null) "Policy" else "None"), 11, "Role", if (pal.tagTypeId != null) "Policy" else "None", 0xFFF1B85E.toInt(), listOf(Text.literal("Open role policy"), Text.literal(if (pal.tagTypeId != null) "Jump to this pal's role policy in the shell." else "This pal has no role policy yet."), Text.literal("Keeps the Command Post open while you retune the role.")), active = pal.tagTypeId != null),
            TextChipButton("crew-drawer-home", DRAWER_LEFT + 108, DRAWER_TOP + 125, chipWidth("Return", "Pasture"), 11, "Return", "Pasture", 0xFFB494FF.toInt(), listOf(Text.literal("Send this pal home"), Text.literal("Return the selected worker to its pasture anchor."), Text.literal("Useful when a worker is stuck in a bad position.")), active = true)
        )
    }

    private fun policyDrawerButtons(policy: PolicyRoleSummary): List<TextChipButton> {
        val filterMode = if (policy.filterSummary.startsWith("Block")) "Block" else if (policy.filterSummary.startsWith("Allow")) "Allow" else "Empty"
        val nbtMode = if (policy.matchSummary.startsWith("Exact")) "Exact" else "Loose"
        val matchMode = if (policy.matchSummary.endsWith("All")) "All" else "Any"
        val buttons = mutableListOf<TextChipButton>()
        if (policy.tagType.usesFilter) {
            buttons += TextChipButton("policy-mode", DRAWER_LEFT + 8, DRAWER_TOP + 68, chipWidth("Mode", filterMode), 11, "Mode", filterMode, 0xFF5ED6BE.toInt(), listOf(Text.literal("Filter mode"), Text.literal("Current: $filterMode"), Text.literal("Toggle between allowing and blocking matching items.")), active = true)
            buttons += TextChipButton("policy-nbt", DRAWER_LEFT + 116, DRAWER_TOP + 68, chipWidth("NBT", nbtMode), 11, "NBT", nbtMode, 0xFF86B8FF.toInt(), listOf(Text.literal("NBT matching"), Text.literal("Current: $nbtMode"), Text.literal("Toggle whether component data must match exactly.")), active = true)
            buttons += TextChipButton("policy-match", DRAWER_LEFT + 8, DRAWER_TOP + 81, chipWidth("Match", matchMode), 11, "Match", matchMode, 0xFFE3B16B.toInt(), listOf(Text.literal("Match mode"), Text.literal("Current: $matchMode"), Text.literal("Cycle whether all rules or any rule can match.")), active = true)
        }
        buttons += TextChipButton("policy-signal", DRAWER_LEFT + 116, DRAWER_TOP + 81, chipWidth("Signal", policy.redstoneLabel), 11, "Signal", policy.redstoneLabel, 0xFFFF8A6B.toInt(), listOf(Text.literal("Redstone mode"), Text.literal("Current: ${policy.redstoneLabel}"), Text.literal("Cycle how this role responds to redstone state.")), active = true)
        if (policy.tagType.supportsTargetList) {
            buttons += TextChipButton("policy-target", DRAWER_LEFT + 8, DRAWER_TOP + 94, chipWidth("Target", policy.targetStrategyLabel), 11, "Target", policy.targetStrategyLabel, 0xFF89B6DE.toInt(), listOf(Text.literal("Target strategy"), Text.literal("Current: ${policy.targetStrategyLabel}"), Text.literal("Cycle how this role chooses among its valid targets.")), active = true)
        }
        buttons += TextChipButton("policy-run", if (policy.tagType.supportsTargetList) DRAWER_LEFT + 116 else DRAWER_LEFT + 8, DRAWER_TOP + 94, chipWidth("Run", policy.runLabel), 11, "Run", policy.runLabel, 0xFFB494FF.toInt(), listOf(Text.literal("Run mode"), Text.literal("Current: ${policy.runLabel}"), Text.literal("Cycle between looping work and one-pass behavior.")), active = true)
        buttons += TextChipButton("policy-reg", DRAWER_LEFT + 8, DRAWER_TOP + 107, chipWidth("Reg", policy.regulatorAmount.toString()), 11, "Reg", policy.regulatorAmount.toString(), 0xFFA6D98C.toInt(), listOf(Text.literal("Regulator amount"), Text.literal("Current: ${policy.regulatorAmount}"), Text.literal("Cycle through safe preset stack targets.")), active = true)
        buttons += TextChipButton("policy-deep", DRAWER_LEFT + 116, DRAWER_TOP + 107, chipWidth("Deep", if (policy.tagType.usesFilter) "Ghost Slots" else "Open"), 11, "Deep", if (policy.tagType.usesFilter) "Ghost Slots" else "Open", 0xFFF1B85E.toInt(), listOf(Text.literal("Deep editor"), Text.literal(if (policy.tagType.usesFilter) "Open the full ghost-slot editor for item rules." else "Open the full role editor for this tag."), Text.literal("Use this when quick controls are not enough.")), active = true)
        return buttons
    }

    private fun policyDrawerIcons(policy: PolicyRoleSummary?): List<IconButton> {
        if (policy == null) return emptyList()
        return listOf(
            IconButton("policy-close", DRAWER_LEFT + DRAWER_WIDTH - 10, DRAWER_TOP + 4, 10, 10, UiGlyph.Close, 0xFFFF8A6B.toInt(), listOf(Text.literal("Close policy drawer"), Text.literal("Return to the role policy list without leaving the Command Post."), Text.literal("Shell context stays intact.")), active = true)
        )
    }

    private fun drawFamilyChip(context: DrawContext, summary: JobFamilySummary, left: Int, top: Int) {
        val text = Text.literal("${summary.family.label} ${summary.count}")
        val width = textRenderer.getWidth(text) + 12
        fillLocal(context, left, top, left + width, top + 11, 0xFF18212A.toInt())
        fillLocal(context, left, top, left + 3, top + 11, familyAccent(summary.family))
        context.drawText(textRenderer, text, left + 6, top + 2, CobblePalsUiTheme.TEXT_MUTED, false)
    }

    private fun drawAlertBar(context: DrawContext, alert: CommandPostAlert, left: Int, top: Int, width: Int) {
        fillLocal(context, left, top, left + width, top + 12, alert.bodyColor)
        fillLocal(context, left, top, left + 22, top + 12, alert.accentColor)
        context.drawText(textRenderer, Text.literal(alert.label), left + 5, top + 2, 0xFF091017.toInt(), false)
        context.drawText(textRenderer, Text.literal(truncate(alert.detail, 34)), left + 28, top + 2, alert.textColor, false)
    }

    private fun drawRoleRow(context: DrawContext, role: JobRoleSummary, left: Int, top: Int, width: Int, active: Boolean) {
        fillLocal(context, left, top, left + width, top + JOB_ROW_HEIGHT - 1, if (active) 0xFF18232E.toInt() else 0xFF111820.toInt())
        fillLocal(context, left, top, left + 2, top + JOB_ROW_HEIGHT - 1, role.causeColor)
        val installText = Text.literal("${role.staffedCount}/${role.installedCount}")
        val installX = left + width - textRenderer.getWidth(installText) - 4
        context.drawText(textRenderer, Text.literal(truncate(role.label, 12)), left + 6, top + 2, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(truncate(role.visibleCause, 11)), left + 78, top + 2, role.causeColor, false)
        context.drawText(textRenderer, installText, installX, top + 2, CobblePalsUiTheme.TEXT_MUTED, false)
    }

    private fun drawPolicyRow(context: DrawContext, role: PolicyRoleSummary, left: Int, top: Int, width: Int, active: Boolean) {
        fillLocal(context, left, top, left + width, top + POLICY_ROW_HEIGHT - 2, if (active) 0xFF18232E.toInt() else 0xFF111820.toInt())
        fillLocal(context, left, top, left + 2, top + POLICY_ROW_HEIGHT - 2, role.causeColor)
        val setupText = Text.literal("${role.configuredCount}/${role.totalCount}")
        val setupX = left + width - textRenderer.getWidth(setupText) - 4
        context.drawText(textRenderer, Text.literal(truncate(role.label, 11)), left + 6, top + 2, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(truncate(role.targetLabel, 11)), left + 78, top + 2, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, setupText, setupX, top + 2, role.causeColor, false)
    }

    private fun drawCrewRow(context: DrawContext, row: CrewRowSummary, left: Int, top: Int, width: Int, active: Boolean) {
        fillLocal(context, left, top, left + width, top + CREW_ROW_HEIGHT - 2, if (active) 0xFF18232E.toInt() else 0xFF111820.toInt())
        fillLocal(context, left, top, left + 2, top + CREW_ROW_HEIGHT - 2, row.statusColor)
        context.drawText(textRenderer, Text.literal(truncate(row.name, 12)), left + 6, top + 2, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(truncate(row.roleLabel, 11)), left + 70, top + 2, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(truncate(row.assignmentLabel, 9)), left + 138, top + 2, 0xFF8CD2FF.toInt(), false)
        context.drawText(textRenderer, Text.literal(truncate(row.statusLabel, 10)), left + 70, top + 10, row.statusColor, false)
    }

    private fun drawSectionButton(context: DrawContext, button: SectionButton, hovered: Boolean) {
        val active = activeView == button.view
        fillLocal(context, button.left, button.top, button.left + button.width, button.top + button.height, if (active) 0xFF1B2933.toInt() else 0xFF121A22.toInt())
        fillLocal(context, button.left, button.top, button.left + 3, button.top + button.height, button.accentColor)
        UiIconButtons.draw(context, x + button.left + 4, y + button.top + 1, 9, 9, button.glyph, button.accentColor, hovered, active)
        context.drawText(textRenderer, Text.literal(button.label), button.left + 17, button.top + 2, if (active) CobblePalsUiTheme.TEXT_PRIMARY else CobblePalsUiTheme.TEXT_MUTED, false)
    }

    private fun drawAccessCard(context: DrawContext, card: AccessCardButton, hovered: Boolean) {
        fillLocal(context, card.left, card.top, card.left + card.width, card.top + card.height, if (hovered) 0xFF18222B.toInt() else 0xFF121A22.toInt())
        fillLocal(context, card.left, card.top, card.left + 3, card.top + card.height, card.accentColor)
        context.drawText(textRenderer, Text.literal(card.title), card.left + 7, card.top + 5, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(truncate(card.summary, 13)), card.left + 7, card.top + 16, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(truncate(card.detail, 18)), card.left + 7, card.top + 27, CobblePalsUiTheme.TEXT_FAINT, false)
    }

    private fun drawTextChip(context: DrawContext, button: TextChipButton, hovered: Boolean) {
        val body = if (button.active) 0xFF18222C.toInt() else 0xFF111820.toInt()
        fillLocal(context, button.left, button.top, button.left + button.width, button.top + button.height, if (hovered) 0xFF1C2733.toInt() else body)
        fillLocal(context, button.left, button.top, button.left + 3, button.top + button.height, button.accentColor)
        context.drawText(textRenderer, Text.literal(button.label), button.left + 6, button.top + 2, CobblePalsUiTheme.TEXT_MUTED, false)
        val valueText = Text.literal(button.value)
        context.drawText(textRenderer, valueText, button.left + button.width - textRenderer.getWidth(valueText) - 5, button.top + 2, CobblePalsUiTheme.TEXT_PRIMARY, false)
    }

    private fun drawIconButton(context: DrawContext, button: IconButton, hovered: Boolean) {
        UiIconButtons.draw(context, x + button.left, y + button.top, button.width, button.height, button.glyph, button.accentColor, hovered, button.active)
    }

    private fun drawDrawerShell(context: DrawContext, title: String, subtitle: String, accent: Int) {
        fillLocal(context, DRAWER_LEFT - 2, DRAWER_TOP - 2, DRAWER_LEFT + DRAWER_WIDTH + 2, DRAWER_TOP + DRAWER_HEIGHT + 2, 0xCC071016.toInt())
        fillLocal(context, DRAWER_LEFT, DRAWER_TOP, DRAWER_LEFT + DRAWER_WIDTH, DRAWER_TOP + DRAWER_HEIGHT, 0xF0141D26.toInt())
        fillLocal(context, DRAWER_LEFT, DRAWER_TOP, DRAWER_LEFT + DRAWER_WIDTH, DRAWER_TOP + 14, 0xF0202E3A.toInt())
        fillLocal(context, DRAWER_LEFT, DRAWER_TOP, DRAWER_LEFT + 4, DRAWER_TOP + DRAWER_HEIGHT, accent)
        context.drawText(textRenderer, Text.literal(title), DRAWER_LEFT + 8, DRAWER_TOP + 3, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(truncate(subtitle, 28)), DRAWER_LEFT + 8, DRAWER_TOP + 17, CobblePalsUiTheme.TEXT_MUTED, false)
    }

    override fun drawBackground(context: DrawContext, delta: Float, mouseX: Int, mouseY: Int) {
        CobblePalsUiTheme.drawRootFrame(context, x, y, backgroundWidth, backgroundHeight, HEADER_HEIGHT)
        CobblePalsUiTheme.drawPanel(context, x, y, JOB_PANEL_LEFT, JOB_PANEL_TOP, JOB_PANEL_WIDTH, JOB_PANEL_HEIGHT, CobblePalsUiTheme.jobsPanel)
        CobblePalsUiTheme.drawPanel(context, x, y, STATUS_PANEL_LEFT, STATUS_PANEL_TOP, STATUS_PANEL_WIDTH, STATUS_PANEL_HEIGHT, CobblePalsUiTheme.statusPanel)
        CobblePalsUiTheme.drawPanel(context, x, y, BOOST_PANEL_LEFT, BOOST_PANEL_TOP, BOOST_PANEL_WIDTH, BOOST_PANEL_HEIGHT, CobblePalsUiTheme.augmentPanel)
        CobblePalsUiTheme.drawPanel(context, x, y, OPERATIONS_PANEL_LEFT, OPERATIONS_PANEL_TOP, OPERATIONS_PANEL_WIDTH, OPERATIONS_PANEL_HEIGHT, CobblePalsUiTheme.jobsPanel)
        if (activeView == CommandPostView.Logistics) {
            CobblePalsUiTheme.drawPanel(context, x, y, STORAGE_PANEL_LEFT, STORAGE_PANEL_TOP, STORAGE_PANEL_WIDTH, STORAGE_PANEL_HEIGHT, CobblePalsUiTheme.inventoryPanel)
            CobblePalsUiTheme.drawPanel(context, x, y, PLAYER_PANEL_LEFT, PLAYER_PANEL_TOP, PLAYER_PANEL_WIDTH, PLAYER_PANEL_HEIGHT, CobblePalsUiTheme.inventoryPanel)
        } else {
            CobblePalsUiTheme.drawPanel(context, x, y, DETAIL_PANEL_LEFT, DETAIL_PANEL_TOP, DETAIL_PANEL_WIDTH, DETAIL_PANEL_HEIGHT, CobblePalsUiTheme.inventoryPanel)
        }

        CobblePalsUiTheme.drawStripedFill(context, x, y, JOB_PANEL_LEFT + 5, JOB_PANEL_TOP + 18, JOB_PANEL_WIDTH - 10, JOB_PANEL_HEIGHT - 24, 0x1E6CD1C9, 6)
        CobblePalsUiTheme.drawStripedFill(context, x, y, STATUS_PANEL_LEFT + 5, STATUS_PANEL_TOP + 18, STATUS_PANEL_WIDTH - 10, STATUS_PANEL_HEIGHT - 24, 0x1EE3B16B, 6)
        CobblePalsUiTheme.drawStripedFill(context, x, y, OPERATIONS_PANEL_LEFT + 5, OPERATIONS_PANEL_TOP + 18, OPERATIONS_PANEL_WIDTH - 10, OPERATIONS_PANEL_HEIGHT - 24, 0x1269D0C9, 6)
        if (activeView == CommandPostView.Logistics) {
            CobblePalsUiTheme.drawStripedFill(context, x, y, STORAGE_PANEL_LEFT + 5, STORAGE_PANEL_TOP + 18, STORAGE_PANEL_WIDTH - 10, STORAGE_PANEL_HEIGHT - 24, 0x124B5A69, 6)
            CobblePalsUiTheme.drawStripedFill(context, x, y, PLAYER_PANEL_LEFT + 5, PLAYER_PANEL_TOP + 18, PLAYER_PANEL_WIDTH - 10, PLAYER_PANEL_HEIGHT - 24, 0x124B5A69, 6)
        } else {
            CobblePalsUiTheme.drawStripedFill(context, x, y, DETAIL_PANEL_LEFT + 5, DETAIL_PANEL_TOP + 18, DETAIL_PANEL_WIDTH - 10, DETAIL_PANEL_HEIGHT - 24, 0x124B5A69, 6)
        }

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
        val state = buildHomeState()
        val sectionButtons = sectionButtons(state)
        val snapshot = currentCrewSnapshot()
        val selectedJob = selectedJobRole(state)
        val selectedPolicy = selectedPolicyRole(state)
        val selectedCrew = selectedCrewRow(snapshot)

        val linkChipText = if (handler.linked) Text.literal("LINKED") else Text.literal("UNLINKED")
        val chipStyle = if (handler.linked) CobblePalsUiTheme.linkedStateChip else CobblePalsUiTheme.unlinkedStateChip
        val chipLeft = backgroundWidth - textRenderer.getWidth(linkChipText) - 24

        context.drawText(textRenderer, screenTitle, titleX, titleY, CobblePalsUiTheme.HEADER_TEXT, false)
        context.drawText(textRenderer, Text.literal(state.subtitle), 12, 20, CobblePalsUiTheme.SUBTITLE_TEXT, false)
        CobblePalsUiTheme.drawHeaderChip(context, textRenderer, x, y, linkChipText, chipLeft, 7, chipStyle)

        sectionButtons.forEach { button ->
            val hovered = contains(mouseX - x, mouseY - y, button.left, button.top, button.width, button.height)
            drawSectionButton(context, button, hovered)
        }

        context.drawText(textRenderer, Text.literal("JOB GRID"), 14, 48, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("CREW HEALTH"), 118, 48, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal("SHARED AUGMENTS"), 14, 144, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(when (activeView) {
            CommandPostView.Home -> "OPERATIONS"
            CommandPostView.Jobs -> "JOB FOCUS"
            CommandPostView.Crew -> "ROSTER STATE"
            CommandPostView.Policy -> "POLICY STATE"
            CommandPostView.Logistics -> "LOGISTICS STATE"
        }), 14, 182, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(when (activeView) {
            CommandPostView.Home -> "ACCESS RAIL"
            CommandPostView.Jobs -> "ROLE BOARD"
            CommandPostView.Crew -> "CREW ROSTER"
            CommandPostView.Policy -> "ROLE POLICIES"
            CommandPostView.Logistics -> "COMMAND BUFFER"
        }), 14, 246, CobblePalsUiTheme.TEXT_PRIMARY, false)

        context.drawText(textRenderer, Text.literal("${state.jobCardCount} installed"), 14, 104, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(truncate(state.jobSummary, 16)), 14, 116, CobblePalsUiTheme.TEXT_MUTED, false)

        CobblePalsUiTheme.drawStatusCard(context, textRenderer, x, y, 122, 64, STATUS_CARD_WIDTH, "Roster", handler.rosterCount, 0xFF4FC6C5.toInt(), 0xFFD9F3F1.toInt())
        CobblePalsUiTheme.drawStatusCard(context, textRenderer, x, y, 178, 64, STATUS_CARD_WIDTH, "Jobs", state.jobCardCount, 0xFFE3B16B.toInt(), 0xFFF4E1BA.toInt())
        CobblePalsUiTheme.drawStatusCard(context, textRenderer, x, y, 122, 86, STATUS_CARD_WIDTH, "Busy", handler.activeCount, 0xFF5FAEE8.toInt(), 0xFFD4EBFF.toInt())
        CobblePalsUiTheme.drawStatusCard(context, textRenderer, x, y, 178, 86, STATUS_CARD_WIDTH, "Ready", state.idleCount, 0xFF9AA7B3.toInt(), 0xFFE2E8EE.toInt())

        state.families.take(4).forEachIndexed { index, family ->
            val chipLeftLocal = if (index % 2 == 0) 120 else 177
            val chipTop = if (index < 2) 109 else 121
            drawFamilyChip(context, family, chipLeftLocal, chipTop)
        }
        context.drawText(textRenderer, Text.literal(truncate(state.augmentSummary, 34)), 108, 156, CobblePalsUiTheme.TEXT_MUTED, false)

        when (activeView) {
            CommandPostView.Home -> {
                drawAlertBar(context, state.alert, 16, 196, OPERATIONS_PANEL_WIDTH - 18)
                if (state.roles.isEmpty()) {
                    context.drawText(textRenderer, Text.literal("No installed roles yet."), 16, 211, CobblePalsUiTheme.TEXT_MUTED, false)
                } else {
                    state.roles.take(3).forEachIndexed { index, role ->
                        drawRoleRow(context, role, 16, 210 + index * JOB_ROW_HEIGHT, OPERATIONS_PANEL_WIDTH - 18, active = false)
                    }
                }
                context.drawText(textRenderer, Text.literal("Home keeps jobs, crew, policy, and buffer flows distinct."), 16, 259, CobblePalsUiTheme.TEXT_MUTED, false)
                homeAccessCards(state).forEach { card ->
                    val hovered = contains(mouseX - x, mouseY - y, card.left, card.top, card.width, card.height)
                    drawAccessCard(context, card, hovered)
                }
            }

            CommandPostView.Jobs -> {
                if (selectedJob == null) {
                    context.drawText(textRenderer, Text.literal("Install role cards to populate the jobs board."), 16, 211, CobblePalsUiTheme.TEXT_MUTED, false)
                    context.drawText(textRenderer, Text.literal("No job rows are available yet."), 16, 268, CobblePalsUiTheme.TEXT_MUTED, false)
                } else {
                    context.drawText(textRenderer, Text.literal("${selectedJob.label} • ${selectedJob.familyLabel}"), 16, 211, CobblePalsUiTheme.TEXT_MUTED, false)
                    context.drawText(textRenderer, Text.literal("Staffed ${selectedJob.staffedCount}/${selectedJob.installedCount} • Active ${selectedJob.activeCount} • ${selectedJob.visibleCause}"), 16, 223, selectedJob.causeColor, false)
                    state.roles.take(MAX_JOB_ROWS).forEachIndexed { index, role ->
                        drawRoleRow(context, role, 18, 266 + index * JOB_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 20, active = role.id == selectedJob.id)
                    }
                    context.drawText(textRenderer, Text.literal(if (selectedJob.assignedNames.isEmpty()) "No pals are currently covering this role." else "Crew: ${truncate(selectedJob.assignedNames.joinToString(", "), 32)}"), 16, 392, CobblePalsUiTheme.TEXT_FAINT, false)
                }
            }

            CommandPostView.Crew -> {
                if (snapshot == null) {
                    context.drawText(textRenderer, Text.literal("Waiting for linked pasture telemetry."), 16, 211, CobblePalsUiTheme.TEXT_MUTED, false)
                    context.drawText(textRenderer, Text.literal("Open the pasture once or stay linked long enough to pull a fresh roster."), 16, 223, CobblePalsUiTheme.TEXT_FAINT, false)
                    context.drawText(textRenderer, Text.literal("No crew rows are available yet."), 16, 268, CobblePalsUiTheme.TEXT_MUTED, false)
                } else {
                    val filtered = filteredCrewRows(snapshot)
                    val visibleRows = visibleCrewRows(snapshot)
                    val blockedCount = snapshot.pals.count { it.isBlocked() }
                    val standbyCount = snapshot.pals.count { it.isStandby() }
                    val faintedCount = snapshot.pals.count { it.isFainted }
                    context.drawText(textRenderer, Text.literal("${snapshot.ownerName}'s pasture • ${filtered.size}/${snapshot.pals.size} visible"), 16, 211, CobblePalsUiTheme.TEXT_MUTED, false)
                    context.drawText(textRenderer, Text.literal("Blocked $blockedCount • Standby $standbyCount • Fainted $faintedCount"), 16, 223, CobblePalsUiTheme.TEXT_FAINT, false)
                    if (filtered.isEmpty()) {
                        context.drawText(textRenderer, Text.literal("No pals match the current filters."), 16, 268, CobblePalsUiTheme.TEXT_MUTED, false)
                    } else {
                        crewFilterButtons(snapshot).forEach { button ->
                            val hovered = contains(mouseX - x, mouseY - y, button.left, button.top, button.width, button.height)
                            drawTextChip(context, button, hovered)
                        }
                        crewPageButtons(snapshot).forEach { button ->
                            val hovered = contains(mouseX - x, mouseY - y, button.left, button.top, button.width, button.height)
                            drawIconButton(context, button, hovered)
                        }
                        visibleRows.forEachIndexed { index, row ->
                            drawCrewRow(context, row, 18, DETAIL_ROWS_TOP + index * CREW_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 20, active = row.snapshotIndex == selectedCrewSnapshotIndex)
                        }
                        context.drawText(textRenderer, Text.literal("Click a row to open the in-shell pal drawer."), 16, 392, CobblePalsUiTheme.TEXT_FAINT, false)
                    }
                    if (selectedCrew != null && filtered.isNotEmpty()) {
                        drawCrewDrawer(context, snapshot)
                    }
                }
            }

            CommandPostView.Policy -> {
                if (state.policyRoles.isEmpty()) {
                    context.drawText(textRenderer, Text.literal("No installed role policies yet."), 16, 211, CobblePalsUiTheme.TEXT_MUTED, false)
                    context.drawText(textRenderer, Text.literal("Install role cards to start tuning policy."), 16, 223, CobblePalsUiTheme.TEXT_FAINT, false)
                } else {
                    context.drawText(textRenderer, Text.literal("Quick controls cover common behavior tuning here."), 16, 211, CobblePalsUiTheme.TEXT_MUTED, false)
                    context.drawText(textRenderer, Text.literal("Use deep edit only when you need full ghost-slot item rules."), 16, 223, CobblePalsUiTheme.TEXT_FAINT, false)
                    state.policyRoles.take(MAX_POLICY_ROWS).forEachIndexed { index, role ->
                        drawPolicyRow(context, role, 18, 266 + index * POLICY_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 20, active = role.rowIndex == selectedPolicyRowIndex)
                    }
                    context.drawText(textRenderer, Text.literal("Click a role row to tune it here."), 16, 392, CobblePalsUiTheme.TEXT_FAINT, false)
                    if (selectedPolicy != null) {
                        drawPolicyDrawer(context, selectedPolicy)
                    }
                }
            }

            CommandPostView.Logistics -> {
                context.drawText(textRenderer, Text.literal("Buffer work lives here so the landing page stays jobs-first."), 16, 211, CobblePalsUiTheme.TEXT_MUTED, false)
                context.drawText(textRenderer, Text.literal("Use this view for item flow and inventory handling."), 16, 223, CobblePalsUiTheme.TEXT_FAINT, false)
                context.drawText(textRenderer, Text.literal("PLAYER INVENTORY"), RouterScreenHandler.PLAYER_INV_X, 324, CobblePalsUiTheme.TEXT_PRIMARY, false)
            }
        }

        if (activeView == CommandPostView.Logistics) {
            context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, CobblePalsUiTheme.TEXT_MUTED, false)
        }
    }

    private fun drawCrewDrawer(context: DrawContext, snapshot: PastureSnapshot) {
        val selected = selectedCrewRow(snapshot) ?: return
        val pal = selected.pal
        drawDrawerShell(context, selected.name, "${selected.roleLabel} • ${selected.statusLabel}", selected.statusColor)
        context.drawText(textRenderer, Text.literal("${friendlyId(selected.species)} • Lv.${selected.level}"), DRAWER_LEFT + 8, DRAWER_TOP + 31, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal("Family: ${selected.family?.label ?: "Idle"}"), DRAWER_LEFT + 8, DRAWER_TOP + 43, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal("Assign: ${selected.assignmentLabel}"), DRAWER_LEFT + 8, DRAWER_TOP + 55, 0xFF8CD2FF.toInt(), false)
        context.drawText(textRenderer, Text.literal(truncate(pal.statusDetailOrFallback(), 34)), DRAWER_LEFT + 8, DRAWER_TOP + 67, selected.statusColor, false)
        context.drawText(textRenderer, Text.literal(truncate(selected.cargoSummary, 34)), DRAWER_LEFT + 8, DRAWER_TOP + 79, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(truncate("Filter: ${pal.filterSummary}", 34)), DRAWER_LEFT + 8, DRAWER_TOP + 91, CobblePalsUiTheme.TEXT_FAINT, false)
        context.drawText(textRenderer, Text.literal(truncate("Augments: ${pal.augmentSummary}", 34)), DRAWER_LEFT + 8, DRAWER_TOP + 103, 0xFFC59BFF.toInt(), false)
        val bindingText = pal.boundPos?.let { "Binding: ${it.x}, ${it.y}, ${it.z}" } ?: "Binding: none"
        context.drawText(textRenderer, Text.literal(truncate(bindingText, 34)), DRAWER_LEFT + 8, DRAWER_TOP + 115, CobblePalsUiTheme.TEXT_FAINT, false)
        crewDrawerIconButtons(snapshot).forEach { button ->
            val hovered = contains(mouseXToLocal(), mouseYToLocal(), button.left, button.top, button.width, button.height)
            drawIconButton(context, button, hovered)
        }
        crewDrawerButtons(snapshot).forEach { button ->
            val hovered = contains(mouseXToLocal(), mouseYToLocal(), button.left, button.top, button.width, button.height)
            drawTextChip(context, button, hovered)
        }
    }

    private fun drawPolicyDrawer(context: DrawContext, policy: PolicyRoleSummary) {
        drawDrawerShell(context, policy.label, "${policy.targetLabel} • ${policy.visibleCause}", policy.causeColor)
        context.drawText(textRenderer, Text.literal(truncate("Filter: ${policy.filterSummary}", 34)), DRAWER_LEFT + 8, DRAWER_TOP + 31, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(truncate("Match: ${policy.matchSummary}", 34)), DRAWER_LEFT + 8, DRAWER_TOP + 43, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(truncate("Signal: ${policy.redstoneLabel}", 34)), DRAWER_LEFT + 8, DRAWER_TOP + 55, CobblePalsUiTheme.TEXT_FAINT, false)
        context.drawText(textRenderer, Text.literal(truncate("Targets: ${policy.targetStrategyLabel} • Extra ${policy.extraTargetCount}", 34)), DRAWER_LEFT + 8, DRAWER_TOP + 118, CobblePalsUiTheme.TEXT_FAINT, false)
        policyDrawerButtons(policy).forEach { button ->
            val hovered = contains(mouseXToLocal(), mouseYToLocal(), button.left, button.top, button.width, button.height)
            drawTextChip(context, button, hovered)
        }
        policyDrawerIcons(policy).forEach { button ->
            val hovered = contains(mouseXToLocal(), mouseYToLocal(), button.left, button.top, button.width, button.height)
            drawIconButton(context, button, hovered)
        }
    }

    private fun mouseXToLocal(): Int = (client?.mouse?.x?.toInt() ?: 0) - x
    private fun mouseYToLocal(): Int = (client?.mouse?.y?.toInt() ?: 0) - y

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val now = System.currentTimeMillis()
        if (now >= nextCrewRefreshAtMs) {
            requestCrewRefresh()
            nextCrewRefreshAtMs = now + 1000L
        }

        renderBackground(context, mouseX, mouseY, delta)
        super.render(context, mouseX, mouseY, delta)
        drawMouseoverTooltip(context, mouseX, mouseY)

        val state = buildHomeState()
        val hoveredTooltip = hoveredTooltip(mouseX - x, mouseY - y, state)
        if (hoveredTooltip?.id != hoveredTooltipId) {
            hoveredTooltipId = hoveredTooltip?.id
            hoveredTooltipSinceMs = if (hoveredTooltip == null) 0L else now
        }
        if (hoveredTooltip != null && now - hoveredTooltipSinceMs >= CobblePalsUiTheme.TOOLTIP_DELAY_MS) {
            context.drawTooltip(textRenderer, hoveredTooltip.lines, mouseX, mouseY)
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_R) {
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
        val snapshot = currentCrewSnapshot()

        sectionButtons(state).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { section ->
            switchView(section.view)
            return true
        }

        when (activeView) {
            CommandPostView.Home -> {
                homeAccessCards(state).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { card ->
                    switchView(card.targetView)
                    return true
                }
            }

            CommandPostView.Jobs -> {
                state.roles.take(MAX_JOB_ROWS).indices.firstOrNull { index ->
                    contains(localMouseX, localMouseY, 18, 266 + index * JOB_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 20, JOB_ROW_HEIGHT - 1)
                }?.let { rowIndex ->
                    selectedJobTagId = state.roles[rowIndex].id
                    return true
                }
            }

            CommandPostView.Crew -> {
                val selected = selectedCrewRow(snapshot)
                if (selected != null) {
                    crewDrawerIconButtons(snapshot).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { drawerButton ->
                        when (drawerButton.id) {
                            "crew-drawer-prev" -> moveCrewSelection(snapshot, -1)
                            "crew-drawer-next" -> moveCrewSelection(snapshot, 1)
                            "crew-drawer-close" -> selectedCrewSnapshotIndex = null
                        }
                        return true
                    }
                    crewDrawerButtons(snapshot).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { drawerButton ->
                        when (drawerButton.id) {
                            "crew-drawer-mode" -> selectedCrewSnapshotIndex?.let { client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.ACTION_CYCLE_CREW_MODE_BASE + it) }
                            "crew-drawer-fallback" -> selectedCrewSnapshotIndex?.let { client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.ACTION_TOGGLE_CREW_FALLBACK_BASE + it) }
                            "crew-drawer-policy" -> {
                                selectPolicyForTag(selected.tagTypeId, state)
                                switchView(CommandPostView.Policy)
                            }
                            "crew-drawer-home" -> snapshot?.let { current -> CobblePalsNetworking.sendTeleportHome(current.pasturePos, selected.pal.pokemonId) }
                        }
                        return true
                    }
                }

                crewFilterButtons(snapshot).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { chip ->
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
                    selectedCrewSnapshotIndex = null
                    return true
                }

                crewPageButtons(snapshot).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { pageButton ->
                    val pageCount = crewPageCount(filteredCrewRows(snapshot))
                    when (pageButton.id) {
                        "crew-page-prev" -> crewPageIndex = (crewPageIndex - 1).coerceAtLeast(0)
                        "crew-page-next" -> crewPageIndex = (crewPageIndex + 1).coerceAtMost(pageCount - 1)
                    }
                    selectedCrewSnapshotIndex = null
                    return true
                }

                val visibleRows = visibleCrewRows(snapshot)
                visibleRows.indices.firstOrNull { index ->
                    contains(localMouseX, localMouseY, 18, DETAIL_ROWS_TOP + index * CREW_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 20, CREW_ROW_HEIGHT - 2)
                }?.let { rowIndex ->
                    selectedCrewSnapshotIndex = visibleRows[rowIndex].snapshotIndex
                    return true
                }
            }

            CommandPostView.Policy -> {
                val selectedPolicy = selectedPolicyRole(state)
                if (selectedPolicy != null) {
                    policyDrawerIcons(selectedPolicy).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let {
                        selectedPolicyRowIndex = null
                        return true
                    }
                    policyDrawerButtons(selectedPolicy).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { chip ->
                        val rowIndex = selectedPolicy.rowIndex
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
                }
                state.policyRoles.indices.firstOrNull { index ->
                    contains(localMouseX, localMouseY, 18, 266 + index * POLICY_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 20, POLICY_ROW_HEIGHT - 2)
                }?.let { rowIndex ->
                    selectedPolicyRowIndex = state.policyRoles[rowIndex].rowIndex
                    return true
                }
            }

            CommandPostView.Logistics -> Unit
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun moveCrewSelection(snapshot: PastureSnapshot?, direction: Int) {
        val filtered = filteredCrewRows(snapshot)
        if (filtered.size <= 1) return
        val currentIndex = filtered.indexOfFirst { it.snapshotIndex == selectedCrewSnapshotIndex }
        if (currentIndex < 0) {
            selectedCrewSnapshotIndex = filtered.first().snapshotIndex
            return
        }
        val nextIndex = (currentIndex + direction).coerceIn(0, filtered.lastIndex)
        selectedCrewSnapshotIndex = filtered[nextIndex].snapshotIndex
    }

    private fun hoveredTooltip(localMouseX: Int, localMouseY: Int, state: CommandPostHomeState): HoverTooltip? {
        val linkChipText = if (handler.linked) Text.literal("LINKED") else Text.literal("UNLINKED")
        val chipLeft = backgroundWidth - textRenderer.getWidth(linkChipText) - 24
        val chipWidth = textRenderer.getWidth(linkChipText) + 16
        val snapshot = currentCrewSnapshot()

        sectionButtons(state).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { button ->
            return HoverTooltip(button.id, button.tooltip)
        }

        if (contains(localMouseX, localMouseY, chipLeft, 7, chipWidth, 13)) {
            return HoverTooltip("link-chip", listOf(Text.literal(if (handler.linked) "Linked pasture active" else "No pasture linked"), Text.literal(if (handler.linked) "Sneak-use this block to relink it." else "Sneak-use near a pasture to link this Command Post.")))
        }

        when (activeView) {
            CommandPostView.Home -> {
                homeAccessCards(state).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { card ->
                    return HoverTooltip(card.id, card.tooltip)
                }
            }

            CommandPostView.Jobs -> {
                state.roles.take(MAX_JOB_ROWS).indices.firstOrNull { index ->
                    contains(localMouseX, localMouseY, 18, 266 + index * JOB_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 20, JOB_ROW_HEIGHT - 1)
                }?.let { index ->
                    return HoverTooltip("job-${state.roles[index].id}", state.roles[index].tooltipLines)
                }
            }

            CommandPostView.Crew -> {
                if (selectedCrewRow(snapshot) != null) {
                    crewDrawerIconButtons(snapshot).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { button ->
                        return HoverTooltip(button.id, button.tooltip)
                    }
                    crewDrawerButtons(snapshot).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { button ->
                        return HoverTooltip(button.id, button.tooltip)
                    }
                }
                crewFilterButtons(snapshot).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { button ->
                    return HoverTooltip(button.id, button.tooltip)
                }
                crewPageButtons(snapshot).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { button ->
                    return HoverTooltip(button.id, button.tooltip)
                }
                val visibleRows = visibleCrewRows(snapshot)
                visibleRows.indices.firstOrNull { index ->
                    contains(localMouseX, localMouseY, 18, DETAIL_ROWS_TOP + index * CREW_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 20, CREW_ROW_HEIGHT - 2)
                }?.let { index ->
                    return HoverTooltip("crew-${visibleRows[index].id}", visibleRows[index].tooltipLines)
                }
            }

            CommandPostView.Policy -> {
                val selectedPolicy = selectedPolicyRole(state)
                if (selectedPolicy != null) {
                    policyDrawerIcons(selectedPolicy).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { button ->
                        return HoverTooltip(button.id, button.tooltip)
                    }
                    policyDrawerButtons(selectedPolicy).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { button ->
                        return HoverTooltip(button.id, button.tooltip)
                    }
                }
                state.policyRoles.indices.firstOrNull { index ->
                    contains(localMouseX, localMouseY, 18, 266 + index * POLICY_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 20, POLICY_ROW_HEIGHT - 2)
                }?.let { index ->
                    return HoverTooltip("policy-${state.policyRoles[index].id}", state.policyRoles[index].tooltipLines)
                }
            }

            CommandPostView.Logistics -> {
                if (contains(localMouseX, localMouseY, STORAGE_PANEL_LEFT, STORAGE_PANEL_TOP, STORAGE_PANEL_WIDTH, STORAGE_PANEL_HEIGHT)) {
                    return HoverTooltip("buffer-storage", listOf(Text.literal("Command Buffer"), Text.literal("This built-in buffer is the default inventory for unbound module flows."), Text.literal("Modules pull from it and return items here when that behavior fits the tag.")))
                }
                if (contains(localMouseX, localMouseY, PLAYER_PANEL_LEFT, PLAYER_PANEL_TOP, PLAYER_PANEL_WIDTH, PLAYER_PANEL_HEIGHT)) {
                    return HoverTooltip("player-inventory", listOf(Text.literal("Player inventory")))
                }
            }
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
}

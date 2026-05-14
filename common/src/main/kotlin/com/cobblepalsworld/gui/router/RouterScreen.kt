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
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.TagTypePresentation
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW

class RouterScreen(
    handler: RouterScreenHandler,
    inventory: PlayerInventory,
    title: Text
) : HandledScreen<RouterScreenHandler>(handler, inventory, title) {

    private val screenTitle = Text.literal("COMMAND POST")

    private enum class CommandPostView {
        Home,
        Crew,
        Policy,
        Logistics
    }

    private data class HoverTooltip(val id: String, val lines: List<Text>)

    private data class SectionButton(
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
        val targetView: CommandPostView?,
        val tooltip: List<Text>
    )

    private data class InstalledCommandCard(
        val tagType: TagType,
        val configured: Boolean
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
        val label: String,
        val count: Int,
        val familyLabel: String,
        val visibleCause: String,
        val causeColor: Int,
        val tooltipLines: List<Text>
    )

    private data class PolicyRoleSummary(
        val id: String,
        val label: String,
        val configuredCount: Int,
        val totalCount: Int,
        val targetLabel: String,
        val visibleCause: String,
        val causeColor: Int,
        val tooltipLines: List<Text>
    )

    private data class CrewRowSummary(
        val id: String,
        val name: String,
        val roleLabel: String,
        val assignmentLabel: String,
        val statusLabel: String,
        val statusColor: Int,
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
        private const val STATUS_CARD_HEIGHT = 16
        private const val MAX_ROLE_ROWS = 3
        private const val ROLE_ROW_HEIGHT = 11
        private const val MAX_CREW_ROWS = 7
        private const val CREW_ROW_HEIGHT = 18
        private const val MAX_POLICY_ROWS = 6
        private const val POLICY_ROW_HEIGHT = 14
        private const val RAIL_TOP = 32
        private const val RAIL_HEIGHT = 11
        private const val HIDDEN_SLOT_X = -1000
        private const val HIDDEN_SLOT_Y = -1000
    }

    private var hoveredTooltipId: String? = null
    private var hoveredTooltipSinceMs: Long = 0L
    private var activeView = CommandPostView.Home
    private var nextCrewRefreshAtMs: Long = 0L

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

    private fun sectionButtons(state: CommandPostHomeState): List<SectionButton> {
        return listOf(
            SectionButton(
                view = CommandPostView.Home,
                left = 10,
                top = RAIL_TOP,
                width = 50,
                height = RAIL_HEIGHT,
                glyph = UiGlyph.Home,
                label = "HOME",
                accentColor = 0xFF69D0C9.toInt(),
                tooltip = listOf(
                    Text.literal("Home view"),
                    Text.literal("Jobs-first Command Post overview."),
                    Text.literal(state.crewSummary)
                )
            ),
            SectionButton(
                view = CommandPostView.Crew,
                left = 64,
                top = RAIL_TOP,
                width = 50,
                height = RAIL_HEIGHT,
                glyph = UiGlyph.Action,
                label = "CREW",
                accentColor = 0xFF4FC6C5.toInt(),
                tooltip = listOf(
                    Text.literal("Crew view"),
                    Text.literal("Read the linked pasture roster from the Command Post."),
                    Text.literal(if (currentCrewSnapshot() != null) "Live roster snapshot available." else "Waiting for linked pasture telemetry.")
                )
            ),
            SectionButton(
                view = CommandPostView.Policy,
                left = 118,
                top = RAIL_TOP,
                width = 56,
                height = RAIL_HEIGHT,
                glyph = UiGlyph.Filter,
                label = "POLICY",
                accentColor = 0xFFF1B85E.toInt(),
                tooltip = listOf(
                    Text.literal("Policy view"),
                    Text.literal("Inspect role setup and edit role cards from the jobs grid."),
                    Text.literal(if (state.setupIssueCount > 0) "${state.setupIssueCount} installed roles still need setup." else "Installed roles are configured.")
                )
            ),
            SectionButton(
                view = CommandPostView.Logistics,
                left = 178,
                top = RAIL_TOP,
                width = 60,
                height = RAIL_HEIGHT,
                glyph = UiGlyph.Data,
                label = "BUFFER",
                accentColor = 0xFF86B8FF.toInt(),
                tooltip = listOf(
                    Text.literal("Logistics view"),
                    Text.literal("Separate the Command Buffer from the landing page."),
                    Text.literal(state.augmentSummary)
                )
            )
        )
    }

    private fun homeAccessCards(state: CommandPostHomeState): List<AccessCardButton> {
        return listOf(
            AccessCardButton(
                id = "crew-card",
                left = DETAIL_PANEL_LEFT + 8,
                top = DETAIL_PANEL_TOP + 24,
                width = DETAIL_PANEL_WIDTH - 16,
                height = 32,
                title = "Crew",
                summary = truncate(state.crewSummary, 32),
                detail = "Switch to Crew for the linked pasture roster.",
                accentColor = 0xFF4FC6C5.toInt(),
                targetView = CommandPostView.Crew,
                tooltip = listOf(
                    Text.literal("Crew access"),
                    Text.literal(state.crewSummary),
                    Text.literal("Switch to Crew to inspect the linked pasture roster.")
                )
            ),
            AccessCardButton(
                id = "policy-card",
                left = DETAIL_PANEL_LEFT + 8,
                top = DETAIL_PANEL_TOP + 62,
                width = DETAIL_PANEL_WIDTH - 16,
                height = 32,
                title = "Policy",
                summary = if (state.setupIssueCount > 0) "${state.setupIssueCount} roles still need setup" else "Installed roles are configured",
                detail = "Switch to Policy to inspect setup and edit role cards.",
                accentColor = 0xFFF1B85E.toInt(),
                targetView = CommandPostView.Policy,
                tooltip = listOf(
                    Text.literal("Policy access"),
                    Text.literal(if (state.setupIssueCount > 0) "${state.setupIssueCount} installed roles still need a target or area." else "Installed roles are configured."),
                    Text.literal("Switch to Policy or hover a role card and press R.")
                )
            ),
            AccessCardButton(
                id = "logistics-card",
                left = DETAIL_PANEL_LEFT + 8,
                top = DETAIL_PANEL_TOP + 100,
                width = DETAIL_PANEL_WIDTH - 16,
                height = 32,
                title = "Logistics",
                summary = "Buffer flow stays out of the landing view",
                detail = "Switch to Logistics to manage the Command Buffer.",
                accentColor = 0xFF86B8FF.toInt(),
                targetView = CommandPostView.Logistics,
                tooltip = listOf(
                    Text.literal("Logistics access"),
                    Text.literal("The Command Buffer lives in a separate view so Home stays calm."),
                    Text.literal("Switch to Logistics to manage items and inventory.")
                )
            )
        )
    }

    private fun switchView(view: CommandPostView) {
        if (activeView == view) return
        activeView = view
        applySlotLayout()
        if (view == CommandPostView.Crew) {
            requestCrewRefresh()
        }
    }

    private fun requestCrewRefresh() {
        handler.linkedPasturePos?.let(CobblePalsNetworking::sendSnapshotRefresh)
    }

    private fun currentCrewSnapshot(): PastureSnapshot? {
        return PastureSnapshotCache.get(handler.linkedPasturePos)
    }

    private fun buildCrewRows(snapshot: PastureSnapshot?): List<CrewRowSummary> {
        if (snapshot == null) return emptyList()
        return snapshot.pals.take(MAX_CREW_ROWS).map { pal ->
            val roleLabel = pal.tagTypeId?.let { tagId ->
                TagType.fromId(tagId)?.let(TagTypePresentation::roleLabel)
            } ?: "No role"
            val cargoSummary = pal.carriedItemDescs.firstOrNull()?.let { "Cargo: $it" } ?: "Cargo: empty"
            CrewRowSummary(
                id = pal.pokemonId.toString(),
                name = pal.displayName,
                roleLabel = roleLabel,
                assignmentLabel = pal.assignmentLabel(),
                statusLabel = pal.statusLabel(),
                statusColor = crewStatusColor(pal),
                tooltipLines = buildList {
                    add(Text.literal(pal.displayName))
                    add(Text.literal("${roleLabel} • ${pal.statusLabel()}"))
                    add(Text.literal(pal.assignmentDetail()))
                    add(Text.literal(pal.statusDetailOrFallback()))
                    add(Text.literal(cargoSummary))
                    add(Text.literal(if (pal.isManagedByCommandPost) "Managed in Command Post" else "Local to pasture"))
                    add(Text.literal("Click to inspect this pal."))
                }
            )
        }
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

    private fun hoveredTooltip(localMouseX: Int, localMouseY: Int, state: CommandPostHomeState): HoverTooltip? {
        val linkChipText = if (handler.linked) Text.literal("LINKED") else Text.literal("UNLINKED")
        val chipLeft = backgroundWidth - textRenderer.getWidth(linkChipText) - 24
        val chipWidth = textRenderer.getWidth(linkChipText) + 16

        val roleRows = state.roles.take(MAX_ROLE_ROWS)
        val policyRows = state.policyRoles.take(MAX_POLICY_ROWS)
        val crewRows = buildCrewRows(currentCrewSnapshot())
        val sectionButtons = sectionButtons(state)
        val homeCards = homeAccessCards(state)

        return when {
            sectionButtons.any { button -> contains(localMouseX, localMouseY, button.left, button.top, button.width, button.height) } -> {
                val button = sectionButtons.first { section -> contains(localMouseX, localMouseY, section.left, section.top, section.width, section.height) }
                HoverTooltip(id = "view-${button.view.name.lowercase()}", lines = button.tooltip)
            }

            contains(localMouseX, localMouseY, chipLeft, 7, chipWidth, 13) -> HoverTooltip(
                id = "link-chip",
                lines = listOf(
                    Text.literal(if (handler.linked) "Linked pasture active" else "No pasture linked"),
                    Text.literal(if (handler.linked) "Sneak-use this block to relink it." else "Sneak-use near a pasture to link this Command Post.")
                )
            )

            contains(localMouseX, localMouseY, JOB_PANEL_LEFT, JOB_PANEL_TOP, JOB_PANEL_WIDTH, JOB_PANEL_HEIGHT) -> HoverTooltip(
                id = "task-matrix",
                lines = listOf(
                    Text.literal("Jobs Grid"),
                    Text.literal("Install tag cards here to define Command Post roles."),
                    Text.literal("Current cards: ${state.jobCardCount}"),
                    Text.literal(state.jobSummary),
                    Text.literal("Hover a tag card and press R to edit it.")
                )
            )

            contains(localMouseX, localMouseY, STATUS_PANEL_LEFT, STATUS_PANEL_TOP, STATUS_PANEL_WIDTH, STATUS_PANEL_HEIGHT) -> HoverTooltip(
                id = "pasture-relay",
                lines = listOf(
                    Text.literal("Crew Health"),
                    Text.literal(state.crewSummary),
                    Text.literal(if (handler.linked) "This Command Post is linked to one pasture." else "Link a nearby pasture to activate installed roles.")
                )
            )

            contains(localMouseX, localMouseY, BOOST_PANEL_LEFT, BOOST_PANEL_TOP, BOOST_PANEL_WIDTH, BOOST_PANEL_HEIGHT) -> HoverTooltip(
                id = "augment-rack",
                lines = listOf(
                    Text.literal("Shared Augments"),
                    Text.literal(state.augmentSummary),
                    Text.literal("Augments in these slots affect every installed role.")
                )
            )

            contains(localMouseX, localMouseY, 122, 64, STATUS_CARD_WIDTH, STATUS_CARD_HEIGHT) -> HoverTooltip(
                id = "roster-card",
                lines = listOf(
                    Text.literal("Roster"),
                    Text.literal("Tethered pals in the linked pasture: ${handler.rosterCount}")
                )
            )

            contains(localMouseX, localMouseY, 178, 64, STATUS_CARD_WIDTH, STATUS_CARD_HEIGHT) -> HoverTooltip(
                id = "jobs-card",
                lines = listOf(
                    Text.literal("Jobs"),
                    Text.literal("Installed tag cards: ${state.jobCardCount}"),
                    Text.literal(if (state.queuedCount > 0) "${state.queuedCount} installed roles are waiting for free pals." else "Installed roles have crew coverage right now.")
                )
            )

            contains(localMouseX, localMouseY, 122, 86, STATUS_CARD_WIDTH, STATUS_CARD_HEIGHT) -> HoverTooltip(
                id = "busy-card",
                lines = listOf(
                    Text.literal("Busy"),
                    Text.literal("Runnable job slots currently doing work: ${handler.activeCount}")
                )
            )

            contains(localMouseX, localMouseY, 178, 86, STATUS_CARD_WIDTH, STATUS_CARD_HEIGHT) -> HoverTooltip(
                id = "ready-card",
                lines = listOf(
                    Text.literal("Ready"),
                    Text.literal("Runnable job slots waiting for work: ${state.idleCount}")
                )
            )

            roleRows.indices.any { index ->
                contains(localMouseX, localMouseY, OPERATIONS_PANEL_LEFT + 6, OPERATIONS_PANEL_TOP + 28 + index * ROLE_ROW_HEIGHT, OPERATIONS_PANEL_WIDTH - 12, ROLE_ROW_HEIGHT - 1)
            } -> {
                val index = roleRows.indices.first { rowIndex ->
                    contains(localMouseX, localMouseY, OPERATIONS_PANEL_LEFT + 6, OPERATIONS_PANEL_TOP + 28 + rowIndex * ROLE_ROW_HEIGHT, OPERATIONS_PANEL_WIDTH - 12, ROLE_ROW_HEIGHT - 1)
                }
                HoverTooltip(
                    id = "role-${roleRows[index].id}",
                    lines = roleRows[index].tooltipLines
                )
            }

            contains(localMouseX, localMouseY, OPERATIONS_PANEL_LEFT, OPERATIONS_PANEL_TOP, OPERATIONS_PANEL_WIDTH, OPERATIONS_PANEL_HEIGHT) -> HoverTooltip(
                id = "operations-board",
                lines = listOf(
                    Text.literal(
                        when (activeView) {
                            CommandPostView.Home -> "Operations board"
                            CommandPostView.Crew -> "Crew board"
                            CommandPostView.Policy -> "Policy board"
                            CommandPostView.Logistics -> "Logistics board"
                        }
                    ),
                    Text.literal(state.alert.detail),
                    Text.literal(
                        when (activeView) {
                            CommandPostView.Home -> "Top installed roles are summarized here by count and current pressure."
                            CommandPostView.Crew -> "Roster data is pulled from the linked pasture instead of a second local list."
                            CommandPostView.Policy -> "Use the jobs grid and hover + R editing to tune role setup."
                            CommandPostView.Logistics -> "Home stays calm while logistics detail lives here and below."
                        }
                    )
                )
            )

            activeView == CommandPostView.Home && homeCards.any { card -> contains(localMouseX, localMouseY, card.left, card.top, card.width, card.height) } -> {
                val card = homeCards.first { access -> contains(localMouseX, localMouseY, access.left, access.top, access.width, access.height) }
                HoverTooltip(id = card.id, lines = card.tooltip)
            }

            activeView == CommandPostView.Crew && crewRows.indices.any { index ->
                contains(localMouseX, localMouseY, DETAIL_PANEL_LEFT + 8, DETAIL_PANEL_TOP + 24 + index * CREW_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 16, CREW_ROW_HEIGHT - 2)
            } -> {
                val index = crewRows.indices.first { rowIndex ->
                    contains(localMouseX, localMouseY, DETAIL_PANEL_LEFT + 8, DETAIL_PANEL_TOP + 24 + rowIndex * CREW_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 16, CREW_ROW_HEIGHT - 2)
                }
                HoverTooltip(id = "crew-${crewRows[index].id}", lines = crewRows[index].tooltipLines)
            }

            activeView == CommandPostView.Policy && policyRows.indices.any { index ->
                contains(localMouseX, localMouseY, DETAIL_PANEL_LEFT + 8, DETAIL_PANEL_TOP + 24 + index * POLICY_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 16, POLICY_ROW_HEIGHT - 2)
            } -> {
                val index = policyRows.indices.first { rowIndex ->
                    contains(localMouseX, localMouseY, DETAIL_PANEL_LEFT + 8, DETAIL_PANEL_TOP + 24 + rowIndex * POLICY_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 16, POLICY_ROW_HEIGHT - 2)
                }
                HoverTooltip(id = "policy-${policyRows[index].id}", lines = policyRows[index].tooltipLines)
            }

            activeView == CommandPostView.Logistics && contains(localMouseX, localMouseY, STORAGE_PANEL_LEFT, STORAGE_PANEL_TOP, STORAGE_PANEL_WIDTH, STORAGE_PANEL_HEIGHT) -> HoverTooltip(
                id = "buffer-storage",
                lines = listOf(
                    Text.literal("Command Buffer"),
                    Text.literal("This built-in buffer is the default inventory for unbound module flows."),
                    Text.literal("Modules pull from it and return items here when that behavior fits the tag.")
                )
            )

            activeView == CommandPostView.Logistics && contains(localMouseX, localMouseY, PLAYER_PANEL_LEFT, PLAYER_PANEL_TOP, PLAYER_PANEL_WIDTH, PLAYER_PANEL_HEIGHT) -> HoverTooltip(
                id = "player-inventory",
                lines = listOf(Text.literal("Player inventory"))
            )

            else -> null
        }
    }

    private fun buildHomeState(): CommandPostHomeState {
        val moduleSlots = handler.slots.take(RouterBlockEntity.MODULE_SLOT_COUNT)
        val installedCards = moduleSlots.mapNotNull { slot ->
            val stack = slot.stack
            val tagItem = stack.item as? TagItem ?: return@mapNotNull null
            InstalledCommandCard(tagItem.tagType, isConfigured(stack, tagItem))
        }

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
                val missingTargets = cards.count { !it.configured }
                val (visibleCause, causeColor) = roleCause(missingTargets, queuedCount, handler.linked, handler.rosterCount, handler.activeCount)
                val count = cards.size
                JobRoleSummary(
                    id = tagType.id,
                    label = TagTypePresentation.roleLabel(tagType),
                    count = count,
                    familyLabel = familyFor(tagType).label,
                    visibleCause = visibleCause,
                    causeColor = causeColor,
                    tooltipLines = buildList {
                        add(Text.literal("${TagTypePresentation.roleLabel(tagType)} • $count installed"))
                        add(Text.literal("Family: ${familyFor(tagType).label}"))
                        if (missingTargets > 0) {
                            add(Text.literal("Setup: $missingTargets still need a target or area."))
                        } else {
                            add(Text.literal("Setup: all installed cards are configured."))
                        }
                        add(Text.literal("Pressure: $visibleCause"))
                    }
                )
            }

        val policyRoles = installedCards
            .groupBy { it.tagType }
            .entries
            .sortedWith(compareByDescending<Map.Entry<TagType, List<InstalledCommandCard>>> { it.value.size }.thenBy { TagTypePresentation.roleLabel(it.key) })
            .map { (tagType, cards) ->
                val totalCount = cards.size
                val configuredCount = cards.count { it.configured }
                val missingCount = totalCount - configuredCount
                val bindingLabel = policyBindingLabel(tagType)
                val visibleCause = when {
                    missingCount > 0 -> "$missingCount need setup"
                    !handler.linked -> "waiting on link"
                    else -> "edit from grid"
                }
                val causeColor = when {
                    missingCount > 0 -> 0xFFF1B85E.toInt()
                    !handler.linked -> 0xFFE07B67.toInt()
                    else -> 0xFF86B8FF.toInt()
                }
                PolicyRoleSummary(
                    id = tagType.id,
                    label = TagTypePresentation.roleLabel(tagType),
                    configuredCount = configuredCount,
                    totalCount = totalCount,
                    targetLabel = bindingLabel,
                    visibleCause = visibleCause,
                    causeColor = causeColor,
                    tooltipLines = buildList {
                        add(Text.literal("${TagTypePresentation.roleLabel(tagType)} • $configuredCount/$totalCount configured"))
                        add(Text.literal("Target: $bindingLabel"))
                        add(Text.literal(if (missingCount > 0) "$missingCount installed card${pluralize(missingCount)} still need setup." else "Installed cards are configured."))
                        add(Text.literal("Click this row to edit one installed role card."))
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
            else -> "All installed roles are configured and ready for crew coverage."
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
            jobCardCount = jobCardCount,
            idleCount = idleCount,
            queuedCount = queuedCount,
            augmentCount = augmentCount,
            setupIssueCount = setupIssueCount,
            subtitle = subtitle,
            jobSummary = jobSummary,
            crewSummary = crewSummary,
            augmentSummary = augmentSummary,
            alert = buildAlert(jobCardCount, setupIssueCount, queuedCount),
            families = families,
            roles = roles,
            policyRoles = policyRoles
        )
    }

    private fun buildAlert(jobCardCount: Int, setupIssueCount: Int, queuedCount: Int): CommandPostAlert {
        return when {
            jobCardCount <= 0 -> CommandPostAlert(
                label = "EMPTY",
                detail = "Install tag cards to define this crew's jobs.",
                accentColor = 0xFF6B7D8B.toInt(),
                bodyColor = 0xFF1D252C.toInt(),
                textColor = 0xFFE1E9EF.toInt()
            )
            !handler.linked -> CommandPostAlert(
                label = "UNLINKED",
                detail = "Link a nearby pasture before installed roles can activate.",
                accentColor = 0xFFE07B67.toInt(),
                bodyColor = 0xFF2E1B18.toInt(),
                textColor = 0xFFFFDDD8.toInt()
            )
            setupIssueCount > 0 -> CommandPostAlert(
                label = "SETUP",
                detail = "$setupIssueCount installed role${pluralize(setupIssueCount)} still need a target or area.",
                accentColor = 0xFFF1B85E.toInt(),
                bodyColor = 0xFF302419.toInt(),
                textColor = 0xFFFFE8C8.toInt()
            )
            handler.rosterCount <= 0 -> CommandPostAlert(
                label = "NO CREW",
                detail = "The linked pasture has no active pals to assign.",
                accentColor = 0xFFCC8F62.toInt(),
                bodyColor = 0xFF2B221B.toInt(),
                textColor = 0xFFF8DFC9.toInt()
            )
            queuedCount > 0 -> CommandPostAlert(
                label = "QUEUE",
                detail = "$queuedCount installed role${pluralize(queuedCount)} are waiting for free pals.",
                accentColor = 0xFF7FAEF7.toInt(),
                bodyColor = 0xFF182331.toInt(),
                textColor = 0xFFD8E8FF.toInt()
            )
            handler.activeCount > 0 -> CommandPostAlert(
                label = "ACTIVE",
                detail = "The linked crew is already working from this Command Post.",
                accentColor = 0xFF5ED6BE.toInt(),
                bodyColor = 0xFF162925.toInt(),
                textColor = 0xFFD7F7F0.toInt()
            )
            else -> CommandPostAlert(
                label = "READY",
                detail = "Installed roles are configured and standing by for work.",
                accentColor = 0xFFA6D98C.toInt(),
                bodyColor = 0xFF1D2A1A.toInt(),
                textColor = 0xFFE1F4D8.toInt()
            )
        }
    }

    private fun isConfigured(stack: net.minecraft.item.ItemStack, tagItem: TagItem): Boolean {
        return !tagItem.tagType.supportsBinding || TagItem.getBoundPos(stack) != null || TagItem.getBoundArea(stack) != null
    }

    private fun familyFor(tagType: TagType): TagRoleFamily {
        return TagTypePresentation.familyOf(tagType)
    }

    private fun roleCause(missingTargets: Int, queuedCount: Int, linked: Boolean, rosterCount: Int, activeCount: Int): Pair<String, Int> {
        return when {
            missingTargets > 0 -> "needs target" to 0xFFF1B85E.toInt()
            !linked -> "link pasture" to 0xFFE07B67.toInt()
            rosterCount <= 0 -> "no crew" to 0xFFCC8F62.toInt()
            queuedCount > 0 -> "crew at cap" to 0xFF7FAEF7.toInt()
            activeCount > 0 -> "working now" to 0xFF5ED6BE.toInt()
            else -> "standing by" to 0xFFA6D98C.toInt()
        }
    }

    private fun familyAccent(family: TagRoleFamily): Int {
        return when (family) {
            TagRoleFamily.Gathering -> 0xFF55D6BE.toInt()
            TagRoleFamily.Logistics -> 0xFF86B8FF.toInt()
            TagRoleFamily.Defense -> 0xFFFF8A6B.toInt()
            TagRoleFamily.Interaction -> 0xFFE7C36A.toInt()
            TagRoleFamily.Care -> 0xFFB494FF.toInt()
        }
    }

    private fun policyBindingLabel(tagType: TagType): String {
        return when (tagType.bindingMode) {
            com.cobblepalsworld.tag.BindingMode.NONE -> "No target"
            com.cobblepalsworld.tag.BindingMode.CONTAINER -> "Container"
            com.cobblepalsworld.tag.BindingMode.POSITION -> "Point target"
            com.cobblepalsworld.tag.BindingMode.AREA -> "Work area"
        }
    }

    private fun pluralize(count: Int, singularSuffix: String = "", pluralSuffix: String = "s"): String {
        return if (count == 1) singularSuffix else pluralSuffix
    }

    private fun truncate(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        return value.take(maxChars - 1) + "..."
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

    private fun drawRoleRow(context: DrawContext, role: JobRoleSummary, left: Int, top: Int, width: Int) {
        fillLocal(context, left, top, left + width, top + ROLE_ROW_HEIGHT - 1, 0xFF111820.toInt())
        fillLocal(context, left, top, left + 2, top + ROLE_ROW_HEIGHT - 1, role.causeColor)

        val countText = Text.literal("x${role.count}")
        val countX = left + width - textRenderer.getWidth(countText) - 4
        val cause = Text.literal(truncate(role.visibleCause, 11))
        val causeX = left + 92

        context.drawText(textRenderer, Text.literal(truncate(role.label, 11)), left + 6, top + 2, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, cause, causeX, top + 2, role.causeColor, false)
        context.drawText(textRenderer, countText, countX, top + 2, CobblePalsUiTheme.TEXT_MUTED, false)
    }

    private fun drawPolicyRow(context: DrawContext, role: PolicyRoleSummary, left: Int, top: Int, width: Int) {
        fillLocal(context, left, top, left + width, top + POLICY_ROW_HEIGHT - 2, 0xFF111820.toInt())
        fillLocal(context, left, top, left + 2, top + POLICY_ROW_HEIGHT - 2, role.causeColor)

        val setupText = Text.literal("${role.configuredCount}/${role.totalCount}")
        val setupX = left + width - textRenderer.getWidth(setupText) - 4

        context.drawText(textRenderer, Text.literal(truncate(role.label, 11)), left + 6, top + 2, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(truncate(role.targetLabel, 10)), left + 80, top + 2, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, setupText, setupX, top + 2, role.causeColor, false)
    }

    private fun drawCrewRow(context: DrawContext, row: CrewRowSummary, left: Int, top: Int, width: Int) {
        fillLocal(context, left, top, left + width, top + CREW_ROW_HEIGHT - 2, 0xFF111820.toInt())
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
        UiIconButtons.draw(context, button.left + 4, button.top + 1, 9, 9, button.glyph, button.accentColor, hovered, active)
        context.drawText(textRenderer, Text.literal(button.label), button.left + 17, button.top + 2, if (active) CobblePalsUiTheme.TEXT_PRIMARY else CobblePalsUiTheme.TEXT_MUTED, false)
    }

    private fun drawAccessCard(context: DrawContext, card: AccessCardButton, hovered: Boolean) {
        fillLocal(context, card.left, card.top, card.left + card.width, card.top + card.height, if (hovered) 0xFF18222B.toInt() else 0xFF121A22.toInt())
        fillLocal(context, card.left, card.top, card.left + 3, card.top + card.height, card.accentColor)
        context.drawText(textRenderer, Text.literal(card.title), card.left + 7, card.top + 5, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(truncate(card.summary, 32)), card.left + 46, card.top + 5, CobblePalsUiTheme.TEXT_MUTED, false)
        context.drawText(textRenderer, Text.literal(truncate(card.detail, 38)), card.left + 7, card.top + 18, CobblePalsUiTheme.TEXT_FAINT, false)
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
        context.drawText(
            textRenderer,
            Text.literal(
                when (activeView) {
                    CommandPostView.Home -> "OPERATIONS"
                    CommandPostView.Crew -> "CREW BOARD"
                    CommandPostView.Policy -> "POLICY BOARD"
                    CommandPostView.Logistics -> "LOGISTICS STATE"
                }
            ),
            14,
            182,
            CobblePalsUiTheme.TEXT_PRIMARY,
            false
        )
        context.drawText(
            textRenderer,
            Text.literal(
                when (activeView) {
                    CommandPostView.Home -> "ACCESS RAIL"
                    CommandPostView.Crew -> "CREW ROSTER"
                    CommandPostView.Policy -> "ROLE POLICIES"
                    CommandPostView.Logistics -> "COMMAND BUFFER"
                }
            ),
            14,
            246,
            CobblePalsUiTheme.TEXT_PRIMARY,
            false
        )

        context.drawText(textRenderer, Text.literal("${state.jobCardCount} installed"), 14, 104, CobblePalsUiTheme.TEXT_PRIMARY, false)
        context.drawText(textRenderer, Text.literal(truncate(state.jobSummary, 16)), 14, 116, CobblePalsUiTheme.TEXT_MUTED, false)

        CobblePalsUiTheme.drawStatusCard(context, textRenderer, x, y, 122, 64, STATUS_CARD_WIDTH, "Roster", handler.rosterCount, 0xFF4FC6C5.toInt(), 0xFFD9F3F1.toInt())
        CobblePalsUiTheme.drawStatusCard(context, textRenderer, x, y, 178, 64, STATUS_CARD_WIDTH, "Jobs", state.jobCardCount, 0xFFE3B16B.toInt(), 0xFFF4E1BA.toInt())
        CobblePalsUiTheme.drawStatusCard(context, textRenderer, x, y, 122, 86, STATUS_CARD_WIDTH, "Busy", handler.activeCount, 0xFF5FAEE8.toInt(), 0xFFD4EBFF.toInt())
        CobblePalsUiTheme.drawStatusCard(context, textRenderer, x, y, 178, 86, STATUS_CARD_WIDTH, "Ready", state.idleCount, 0xFF9AA7B3.toInt(), 0xFFE2E8EE.toInt())

        state.families.take(4).forEachIndexed { index, family ->
            val chipLeft = if (index % 2 == 0) 120 else 177
            val chipTop = if (index < 2) 109 else 121
            drawFamilyChip(context, family, chipLeft, chipTop)
        }

        context.drawText(textRenderer, Text.literal(truncate(state.augmentSummary, 34)), 108, 156, CobblePalsUiTheme.TEXT_MUTED, false)

        drawAlertBar(context, state.alert, 16, 196, OPERATIONS_PANEL_WIDTH - 18)

        when (activeView) {
            CommandPostView.Home -> {
                val visibleRoles = state.roles.take(MAX_ROLE_ROWS)
                if (visibleRoles.isEmpty()) {
                    context.drawText(textRenderer, Text.literal("No installed roles yet."), 16, 211, CobblePalsUiTheme.TEXT_MUTED, false)
                } else {
                    visibleRoles.forEachIndexed { index, role ->
                        drawRoleRow(context, role, 16, 210 + index * ROLE_ROW_HEIGHT, OPERATIONS_PANEL_WIDTH - 18)
                    }
                }

                context.drawText(textRenderer, Text.literal("Home keeps crews, policy, and logistics separated."), 16, 259, CobblePalsUiTheme.TEXT_MUTED, false)
                homeAccessCards(state).forEach { card ->
                    val hovered = contains(mouseX - x, mouseY - y, card.left, card.top, card.width, card.height)
                    drawAccessCard(context, card, hovered)
                }
            }

            CommandPostView.Crew -> {
                val snapshot = currentCrewSnapshot()
                if (snapshot == null) {
                    context.drawText(textRenderer, Text.literal("Waiting for linked pasture telemetry."), 16, 211, CobblePalsUiTheme.TEXT_MUTED, false)
                    context.drawText(textRenderer, Text.literal("Open the pasture once or stay linked long enough to pull a fresh roster."), 16, 223, CobblePalsUiTheme.TEXT_FAINT, false)
                    context.drawText(textRenderer, Text.literal("No crew rows are available yet."), 16, 268, CobblePalsUiTheme.TEXT_MUTED, false)
                } else {
                    val blockedCount = snapshot.pals.count { it.isBlocked() }
                    val standbyCount = snapshot.pals.count { it.isStandby() }
                    val faintedCount = snapshot.pals.count { it.isFainted }
                    context.drawText(textRenderer, Text.literal("${snapshot.ownerName}'s pasture • ${snapshot.pals.size} pals"), 16, 211, CobblePalsUiTheme.TEXT_MUTED, false)
                    context.drawText(textRenderer, Text.literal("Blocked $blockedCount • Standby $standbyCount • Fainted $faintedCount"), 16, 223, CobblePalsUiTheme.TEXT_FAINT, false)

                    val rows = buildCrewRows(snapshot)
                    if (rows.isEmpty()) {
                        context.drawText(textRenderer, Text.literal("No pals are currently tethered to the linked pasture."), 16, 268, CobblePalsUiTheme.TEXT_MUTED, false)
                    } else {
                        rows.forEachIndexed { index, row ->
                            drawCrewRow(context, row, 18, 266 + index * CREW_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 20)
                        }
                    }
                    context.drawText(textRenderer, Text.literal("Click a row to open that pal's inspector."), 16, 392, CobblePalsUiTheme.TEXT_FAINT, false)
                }
            }

            CommandPostView.Policy -> {
                context.drawText(textRenderer, Text.literal("Setup issues stay visible here so Home stays calm."), 16, 211, CobblePalsUiTheme.TEXT_MUTED, false)
                val policyRows = state.policyRoles.take(MAX_POLICY_ROWS)
                if (policyRows.isEmpty()) {
                    context.drawText(textRenderer, Text.literal("No installed role policies yet."), 16, 268, CobblePalsUiTheme.TEXT_MUTED, false)
                } else {
                    policyRows.forEachIndexed { index, role ->
                        drawPolicyRow(context, role, 18, 266 + index * POLICY_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 20)
                    }
                }
                context.drawText(textRenderer, Text.literal("Click a policy row or press R on a role card to edit it."), 16, 392, CobblePalsUiTheme.TEXT_FAINT, false)
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
                client?.interactionManager?.clickButton(handler.syncId, 100 + moduleIndex)
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val localMouseX = (mouseX - x).toInt()
            val localMouseY = (mouseY - y).toInt()
            val state = buildHomeState()

            sectionButtons(state).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { section ->
                switchView(section.view)
                return true
            }

            if (activeView == CommandPostView.Home) {
                homeAccessCards(state).firstOrNull { contains(localMouseX, localMouseY, it.left, it.top, it.width, it.height) }?.let { card ->
                    card.targetView?.let(::switchView)
                    return true
                }
            }

            if (activeView == CommandPostView.Crew) {
                val crewRows = buildCrewRows(currentCrewSnapshot())
                crewRows.indices.firstOrNull { index ->
                    contains(localMouseX, localMouseY, DETAIL_PANEL_LEFT + 8, DETAIL_PANEL_TOP + 24 + index * CREW_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 16, CREW_ROW_HEIGHT - 2)
                }?.let { rowIndex ->
                    client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.ACTION_OPEN_CREW_ROW_BASE + rowIndex)
                    return true
                }
            }

            if (activeView == CommandPostView.Policy) {
                val policyRows = state.policyRoles.take(MAX_POLICY_ROWS)
                policyRows.indices.firstOrNull { index ->
                    contains(localMouseX, localMouseY, DETAIL_PANEL_LEFT + 8, DETAIL_PANEL_TOP + 24 + index * POLICY_ROW_HEIGHT, DETAIL_PANEL_WIDTH - 16, POLICY_ROW_HEIGHT - 2)
                }?.let { rowIndex ->
                    client?.interactionManager?.clickButton(handler.syncId, RouterScreenHandler.ACTION_OPEN_POLICY_ROW_BASE + rowIndex)
                    return true
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
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
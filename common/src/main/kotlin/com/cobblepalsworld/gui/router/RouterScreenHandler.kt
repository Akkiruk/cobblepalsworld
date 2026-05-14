package com.cobblepalsworld.gui.router

import com.cobblepalsworld.augment.AugmentItem
import com.cobblepalsworld.gui.filter.TagFilterScreenHandler
import com.cobblepalsworld.gui.assignment.PokemonTagScreenHandler
import com.cobblepalsworld.gui.MenuTypes
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.pasture.TagAssignmentManager
import com.cobblepalsworld.gui.pasture.PastureSnapshotFactory
import com.cobblepalsworld.router.RouterBlockEntity
import com.cobblepalsworld.tag.TagItem
import com.cobblepalsworld.tag.TagSpec
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.TagTypePresentation
import com.cobblepalsworld.tag.TargetStrategy
import com.cobblepalsworld.tag.RedstoneControlMode
import com.cobblepalsworld.tag.filter.FilterMatchMode
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ArrayPropertyDelegate
import net.minecraft.screen.PropertyDelegate
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.Slot
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

class RouterScreenHandler : ScreenHandler {
    private val routerInventory: Inventory
    private val routerData: PropertyDelegate

    companion object {
        const val BACKGROUND_WIDTH = 300
        const val BACKGROUND_HEIGHT = 414
        const val MODULE_COLUMNS = 3
        const val MODULE_ROWS = 3
        const val MODULE_START_X = 20
        const val MODULE_START_Y = 78
        const val BOOST_START_X = 82
        const val BOOST_START_Y = 144
        const val STORAGE_COLUMNS = 9
        const val STORAGE_ROWS = 3
        const val STORAGE_START_X = 69
        const val STORAGE_START_Y = 220
        const val PLAYER_INV_X = 69
        const val PLAYER_INV_Y = 312
        const val MODULE_SCREEN_SLOT_COUNT = RouterBlockEntity.MODULE_SLOT_COUNT
        const val UPGRADE_SCREEN_SLOT_START = MODULE_SCREEN_SLOT_COUNT
        const val STORAGE_SCREEN_SLOT_START = UPGRADE_SCREEN_SLOT_START + RouterBlockEntity.UPGRADE_SLOT_COUNT
        const val COMMAND_SLOT_COUNT = STORAGE_SCREEN_SLOT_START + RouterBlockEntity.STORAGE_SLOT_COUNT
        const val ACTION_EDIT_MODULE_BASE = 100
        const val ACTION_OPEN_POLICY_ROW_BASE = 200
        const val ACTION_OPEN_CREW_ROW_BASE = 300
        const val ACTION_CYCLE_CREW_MODE_BASE = 400
        const val ACTION_TOGGLE_CREW_FALLBACK_BASE = 700
        private const val DYNAMIC_CREW_ACTION_LIMIT = 256
        private const val ACTION_POLICY_QUICK_BASE = 1000
        private const val POLICY_ACTION_STRIDE = 16
        const val POLICY_ACTION_TOGGLE_WHITELIST = 0
        const val POLICY_ACTION_TOGGLE_NBT = 1
        const val POLICY_ACTION_CYCLE_MATCH = 2
        const val POLICY_ACTION_CYCLE_SIGNAL = 3
        const val POLICY_ACTION_CYCLE_TARGET = 4
        const val POLICY_ACTION_TOGGLE_RUN = 5
        const val POLICY_ACTION_CYCLE_REGULATOR = 6

        private val REGULATOR_PRESETS = intArrayOf(1, 4, 8, 16, 32, 64)

        fun isCommandCard(stack: ItemStack): Boolean = stack.item is TagItem

        fun policyQuickActionId(rowIndex: Int, action: Int): Int {
            return ACTION_POLICY_QUICK_BASE + action * POLICY_ACTION_STRIDE + rowIndex
        }
    }

    constructor(syncId: Int, playerInventory: PlayerInventory) : super(MenuTypes.ROUTER.get(), syncId) {
        this.routerInventory = SimpleInventory(RouterBlockEntity.TOTAL_SLOTS)
        this.routerData = ArrayPropertyDelegate(25)
        setupSlots(playerInventory)
    }

    constructor(syncId: Int, playerInventory: PlayerInventory, routerInventory: Inventory, routerData: PropertyDelegate) : super(MenuTypes.ROUTER.get(), syncId) {
        checkSize(routerInventory, RouterBlockEntity.TOTAL_SLOTS)
        checkDataCount(routerData, 25)
        this.routerInventory = routerInventory
        this.routerData = routerData
        setupSlots(playerInventory)
    }

    val linked: Boolean get() = routerData.get(0) != 0
    val rosterCount: Int get() = routerData.get(1)
    val assignedCount: Int get() = routerData.get(2)
    val activeCount: Int get() = routerData.get(3)
    val linkedPasturePos: BlockPos?
        get() = if (linked) BlockPos(routerData.get(4), routerData.get(5), routerData.get(6)) else null

    fun moduleAssigned(moduleIndex: Int): Boolean {
        if (moduleIndex !in 0 until RouterBlockEntity.MODULE_SLOT_COUNT) return false
        return routerData.get(7 + moduleIndex) != 0
    }

    fun moduleActive(moduleIndex: Int): Boolean {
        if (moduleIndex !in 0 until RouterBlockEntity.MODULE_SLOT_COUNT) return false
        return routerData.get(16 + moduleIndex) != 0
    }

    private fun setupSlots(playerInventory: PlayerInventory) {
        addProperties(routerData)

        for (row in 0 until MODULE_ROWS) {
            for (col in 0 until MODULE_COLUMNS) {
                val slotIndex = RouterBlockEntity.MODULE_SLOT_START + row * MODULE_COLUMNS + col
                addSlot(object : Slot(routerInventory, slotIndex, MODULE_START_X + col * 18, MODULE_START_Y + row * 18) {
                    override fun canInsert(stack: ItemStack): Boolean = isCommandCard(stack)
                    override fun getMaxItemCount(): Int = 1
                })
            }
        }

        for (index in 0 until RouterBlockEntity.UPGRADE_SLOT_COUNT) {
            val slotIndex = RouterBlockEntity.UPGRADE_SLOT_START + index
            addSlot(object : Slot(routerInventory, slotIndex, BOOST_START_X + index * 18, BOOST_START_Y) {
                override fun canInsert(stack: ItemStack): Boolean = stack.item is AugmentItem
            })
        }

        for (row in 0 until STORAGE_ROWS) {
            for (col in 0 until STORAGE_COLUMNS) {
                val slotIndex = RouterBlockEntity.STORAGE_SLOT_START + row * STORAGE_COLUMNS + col
                addSlot(Slot(routerInventory, slotIndex, STORAGE_START_X + col * 18, STORAGE_START_Y + row * 18))
            }
        }

        for (row in 0..2) {
            for (col in 0..8) {
                addSlot(Slot(playerInventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18))
            }
        }

        for (col in 0..8) {
            addSlot(Slot(playerInventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58))
        }
    }

    override fun quickMove(player: PlayerEntity, slotIndex: Int): ItemStack {
        val slot = slots.getOrNull(slotIndex) ?: return ItemStack.EMPTY
        if (!slot.hasStack()) return ItemStack.EMPTY

        val original = slot.stack
        val moved = original.copy()

        if (slotIndex < COMMAND_SLOT_COUNT) {
            if (!insertItem(original, COMMAND_SLOT_COUNT, slots.size, true)) {
                return ItemStack.EMPTY
            }
        } else {
            val inserted = when {
                isCommandCard(original) -> {
                    insertItem(original, 0, MODULE_SCREEN_SLOT_COUNT, false) ||
                        insertItem(original, STORAGE_SCREEN_SLOT_START, COMMAND_SLOT_COUNT, false)
                }
                original.item is AugmentItem -> {
                    insertItem(original, UPGRADE_SCREEN_SLOT_START, STORAGE_SCREEN_SLOT_START, false) ||
                        insertItem(original, STORAGE_SCREEN_SLOT_START, COMMAND_SLOT_COUNT, false)
                }
                else -> insertItem(original, STORAGE_SCREEN_SLOT_START, COMMAND_SLOT_COUNT, false)
            }
            if (!inserted) {
                return ItemStack.EMPTY
            }
        }

        if (original.isEmpty) {
            slot.stack = ItemStack.EMPTY
        } else {
            slot.markDirty()
        }

        return moved
    }

    override fun onButtonClick(player: PlayerEntity, id: Int): Boolean {
        if (player.world.isClient) return true

        return when {
            id in ACTION_EDIT_MODULE_BASE until ACTION_EDIT_MODULE_BASE + RouterBlockEntity.MODULE_SLOT_COUNT -> {
                val moduleIndex = id - ACTION_EDIT_MODULE_BASE
                openRoleEditor(player, RouterBlockEntity.MODULE_SLOT_START + moduleIndex)
            }

            id in ACTION_OPEN_POLICY_ROW_BASE until ACTION_OPEN_POLICY_ROW_BASE + RouterBlockEntity.MODULE_SLOT_COUNT -> {
                openPolicyRow(player, id - ACTION_OPEN_POLICY_ROW_BASE)
            }

            id in ACTION_OPEN_CREW_ROW_BASE until ACTION_OPEN_CREW_ROW_BASE + 9 -> {
                openCrewRow(player, id - ACTION_OPEN_CREW_ROW_BASE)
            }

            id in ACTION_CYCLE_CREW_MODE_BASE until ACTION_CYCLE_CREW_MODE_BASE + DYNAMIC_CREW_ACTION_LIMIT -> {
                cycleCrewMode(player, id - ACTION_CYCLE_CREW_MODE_BASE)
            }

            id in ACTION_TOGGLE_CREW_FALLBACK_BASE until ACTION_TOGGLE_CREW_FALLBACK_BASE + DYNAMIC_CREW_ACTION_LIMIT -> {
                toggleCrewFallback(player, id - ACTION_TOGGLE_CREW_FALLBACK_BASE)
            }

            id in ACTION_POLICY_QUICK_BASE until ACTION_POLICY_QUICK_BASE + POLICY_ACTION_STRIDE * 7 -> {
                handlePolicyQuickAction(player, id)
            }

            else -> false
        }
    }

    private fun cycleCrewMode(player: PlayerEntity, snapshotIndex: Int): Boolean {
        val pokemonId = resolveCrewPokemonId(player, snapshotIndex) ?: return false
        val current = TagAssignmentManager.getProfile(pokemonId)
        val nextMode = com.cobblepalsworld.pasture.WorkerAssignmentMode.entries[
            (current.mode.ordinal + 1) % com.cobblepalsworld.pasture.WorkerAssignmentMode.entries.size
        ]
        TagAssignmentManager.updateProfile(pokemonId, mode = nextMode)
        markAssignmentChange(player)
        return true
    }

    private fun toggleCrewFallback(player: PlayerEntity, snapshotIndex: Int): Boolean {
        val pokemonId = resolveCrewPokemonId(player, snapshotIndex) ?: return false
        val current = TagAssignmentManager.getProfile(pokemonId)
        TagAssignmentManager.updateProfile(pokemonId, allowFallback = !current.allowFallback)
        markAssignmentChange(player)
        return true
    }

    private fun resolveCrewPokemonId(player: PlayerEntity, snapshotIndex: Int): java.util.UUID? {
        val serverWorld = player.world as? ServerWorld ?: return null
        val router = routerInventory as? RouterBlockEntity ?: return null
        val pasture = router.linkedPasture(serverWorld) ?: return null
        val snapshot = PastureSnapshotFactory.create(serverWorld, pasture)
        return snapshot.pals.getOrNull(snapshotIndex)?.pokemonId
    }

    private fun handlePolicyQuickAction(player: PlayerEntity, id: Int): Boolean {
        val payload = id - ACTION_POLICY_QUICK_BASE
        val rowIndex = payload % POLICY_ACTION_STRIDE
        val action = payload / POLICY_ACTION_STRIDE

        return mutatePolicyRow(player, rowIndex) { spec ->
            when (action) {
                POLICY_ACTION_TOGGLE_WHITELIST -> spec.copy(filter = spec.filter.copy(whitelist = !spec.filter.whitelist))
                POLICY_ACTION_TOGGLE_NBT -> spec.copy(filter = spec.filter.copy(matchNbt = !spec.filter.matchNbt))
                POLICY_ACTION_CYCLE_MATCH -> spec.copy(filter = spec.filter.copy(matchMode = nextMatchMode(spec.filter.matchMode)))
                POLICY_ACTION_CYCLE_SIGNAL -> spec.copy(settings = spec.settings.copy(redstoneMode = nextRedstoneMode(spec.settings.redstoneMode)))
                POLICY_ACTION_CYCLE_TARGET -> spec.copy(settings = spec.settings.copy(targetStrategy = nextTargetStrategy(spec.settings.targetStrategy)))
                POLICY_ACTION_TOGGLE_RUN -> spec.copy(settings = spec.settings.copy(terminateAfterSuccess = !spec.settings.terminateAfterSuccess))
                POLICY_ACTION_CYCLE_REGULATOR -> spec.copy(settings = spec.settings.copy(regulatorAmount = nextRegulator(spec.settings.regulatorAmount)))
                else -> spec
            }
        }
    }

    private fun mutatePolicyRow(player: PlayerEntity, rowIndex: Int, transform: (TagSpec) -> TagSpec): Boolean {
        val moduleIndex = policyModules().getOrNull(rowIndex)?.moduleIndex ?: return false
        val inventorySlot = RouterBlockEntity.MODULE_SLOT_START + moduleIndex
        val stack = routerInventory.getStack(inventorySlot)
        val tagItem = stack.item as? TagItem ?: return false
        val registries = player.world.registryManager
        val original = TagItem.getSpec(stack, registries) ?: TagSpec(type = tagItem.tagType)
        val updated = transform(original)
        TagItem.setSpec(stack, updated, registries)
        (routerInventory as? RouterBlockEntity)?.markModuleSlotChanged(inventorySlot)
        routerInventory.markDirty()
        markAssignmentChange(player)
        return true
    }

    private fun nextMatchMode(mode: FilterMatchMode): FilterMatchMode {
        return FilterMatchMode.entries[(mode.ordinal + 1) % FilterMatchMode.entries.size]
    }

    private fun nextRedstoneMode(mode: RedstoneControlMode): RedstoneControlMode {
        return RedstoneControlMode.entries[(mode.ordinal + 1) % RedstoneControlMode.entries.size]
    }

    private fun nextTargetStrategy(strategy: TargetStrategy): TargetStrategy {
        return TargetStrategy.entries[(strategy.ordinal + 1) % TargetStrategy.entries.size]
    }

    private fun nextRegulator(current: Int): Int {
        val index = REGULATOR_PRESETS.indexOfFirst { it >= current }.let { if (it >= 0) it else REGULATOR_PRESETS.lastIndex }
        return REGULATOR_PRESETS[(index + 1) % REGULATOR_PRESETS.size]
    }

    private fun markAssignmentChange(player: PlayerEntity) {
        val serverWorld = player.world as? ServerWorld ?: return
        CobblePalsSaveData.markDirty(serverWorld)
        sendContentUpdates()
    }

    private fun openRoleEditor(player: PlayerEntity, inventorySlot: Int): Boolean {
        val stack = routerInventory.getStack(inventorySlot)
        if (stack.item !is TagItem) return false

        if (!player.world.isClient) {
            player.openHandledScreen(SimpleNamedScreenHandlerFactory(
                { syncId, inv, _ -> TagFilterScreenHandler(syncId, inv, routerInventory, inventorySlot) },
                Text.translatable("screen.cobblepalsworld.tag_filter")
            ))
        }
        return true
    }

    private fun openPolicyRow(player: PlayerEntity, rowIndex: Int): Boolean {
        val targetModuleIndex = policyModules().getOrNull(rowIndex)?.moduleIndex ?: return false
        return openRoleEditor(player, RouterBlockEntity.MODULE_SLOT_START + targetModuleIndex)
    }

    private fun openCrewRow(player: PlayerEntity, rowIndex: Int): Boolean {
        if (player.world.isClient) return true
        val serverWorld = player.world as? ServerWorld ?: return false
        val router = routerInventory as? RouterBlockEntity ?: return false
        val pasture = router.linkedPasture(serverWorld) ?: return false
        val snapshot = PastureSnapshotFactory.create(serverWorld, pasture)
        val pal = snapshot.pals.getOrNull(rowIndex) ?: return false

        player.openHandledScreen(SimpleNamedScreenHandlerFactory(
            { syncId, inv, _ -> PokemonTagScreenHandler(syncId, inv, pal.pokemonId) },
            Text.translatable("screen.cobblepalsworld.pokemon_tag")
        ))
        return true
    }

    private fun policyModules(): List<PolicyModuleTarget> {
        return (0 until RouterBlockEntity.MODULE_SLOT_COUNT)
            .mapNotNull { moduleIndex ->
                val stack = routerInventory.getStack(RouterBlockEntity.MODULE_SLOT_START + moduleIndex)
                val tagItem = stack.item as? TagItem ?: return@mapNotNull null
                PolicyModuleTarget(tagItem.tagType, moduleIndex)
            }
            .sortedBy { it.moduleIndex }
    }

    private data class PolicyModuleTarget(
        val tagType: TagType,
        val moduleIndex: Int
    )

    override fun canUse(player: PlayerEntity): Boolean = routerInventory.canPlayerUse(player)
}
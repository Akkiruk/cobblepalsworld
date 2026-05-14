package com.cobblepalsworld.gui.router

import com.cobblepalsworld.augment.AugmentItem
import com.cobblepalsworld.gui.assignment.PokemonTagScreenHandler
import com.cobblepalsworld.gui.filter.TagFilterScreenHandler
import com.cobblepalsworld.gui.MenuTypes
import com.cobblepalsworld.gui.pasture.PastureSnapshotFactory
import com.cobblepalsworld.router.RouterBlockEntity
import com.cobblepalsworld.tag.TagItem
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.TagTypePresentation
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
        const val BACKGROUND_WIDTH = 248
        const val BACKGROUND_HEIGHT = 414
        const val MODULE_COLUMNS = 3
        const val MODULE_ROWS = 3
        const val MODULE_START_X = 20
        const val MODULE_START_Y = 60
        const val BOOST_START_X = 80
        const val BOOST_START_Y = 150
        const val STORAGE_COLUMNS = 9
        const val STORAGE_ROWS = 3
        const val STORAGE_START_X = 16
        const val STORAGE_START_Y = 260
        const val PLAYER_INV_X = 16
        const val PLAYER_INV_Y = 330
        const val MODULE_SCREEN_SLOT_COUNT = RouterBlockEntity.MODULE_SLOT_COUNT
        const val UPGRADE_SCREEN_SLOT_START = MODULE_SCREEN_SLOT_COUNT
        const val STORAGE_SCREEN_SLOT_START = UPGRADE_SCREEN_SLOT_START + RouterBlockEntity.UPGRADE_SLOT_COUNT
        const val COMMAND_SLOT_COUNT = STORAGE_SCREEN_SLOT_START + RouterBlockEntity.STORAGE_SLOT_COUNT
        const val ACTION_EDIT_MODULE_BASE = 100
        const val ACTION_OPEN_POLICY_ROW_BASE = 200
        const val ACTION_OPEN_CREW_ROW_BASE = 300

        fun isCommandCard(stack: ItemStack): Boolean = stack.item is TagItem
    }

    constructor(syncId: Int, playerInventory: PlayerInventory) : super(MenuTypes.ROUTER.get(), syncId) {
        this.routerInventory = SimpleInventory(RouterBlockEntity.TOTAL_SLOTS)
        this.routerData = ArrayPropertyDelegate(7)
        setupSlots(playerInventory)
    }

    constructor(syncId: Int, playerInventory: PlayerInventory, routerInventory: Inventory, routerData: PropertyDelegate) : super(MenuTypes.ROUTER.get(), syncId) {
        checkSize(routerInventory, RouterBlockEntity.TOTAL_SLOTS)
        checkDataCount(routerData, 7)
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

            else -> false
        }
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
        val targetModuleIndex = groupedPolicyModules().getOrNull(rowIndex)?.moduleIndex ?: return false
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

    private fun groupedPolicyModules(): List<PolicyModuleTarget> {
        return (0 until RouterBlockEntity.MODULE_SLOT_COUNT)
            .mapNotNull { moduleIndex ->
                val stack = routerInventory.getStack(RouterBlockEntity.MODULE_SLOT_START + moduleIndex)
                val tagItem = stack.item as? TagItem ?: return@mapNotNull null
                moduleIndex to tagItem.tagType
            }
            .groupBy({ it.second }, { it.first })
            .entries
            .sortedWith(compareByDescending<Map.Entry<TagType, List<Int>>> { it.value.size }.thenBy { TagTypePresentation.roleLabel(it.key) })
            .map { (tagType, moduleIndices) -> PolicyModuleTarget(tagType, moduleIndices.first()) }
    }

    private data class PolicyModuleTarget(
        val tagType: TagType,
        val moduleIndex: Int
    )

    override fun canUse(player: PlayerEntity): Boolean = routerInventory.canPlayerUse(player)
}
package com.cobblepalsworld.router

import com.cobblepalsworld.augment.AugmentItem
import com.cobblepalsworld.augment.AugmentSet
import com.cobblepalsworld.augment.AugmentType
import com.cobblepalsworld.gui.router.RouterScreenHandler
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagItem
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SidedInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.registry.RegistryWrapper
import net.minecraft.screen.ArrayPropertyDelegate
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.PropertyDelegate
import net.minecraft.screen.ScreenHandler
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World
import java.util.UUID

class RouterBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(RouterRegistry.ROUTER_BLOCK_ENTITY.get(), pos, state), SidedInventory, NamedScreenHandlerFactory {
    companion object {
        const val BUFFER_SLOT = 0
        const val MODULE_SLOT_START = 1
        const val MODULE_SLOT_COUNT = 9
        const val MODULE_SLOT_END = MODULE_SLOT_START + MODULE_SLOT_COUNT
        const val UPGRADE_SLOT_START = MODULE_SLOT_END
        const val UPGRADE_SLOT_COUNT = 5
        const val UPGRADE_SLOT_END = UPGRADE_SLOT_START + UPGRADE_SLOT_COUNT
        const val TOTAL_SLOTS = UPGRADE_SLOT_END
        private val AUTOMATION_SLOTS = intArrayOf(BUFFER_SLOT)

        fun tick(world: World, pos: BlockPos, state: BlockState, blockEntity: RouterBlockEntity) {
            val serverWorld = world as? ServerWorld ?: return
            RouterExecutionEngine.tick(serverWorld, pos, state, blockEntity)
        }
    }

    private val inventory = SimpleInventory(TOTAL_SLOTS)
    private val propertyDelegate = object : PropertyDelegate {
        override fun get(index: Int): Int {
            val buffer = getStack(BUFFER_SLOT)
            return when (index) {
                0 -> if (buffer.isEmpty) 0 else buffer.count
                1 -> if (buffer.isEmpty) 64 else buffer.maxCount
                2 -> if (cachedState.get(net.minecraft.state.property.Properties.POWERED)) 1 else 0
                3 -> cooldownTicks
                else -> 0
            }
        }

        override fun set(index: Int, value: Int) {}

        override fun size(): Int = 4
    }

    private val pulseStates = BooleanArray(MODULE_SLOT_COUNT)
    private val targetCursor = IntArray(MODULE_SLOT_COUNT)

    var cooldownTicks: Int = 0
    private var ownerUuid: UUID? = null
    private var ownerName: String = ""

    override fun getDisplayName(): Text = Text.translatable("block.cobblepalsworld.router")

    override fun createMenu(syncId: Int, playerInventory: PlayerInventory, player: PlayerEntity): ScreenHandler {
        return RouterScreenHandler(syncId, playerInventory, this, propertyDelegate)
    }

    fun canAccess(player: PlayerEntity): Boolean {
        val owner = ownerUuid ?: return true
        return player.uuid == owner || player.hasPermissionLevel(2)
    }

    fun setOwner(player: PlayerEntity) {
        if (ownerUuid == null) {
            ownerUuid = player.uuid
            ownerName = player.name.string
            markDirty()
        }
    }

    fun ownerName(): String = ownerName

    fun ownerUuid(): UUID? = ownerUuid

    fun tagInModuleSlot(index: Int, registries: RegistryWrapper.WrapperLookup, augments: AugmentSet): TagInstance? {
        val stack = getStack(MODULE_SLOT_START + index)
        val item = stack.item as? TagItem ?: return null
        return TagInstance(
            type = item.tagType,
            filter = TagItem.getFilter(stack, registries),
            boundPos = TagItem.getBoundPos(stack),
            augments = augments,
            settings = TagItem.getSettings(stack)
        )
    }

    fun nextTargetIndex(moduleIndex: Int, targetCount: Int): Int {
        if (targetCount <= 0) return 0
        return targetCursor[moduleIndex] % targetCount
    }

    fun advanceTargetIndex(moduleIndex: Int, targetCount: Int) {
        if (targetCount > 1) {
            targetCursor[moduleIndex] = (targetCursor[moduleIndex] + 1) % targetCount
        }
    }

    fun lastPulseState(moduleIndex: Int): Boolean = pulseStates[moduleIndex]

    fun setPulseState(moduleIndex: Int, powered: Boolean) {
        pulseStates[moduleIndex] = powered
    }

    fun installedAugments(): AugmentSet {
        val levels = mutableMapOf<AugmentType, Int>()
        for (slot in UPGRADE_SLOT_START until UPGRADE_SLOT_END) {
            val augmentItem = getStack(slot).item as? AugmentItem ?: continue
            levels[augmentItem.augmentType] = (levels[augmentItem.augmentType] ?: 0) + getStack(slot).count
        }
        return AugmentSet(
            levels.map { (type, level) -> AugmentSet.AugmentEntry(type, level.coerceAtMost(type.maxLevel)) }
        )
    }

    fun updatePowered(powered: Boolean) {
        if (cachedState.get(net.minecraft.state.property.Properties.POWERED) == powered) return
        world?.setBlockState(pos, cachedState.with(net.minecraft.state.property.Properties.POWERED, powered), Block.NOTIFY_LISTENERS)
    }

    override fun writeNbt(nbt: NbtCompound, registries: RegistryWrapper.WrapperLookup) {
        super.writeNbt(nbt, registries)

        ownerUuid?.let { nbt.putUuid("Owner", it) }
        if (ownerName.isNotEmpty()) {
            nbt.putString("OwnerName", ownerName)
        }
        nbt.putInt("Cooldown", cooldownTicks)

        val items = NbtList()
        for (slot in 0 until TOTAL_SLOTS) {
            val stack = getStack(slot)
            if (stack.isEmpty) continue

            val entry = NbtCompound()
            entry.putByte("Slot", slot.toByte())
            entry.put("Item", stack.encodeAllowEmpty(registries))
            items.add(entry)
        }
        nbt.put("Items", items)
    }

    override fun readNbt(nbt: NbtCompound, registries: RegistryWrapper.WrapperLookup) {
        super.readNbt(nbt, registries)

        clear()
        ownerUuid = if (nbt.containsUuid("Owner")) nbt.getUuid("Owner") else null
        ownerName = nbt.getString("OwnerName")
        cooldownTicks = nbt.getInt("Cooldown")

        val items = nbt.getList("Items", 10)
        for (index in 0 until items.size) {
            val entry = items.getCompound(index)
            val slot = entry.getByte("Slot").toInt()
            if (slot !in 0 until TOTAL_SLOTS || !entry.contains("Item")) continue
            val decoded = ItemStack.fromNbt(registries, entry.get("Item")!!)
            decoded.ifPresent { inventory.setStack(slot, it) }
        }
    }

    override fun clear() {
        inventory.clear()
    }

    override fun size(): Int = TOTAL_SLOTS

    override fun isEmpty(): Boolean = (0 until TOTAL_SLOTS).all { getStack(it).isEmpty }

    override fun getStack(slot: Int): ItemStack = inventory.getStack(slot)

    override fun removeStack(slot: Int, amount: Int): ItemStack {
        val removed = inventory.removeStack(slot, amount)
        if (!removed.isEmpty) markDirty()
        return removed
    }

    override fun removeStack(slot: Int): ItemStack {
        val removed = inventory.removeStack(slot)
        if (!removed.isEmpty) markDirty()
        return removed
    }

    override fun setStack(slot: Int, stack: ItemStack) {
        inventory.setStack(slot, stack)
        if (!stack.isEmpty && stack.count > stack.maxCount) {
            stack.count = stack.maxCount
        }
        markDirty()
    }

    override fun canPlayerUse(player: PlayerEntity): Boolean {
        if (world?.getBlockEntity(pos) !== this) return false
        if (!canAccess(player)) return false
        return player.squaredDistanceTo(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) <= 64.0
    }

    override fun markDirty() {
        super.markDirty()
        world?.updateComparators(pos, cachedState.block)
    }

    override fun getAvailableSlots(side: Direction): IntArray = AUTOMATION_SLOTS

    override fun canInsert(slot: Int, stack: ItemStack, dir: Direction?): Boolean {
        return slot == BUFFER_SLOT && RouterScreenHandler.isBufferItem(stack)
    }

    override fun canExtract(slot: Int, stack: ItemStack, dir: Direction): Boolean = slot == BUFFER_SLOT
}
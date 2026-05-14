package com.cobblepalsworld.router

import com.cobblepalsworld.augment.AugmentItem
import com.cobblepalsworld.augment.AugmentSet
import com.cobblepalsworld.augment.AugmentType
import com.cobblepalsworld.behavior.state.StateManager
import com.cobblepalsworld.behavior.state.WorkerPhase
import com.cobblepalsworld.crew.CommandPostCrewLifecycle
import com.cobblepalsworld.crew.CommandPostCrewManager
import com.cobblepalsworld.gui.router.RouterScreenHandler
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.assignment.TagAssignmentManager
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagItem
import com.cobblepalsworld.tag.TagRegistry
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
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.PropertyDelegate
import net.minecraft.screen.ScreenHandler
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.world.World
import java.util.UUID

class RouterBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(RouterRegistry.ROUTER_BLOCK_ENTITY.get(), pos, state), SidedInventory, NamedScreenHandlerFactory {
    companion object {
        const val MODULE_SLOT_START = 0
        const val MODULE_SLOT_COUNT = 9
        const val MODULE_SLOT_END = MODULE_SLOT_START + MODULE_SLOT_COUNT
        const val UPGRADE_SLOT_START = MODULE_SLOT_END
        const val UPGRADE_SLOT_COUNT = 5
        const val UPGRADE_SLOT_END = UPGRADE_SLOT_START + UPGRADE_SLOT_COUNT
        const val STORAGE_SLOT_START = UPGRADE_SLOT_END
        const val STORAGE_SLOT_COUNT = 27
        const val STORAGE_SLOT_END = STORAGE_SLOT_START + STORAGE_SLOT_COUNT
        const val TOTAL_SLOTS = STORAGE_SLOT_END
        private val AUTOMATION_SLOTS = IntArray(STORAGE_SLOT_COUNT) { STORAGE_SLOT_START + it }

        fun tick(world: World, pos: BlockPos, state: BlockState, blockEntity: RouterBlockEntity) {
            val serverWorld = world as? ServerWorld ?: return
            RouterExecutionEngine.tick(serverWorld, pos, state, blockEntity)
        }
    }

    private val inventory = SimpleInventory(TOTAL_SLOTS)
    private val storageInventoryView: Inventory = RouterStorageInventory(this)
    private val propertyDelegate = object : PropertyDelegate {
        override fun get(index: Int): Int {
            return when (index) {
                0 -> if (nativeCrewCount() > 0) 1 else 0
                1 -> nativeCrewCount().takeIf { it > 0 } ?: linkedWorkerCount
                2 -> assignedWorkerCount
                3 -> activeWorkerCount
                4, 5, 6 -> 0
                in 7 until 7 + MODULE_SLOT_COUNT -> {
                    val moduleIndex = index - 7
                    if (assignedWorkers[moduleIndex] != null) 1 else 0
                }
                in 16 until 16 + MODULE_SLOT_COUNT -> {
                    val moduleIndex = index - 16
                    val pokemonId = assignedWorkers[moduleIndex]
                    if (pokemonId != null && StateManager.get(pokemonId)?.phase?.let { it != WorkerPhase.IDLE } == true) 1 else 0
                }
                25 -> pos.x
                26 -> pos.y
                27 -> pos.z
                else -> 0
            }
        }

        override fun set(index: Int, value: Int) {}

        override fun size(): Int = 28
    }

    var cooldownTicks: Int = 0
    private var ownerUuid: UUID? = null
    private var ownerName: String = ""
    private var dispatchCursor: Int = 0
    private var moduleExecutionCursor: Int = 0
    private val assignedWorkers = arrayOfNulls<UUID>(MODULE_SLOT_COUNT)
    private val moduleReadyTicks = LongArray(MODULE_SLOT_COUNT)
    private val moduleSearchCursors = IntArray(MODULE_SLOT_COUNT)
    private val moduleRoundRobinIndices = IntArray(MODULE_SLOT_COUNT)
    private val moduleLastWorkTicks = LongArray(MODULE_SLOT_COUNT) { Long.MIN_VALUE }
    private val moduleLastRedstonePower = BooleanArray(MODULE_SLOT_COUNT)
    private var linkedWorkerCount: Int = 0
    private var assignedWorkerCount: Int = 0
    private var activeWorkerCount: Int = 0

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

    fun ownerUuid(): UUID? = ownerUuid

    fun tagInModuleSlot(index: Int, registries: RegistryWrapper.WrapperLookup, augments: AugmentSet): TagInstance? {
        val stack = TagRegistry.normalizeInventorySlot(this, MODULE_SLOT_START + index)
        return TagItem.toTagInstance(stack, registries, augments = augments, controllerPos = pos.toImmutable())
    }

    fun storageInventory(): Inventory = storageInventoryView

    fun isStorageSlot(slot: Int): Boolean = slot in STORAGE_SLOT_START until STORAGE_SLOT_END

    fun installedAugments(): AugmentSet {
        val levels = mutableMapOf<AugmentType, Int>()
        for (slot in UPGRADE_SLOT_START until UPGRADE_SLOT_END) {
            val stack = getStack(slot)
            val augmentItem = stack.item as? AugmentItem ?: continue
            levels[augmentItem.augmentType] = (levels[augmentItem.augmentType] ?: 0) + stack.count
        }
        return AugmentSet(
            levels.map { (type, level) -> AugmentSet.AugmentEntry(type, level.coerceAtMost(type.maxLevel)) }
        )
    }

    fun assignedWorker(moduleIndex: Int): UUID? = assignedWorkers.getOrNull(moduleIndex)

    fun moduleExecutionStart(): Int = Math.floorMod(moduleExecutionCursor, MODULE_SLOT_COUNT)

    fun advanceModuleExecutionCursor(step: Int = 1) {
        moduleExecutionCursor = Math.floorMod(moduleExecutionCursor + step, MODULE_SLOT_COUNT)
    }

    fun isModuleReady(moduleIndex: Int, worldTime: Long): Boolean {
        if (moduleIndex !in 0 until MODULE_SLOT_COUNT) return false
        return moduleReadyTicks[moduleIndex] <= worldTime
    }

    fun setModuleReadyTick(moduleIndex: Int, readyTick: Long) {
        if (moduleIndex !in 0 until MODULE_SLOT_COUNT) return
        moduleReadyTicks[moduleIndex] = readyTick
    }

    fun moduleSearchCursor(moduleIndex: Int): Int {
        if (moduleIndex !in 0 until MODULE_SLOT_COUNT) return 0
        return moduleSearchCursors[moduleIndex]
    }

    fun setModuleSearchCursor(moduleIndex: Int, cursor: Int) {
        if (moduleIndex !in 0 until MODULE_SLOT_COUNT) return
        moduleSearchCursors[moduleIndex] = cursor
    }

    fun moduleRoundRobinIndex(moduleIndex: Int): Int {
        if (moduleIndex !in 0 until MODULE_SLOT_COUNT) return 0
        return moduleRoundRobinIndices[moduleIndex]
    }

    fun setModuleRoundRobinIndex(moduleIndex: Int, index: Int) {
        if (moduleIndex !in 0 until MODULE_SLOT_COUNT) return
        moduleRoundRobinIndices[moduleIndex] = index
    }

    fun lastModuleRedstonePower(moduleIndex: Int): Boolean {
        if (moduleIndex !in 0 until MODULE_SLOT_COUNT) return false
        return moduleLastRedstonePower[moduleIndex]
    }

    fun setLastModuleRedstonePower(moduleIndex: Int, powered: Boolean) {
        if (moduleIndex !in 0 until MODULE_SLOT_COUNT) return
        moduleLastRedstonePower[moduleIndex] = powered
    }

    fun markModuleWorked(moduleIndex: Int, worldTime: Long) {
        if (moduleIndex !in 0 until MODULE_SLOT_COUNT) return
        moduleLastWorkTicks[moduleIndex] = worldTime
    }

    fun wasModuleRecentlyActive(moduleIndex: Int, worldTime: Long, activeWindow: Long): Boolean {
        if (moduleIndex !in 0 until MODULE_SLOT_COUNT) return false
        val lastWorkTick = moduleLastWorkTicks[moduleIndex]
        return lastWorkTick != Long.MIN_VALUE && worldTime - lastWorkTick <= activeWindow
    }

    fun clearModuleRuntime(moduleIndex: Int) {
        if (moduleIndex !in 0 until MODULE_SLOT_COUNT) return
        moduleReadyTicks[moduleIndex] = 0L
        moduleSearchCursors[moduleIndex] = 0
        moduleRoundRobinIndices[moduleIndex] = 0
        moduleLastWorkTicks[moduleIndex] = Long.MIN_VALUE
        moduleLastRedstonePower[moduleIndex] = false
    }

    fun clearAllModuleRuntime() {
        moduleExecutionCursor = 0
        for (index in 0 until MODULE_SLOT_COUNT) {
            clearModuleRuntime(index)
        }
    }

    fun markModuleSlotChanged(slot: Int) {
        moduleIndexForSlot(slot)?.let { moduleIndex ->
            clearModuleRuntime(moduleIndex)
            cooldownTicks = 0
            markDirty()
        }
    }

    fun dispatchCursorStart(rosterSize: Int): Int {
        if (rosterSize <= 0) return 0
        return dispatchCursor % rosterSize
    }

    fun advanceDispatchCursor(rosterSize: Int) {
        if (rosterSize <= 0) return
        dispatchCursor = (dispatchCursor + 1) % rosterSize
    }

    fun setAssignedWorker(moduleIndex: Int, pokemonId: UUID?) {
        if (moduleIndex !in 0 until MODULE_SLOT_COUNT) return
        if (assignedWorkers[moduleIndex] == pokemonId) return
        assignedWorkers[moduleIndex] = pokemonId
        markDirty()
    }

    fun removeAssignedWorker(pokemonId: UUID) {
        var changed = false
        for (index in assignedWorkers.indices) {
            if (assignedWorkers[index] == pokemonId) {
                assignedWorkers[index] = null
                clearModuleRuntime(index)
                changed = true
            }
        }
        if (changed) {
            markDirty()
        }
    }

    fun clearAssignedWorkers() {
        var changed = false
        for (index in assignedWorkers.indices) {
            if (assignedWorkers[index] != null) {
                assignedWorkers[index] = null
                changed = true
            }
        }
        if (changed) {
            markDirty()
        }
    }

    fun comparatorOutput(): Int {
        if (assignedWorkerCount <= 0) return 0
        return MathHelper.ceil(assignedWorkerCount * 15.0f / MODULE_SLOT_COUNT.toFloat()).coerceIn(0, 15)
    }

    fun updateStatus(linked: Boolean, roster: Int, assigned: Int, active: Int) {
        val nextLinked = roster.coerceAtLeast(0)
        val nextAssigned = assigned.coerceAtLeast(0)
        val nextActive = active.coerceAtLeast(0)
        if (linkedWorkerCount == nextLinked && assignedWorkerCount == nextAssigned && activeWorkerCount == nextActive) {
            return
        }
        linkedWorkerCount = nextLinked
        assignedWorkerCount = nextAssigned
        activeWorkerCount = nextActive
        markDirty()
    }

    fun updatePowered(powered: Boolean) {
        if (cachedState.get(Properties.POWERED) == powered) return
        world?.setBlockState(pos, cachedState.with(Properties.POWERED, powered), Block.NOTIFY_LISTENERS)
    }

    fun clearControlledAssignments(world: ServerWorld) {
        val dimensionId = world.registryKey.value.toString()
        val cleanupPos = pos.toImmutable()
        val controlled = TagAssignmentManager.findControlledBy(dimensionId, pos)
        val nativeCrew = CommandPostCrewManager.findMembers(dimensionId, pos)
        var changed = false
        controlled.forEach { pokemonId ->
            com.cobblepalsworld.behavior.TagExecutionEngine.cleanup(pokemonId, world, cleanupPos)
            if (TagAssignmentManager.removeIfControlledBy(pokemonId, dimensionId, pos) != null) {
                changed = true
            }
        }
        nativeCrew.forEach { member ->
            CommandPostCrewLifecycle.recall(world, member, ownerUuid)
            com.cobblepalsworld.behavior.TagExecutionEngine.cleanup(member.pokemonId, world, cleanupPos)
            TagAssignmentManager.removeIfControlledBy(member.pokemonId, dimensionId, pos)
            if (CommandPostCrewManager.remove(member.pokemonId, dimensionId, pos) != null) {
                changed = true
            }
        }
        clearAssignedWorkers()
        clearAllModuleRuntime()
        updateStatus(false, 0, 0, 0)
        updatePowered(false)
        if (changed) {
            CobblePalsSaveData.markDirty(world)
        }
    }

    private fun nativeCrewCount(): Int {
        val serverWorld = world as? ServerWorld ?: return 0
        return CommandPostCrewManager.countAt(serverWorld.registryKey.value.toString(), pos)
    }

    override fun writeNbt(nbt: NbtCompound, registries: RegistryWrapper.WrapperLookup) {
        super.writeNbt(nbt, registries)

        ownerUuid?.let { nbt.putUuid("Owner", it) }
        if (ownerName.isNotEmpty()) {
            nbt.putString("OwnerName", ownerName)
        }
        nbt.putInt("Cooldown", cooldownTicks)
        nbt.putInt("DispatchCursor", dispatchCursor)

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
        dispatchCursor = nbt.getInt("DispatchCursor")
        assignedWorkers.fill(null)
        clearAllModuleRuntime()
        linkedWorkerCount = 0
        assignedWorkerCount = 0
        activeWorkerCount = 0

        val items = nbt.getList("Items", 10)
        for (index in 0 until items.size) {
            val entry = items.getCompound(index)
            val slot = entry.getByte("Slot").toInt()
            if (slot !in 0 until TOTAL_SLOTS || !entry.contains("Item")) continue
            val decoded = ItemStack.fromNbt(registries, entry.get("Item")!!)
            decoded.ifPresent { inventory.setStack(slot, TagRegistry.normalizeStack(it)) }
        }
    }

    override fun clear() {
        inventory.clear()
        clearAllModuleRuntime()
    }

    override fun size(): Int = TOTAL_SLOTS

    override fun isEmpty(): Boolean = (0 until TOTAL_SLOTS).all { getStack(it).isEmpty }

    override fun getStack(slot: Int): ItemStack = inventory.getStack(slot)

    override fun removeStack(slot: Int, amount: Int): ItemStack {
        val removed = inventory.removeStack(slot, amount)
        if (!removed.isEmpty) {
            markModuleSlotChanged(slot)
        }
        return removed
    }

    override fun removeStack(slot: Int): ItemStack {
        val removed = inventory.removeStack(slot)
        if (!removed.isEmpty) {
            markModuleSlotChanged(slot)
        }
        return removed
    }

    override fun setStack(slot: Int, stack: ItemStack) {
        val normalized = TagRegistry.normalizeStack(stack)
        inventory.setStack(slot, normalized)
        if (!normalized.isEmpty && normalized.count > normalized.maxCount) {
            normalized.count = normalized.maxCount
        }
        if (moduleIndexForSlot(slot) != null) {
            markModuleSlotChanged(slot)
        } else {
            markDirty()
        }
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

    override fun canInsert(slot: Int, stack: ItemStack, dir: Direction?): Boolean = isStorageSlot(slot)

    override fun canExtract(slot: Int, stack: ItemStack, dir: Direction): Boolean = isStorageSlot(slot)

    private fun moduleIndexForSlot(slot: Int): Int? {
        if (slot !in MODULE_SLOT_START until MODULE_SLOT_END) return null
        return slot - MODULE_SLOT_START
    }
}
package com.cobblepalsworld.router

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblepalsworld.augment.AugmentItem
import com.cobblepalsworld.augment.AugmentSet
import com.cobblepalsworld.augment.AugmentType
import com.cobblepalsworld.gui.router.RouterScreenHandler
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.pasture.TagAssignmentManager
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagItem
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
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
        // Slot 0 stays unused so saved router inventories keep the same layout after the Command Post rework.
        const val RESERVED_SLOT = 0
        const val MODULE_SLOT_START = 1
        const val MODULE_SLOT_COUNT = 9
        const val MODULE_SLOT_END = MODULE_SLOT_START + MODULE_SLOT_COUNT
        const val UPGRADE_SLOT_START = MODULE_SLOT_END
        const val UPGRADE_SLOT_COUNT = 5
        const val UPGRADE_SLOT_END = UPGRADE_SLOT_START + UPGRADE_SLOT_COUNT
        const val TOTAL_SLOTS = UPGRADE_SLOT_END
        private val AUTOMATION_SLOTS = intArrayOf()
        private const val LINK_RADIUS = 12
        private const val LINK_HEIGHT = 4

        fun tick(world: World, pos: BlockPos, state: BlockState, blockEntity: RouterBlockEntity) {
            val serverWorld = world as? ServerWorld ?: return
            RouterExecutionEngine.tick(serverWorld, pos, state, blockEntity)
        }
    }

    private val inventory = SimpleInventory(TOTAL_SLOTS)
    private val propertyDelegate = object : PropertyDelegate {
        override fun get(index: Int): Int {
            return when (index) {
                0 -> if (hasLinkedPasture()) 1 else 0
                1 -> linkedWorkerCount
                2 -> assignedWorkerCount
                3 -> activeWorkerCount
                else -> 0
            }
        }

        override fun set(index: Int, value: Int) {}

        override fun size(): Int = 4
    }

    var cooldownTicks: Int = 0
    private var ownerUuid: UUID? = null
    private var ownerName: String = ""
    private var linkedPastureDimension: String? = null
    private var linkedPasturePos: BlockPos? = null
    private var dispatchCursor: Int = 0
    private val assignedWorkers = arrayOfNulls<UUID>(MODULE_SLOT_COUNT)
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

    fun hasLinkedPasture(): Boolean = linkedPastureDimension != null && linkedPasturePos != null

    fun linkedPastureAnchor(): BlockPos = linkedPasturePos ?: pos

    fun linkedPastureLocation(): BlockPos? = linkedPasturePos?.toImmutable()

    fun linkedPasture(world: ServerWorld): PokemonPastureBlockEntity? {
        val dimensionId = linkedPastureDimension ?: return null
        val pasturePos = linkedPasturePos ?: return null
        if (dimensionId != world.registryKey.value.toString()) return null
        return world.getBlockEntity(pasturePos) as? PokemonPastureBlockEntity
    }

    fun relinkToNearbyPasture(world: World): Boolean {
        val nearest = findNearbyPasture(world)
        return if (nearest == null) {
            unlinkPasture()
            false
        } else {
            setLinkedPasture(world.registryKey.value.toString(), nearest.pos)
            true
        }
    }

    private fun findNearbyPasture(world: World): PokemonPastureBlockEntity? {
        var nearest: PokemonPastureBlockEntity? = null
        var nearestDistance = Double.MAX_VALUE
        for (candidatePos in BlockPos.iterateOutwards(pos, LINK_RADIUS, LINK_HEIGHT, LINK_RADIUS)) {
            val pasture = world.getBlockEntity(candidatePos) as? PokemonPastureBlockEntity ?: continue
            val distance = candidatePos.getSquaredDistance(pos)
            if (distance < nearestDistance) {
                nearest = pasture
                nearestDistance = distance
            }
        }
        return nearest
    }

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
        val nextLinked = if (linked) roster else 0
        val nextAssigned = if (linked) assigned else 0
        val nextActive = if (linked) active else 0
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
        val cleanupPos = linkedPastureAnchor()
        val controlled = TagAssignmentManager.findControlledBy(dimensionId, pos)
        var changed = false
        controlled.forEach { pokemonId ->
            if (TagAssignmentManager.removeIfControlledBy(pokemonId, dimensionId, pos) != null) {
                com.cobblepalsworld.behavior.TagExecutionEngine.cleanup(pokemonId, world, cleanupPos)
                changed = true
            }
        }
        clearAssignedWorkers()
        updateStatus(false, 0, 0, 0)
        updatePowered(false)
        if (changed) {
            CobblePalsSaveData.markDirty(world)
        }
    }

    fun unlinkPasture() {
        clearLinkedPasture()
    }

    private fun setLinkedPasture(dimensionId: String, pasturePos: BlockPos) {
        val immutablePos = pasturePos.toImmutable()
        if (linkedPastureDimension == dimensionId && linkedPasturePos == immutablePos) return
        linkedPastureDimension = dimensionId
        linkedPasturePos = immutablePos
        markDirty()
    }

    private fun clearLinkedPasture() {
        if (linkedPastureDimension == null && linkedPasturePos == null) return
        linkedPastureDimension = null
        linkedPasturePos = null
        markDirty()
    }

    override fun writeNbt(nbt: NbtCompound, registries: RegistryWrapper.WrapperLookup) {
        super.writeNbt(nbt, registries)

        ownerUuid?.let { nbt.putUuid("Owner", it) }
        if (ownerName.isNotEmpty()) {
            nbt.putString("OwnerName", ownerName)
        }
        nbt.putInt("Cooldown", cooldownTicks)
        nbt.putInt("DispatchCursor", dispatchCursor)

        linkedPastureDimension?.let { dimensionId ->
            nbt.putString("LinkedPastureDimension", dimensionId)
            linkedPasturePos?.let { pasturePos ->
                nbt.putInt("LinkedPastureX", pasturePos.x)
                nbt.putInt("LinkedPastureY", pasturePos.y)
                nbt.putInt("LinkedPastureZ", pasturePos.z)
            }
        }

        val assignedNbt = NbtList()
        assignedWorkers.forEachIndexed { index, pokemonId ->
            if (pokemonId == null) return@forEachIndexed
            val entry = NbtCompound()
            entry.putInt("Slot", index)
            entry.putUuid("Pokemon", pokemonId)
            assignedNbt.add(entry)
        }
        nbt.put("AssignedWorkers", assignedNbt)

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
        linkedPastureDimension = if (nbt.contains("LinkedPastureDimension")) nbt.getString("LinkedPastureDimension") else null
        linkedPasturePos = if (nbt.contains("LinkedPastureX")) {
            BlockPos(nbt.getInt("LinkedPastureX"), nbt.getInt("LinkedPastureY"), nbt.getInt("LinkedPastureZ"))
        } else {
            null
        }
        assignedWorkers.fill(null)
        val assignedNbt = nbt.getList("AssignedWorkers", 10)
        for (index in 0 until assignedNbt.size) {
            val entry = assignedNbt.getCompound(index)
            val slot = entry.getInt("Slot")
            if (slot !in 0 until MODULE_SLOT_COUNT || !entry.containsUuid("Pokemon")) continue
            assignedWorkers[slot] = entry.getUuid("Pokemon")
        }
        linkedWorkerCount = 0
        assignedWorkerCount = 0
        activeWorkerCount = 0

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

    override fun canInsert(slot: Int, stack: ItemStack, dir: Direction?): Boolean = false

    override fun canExtract(slot: Int, stack: ItemStack, dir: Direction): Boolean = false
}
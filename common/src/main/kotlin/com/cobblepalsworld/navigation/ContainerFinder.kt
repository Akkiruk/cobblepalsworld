package com.cobblepalsworld.navigation

import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.router.RouterBlockEntity
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.filter.FilterMatcher
import net.minecraft.block.ChestBlock
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.entity.vehicle.StorageMinecartEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World

object ContainerFinder {
    private fun searchPositions(origin: BlockPos, range: Int): Iterable<BlockPos> =
        BlockPos.iterateOutwards(origin, range, range, range)

    fun findClosest(world: World, origin: BlockPos, range: Int = 16): BlockPos? {
        return findClosestMatching(world, origin, range)
    }

    fun findClosestExcluding(world: World, origin: BlockPos, range: Int = 16, exclude: Set<BlockPos>): BlockPos? {
        return findClosestMatching(world, origin, range, exclude)
    }

    fun findClosestMatching(
        world: World,
        origin: BlockPos,
        range: Int = 16,
        exclude: Set<BlockPos> = emptySet(),
        predicate: (Inventory, BlockPos) -> Boolean = { _, _ -> true }
    ): BlockPos? {
        for (pos in searchPositions(origin, range)) {
            val immutablePos = pos.toImmutable()
            if (immutablePos in exclude) continue

            val inventory = getInventoryAt(world, immutablePos) ?: continue
            if (predicate(inventory, immutablePos)) {
                return immutablePos
            }
        }
        return null
    }

    fun findAllMatching(
        world: World,
        origin: BlockPos,
        range: Int = 16,
        exclude: Set<BlockPos> = emptySet(),
        limit: Int = Int.MAX_VALUE,
        predicate: (Inventory, BlockPos) -> Boolean = { _, _ -> true }
    ): List<BlockPos> {
        val matches = mutableListOf<BlockPos>()
        for (pos in searchPositions(origin, range)) {
            if (matches.size >= limit) break
            val immutablePos = pos.toImmutable()
            if (immutablePos in exclude) continue

            val inventory = getInventoryAt(world, immutablePos) ?: continue
            if (predicate(inventory, immutablePos)) {
                matches += immutablePos
            }
        }
        return matches
    }

    fun isContainer(world: World, pos: BlockPos): Boolean {
        return getInventoryAt(world, pos) != null
    }

    /**
     * Gets the inventory at [pos], properly combining both halves of a double chest.
     * For non-chest containers, returns the block entity inventory directly.
     */
    fun getInventoryAt(world: World, pos: BlockPos): Inventory? {
        val blockEntity = world.getBlockEntity(pos)
        if (blockEntity is RouterBlockEntity) {
            return blockEntity.storageInventory()
        }
        if (blockEntity is ChestBlockEntity) {
            val state = world.getBlockState(pos)
            val block = state.block
            if (block is ChestBlock) {
                return ChestBlock.getInventory(block, state, world, pos, false)
            }
        }
        return blockEntity as? Inventory
    }

    fun controllerBufferPos(world: World, tag: TagInstance): BlockPos? {
        val controllerPos = tag.controllerPos?.toImmutable() ?: return null
        return if (getInventoryAt(world, controllerPos) != null) controllerPos else null
    }

    fun hasSpace(inventory: Inventory): Boolean {
        for (slot in 0 until inventory.size()) {
            val stack = inventory.getStack(slot)
            if (stack.isEmpty || stack.count < stack.maxCount) {
                return true
            }
        }
        return false
    }

    fun findControllerFirstMatching(
        world: World,
        origin: BlockPos,
        tag: TagInstance,
        range: Int = 16,
        exclude: Set<BlockPos> = emptySet(),
        predicate: (Inventory, BlockPos) -> Boolean = { _, _ -> true }
    ): BlockPos? {
        val controllerPos = controllerBufferPos(world, tag)
        if (controllerPos != null && controllerPos !in exclude) {
            val inventory = getInventoryAt(world, controllerPos)
            if (inventory != null && predicate(inventory, controllerPos)) {
                return controllerPos
            }
        }

        return findClosestMatching(world, origin, range, exclude, predicate)
    }

    fun findControllerFirstCachedMatching(
        world: World,
        origin: BlockPos,
        tag: TagInstance,
        state: WorkerState,
        range: Int = 16,
        exclude: Set<BlockPos> = emptySet(),
        predicate: (Inventory, BlockPos) -> Boolean = { _, _ -> true }
    ): BlockPos? {
        val cacheTtl = ConfigManager.config.general.containerCacheTicks.toLong()
        val cachedPos = state.cachedSourceContainerPos
        if (cachedPos != null
            && cachedPos !in exclude
            && world.time - state.sourceContainerCacheTime < cacheTtl
        ) {
            val cachedInventory = getInventoryAt(world, cachedPos)
            if (cachedInventory != null && predicate(cachedInventory, cachedPos)) {
                return cachedPos
            }
        }

        val foundPos = findControllerFirstMatching(world, origin, tag, range, exclude, predicate)
        if (foundPos != null) {
            state.cachedSourceContainerPos = foundPos
            state.sourceContainerCacheTime = world.time
        } else if (cachedPos != null && world.time - state.sourceContainerCacheTime >= cacheTtl) {
            state.cachedSourceContainerPos = null
        }
        return foundPos
    }

    /**
     * Find the nearest entity with an inventory (e.g. minecart chests) within range.
     * Used by the PUSHING augment.
     */
    fun findClosestEntityInventory(world: World, origin: BlockPos, range: Int = 16): Inventory? {
        val box = Box(origin).expand(range.toDouble())
        return world.getEntitiesByClass(StorageMinecartEntity::class.java, box) { true }
            .minByOrNull { it.squaredDistanceTo(origin.x + 0.5, origin.y + 0.5, origin.z + 0.5) }
    }

    fun depositFromInventory(world: World, containerPos: BlockPos, source: Inventory) {
        val target = getInventoryAt(world, containerPos) ?: return
        transferItems(target, source)
        target.markDirty()
    }

    /**
     * Regulated deposit: only deposits enough of each item so the target reaches the
     * configured amount for that item across the whole inventory. Prevents overfilling.
     */
    fun depositRegulated(world: World, containerPos: BlockPos, source: Inventory, tag: TagInstance) {
        val target = getInventoryAt(world, containerPos) ?: return

        for (slot in 0 until source.size()) {
            val stack = source.getStack(slot)
            if (stack.isEmpty) continue

            // Count how many of this item already exist in target
            var existingCount = 0
            for (t in 0 until target.size()) {
                val targetStack = target.getStack(t)
                if (ItemStack.areItemsAndComponentsEqual(targetStack, stack)) {
                    existingCount += targetStack.count
                }
            }

            // Only deposit enough to reach the configured total across the destination.
            val desiredTotal = tag.settings.regulatorAmount.coerceAtLeast(1)
            val toDeposit = (desiredTotal - existingCount).coerceIn(0, stack.count)
            if (toDeposit <= 0) continue

            val depositStack = stack.copyWithCount(toDeposit)
            val remaining = insertStack(target, depositStack)
            val leftover = stack.count - toDeposit + remaining.count
            source.setStack(slot, if (leftover > 0) stack.copyWithCount(leftover) else ItemStack.EMPTY)
        }
        target.markDirty()
    }

    /**
     * Deposit into an entity inventory (e.g. minecart chest). Used by PUSHING augment.
     */
    fun depositIntoEntity(entityInventory: Inventory, source: Inventory) {
        transferItems(entityInventory, source)
        entityInventory.markDirty()
    }

    private fun transferItems(target: Inventory, source: Inventory) {
        for (slot in 0 until source.size()) {
            val stack = source.getStack(slot)
            if (stack.isEmpty) continue
            val remaining = insertStack(target, stack.copy())
            source.setStack(slot, remaining)
        }
    }

    fun insertStack(inventory: Inventory, stack: ItemStack): ItemStack {
        if (stack.isEmpty) return ItemStack.EMPTY
        var remaining = stack.copy()

        // Merge into existing stacks first
        for (i in 0 until inventory.size()) {
            val slot = inventory.getStack(i)
            if (ItemStack.areItemsAndComponentsEqual(slot, remaining) && slot.count < slot.maxCount) {
                val transfer = minOf(remaining.count, slot.maxCount - slot.count)
                slot.increment(transfer)
                remaining.decrement(transfer)
                inventory.setStack(i, slot)
                if (remaining.isEmpty) return ItemStack.EMPTY
            }
        }

        // Then fill empty slots
        for (i in 0 until inventory.size()) {
            if (inventory.getStack(i).isEmpty) {
                inventory.setStack(i, remaining)
                return ItemStack.EMPTY
            }
        }

        return remaining
    }
}

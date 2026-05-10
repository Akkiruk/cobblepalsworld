package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.CobblePalsWorldTags
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.inventory.PokemonInventory
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.pasture.PastureWorkerManager
import com.cobblepalsworld.platform.ActivatorPlatformBridge
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.filter.FilterMatcher
import net.minecraft.entity.ItemEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

/**
 * Deterministic fake-player activator.
 * Pulls matching items into the Pokemon carry buffer, then right-clicks one
 * exact bound target instead of scanning the surrounding world.
 */
object ActivatorBehavior : TagBehavior {
    override val tagType = TagType.ACTIVATOR
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range
    override val handlesOwnInventory = true
    override fun idleRetryTicks(tag: TagInstance, state: WorkerState): Long = 30L

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val hasUsableItems = hasUsableItems(InventoryManager.get(entity.pokemon.uuid), tag)

        val range = effectiveRange(tag, state)

        if (!hasUsableItems) {
            return ContainerFinder.findControllerFirstCachedMatching(world, origin, tag, state, range) { _, pos ->
                containerHasUsableItems(world, pos, tag)
            }
        }

        val boundTarget = tag.boundPos ?: return null
        return if (!ClaimManager.isClaimedByOther(boundTarget, entity.pokemon.uuid, world)) boundTarget else null
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        if (!hasUsableItems(InventoryManager.get(entity.pokemon.uuid), tag) && ContainerFinder.isContainer(world, target)) {
            return extractUsableItems(world, target, entity, tag, state)
        }

        val serverWorld = world as? ServerWorld ?: return WorkResult.Done()
        return if (runActivatorPass(serverWorld, entity, target, tag)) WorkResult.Done() else WorkResult.Repeat
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return target == tag.boundPos || ContainerFinder.isContainer(world, target)
    }

    private fun hasUsableItems(pokemonInv: PokemonInventory?, tag: TagInstance): Boolean {
        if (pokemonInv == null) return false
        return (0 until pokemonInv.size()).any { slot -> isUsableItem(pokemonInv.getStack(slot), tag) }
    }

    private fun isUsableItem(stack: ItemStack, tag: TagInstance): Boolean {
        if (stack.isEmpty) return false
        if (stack.isIn(CobblePalsWorldTags.Items.ACTIVATOR_BLACKLIST)) return false
        return FilterMatcher.matches(stack, tag.filter)
    }

    private fun runActivatorPass(world: ServerWorld, entity: PokemonEntity, target: BlockPos, tag: TagInstance): Boolean {
        val pokemonInv = InventoryManager.getOrCreate(entity.pokemon)
        val player = ActivatorPlatformBridge.fakePlayer(world)
        val sourceSlots = mutableListOf<Int>()

        for (slot in 0 until pokemonInv.size()) {
            if (isUsableItem(pokemonInv.getStack(slot), tag)) {
                sourceSlots += slot
            }
        }

        if (sourceSlots.isEmpty()) {
            return false
        }

        for (sourceSlot in sourceSlots) {
            val heldStack = pokemonInv.getStack(sourceSlot).copy()
            if (heldStack.isEmpty) continue

            prepareFakePlayer(player, entity, heldStack)

            if (tryUseOnBlock(world, entity, player, target, heldStack)) {
                syncFakePlayerResult(world, entity, pokemonInv, player, sourceSlot)
                return true
            }

            if (tryUseOnEntities(world, entity, player, target)) {
                syncFakePlayerResult(world, entity, pokemonInv, player, sourceSlot)
                return true
            }

            if (tryUseInAir(world, player, heldStack)) {
                syncFakePlayerResult(world, entity, pokemonInv, player, sourceSlot)
                return true
            }
        }

        return false
    }

    private fun tryUseOnBlock(
        world: ServerWorld,
        entity: PokemonEntity,
        player: ServerPlayerEntity,
        target: BlockPos,
        heldStack: ItemStack
    ): Boolean {
        val state = world.getBlockState(target)
        if (state.isAir) return false

        for (face in faceOrder(entity.blockPos, target)) {
            val hitPos = Vec3d.ofCenter(target)
            val hitResult = BlockHitResult(hitPos, face, target.toImmutable(), false)
            if (ActivatorPlatformBridge.useItemOnBlock(player, world, heldStack, hitResult)) {
                return true
            }
        }

        return false
    }

    private fun tryUseOnEntities(
        world: ServerWorld,
        entity: PokemonEntity,
        player: ServerPlayerEntity,
        target: BlockPos
    ): Boolean {
        val searchBox = Box(target).expand(1.5)
        val targetCenter = Vec3d.ofCenter(target)
        val targets = world.getOtherEntities(entity, searchBox) {
            it.isAlive && !it.type.isIn(CobblePalsWorldTags.EntityTypes.ACTIVATOR_INTERACT_BLACKLIST)
        }

        var nearestEntity: net.minecraft.entity.Entity? = null
        var nearestDistance = Double.MAX_VALUE
        for (candidate in targets) {
            val distance = candidate.squaredDistanceTo(targetCenter)
            if (distance < nearestDistance) {
                nearestEntity = candidate
                nearestDistance = distance
            }
        }

        if (nearestEntity != null && ActivatorPlatformBridge.interactEntity(player, nearestEntity)) {
                return true
        }

        return false
    }

    private fun tryUseInAir(world: ServerWorld, player: ServerPlayerEntity, heldStack: ItemStack): Boolean {
        return ActivatorPlatformBridge.useItem(player, world, heldStack)
    }

    private fun prepareFakePlayer(player: ServerPlayerEntity, entity: PokemonEntity, heldStack: ItemStack) {
        clearFakePlayerInventory(player)
        player.inventory.selectedSlot = 0
        player.refreshPositionAndAngles(entity.x, entity.y, entity.z, entity.yaw, entity.pitch)
        player.setStackInHand(Hand.MAIN_HAND, heldStack)
    }

    private fun clearFakePlayerInventory(player: ServerPlayerEntity) {
        for (slot in 0 until player.inventory.size()) {
            player.inventory.setStack(slot, ItemStack.EMPTY)
        }
    }

    private fun syncFakePlayerResult(
        world: ServerWorld,
        entity: PokemonEntity,
        pokemonInv: PokemonInventory,
        player: ServerPlayerEntity,
        sourceSlot: Int
    ) {
        val selectedSlot = player.inventory.selectedSlot
        val mainHandResult = player.getStackInHand(Hand.MAIN_HAND).copy()
        var changed = false
        pokemonInv.setStack(sourceSlot, mainHandResult)
        changed = true

        for (slot in 0 until player.inventory.size()) {
            if (slot == selectedSlot) continue
            val extra = player.inventory.getStack(slot)
            if (extra.isEmpty) continue

            val remainder = pokemonInv.insertStack(extra.copy())
            if (!remainder.isEmpty) {
                world.spawnEntity(
                    ItemEntity(
                        world,
                        entity.x,
                        entity.y + 0.5,
                        entity.z,
                        remainder
                    )
                )
            }
            player.inventory.setStack(slot, ItemStack.EMPTY)
            changed = true
        }

        if (changed) {
            PastureWorkerManager.markDirtyNow(world)
        }
    }

    private fun faceOrder(origin: BlockPos, target: BlockPos): List<Direction> {
        val primary = Direction.getFacing(
            (origin.x - target.x).toDouble(),
            (origin.y - target.y).toDouble(),
            (origin.z - target.z).toDouble()
        )
        return Direction.entries.sortedBy { direction -> if (direction == primary) 0 else 1 }
    }

    private fun extractUsableItems(
        world: World, target: BlockPos, entity: PokemonEntity, tag: TagInstance, state: WorkerState
    ): WorkResult {
        val container = ContainerFinder.getInventoryAt(world, target) ?: return WorkResult.Done()
        val pokemonInv = InventoryManager.getOrCreate(entity.pokemon)
        val maxItems = effectiveMaxItems(tag, state)
        var extracted = 0

        for (slot in 0 until container.size()) {
            if (extracted >= maxItems) break
            val stack = container.getStack(slot)
            if (!isUsableItem(stack, tag)) continue

            val requested = stack.copyWithCount(minOf(stack.count, maxItems - extracted))
            val remainder = pokemonInv.insertStack(requested)
            val inserted = requested.count - remainder.count
            if (inserted <= 0) continue

            stack.decrement(inserted)
            if (stack.isEmpty) container.setStack(slot, ItemStack.EMPTY)
            extracted += inserted
        }
        container.markDirty()
        if (extracted > 0) {
            PastureWorkerManager.markDirtyNow(world)
        }
        return WorkResult.Done()
    }

    private fun containerHasUsableItems(world: World, pos: BlockPos, tag: TagInstance): Boolean {
        val container = ContainerFinder.getInventoryAt(world, pos) ?: return false
        for (slot in 0 until container.size()) {
            val stack = container.getStack(slot)
            if (isUsableItem(stack, tag)) return true
        }
        return false
    }
}

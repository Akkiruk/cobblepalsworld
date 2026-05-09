package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.CobblePalsWorldTags
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.inventory.PokemonInventory
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.platform.ActivatorPlatformBridge
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.filter.FilterMatcher
import net.minecraft.block.Block
import net.minecraft.entity.Entity
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.item.HoeItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.ShovelItem
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.EnumSet

/**
 * Modular Routers-style activator behavior.
 * Pulls any non-blacklisted items from a source container, then uses a fake player to
 * right-click nearby blocks, entities, or the air from the Pokémon's current position.
 */
object ActivatorBehavior : TagBehavior {
    override val tagType = TagType.ACTIVATOR
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range
    override val handlesOwnInventory = true

    private val runtimeItemBlacklist = mutableSetOf<Item>()
    private val runtimeBlockBlacklist = mutableSetOf<Block>()

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val pokemonInv = InventoryManager.get(entity.pokemon.uuid)
        val hasUsableItems = pokemonInv != null && (0 until pokemonInv.size()).any { slot ->
            val stack = pokemonInv.getStack(slot)
            isUsableItem(stack, tag)
        }

        val canUseEmptyHand = allowsEmptyHand(tag)

        val range = effectiveRange(tag, state)

        // Phase 1: If no usable items and no empty-hand mode, find a source container.
        if (!hasUsableItems && !canUseEmptyHand) {
            val boundPos = tag.boundPos
            if (boundPos != null) {
                return if (ContainerFinder.isContainer(world, boundPos)
                    && containerHasUsableItems(world, boundPos, tag)
                ) boundPos else null
            }

            return BlockPos.iterateOutwards(origin, range, range / 2, range)
                .firstOrNull { pos ->
                    ContainerFinder.isContainer(world, pos) && containerHasUsableItems(world, pos, tag)
                }?.toImmutable()
        }

        // Phase 2: Behave like a stationary router once stocked.
        return if (!ClaimManager.isClaimedByOther(origin, entity.pokemon.uuid, world)) origin else null
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        if (ContainerFinder.isContainer(world, target)) {
            return extractUsableItems(world, target, entity, tag, state)
        }

        val serverWorld = world as? ServerWorld ?: return WorkResult.Done()
        return if (runActivatorPass(serverWorld, entity, tag, effectiveRange(tag, state))) WorkResult.Done() else WorkResult.Repeat
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return true
    }

    private fun isUsableItem(stack: ItemStack, tag: TagInstance): Boolean {
        if (stack.isEmpty) return false
        if (stack.item in runtimeItemBlacklist) return false
        if (stack.isIn(CobblePalsWorldTags.Items.ACTIVATOR_BLACKLIST)) return false
        return FilterMatcher.matches(stack, tag.filter)
    }

    private fun allowsEmptyHand(tag: TagInstance): Boolean {
        return tag.filter.isEmpty() && !tag.filter.whitelist
    }

    private fun runActivatorPass(world: ServerWorld, entity: PokemonEntity, tag: TagInstance, range: Int): Boolean {
        val pokemonInv = InventoryManager.getOrCreate(entity.pokemon)
        val player = ActivatorPlatformBridge.fakePlayer(world)
        val sourceSlots = mutableListOf<Int?>()

        for (slot in 0 until pokemonInv.size()) {
            if (isUsableItem(pokemonInv.getStack(slot), tag)) {
                sourceSlots += slot
            }
        }

        if (allowsEmptyHand(tag)) {
            sourceSlots += null
        }

        if (sourceSlots.isEmpty()) {
            return false
        }

        for (sourceSlot in sourceSlots) {
            val heldStack = sourceSlot?.let { pokemonInv.getStack(it).copy() } ?: ItemStack.EMPTY
            if (!heldStack.isEmpty && heldStack.item in runtimeItemBlacklist) continue

            prepareFakePlayer(player, entity, heldStack)

            if (tryUseOnBlocks(world, entity, player, heldStack, range)) {
                syncFakePlayerResult(world, entity, pokemonInv, player, sourceSlot)
                return true
            }

            if (tryUseOnEntities(world, entity, player, heldStack, range)) {
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

    private fun tryUseOnBlocks(
        world: ServerWorld,
        entity: PokemonEntity,
        player: ServerPlayerEntity,
        heldStack: ItemStack,
        range: Int
    ): Boolean {
        val origin = entity.blockPos

        for (pos in BlockPos.iterateOutwards(origin, range, maxOf(1, range / 2), range)) {
            val state = world.getBlockState(pos)
            if (state.isAir || state.block in runtimeBlockBlacklist) continue

            for (face in faceOrder(entity.blockPos, pos)) {
                val hitPos = Vec3d.ofCenter(pos)
                val hitResult = BlockHitResult(hitPos, face, pos.toImmutable(), false)
                try {
                    if (ActivatorPlatformBridge.useItemOnBlock(player, world, heldStack, hitResult)) {
                        return true
                    }
                } catch (exception: Exception) {
                    blacklistBlockUseFailure(heldStack, state.block, exception)
                    break
                }
            }
        }

        return false
    }

    private fun tryUseOnEntities(
        world: ServerWorld,
        entity: PokemonEntity,
        player: ServerPlayerEntity,
        heldStack: ItemStack,
        range: Int
    ): Boolean {
        val searchBox = Box(entity.blockPos).expand(range.toDouble())
        val targets = world.getOtherEntities(entity, searchBox) {
            it.isAlive && !it.type.isIn(CobblePalsWorldTags.EntityTypes.ACTIVATOR_INTERACT_BLACKLIST)
        }.sortedBy { it.squaredDistanceTo(entity) }

        for (target in targets) {
            try {
                if (ActivatorPlatformBridge.interactEntity(player, target)) {
                    return true
                }
            } catch (exception: Exception) {
                blacklistItemFailure(heldStack, exception)
                return false
            }
        }

        return false
    }

    private fun tryUseInAir(world: ServerWorld, player: ServerPlayerEntity, heldStack: ItemStack): Boolean {
        return try {
            ActivatorPlatformBridge.useItem(player, world, heldStack)
        } catch (exception: Exception) {
            blacklistItemFailure(heldStack, exception)
            false
        }
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
        sourceSlot: Int?
    ) {
        val selectedSlot = player.inventory.selectedSlot
        val mainHandResult = player.getStackInHand(Hand.MAIN_HAND).copy()
        if (sourceSlot != null) {
            pokemonInv.setStack(sourceSlot, mainHandResult)
        }

        for (slot in 0 until player.inventory.size()) {
            if (slot == selectedSlot) continue
            val extra = player.inventory.getStack(slot)
            if (extra.isEmpty) continue

            val remainder = insertIntoPokemonInventory(pokemonInv, extra.copy())
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
        }
    }

    private fun insertIntoPokemonInventory(inventory: PokemonInventory, stack: ItemStack): ItemStack {
        val remaining = stack.copy()

        for (slot in 0 until inventory.size()) {
            val existing = inventory.getStack(slot)
            if (existing.isEmpty) continue
            if (!ItemStack.areItemsAndComponentsEqual(existing, remaining)) continue

            val transferable = minOf(existing.maxCount - existing.count, remaining.count)
            if (transferable <= 0) continue
            existing.increment(transferable)
            remaining.decrement(transferable)
            if (remaining.isEmpty) return ItemStack.EMPTY
        }

        for (slot in 0 until inventory.size()) {
            if (!inventory.getStack(slot).isEmpty) continue
            inventory.setStack(slot, remaining.copy())
            return ItemStack.EMPTY
        }

        return remaining
    }

    private fun blacklistItemFailure(stack: ItemStack, exception: Exception) {
        if (stack.isEmpty) return
        runtimeItemBlacklist += stack.item
        CobblePalsWorld.LOGGER.error("Activator item {} threw while being used. Blacklisting it until restart.", stack.item, exception)
    }

    private fun blacklistBlockUseFailure(stack: ItemStack, block: Block, exception: Exception) {
        if (!stack.isEmpty) {
            runtimeItemBlacklist += stack.item
        }
        runtimeBlockBlacklist += block
        CobblePalsWorld.LOGGER.error("Activator use on block {} with item {} threw. Blacklisting both until restart.", block, stack.item, exception)
    }

    private fun faceOrder(origin: BlockPos, target: BlockPos): List<Direction> {
        val primary = Direction.getFacing(
            (origin.x - target.x).toDouble(),
            (origin.y - target.y).toDouble(),
            (origin.z - target.z).toDouble()
        )
        val faces = EnumSet.allOf(Direction::class.java)
        faces.remove(primary)
        return buildList {
            add(primary)
            addAll(faces)
        }
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

            val freeSlot = (0 until pokemonInv.size()).firstOrNull { pokemonInv.getStack(it).isEmpty } ?: break
            val toTake = minOf(stack.count, maxItems - extracted)
            pokemonInv.setStack(freeSlot, stack.copyWithCount(toTake))
            stack.decrement(toTake)
            if (stack.isEmpty) container.setStack(slot, ItemStack.EMPTY)
            extracted += toTake
        }
        container.markDirty()
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

package com.cobblepalsworld.router

import com.cobblepalsworld.augment.AugmentSet
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.behaviors.ActivatorBehavior
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.platform.ActivatorPlatformBridge
import com.cobblepalsworld.tag.ActivatorActionMode
import com.cobblepalsworld.tag.RedstoneControlMode
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagTarget
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.TargetStrategy
import com.cobblepalsworld.tag.filter.FilterMatcher
import net.minecraft.entity.ExperienceOrbEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.block.Block
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.roundToInt

object RouterExecutionEngine {
    private const val BASE_COOLDOWN = 40

    fun tick(world: ServerWorld, pos: BlockPos, state: net.minecraft.block.BlockState, router: RouterBlockEntity) {
        var detectorOutput = false

        if (router.cooldownTicks > 0) {
            router.cooldownTicks -= 1
            detectorOutput = hasDetectorMatch(world, pos, router)
            router.updatePowered(detectorOutput)
            return
        }

        val augments = router.installedAugments()
        val registries = world.registryManager
        var acted = false

        for (moduleIndex in 0 until RouterBlockEntity.MODULE_SLOT_COUNT) {
            val tag = router.tagInModuleSlot(moduleIndex, registries, augments) ?: continue

            if (tag.type == TagType.LOOKOUT) {
                detectorOutput = detectorOutput || executeLookout(world, pos, router, tag)
                if (tag.settings.terminateAfterSuccess && detectorOutput) {
                    break
                }
                continue
            }

            if (!passesRedstoneGate(world, pos, router, moduleIndex, tag)) continue

            val didAct = when (tag.type) {
                TagType.COURIER -> executeCourier(world, pos, router, moduleIndex, tag)
                TagType.STASHER -> executeStasher(world, pos, router, moduleIndex, tag)
                TagType.DROPPER -> executeDropper(world, pos, router, tag)
                TagType.FLINGER -> executeFlinger(world, pos, state, router, tag)
                TagType.VOID -> executeVoid(world, pos, router, tag)
                TagType.PLAYER -> executePlayer(world, pos, router, tag)
                TagType.PLANTER -> executePlacer(world, pos, router, tag)
                TagType.ACTIVATOR -> executeActivator(world, pos, router, tag)
                TagType.VACUUM -> executeVacuum(world, pos, router, tag)
                else -> false
            }

            if (didAct) {
                acted = true
                if (tag.settings.terminateAfterSuccess) {
                    break
                }
            }
        }

        router.updatePowered(detectorOutput)
        router.cooldownTicks = effectiveCooldown(augments)
        if (acted) {
            router.markDirty()
        }
    }

    private fun effectiveCooldown(augments: AugmentSet): Int {
        return (BASE_COOLDOWN * augments.speedMultiplier()).roundToInt().coerceAtLeast(4)
    }

    private fun effectiveRange(type: TagType, augments: AugmentSet): Int {
        return when (type) {
            TagType.COURIER, TagType.STASHER, TagType.PLAYER, TagType.LOOKOUT -> 16 + augments.extraRange()
            TagType.ACTIVATOR, TagType.VACUUM, TagType.PLANTER -> 8 + augments.extraRange()
            else -> 8 + augments.extraRange()
        }
    }

    private fun passesRedstoneGate(world: ServerWorld, pos: BlockPos, router: RouterBlockEntity, moduleIndex: Int, tag: TagInstance): Boolean {
        val powered = world.isReceivingRedstonePower(pos)
        val shouldRun = when (tag.settings.redstoneMode) {
            RedstoneControlMode.ALWAYS -> true
            RedstoneControlMode.HIGH -> powered
            RedstoneControlMode.LOW -> !powered
            RedstoneControlMode.NEVER -> false
            RedstoneControlMode.PULSE -> powered && !router.lastPulseState(moduleIndex)
        }
        router.setPulseState(moduleIndex, powered)
        return shouldRun
    }

    private fun executeCourier(world: ServerWorld, pos: BlockPos, router: RouterBlockEntity, moduleIndex: Int, tag: TagInstance): Boolean {
        val buffer = router.getStack(RouterBlockEntity.BUFFER_SLOT)
        return if (buffer.isEmpty) {
            val source = findSourceContainer(world, pos, tag, effectiveRange(tag.type, tag.augments)) ?: return false
            moveFromContainerToBuffer(world, router, source, tag.filter)
        } else {
            depositBufferToTargets(world, pos, router, moduleIndex, tag, exclude = emptySet())
        }
    }

    private fun executeStasher(world: ServerWorld, pos: BlockPos, router: RouterBlockEntity, moduleIndex: Int, tag: TagInstance): Boolean {
        val buffer = router.getStack(RouterBlockEntity.BUFFER_SLOT)
        val source = tag.boundPos ?: return false
        return if (buffer.isEmpty) {
            moveFromContainerToBuffer(world, router, source, tag.filter)
        } else {
            depositBufferToTargets(world, pos, router, moduleIndex, tag, exclude = setOf(source))
        }
    }

    private fun executeDropper(world: ServerWorld, pos: BlockPos, router: RouterBlockEntity, tag: TagInstance): Boolean {
        val buffer = router.getStack(RouterBlockEntity.BUFFER_SLOT)
        if (buffer.isEmpty) {
            val source = findSourceContainer(world, pos, tag, effectiveRange(tag.type, tag.augments), exclude = setOfNotNull(tag.boundPos)) ?: return false
            return moveFromContainerToBuffer(world, router, source, tag.filter)
        }

        val dropPos = tag.boundPos ?: pos
        val dropped = buffer.copy()
        val itemEntity = ItemEntity(world, dropPos.x + 0.5, dropPos.y + 0.5, dropPos.z + 0.5, dropped)
        itemEntity.setVelocity(0.0, 0.0, 0.0)
        world.spawnEntity(itemEntity)
        router.setStack(RouterBlockEntity.BUFFER_SLOT, ItemStack.EMPTY)
        return true
    }

    private fun executePlayer(world: ServerWorld, pos: BlockPos, router: RouterBlockEntity, tag: TagInstance): Boolean {
        val buffer = router.getStack(RouterBlockEntity.BUFFER_SLOT)
        return if (buffer.isEmpty) {
            val source = findSourceContainer(world, pos, tag, effectiveRange(tag.type, tag.augments)) ?: return false
            moveFromContainerToBuffer(world, router, source, tag.filter)
        } else {
            val ownerId = router.ownerUuid() ?: return false
            val player = world.server.playerManager.getPlayer(ownerId)
                ?.takeIf { it.blockPos.getSquaredDistance(pos) <= effectiveRange(tag.type, tag.augments).toDouble() * effectiveRange(tag.type, tag.augments) }
                ?: return false
            val remaining = ContainerFinder.insertStack(player.inventory, buffer.copy())
            val moved = remaining.count != buffer.count
            if (moved) {
                router.setStack(RouterBlockEntity.BUFFER_SLOT, remaining)
                player.inventory.markDirty()
                player.currentScreenHandler.sendContentUpdates()
            }
            moved
        }
    }

    private fun executeVoid(world: ServerWorld, pos: BlockPos, router: RouterBlockEntity, tag: TagInstance): Boolean {
        val buffer = router.getStack(RouterBlockEntity.BUFFER_SLOT)
        return if (buffer.isEmpty) {
            val source = findSourceContainer(world, pos, tag, effectiveRange(tag.type, tag.augments)) ?: return false
            moveFromContainerToBuffer(world, router, source, tag.filter)
        } else {
            router.setStack(RouterBlockEntity.BUFFER_SLOT, ItemStack.EMPTY)
            true
        }
    }

    private fun executeFlinger(world: ServerWorld, pos: BlockPos, state: net.minecraft.block.BlockState, router: RouterBlockEntity, tag: TagInstance): Boolean {
        val buffer = router.getStack(RouterBlockEntity.BUFFER_SLOT)
        return if (buffer.isEmpty) {
            val source = findSourceContainer(world, pos, tag, effectiveRange(tag.type, tag.augments), exclude = setOfNotNull(tag.boundPos)) ?: return false
            moveFromContainerToBuffer(world, router, source, tag.filter)
        } else {
            val launched = buffer.copy()
            val entity = ItemEntity(world, pos.x + 0.5, pos.y + 0.75, pos.z + 0.5, launched)
            val direction = if (tag.boundPos != null && tag.boundPos != pos) {
                Vec3d(
                    tag.boundPos.x + 0.5 - (pos.x + 0.5),
                    tag.boundPos.y + 0.5 - (pos.y + 0.75),
                    tag.boundPos.z + 0.5 - (pos.z + 0.5)
                ).normalize()
            } else {
                Vec3d.of(state.get(net.minecraft.state.property.Properties.HORIZONTAL_FACING).vector).normalize()
            }
            entity.setVelocity(direction.multiply(0.9).add(0.0, 0.15, 0.0))
            world.spawnEntity(entity)
            router.setStack(RouterBlockEntity.BUFFER_SLOT, ItemStack.EMPTY)
            true
        }
    }

    private fun executePlacer(world: ServerWorld, pos: BlockPos, router: RouterBlockEntity, tag: TagInstance): Boolean {
        val buffer = router.getStack(RouterBlockEntity.BUFFER_SLOT)
        return if (buffer.isEmpty) {
            val source = findSourceContainer(world, pos, tag, effectiveRange(tag.type, tag.augments), exclude = setOfNotNull(tag.boundPos)) ?: return false
            moveFromContainerToBuffer(world, router, source, tag.filter) { it.item is BlockItem }
        } else {
            val blockItem = buffer.item as? BlockItem ?: return false
            val targetPos = findPlacementTarget(world, pos, tag.boundPos ?: pos, effectiveRange(tag.type, tag.augments)) ?: return false
            val state = blockItem.block.defaultState
            if (!state.canPlaceAt(world, targetPos)) return false
            world.setBlockState(targetPos, state, Block.NOTIFY_ALL)
            val updated = buffer.copy().apply { decrement(1) }
            router.setStack(RouterBlockEntity.BUFFER_SLOT, if (updated.isEmpty) ItemStack.EMPTY else updated)
            true
        }
    }

    private fun executeActivator(world: ServerWorld, pos: BlockPos, router: RouterBlockEntity, tag: TagInstance): Boolean {
        val player = ActivatorPlatformBridge.fakePlayer(world)
        player.refreshPositionAndAngles(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5, 0f, 0f)

        val buffer = router.getStack(RouterBlockEntity.BUFFER_SLOT)
        player.setStackInHand(Hand.MAIN_HAND, buffer.copy())

        val targetPos = tag.boundPos
        if (targetPos != null && tag.settings.activatorMode != ActivatorActionMode.ATTACK_ONLY) {
            val hit = BlockHitResult(Vec3d.ofCenter(targetPos), Direction.UP, targetPos, false)
            if (ActivatorPlatformBridge.useItemOnBlock(player, world, player.getStackInHand(Hand.MAIN_HAND), hit)) {
                syncActivatorResult(router, player)
                return true
            }
        }

        val range = effectiveRange(tag.type, tag.augments)
        val entityBox = Box(pos).expand(range.toDouble())
        val targetEntity = world.getOtherEntities(null, entityBox)
            .filterIsInstance<LivingEntity>()
            .firstOrNull()

        if (targetEntity != null) {
            val interacted = when (tag.settings.activatorMode) {
                ActivatorActionMode.INTERACT_ONLY -> ActivatorPlatformBridge.interactEntity(player, targetEntity)
                ActivatorActionMode.ATTACK_ONLY -> ActivatorPlatformBridge.attackEntity(player, targetEntity)
                ActivatorActionMode.INTERACT_THEN_ATTACK -> {
                    ActivatorPlatformBridge.interactEntity(player, targetEntity) || ActivatorPlatformBridge.attackEntity(player, targetEntity)
                }
            }
            if (interacted) {
                syncActivatorResult(router, player)
                return true
            }
        }

        if (tag.settings.activatorMode != ActivatorActionMode.ATTACK_ONLY) {
            val usedInAir = ActivatorPlatformBridge.useItem(player, world, player.getStackInHand(Hand.MAIN_HAND))
            if (usedInAir) {
                syncActivatorResult(router, player)
                return true
            }
        }

        return false
    }

    private fun syncActivatorResult(router: RouterBlockEntity, player: ServerPlayerEntity) {
        router.setStack(RouterBlockEntity.BUFFER_SLOT, player.getStackInHand(Hand.MAIN_HAND).copy())
    }

    private fun executeVacuum(world: ServerWorld, pos: BlockPos, router: RouterBlockEntity, tag: TagInstance): Boolean {
        val range = effectiveRange(tag.type, tag.augments)
        val buffer = router.getStack(RouterBlockEntity.BUFFER_SLOT)
        val itemEntity = world.getEntitiesByClass(ItemEntity::class.java, Box(pos).expand(range.toDouble())) {
            val stack = it.stack
            !stack.isEmpty && FilterMatcher.matches(stack, tag.filter) && canMergeIntoBuffer(buffer, stack)
        }.minByOrNull { it.squaredDistanceTo(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) } ?: return false

        val remaining = mergeIntoBuffer(router, itemEntity.stack.copy())
        val moved = remaining.count != itemEntity.stack.count
        if (!moved) return false

        if (remaining.isEmpty) {
            itemEntity.discard()
        } else {
            itemEntity.stack = remaining
        }

        if (tag.augments.vacuumsXp()) {
            val owner = world.server.playerManager.playerList.firstOrNull { router.canAccess(it) && it.blockPos.getSquaredDistance(pos) <= range.toDouble() * range }
            if (owner != null) {
                world.getEntitiesByClass(ExperienceOrbEntity::class.java, Box(pos).expand(range.toDouble())) { true }
                    .forEach {
                        owner.addExperience(it.experienceAmount)
                        it.discard()
                    }
            }
        }

        return true
    }

    private fun hasDetectorMatch(world: ServerWorld, pos: BlockPos, router: RouterBlockEntity): Boolean {
        val augments = router.installedAugments()
        val registries = world.registryManager
        for (moduleIndex in 0 until RouterBlockEntity.MODULE_SLOT_COUNT) {
            val tag = router.tagInModuleSlot(moduleIndex, registries, augments) ?: continue
            if (tag.type == TagType.LOOKOUT && executeLookout(world, pos, router, tag)) {
                return true
            }
        }
        return false
    }

    private fun executeLookout(world: ServerWorld, pos: BlockPos, router: RouterBlockEntity, tag: TagInstance): Boolean {
        val range = effectiveRange(tag.type, tag.augments)
        val target = tag.boundPos?.takeIf { ContainerFinder.isContainer(world, it) }
            ?: ContainerFinder.findClosest(world, pos, range)
            ?: return false

        val inventory = ContainerFinder.getInventoryAt(world, target) ?: return false
        for (slot in 0 until inventory.size()) {
            val stack = inventory.getStack(slot)
            if (!stack.isEmpty && FilterMatcher.matches(stack, tag.filter)) {
                return true
            }
        }
        return false
    }

    private fun moveFromContainerToBuffer(
        world: ServerWorld,
        router: RouterBlockEntity,
        sourcePos: BlockPos,
        filter: com.cobblepalsworld.tag.filter.TagFilter,
        extraPredicate: (ItemStack) -> Boolean = { true }
    ): Boolean {
        val container = ContainerFinder.getInventoryAt(world, sourcePos) ?: return false
        val buffer = router.getStack(RouterBlockEntity.BUFFER_SLOT)

        for (slot in 0 until container.size()) {
            val stack = container.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, filter) || !extraPredicate(stack)) continue
            if (!canMergeIntoBuffer(buffer, stack)) continue

            val moved = minOf(stack.count, if (buffer.isEmpty) stack.maxCount else buffer.maxCount - buffer.count)
            if (moved <= 0) continue

            val pulled = stack.copyWithCount(moved)
            mergeIntoBuffer(router, pulled)
            stack.decrement(moved)
            if (stack.isEmpty) container.setStack(slot, ItemStack.EMPTY)
            container.markDirty()
            return true
        }

        return false
    }

    private fun depositBufferToTargets(
        world: ServerWorld,
        origin: BlockPos,
        router: RouterBlockEntity,
        moduleIndex: Int,
        tag: TagInstance,
        exclude: Set<BlockPos>
    ): Boolean {
        val targets = configuredTargets(world, origin, tag, exclude)
        if (targets.isEmpty()) return false

        val orderedTargets = when (tag.settings.targetStrategy) {
            TargetStrategy.ROUND_ROBIN -> targets
            TargetStrategy.NEAREST_FIRST -> targets.sortedBy { distanceScore(world, origin, it) }
            TargetStrategy.FURTHEST_FIRST -> targets.sortedByDescending { distanceScore(world, origin, it) }
            TargetStrategy.RANDOM -> targets.shuffled()
        }

        val selected = if (tag.settings.targetStrategy == TargetStrategy.ROUND_ROBIN) {
            orderedTargets[router.nextTargetIndex(moduleIndex, orderedTargets.size)]
        } else {
            orderedTargets.first()
        }

        val targetWorld = resolveWorld(world, selected.dimensionId) ?: return false
        val targetInventory = ContainerFinder.getInventoryAt(targetWorld, selected.pos) ?: return false
        val buffer = router.getStack(RouterBlockEntity.BUFFER_SLOT)
        if (buffer.isEmpty || !FilterMatcher.matches(buffer, tag.filter)) return false

        val remaining = ContainerFinder.insertStack(targetInventory, buffer.copy())
        if (remaining.count == buffer.count) return false

        router.setStack(RouterBlockEntity.BUFFER_SLOT, remaining)
        targetInventory.markDirty()
        router.advanceTargetIndex(moduleIndex, orderedTargets.size)
        return true
    }

    private fun configuredTargets(world: ServerWorld, origin: BlockPos, tag: TagInstance, exclude: Set<BlockPos>): List<TagTarget> {
        val sameDimension = world.registryKey.value.toString()
        val configured = buildList {
            tag.boundPos?.let { add(TagTarget(sameDimension, it.toImmutable())) }
            addAll(tag.settings.extraTargets)
        }.filterNot { it.dimensionId == sameDimension && it.pos in exclude }

        if (configured.isNotEmpty()) {
            return configured.filter { target ->
                val targetWorld = resolveWorld(world, target.dimensionId) ?: return@filter false
                ContainerFinder.isContainer(targetWorld, target.pos)
            }
        }

        return ContainerFinder.findAllMatching(world, origin, effectiveRange(tag.type, tag.augments), exclude).map {
            TagTarget(sameDimension, it.toImmutable())
        }
    }

    private fun findSourceContainer(
        world: ServerWorld,
        origin: BlockPos,
        tag: TagInstance,
        range: Int,
        exclude: Set<BlockPos> = emptySet()
    ): BlockPos? {
        val configuredTargets = tag.settings.extraTargets
            .filter { it.dimensionId == world.registryKey.value.toString() }
            .map { it.pos }
        val fullExclude = buildSet {
            addAll(exclude)
            tag.boundPos?.let { add(it) }
            addAll(configuredTargets)
        }

        return ContainerFinder.findClosestMatching(world, origin, range, fullExclude) { inventory, _ ->
            (0 until inventory.size()).any { slot ->
                val stack = inventory.getStack(slot)
                !stack.isEmpty && FilterMatcher.matches(stack, tag.filter)
            }
        }
    }

    private fun findPlacementTarget(world: ServerWorld, origin: BlockPos, center: BlockPos, range: Int): BlockPos? {
        if (world.getBlockState(center).isReplaceable) return center
        return BlockPos.iterateOutwards(center, range, range / 2, range)
            .firstOrNull { world.getBlockState(it).isReplaceable }
            ?.toImmutable()
            ?: BlockPos.iterateOutwards(origin, range, range / 2, range)
                .firstOrNull { world.getBlockState(it).isReplaceable }
                ?.toImmutable()
    }

    private fun resolveWorld(world: ServerWorld, dimensionId: String): ServerWorld? {
        val id = Identifier.tryParse(dimensionId) ?: return null
        return world.server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id))
    }

    private fun distanceScore(world: ServerWorld, origin: BlockPos, target: TagTarget): Double {
        return if (target.dimensionId == world.registryKey.value.toString()) {
            target.pos.getSquaredDistance(origin)
        } else {
            Double.MAX_VALUE
        }
    }

    private fun mergeIntoBuffer(router: RouterBlockEntity, stack: ItemStack): ItemStack {
        val buffer = router.getStack(RouterBlockEntity.BUFFER_SLOT)
        if (stack.isEmpty) return ItemStack.EMPTY
        if (buffer.isEmpty) {
            val transfer = minOf(stack.count, stack.maxCount)
            router.setStack(RouterBlockEntity.BUFFER_SLOT, stack.copyWithCount(transfer))
            return stack.copyWithCount(stack.count - transfer)
        }

        if (!ItemStack.areItemsAndComponentsEqual(buffer, stack) || buffer.count >= buffer.maxCount) {
            return stack
        }

        val transfer = minOf(stack.count, buffer.maxCount - buffer.count)
        val updated = buffer.copy().apply { increment(transfer) }
        router.setStack(RouterBlockEntity.BUFFER_SLOT, updated)
        return stack.copyWithCount(stack.count - transfer)
    }

    private fun canMergeIntoBuffer(buffer: ItemStack, incoming: ItemStack): Boolean {
        return buffer.isEmpty || (ItemStack.areItemsAndComponentsEqual(buffer, incoming) && buffer.count < buffer.maxCount)
    }
}
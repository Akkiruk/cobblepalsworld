package com.cobblepalsworld.router

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.CobblePalsWorldTags
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.platform.ActivatorPlatformBridge
import com.cobblepalsworld.tag.BoundArea
import com.cobblepalsworld.tag.RedstoneControlMode
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagTarget
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.TargetStrategy
import com.cobblepalsworld.tag.filter.FilterMatcher
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.CocoaBlock
import net.minecraft.block.CropBlock
import net.minecraft.block.NetherWartBlock
import net.minecraft.block.SugarCaneBlock
import net.minecraft.block.SweetBerryBushBlock
import net.minecraft.entity.Entity
import net.minecraft.entity.ExperienceOrbEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.passive.AnimalEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.particle.ParticleEffect
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.BlockTags
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.WorldEvents
import kotlin.math.roundToLong

object RouterModuleExecutor {
    private const val MODULES_PER_TICK = 2
    private const val ACTIVE_WINDOW = 40L

    private val breakerBannedBlocks = setOf(
        Blocks.BEDROCK,
        Blocks.BARRIER,
        Blocks.END_PORTAL,
        Blocks.END_PORTAL_FRAME,
        Blocks.END_GATEWAY,
        Blocks.NETHER_PORTAL,
        Blocks.COMMAND_BLOCK,
        Blocks.CHAIN_COMMAND_BLOCK,
        Blocks.REPEATING_COMMAND_BLOCK,
        Blocks.STRUCTURE_BLOCK,
        Blocks.STRUCTURE_VOID,
        Blocks.JIGSAW,
        Blocks.SPAWNER,
        Blocks.TRIAL_SPAWNER,
        Blocks.REINFORCED_DEEPSLATE,
        Blocks.OBSIDIAN,
        Blocks.CRYING_OBSIDIAN,
        Blocks.RESPAWN_ANCHOR,
        Blocks.DRAGON_EGG,
        Blocks.BUDDING_AMETHYST,
    )

    private val breakerBannedPatterns = listOf(
        "shulker_box",
        "pasture",
    )
    private val shepherdRecentlyFed = mutableMapOf<java.util.UUID, Long>()

    fun tick(world: ServerWorld, router: RouterBlockEntity, tasksBySlot: Map<Int, TagInstance>) {
        val start = router.moduleExecutionStart()
        repeat(MODULES_PER_TICK) { offset ->
            val slotIndex = (start + offset) % RouterBlockEntity.MODULE_SLOT_COUNT
            val tag = tasksBySlot[slotIndex]
            if (tag == null) {
                router.clearModuleRuntime(slotIndex)
                return@repeat
            }
            if (!tag.type.controllerNative) {
                return@repeat
            }
            if (!shouldRun(world, router, slotIndex, tag)) {
                return@repeat
            }
            if (!router.isModuleReady(slotIndex, world.time)) {
                return@repeat
            }

            val didWork = try {
                executeModule(world, router, slotIndex, tag)
            } catch (e: Exception) {
                CobblePalsWorld.LOGGER.error("Error executing controller module ${tag.type.id} in slot ${slotIndex + 1}", e)
                false
            }

            router.setModuleReadyTick(slotIndex, world.time + cooldownTicks(tag))
            if (didWork) {
                router.markModuleWorked(slotIndex, world.time)
            }
        }
        router.advanceModuleExecutionCursor(MODULES_PER_TICK)
    }

    fun wasRecentlyActive(router: RouterBlockEntity, slotIndex: Int, worldTime: Long): Boolean {
        return router.wasModuleRecentlyActive(slotIndex, worldTime, ACTIVE_WINDOW)
    }

    private fun shouldRun(world: ServerWorld, router: RouterBlockEntity, slotIndex: Int, tag: TagInstance): Boolean {
        if (!tag.augments.isRedstoneControlled()) return true

        val powered = world.isReceivingRedstonePower(router.pos)
        val lastPowered = router.lastModuleRedstonePower(slotIndex)
        router.setLastModuleRedstonePower(slotIndex, powered)

        return when (tag.settings.redstoneMode) {
            RedstoneControlMode.ALWAYS -> true
            RedstoneControlMode.HIGH -> powered
            RedstoneControlMode.LOW -> !powered
            RedstoneControlMode.NEVER -> false
            RedstoneControlMode.PULSE -> powered && !lastPowered
        }
    }

    private fun executeModule(world: ServerWorld, router: RouterBlockEntity, slotIndex: Int, tag: TagInstance): Boolean {
        return when (tag.type) {
            TagType.BREAKER -> executeBreaker(world, router, tag)
            TagType.GUARDIAN -> executeGuardian(world, router, tag)
            TagType.HARVESTER -> executeHarvester(world, router, slotIndex, tag)
            TagType.VACUUM -> executeVacuum(world, tag)
            TagType.COURIER -> executeSender(world, tag)
            TagType.PULLER -> executePuller(world, tag)
            TagType.STASHER -> executeDistributor(world, router, slotIndex, tag)
            TagType.DROPPER -> executeDropper(world, tag)
            TagType.VOID -> executeVoid(world, tag)
            TagType.ACTIVATOR -> executeActivator(world, tag)
            TagType.SHEPHERD -> executeShepherd(world, tag)
        }
    }

    private fun executeSender(world: ServerWorld, tag: TagInstance): Boolean {
        val source = controllerInventory(world, tag) ?: return false
        val targetPos = tag.boundPos ?: return false
        val target = ContainerFinder.getInventoryAt(world, targetPos) ?: return false
        val moved = moveMatchingItems(source, target, tag, effectiveTransferLimit(tag))
        if (moved > 0) {
            pulseWork(world, targetPos, tag.type)
        }
        return moved > 0
    }

    private fun executePuller(world: ServerWorld, tag: TagInstance): Boolean {
        val sourcePos = tag.boundPos ?: return false
        val source = ContainerFinder.getInventoryAt(world, sourcePos) ?: return false
        val target = controllerInventory(world, tag) ?: return false
        val moved = moveMatchingItems(source, target, tag, effectiveTransferLimit(tag))
        if (moved > 0) {
            pulseWork(world, sourcePos, tag.type)
        }
        return moved > 0
    }

    private fun executeDistributor(world: ServerWorld, router: RouterBlockEntity, slotIndex: Int, tag: TagInstance): Boolean {
        val source = controllerInventory(world, tag) ?: return false
        val targets = distributorTargets(world, tag, tag.controllerPos ?: router.pos)
        if (targets.isEmpty()) return false

        val startIndex = when (tag.settings.targetStrategy) {
            TargetStrategy.ROUND_ROBIN -> Math.floorMod(router.moduleRoundRobinIndex(slotIndex), targets.size)
            else -> 0
        }

        for (offset in targets.indices) {
            val targetPos = targets[(startIndex + offset) % targets.size]
            val target = ContainerFinder.getInventoryAt(world, targetPos) ?: continue
            val moved = moveMatchingItems(source, target, tag, effectiveTransferLimit(tag))
            if (moved <= 0) continue

            if (tag.settings.targetStrategy == TargetStrategy.ROUND_ROBIN) {
                router.setModuleRoundRobinIndex(slotIndex, (startIndex + offset + 1) % targets.size)
            }
            pulseWork(world, targetPos, tag.type)
            return true
        }

        return false
    }

    private fun executeDropper(world: ServerWorld, tag: TagInstance): Boolean {
        val source = controllerInventory(world, tag) ?: return false
        val targetPos = tag.boundPos ?: tag.controllerPos ?: return false
        val dropped = extractMatchingStacks(source, tag, effectiveTransferLimit(tag))
        if (dropped.isEmpty()) return false

        val spawnX = targetPos.x + 0.5
        val spawnY = targetPos.y + 0.5
        val spawnZ = targetPos.z + 0.5
        dropped.forEach { stack ->
            val entity = ItemEntity(world, spawnX, spawnY, spawnZ, stack)
            entity.setVelocity(0.0, 0.0, 0.0)
            world.spawnEntity(entity)
        }
        pulseWork(world, targetPos, tag.type)
        return true
    }

    private fun executeVoid(world: ServerWorld, tag: TagInstance): Boolean {
        val source = controllerInventory(world, tag) ?: return false
        val removed = deleteMatchingItems(source, tag, effectiveTransferLimit(tag))
        if (removed > 0) {
            pulseWork(world, tag.controllerPos ?: return true, tag.type)
        }
        return removed > 0
    }

    private fun executeBreaker(world: ServerWorld, router: RouterBlockEntity, tag: TagInstance): Boolean {
        val targetPos = tag.boundPos ?: return false
        if (!canBreak(world, targetPos, setOf(router.pos, router.linkedPastureAnchor()))) return false

        val blockState = world.getBlockState(targetPos)
        val drops = Block.getDroppedStacks(
            blockState,
            world,
            targetPos,
            world.getBlockEntity(targetPos),
            null,
            miningToolFor(blockState)
        )
        world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, targetPos, Block.getRawIdFromState(blockState))
        world.setBlockState(targetPos, Blocks.AIR.defaultState)
        depositOrDrop(world, tag, targetPos, drops)
        pulseWork(world, targetPos, tag.type)
        return true
    }

    private fun executeGuardian(world: ServerWorld, router: RouterBlockEntity, tag: TagInstance): Boolean {
        val anchorPos = tag.controllerPos ?: router.pos
        val range = effectiveRange(tag).toDouble()
        val hostile = findNearestHostile(world, anchorPos, range) ?: return false
        val damage = guardianDamage(world, router)
        val damageSource = world.damageSources.generic()
        if (!hostile.damage(damageSource, damage)) return false

        pulseWork(world, hostile.blockPos, tag.type)
        return true
    }

    private fun executeHarvester(world: ServerWorld, router: RouterBlockEntity, slotIndex: Int, tag: TagInstance): Boolean {
        val area = harvestArea(tag) ?: return false
        val volume = area.volume()
        if (volume <= 0) return false

        val startCursor = Math.floorMod(router.moduleSearchCursor(slotIndex), volume)
        for (offset in 0 until volume) {
            val index = (startCursor + offset) % volume
            val targetPos = area.positionAt(index)
            if (!isMatureCrop(world, targetPos)) continue

            router.setModuleSearchCursor(slotIndex, (index + 1) % volume)
            val drops = harvestCrop(world, targetPos)
            if (drops.isEmpty()) {
                return false
            }
            depositOrDrop(world, tag, targetPos, drops)
            pulseWork(world, targetPos, tag.type)
            return true
        }

        return false
    }

    private fun executeVacuum(world: ServerWorld, tag: TagInstance): Boolean {
        val controllerPos = tag.controllerPos ?: return false
        val target = controllerInventory(world, tag) ?: return false
        val range = effectiveRange(tag)
        val searchBox = Box(
            controllerPos.x.toDouble() - range,
            controllerPos.y.toDouble() - range / 2.0,
            controllerPos.z.toDouble() - range,
            controllerPos.x.toDouble() + range + 1,
            controllerPos.y.toDouble() + range / 2.0 + 1,
            controllerPos.z.toDouble() + range + 1
        )

        var movedAny = false
        var remainingCapacity = effectiveTransferLimit(tag)
        val itemEntities = world.getEntitiesByClass(ItemEntity::class.java, searchBox) { entity ->
            entity.isAlive && FilterMatcher.matches(entity.stack, tag.filter)
        }
        for (itemEntity in itemEntities) {
            if (remainingCapacity <= 0) break
            val requested = itemEntity.stack.copyWithCount(minOf(itemEntity.stack.count, remainingCapacity))
            val remainder = ContainerFinder.insertStack(target, requested)
            val inserted = requested.count - remainder.count
            if (inserted <= 0) continue

            movedAny = true
            remainingCapacity -= inserted
            if (inserted >= itemEntity.stack.count) {
                itemEntity.discard()
            } else {
                itemEntity.stack.decrement(inserted)
            }
        }
        if (movedAny) {
            target.markDirty()
        }

        var collectedXp = false
        if (tag.augments.vacuumsXp()) {
            val xpOrbs = world.getEntitiesByClass(ExperienceOrbEntity::class.java, searchBox) { it.isAlive }
            if (xpOrbs.isNotEmpty()) {
                val nearestPlayer = world.getClosestPlayer(
                    controllerPos.x + 0.5,
                    controllerPos.y + 0.5,
                    controllerPos.z + 0.5,
                    range * 2.0,
                    false
                )
                if (nearestPlayer != null) {
                    var totalXp = 0
                    xpOrbs.forEach { orb ->
                        totalXp += orb.experienceAmount
                        orb.discard()
                    }
                    nearestPlayer.addExperience(totalXp)
                    collectedXp = totalXp > 0
                }
            }
        }

        if (movedAny || collectedXp) {
            pulseWork(world, controllerPos, tag.type)
        }
        return movedAny || collectedXp
    }

    private fun executeShepherd(world: ServerWorld, tag: TagInstance): Boolean {
        val anchorPos = tag.boundPos ?: tag.controllerPos ?: return false
        val controller = controllerInventory(world, tag) ?: return false
        pruneShepherdCooldowns(world.time)

        val animal = findNearestBreedableAnimal(world, anchorPos, effectiveRange(tag), controller, world.time) ?: return false
        if (!consumeBreedingFood(controller, animal)) return false

        animal.lovePlayer(null)
        shepherdRecentlyFed[animal.uuid] = world.time
        controller.markDirty()
        pulseWork(world, animal.blockPos, tag.type)
        return true
    }

    private fun executeActivator(world: ServerWorld, tag: TagInstance): Boolean {
        val controllerPos = tag.controllerPos ?: return false
        val targetPos = tag.boundPos ?: return false
        val controller = controllerInventory(world, tag) ?: return false
        val extracted = pullActivatorStack(controller, tag) ?: return false
        val player = ActivatorPlatformBridge.fakePlayer(world)

        prepareFakePlayer(player, targetPos, extracted.second)
        val heldStack = player.getStackInHand(Hand.MAIN_HAND)
        val didUse = tryUseOnBlock(world, player, targetPos, controllerPos)
            || tryUseOnEntities(world, player, targetPos)
            || ActivatorPlatformBridge.useItem(player, world, heldStack)

        if (!didUse) {
            controller.setStack(extracted.first, extracted.second)
            controller.markDirty()
            return false
        }

        syncFakePlayerResult(world, controllerPos, controller, player, extracted.first)
        pulseWork(world, targetPos, tag.type)
        return true
    }

    private fun controllerInventory(world: ServerWorld, tag: TagInstance): Inventory? {
        val controllerPos = tag.controllerPos ?: return null
        return ContainerFinder.getInventoryAt(world, controllerPos)
    }

    private fun effectiveRange(tag: TagInstance): Int {
        val config = ConfigManager.config.getTagConfig(tag.type)
        return config.range + tag.augments.extraRange()
    }

    private fun effectiveTransferLimit(tag: TagInstance): Int {
        val config = ConfigManager.config.getTagConfig(tag.type)
        return (config.maxItemsPerTrip + tag.augments.extraItemsPerTrip()).coerceAtLeast(1)
    }

    private fun cooldownTicks(tag: TagInstance): Long {
        val baseTicks = when (tag.type) {
            TagType.BREAKER -> 5L
            TagType.GUARDIAN -> 10L
            TagType.HARVESTER -> 10L
            TagType.VACUUM -> 10L
            TagType.ACTIVATOR -> 10L
            TagType.SHEPHERD -> 15L
            else -> 5L
        }
        return (baseTicks * tag.augments.speedMultiplier()).roundToLong().coerceAtLeast(5L)
    }

    private fun moveMatchingItems(source: Inventory, target: Inventory, tag: TagInstance, limit: Int): Int {
        var moved = 0
        for (slot in 0 until source.size()) {
            if (moved >= limit) break
            val stack = source.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val regulatorCap = if (tag.augments.isRegulator()) {
                (tag.settings.regulatorAmount - countEquivalentItems(target, stack)).coerceAtLeast(0)
            } else {
                Int.MAX_VALUE
            }
            if (regulatorCap <= 0) continue

            val requestedCount = minOf(stack.count, limit - moved, regulatorCap)
            val requested = stack.copyWithCount(requestedCount)
            val remainder = ContainerFinder.insertStack(target, requested)
            val inserted = requestedCount - remainder.count
            if (inserted <= 0) continue

            stack.decrement(inserted)
            if (stack.isEmpty) {
                source.setStack(slot, ItemStack.EMPTY)
            }
            moved += inserted
        }
        if (moved > 0) {
            source.markDirty()
            target.markDirty()
        }
        return moved
    }

    private fun extractMatchingStacks(source: Inventory, tag: TagInstance, limit: Int): List<ItemStack> {
        val extracted = mutableListOf<ItemStack>()
        var remaining = limit
        for (slot in 0 until source.size()) {
            if (remaining <= 0) break
            val stack = source.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val toTake = minOf(stack.count, remaining)
            extracted += stack.copyWithCount(toTake)
            stack.decrement(toTake)
            if (stack.isEmpty) {
                source.setStack(slot, ItemStack.EMPTY)
            }
            remaining -= toTake
        }
        if (extracted.isNotEmpty()) {
            source.markDirty()
        }
        return extracted
    }

    private fun deleteMatchingItems(source: Inventory, tag: TagInstance, limit: Int): Int {
        var removed = 0
        for (slot in 0 until source.size()) {
            if (removed >= limit) break
            val stack = source.getStack(slot)
            if (stack.isEmpty || !FilterMatcher.matches(stack, tag.filter)) continue

            val toRemove = minOf(stack.count, limit - removed)
            stack.decrement(toRemove)
            if (stack.isEmpty) {
                source.setStack(slot, ItemStack.EMPTY)
            }
            removed += toRemove
        }
        if (removed > 0) {
            source.markDirty()
        }
        return removed
    }

    private fun depositOrDrop(world: ServerWorld, tag: TagInstance, fallbackPos: BlockPos, drops: List<ItemStack>) {
        val controller = controllerInventory(world, tag)
        if (controller == null) {
            spawnDrops(world, fallbackPos, drops)
            return
        }

        drops.forEach { stack ->
            val remainder = ContainerFinder.insertStack(controller, stack.copy())
            if (!remainder.isEmpty) {
                spawnDrops(world, fallbackPos, listOf(remainder))
            }
        }
        controller.markDirty()
    }

    private fun spawnDrops(world: ServerWorld, pos: BlockPos, drops: List<ItemStack>) {
        drops.filterNot { it.isEmpty }.forEach { stack ->
            world.spawnEntity(ItemEntity(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, stack))
        }
    }

    private fun distributorTargets(world: ServerWorld, tag: TagInstance, origin: BlockPos): List<BlockPos> {
        val sourcePos = tag.controllerPos
        val explicitTargets = buildList {
            tag.boundPos?.let { add(it) }
            addAll(
                tag.settings.extraTargets
                    .filter { it.dimensionId == world.registryKey.value.toString() }
                    .map(TagTarget::pos)
            )
        }
            .filter { it != sourcePos && ContainerFinder.isContainer(world, it) }
            .distinct()

        return when (tag.settings.targetStrategy) {
            TargetStrategy.NEAREST_FIRST -> explicitTargets.sortedBy { it.getSquaredDistance(origin) }
            TargetStrategy.FURTHEST_FIRST -> explicitTargets.sortedByDescending { it.getSquaredDistance(origin) }
            TargetStrategy.RANDOM -> explicitTargets.shuffled()
            else -> explicitTargets
        }
    }

    private fun guardianDamage(world: ServerWorld, router: RouterBlockEntity): Float {
        val pasture = router.linkedPasture(world) ?: return 8.0f
        var highestLevel = 20
        for (tethering in pasture.tetheredPokemon) {
            val pokemon = try {
                tethering.getPokemon()
            } catch (_: Exception) {
                null
            } ?: continue
            if (pokemon.isFainted()) continue
            val entity = pokemon.entity ?: continue
            if (entity.dataTracker.get(PokemonEntity.POSE_TYPE) == com.cobblemon.mod.common.entity.PoseType.SLEEP) continue
            if (pokemon.level > highestLevel) highestLevel = pokemon.level
        }
        return 4.0f + (highestLevel / 5.0f)
    }

    private fun findNearestHostile(world: ServerWorld, center: BlockPos, range: Double): HostileEntity? {
        val box = Box(center).expand(range)
        var nearest: HostileEntity? = null
        var nearestDistance = Double.MAX_VALUE
        for (hostile in world.getEntitiesByClass(HostileEntity::class.java, box) { it.isAlive }) {
            val distance = hostile.squaredDistanceTo(center.x + 0.5, center.y + 0.5, center.z + 0.5)
            if (distance < nearestDistance) {
                nearest = hostile
                nearestDistance = distance
            }
        }
        return nearest
    }

    private fun pruneShepherdCooldowns(worldTime: Long) {
        shepherdRecentlyFed.entries.removeIf { worldTime - it.value > 6000L }
    }

    private fun findNearestBreedableAnimal(
        world: ServerWorld,
        center: BlockPos,
        range: Int,
        source: Inventory,
        worldTime: Long
    ): AnimalEntity? {
        val box = Box(center).expand(range.toDouble())
        var nearest: AnimalEntity? = null
        var nearestDistance = Double.MAX_VALUE
        for (animal in world.getEntitiesByClass(AnimalEntity::class.java, box) { it.isAlive }) {
            if (!canBreed(animal, worldTime)) continue
            if (!hasBreedingFood(source, animal)) continue

            val distance = animal.squaredDistanceTo(center.x + 0.5, center.y + 0.5, center.z + 0.5)
            if (distance < nearestDistance) {
                nearest = animal
                nearestDistance = distance
            }
        }
        return nearest
    }

    private fun canBreed(animal: AnimalEntity, worldTime: Long): Boolean {
        if (animal.isBaby) return false
        if (animal.loveTicks > 0) return false
        if (animal.breedingAge > 0) return false
        val lastFed = shepherdRecentlyFed[animal.uuid] ?: return true
        return worldTime - lastFed > 6000L
    }

    private fun hasBreedingFood(source: Inventory, animal: AnimalEntity): Boolean {
        for (slot in 0 until source.size()) {
            val stack = source.getStack(slot)
            if (stack.isEmpty || !isBreedingFood(stack)) continue
            if (animal.isBreedingItem(stack)) return true
        }
        return false
    }

    private fun consumeBreedingFood(source: Inventory, animal: AnimalEntity): Boolean {
        for (slot in 0 until source.size()) {
            val stack = source.getStack(slot)
            if (stack.isEmpty || !isBreedingFood(stack)) continue
            if (!animal.isBreedingItem(stack)) continue

            stack.decrement(1)
            if (stack.isEmpty) {
                source.setStack(slot, ItemStack.EMPTY)
            }
            return true
        }
        return false
    }

    private fun isBreedingFood(stack: ItemStack): Boolean {
        val item = stack.item
        return item == Items.WHEAT || item == Items.CARROT || item == Items.POTATO
            || item == Items.BEETROOT || item == Items.GOLDEN_CARROT
            || item == Items.WHEAT_SEEDS || item == Items.MELON_SEEDS
            || item == Items.PUMPKIN_SEEDS || item == Items.BEETROOT_SEEDS
            || item == Items.TORCHFLOWER_SEEDS
            || item == Items.SEAGRASS || item == Items.SWEET_BERRIES
            || item == Items.GLOW_BERRIES || item == Items.BAMBOO
    }

    private fun countEquivalentItems(inventory: Inventory, stack: ItemStack): Int {
        var count = 0
        for (slot in 0 until inventory.size()) {
            val existing = inventory.getStack(slot)
            if (ItemStack.areItemsAndComponentsEqual(existing, stack)) {
                count += existing.count
            }
        }
        return count
    }

    private fun canBreak(world: ServerWorld, pos: BlockPos, protectedPositions: Set<BlockPos>): Boolean {
        if (pos in protectedPositions) return false
        val state = world.getBlockState(pos)
        if (state.isAir) return false
        if (state.getHardness(world, pos) < 0f) return false
        if (state.block in breakerBannedBlocks) return false
        val blockId = Registries.BLOCK.getId(state.block).path
        if (breakerBannedPatterns.any(blockId::contains)) return false
        return true
    }

    private fun miningToolFor(blockState: BlockState): ItemStack {
        return when {
            blockState.isIn(BlockTags.AXE_MINEABLE) -> ItemStack(Items.NETHERITE_AXE)
            blockState.isIn(BlockTags.SHOVEL_MINEABLE) -> ItemStack(Items.NETHERITE_SHOVEL)
            blockState.isIn(BlockTags.HOE_MINEABLE) -> ItemStack(Items.NETHERITE_HOE)
            blockState.isOf(Blocks.COBWEB) -> ItemStack(Items.NETHERITE_SWORD)
            else -> ItemStack(Items.NETHERITE_PICKAXE)
        }
    }

    private fun harvestArea(tag: TagInstance): BoundArea? {
        return tag.boundArea ?: tag.boundPos?.let { BoundArea.of(it, it) }
    }

    private fun isMatureCrop(world: ServerWorld, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        val block = state.block
        return when {
            block is CropBlock -> block.isMature(state)
            block is NetherWartBlock -> state.get(NetherWartBlock.AGE) >= 3
            block is SweetBerryBushBlock -> state.get(SweetBerryBushBlock.AGE) >= 2
            block is CocoaBlock -> state.get(CocoaBlock.AGE) >= 2
            state.isOf(Blocks.MELON) -> true
            state.isOf(Blocks.PUMPKIN) && !state.isOf(Blocks.CARVED_PUMPKIN) -> true
            block is SugarCaneBlock -> world.getBlockState(pos.down()).block is SugarCaneBlock
            else -> false
        }
    }

    private fun harvestCrop(world: ServerWorld, targetPos: BlockPos): List<ItemStack> {
        val state = world.getBlockState(targetPos)
        val block = state.block
        val drops = mutableListOf<ItemStack>()

        when {
            block is SweetBerryBushBlock -> {
                val age = state.get(SweetBerryBushBlock.AGE)
                val berryCount = if (age == 3) 2 + world.random.nextInt(2) else 1 + world.random.nextInt(2)
                drops += ItemStack(Items.SWEET_BERRIES, berryCount)
                world.setBlockState(targetPos, state.with(SweetBerryBushBlock.AGE, 1), Block.NOTIFY_ALL)
            }
            block is SugarCaneBlock -> {
                drops += Block.getDroppedStacks(state, world, targetPos, null)
                world.breakBlock(targetPos, false)
            }
            state.isOf(Blocks.MELON) || state.isOf(Blocks.PUMPKIN) -> {
                drops += Block.getDroppedStacks(state, world, targetPos, null)
                world.breakBlock(targetPos, false)
            }
            block is CocoaBlock -> {
                drops += Block.getDroppedStacks(state, world, targetPos, null)
                world.setBlockState(targetPos, state.with(CocoaBlock.AGE, 0), Block.NOTIFY_ALL)
            }
            block is CropBlock -> {
                drops += Block.getDroppedStacks(state, world, targetPos, null)
                world.setBlockState(targetPos, block.defaultState, Block.NOTIFY_ALL)
            }
            block is NetherWartBlock -> {
                drops += Block.getDroppedStacks(state, world, targetPos, null)
                world.setBlockState(targetPos, block.defaultState, Block.NOTIFY_ALL)
            }
        }

        if (block is CropBlock || block is NetherWartBlock) {
            removeSeedFromDrops(drops, block)
        }
        drops.removeAll(ItemStack::isEmpty)
        return drops
    }

    private fun removeSeedFromDrops(drops: MutableList<ItemStack>, block: Block) {
        val seedItem = when (block) {
            is CropBlock -> block.getPickStack(null, null, block.defaultState)?.item
            is NetherWartBlock -> Items.NETHER_WART
            else -> null
        } ?: return

        for (index in drops.indices) {
            val stack = drops[index]
            if (stack.item != seedItem || stack.count <= 0) continue
            stack.decrement(1)
            if (stack.isEmpty) {
                drops[index] = ItemStack.EMPTY
            }
            return
        }
    }

    private fun pullActivatorStack(controller: Inventory, tag: TagInstance): Pair<Int, ItemStack>? {
        for (slot in 0 until controller.size()) {
            val stack = controller.getStack(slot)
            if (stack.isEmpty) continue
            if (stack.isIn(CobblePalsWorldTags.Items.ACTIVATOR_BLACKLIST)) continue
            if (!FilterMatcher.matches(stack, tag.filter)) continue
            val extracted = stack.copy()
            controller.setStack(slot, ItemStack.EMPTY)
            controller.markDirty()
            return slot to extracted
        }
        return null
    }

    private fun prepareFakePlayer(player: ServerPlayerEntity, targetPos: BlockPos, heldStack: ItemStack) {
        clearFakePlayerInventory(player)
        player.inventory.selectedSlot = 0
        player.refreshPositionAndAngles(targetPos.x + 0.5, targetPos.y + 0.5, targetPos.z + 0.5, 0f, 0f)
        player.setStackInHand(Hand.MAIN_HAND, heldStack)
    }

    private fun clearFakePlayerInventory(player: ServerPlayerEntity) {
        for (slot in 0 until player.inventory.size()) {
            player.inventory.setStack(slot, ItemStack.EMPTY)
        }
    }

    private fun tryUseOnBlock(world: ServerWorld, player: ServerPlayerEntity, targetPos: BlockPos, originPos: BlockPos): Boolean {
        val state = world.getBlockState(targetPos)
        if (state.isAir) return false

        for (face in faceOrder(originPos, targetPos)) {
            val hitResult = BlockHitResult(Vec3d.ofCenter(targetPos), face, targetPos, false)
            if (ActivatorPlatformBridge.useItemOnBlock(player, world, player.getStackInHand(Hand.MAIN_HAND), hitResult)) {
                return true
            }
        }

        return false
    }

    private fun tryUseOnEntities(world: ServerWorld, player: ServerPlayerEntity, targetPos: BlockPos): Boolean {
        val targetCenter = Vec3d.ofCenter(targetPos)
        val searchBox = Box(targetPos).expand(1.5)
        val candidates = world.getOtherEntities(player, searchBox) { entity ->
            entity.isAlive && !entity.type.isIn(CobblePalsWorldTags.EntityTypes.ACTIVATOR_INTERACT_BLACKLIST)
        }
        var nearestEntity: Entity? = null
        var nearestDistance = Double.MAX_VALUE
        for (candidate in candidates) {
            val distance = candidate.squaredDistanceTo(targetCenter)
            if (distance < nearestDistance) {
                nearestEntity = candidate
                nearestDistance = distance
            }
        }
        return nearestEntity != null && ActivatorPlatformBridge.interactEntity(player, nearestEntity)
    }

    private fun syncFakePlayerResult(
        world: ServerWorld,
        controllerPos: BlockPos,
        controller: Inventory,
        player: ServerPlayerEntity,
        sourceSlot: Int
    ) {
        val selectedSlot = player.inventory.selectedSlot
        controller.setStack(sourceSlot, player.getStackInHand(Hand.MAIN_HAND).copy())

        for (slot in 0 until player.inventory.size()) {
            if (slot == selectedSlot) continue
            val extra = player.inventory.getStack(slot)
            if (extra.isEmpty) continue

            val remainder = ContainerFinder.insertStack(controller, extra.copy())
            if (!remainder.isEmpty) {
                world.spawnEntity(
                    ItemEntity(
                        world,
                        controllerPos.x + 0.5,
                        controllerPos.y + 0.5,
                        controllerPos.z + 0.5,
                        remainder
                    )
                )
            }
            player.inventory.setStack(slot, ItemStack.EMPTY)
        }
        controller.markDirty()
    }

    private fun faceOrder(origin: BlockPos, target: BlockPos): List<Direction> {
        val primary = Direction.getFacing(
            (origin.x - target.x).toDouble(),
            (origin.y - target.y).toDouble(),
            (origin.z - target.z).toDouble()
        )
        return Direction.entries.sortedBy { direction -> if (direction == primary) 0 else 1 }
    }

    private fun pulseWork(world: ServerWorld, pos: BlockPos, type: TagType) {
        spawnParticles(world, pos, type.workParticle, 6)
        world.playSound(null, pos, type.workSound, SoundCategory.BLOCKS, 0.5f, 1.0f + (world.random.nextFloat() - 0.5f) * 0.2f)
    }

    private fun spawnParticles(world: ServerWorld, pos: BlockPos, particle: ParticleEffect, count: Int) {
        world.spawnParticles(
            particle,
            pos.x + 0.5,
            pos.y + 1.0,
            pos.z + 0.5,
            count,
            0.3,
            0.3,
            0.3,
            0.02
        )
    }
}
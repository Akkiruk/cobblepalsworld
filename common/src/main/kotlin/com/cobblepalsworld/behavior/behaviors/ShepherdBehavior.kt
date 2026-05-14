package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.inventory.PokemonInventory
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.persistence.CobblePalsSaveData
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import net.minecraft.entity.passive.AnimalEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Feeds and breeds animals near a bound pen area.
 * Phase 1: Extract breeding food from a nearby container.
 * Phase 2: Find a breedable animal near origin/bound pos and feed it.
 *
 * Bound → the pen area where animals are. Source container found automatically.
 */
object ShepherdBehavior : TagBehavior {
    override val tagType = TagType.SHEPHERD
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range
    override val handlesOwnInventory = true
    override fun arrivalDelayTicks(tag: TagInstance, state: WorkerState): Long = 0L
    override fun idleRetryTicks(tag: TagInstance, state: WorkerState): Long = 40L

    private const val FEED_RANGE = 3.0

    // Track which animals we've recently fed to avoid feeding the same one twice
    private val recentlyFed = mutableMapOf<UUID, Long>()
    private val trackedAnimals = ConcurrentHashMap<UUID, UUID>()

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val pokemonInv = InventoryManager.get(entity.pokemon.uuid)
        val hasFood = pokemonInv != null && (0 until pokemonInv.size()).any {
            !pokemonInv.getStack(it).isEmpty && isBreedingFood(pokemonInv.getStack(it))
        }

        val range = effectiveRange(tag, state)

        if (hasFood) {
            // Phase 2: find a breedable animal
            val penArea = tag.boundPos ?: origin
            resolveTrackedAnimal(world, entity.pokemon.uuid, penArea, range)?.let { return it.blockPos.toImmutable() }

            val animal = findBreedableAnimal(world, penArea, range) ?: return null
            trackedAnimals[entity.pokemon.uuid] = animal.uuid
            return animal.blockPos.toImmutable()
        }

        // Phase 1: find container with breeding food
        return findFoodContainer(world, origin, tag, state, range)
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        if (world !is ServerWorld) return WorkResult.Done()
        val pokemonInv = InventoryManager.getOrCreate(entity.pokemon)

        // At container → extract food
        if (ContainerFinder.isContainer(world, target)) {
            return extractFood(world, target, pokemonInv)
        }

        val animal = resolveTrackedAnimal(world, entity.pokemon.uuid, target, effectiveRange(tag, state))
            ?: findBreedableAnimal(world, target, FEED_RANGE.toInt())
            ?: run {
                trackedAnimals.remove(entity.pokemon.uuid)
                return WorkResult.Done()
            }

        trackedAnimals[entity.pokemon.uuid] = animal.uuid
        if (animal.squaredDistanceTo(entity) > FEED_RANGE * FEED_RANGE) {
            return WorkResult.MoveTo(animal.blockPos.toImmutable())
        }

        // Find matching food in inventory
        for (slot in 0 until pokemonInv.size()) {
            val stack = pokemonInv.getStack(slot)
            if (stack.isEmpty || !animal.isBreedingItem(stack)) continue

            animal.lovePlayer(null)
            recentlyFed[animal.uuid] = world.time
            trackedAnimals.remove(entity.pokemon.uuid)
            stack.decrement(1)
            if (stack.isEmpty) pokemonInv.setStack(slot, ItemStack.EMPTY)
            (world as? net.minecraft.server.world.ServerWorld)?.let(CobblePalsSaveData::markDirty)
            break
        }

        return WorkResult.Done()
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        // Valid if it's a container or there are animals nearby
        if (ContainerFinder.isContainer(world, target)) return true
        val box = Box(target).expand(FEED_RANGE)
        return world.getEntitiesByClass(AnimalEntity::class.java, box) { it.isAlive }
            .any { canBreed(it, world) }
    }

    fun cleanup(pokemonId: UUID) {
        trackedAnimals.remove(pokemonId)
        // Clean old entries periodically
        val cutoff = recentlyFed.values.minOrNull()?.let { it + 6000 } ?: return
        recentlyFed.entries.removeIf { it.value < cutoff }
    }

    fun clearAllRuntimeState() {
        recentlyFed.clear()
        trackedAnimals.clear()
    }

    private fun findBreedableAnimal(world: World, center: BlockPos, range: Int): AnimalEntity? {
        val box = Box(center).expand(range.toDouble())
        var nearest: AnimalEntity? = null
        var nearestDistance = Double.MAX_VALUE
        for (animal in world.getEntitiesByClass(AnimalEntity::class.java, box) { it.isAlive }) {
            if (!canBreed(animal, world)) continue
            val distance = animal.squaredDistanceTo(center.x + 0.5, center.y + 0.5, center.z + 0.5)
            if (distance < nearestDistance) {
                nearest = animal
                nearestDistance = distance
            }
        }
        return nearest
    }

    private fun canBreed(animal: AnimalEntity, world: World): Boolean {
        if (animal.isBaby) return false
        if (animal.loveTicks > 0) return false
        if (animal.breedingAge > 0) return false
        // Don't re-feed animals we recently fed
        val lastFed = recentlyFed[animal.uuid] ?: return true
        return world.time - lastFed > 6000 // 5 minutes cooldown
    }

    private fun resolveTrackedAnimal(world: World, pokemonId: UUID, center: BlockPos, range: Int): AnimalEntity? {
        val serverWorld = world as? ServerWorld ?: return null
        val trackedId = trackedAnimals[pokemonId] ?: return null
        val animal = serverWorld.getEntity(trackedId) as? AnimalEntity ?: run {
            trackedAnimals.remove(pokemonId)
            return null
        }
        if (!animal.isAlive || !canBreed(animal, world) || animal.squaredDistanceTo(center.x + 0.5, center.y + 0.5, center.z + 0.5) > range.toDouble() * range) {
            trackedAnimals.remove(pokemonId)
            return null
        }
        return animal
    }

    private fun findFoodContainer(world: World, origin: BlockPos, tag: TagInstance, state: WorkerState, range: Int): BlockPos? {
        val exclude = mutableSetOf(origin)
        return ContainerFinder.findControllerFirstCachedMatching(world, origin, tag, state, range, exclude) { _, pos ->
            containerHasFood(world, pos)
        }
    }

    private fun extractFood(
        world: World, target: BlockPos,
        pokemonInv: PokemonInventory
    ): WorkResult {
        val container = ContainerFinder.getInventoryAt(world, target) ?: return WorkResult.Done()
        var extracted = 0
        val maxItems = 4 // Small batches — shepherd carries a few handfuls of feed

        for (slot in 0 until container.size()) {
            if (extracted >= maxItems) break
            val stack = container.getStack(slot)
            if (stack.isEmpty || !isBreedingFood(stack)) continue

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
            (world as? net.minecraft.server.world.ServerWorld)?.let(CobblePalsSaveData::markDirty)
        }
        return WorkResult.Done()
    }

    private fun containerHasFood(world: World, pos: BlockPos): Boolean {
        val container = ContainerFinder.getInventoryAt(world, pos) ?: return false
        for (slot in 0 until container.size()) {
            if (!container.getStack(slot).isEmpty && isBreedingFood(container.getStack(slot))) return true
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
}

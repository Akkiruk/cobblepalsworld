package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.inventory.PokemonInventory
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.pasture.PastureWorkerManager
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

    // Track which animals we've recently fed to avoid feeding the same one twice
    private val recentlyFed = mutableMapOf<UUID, Long>()

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
            val animal = findBreedableAnimal(world, penArea, range)
            return animal?.blockPos
        }

        // Phase 1: find container with breeding food
        return findFoodContainer(world, origin, tag, range)
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

        // Near animal → feed it
        val range = 3.0
        val box = Box(target).expand(range)
        val animal = world.getEntitiesByClass(AnimalEntity::class.java, box) { it.isAlive }
            .filter { canBreed(it, world) }
            .minByOrNull { it.squaredDistanceTo(entity) }

        if (animal != null) {
            // Find matching food in inventory
            for (slot in 0 until pokemonInv.size()) {
                val stack = pokemonInv.getStack(slot)
                if (stack.isEmpty || !animal.isBreedingItem(stack)) continue

                animal.lovePlayer(null)
                recentlyFed[animal.uuid] = world.time
                stack.decrement(1)
                if (stack.isEmpty) pokemonInv.setStack(slot, ItemStack.EMPTY)
                PastureWorkerManager.markDirtyNow(world)
                break
            }
        }

        return WorkResult.Done()
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        // Valid if it's a container or there are animals nearby
        if (ContainerFinder.isContainer(world, target)) return true
        val box = Box(target).expand(3.0)
        return world.getEntitiesByClass(AnimalEntity::class.java, box) { it.isAlive }.isNotEmpty()
    }

    fun cleanup(pokemonId: UUID) {
        // Clean old entries periodically
        val cutoff = recentlyFed.values.minOrNull()?.let { it + 6000 } ?: return
        recentlyFed.entries.removeIf { it.value < cutoff }
    }

    private fun findBreedableAnimal(world: World, center: BlockPos, range: Int): AnimalEntity? {
        val box = Box(center).expand(range.toDouble())
        return world.getEntitiesByClass(AnimalEntity::class.java, box) { it.isAlive }
            .filter { canBreed(it, world) }
            .minByOrNull { it.squaredDistanceTo(center.x + 0.5, center.y + 0.5, center.z + 0.5) }
    }

    private fun canBreed(animal: AnimalEntity, world: World): Boolean {
        if (animal.isBaby) return false
        if (animal.loveTicks > 0) return false
        if (animal.breedingAge > 0) return false
        // Don't re-feed animals we recently fed
        val lastFed = recentlyFed[animal.uuid] ?: return true
        return world.time - lastFed > 6000 // 5 minutes cooldown
    }

    private fun findFoodContainer(world: World, origin: BlockPos, tag: TagInstance, range: Int): BlockPos? {
        val exclude = mutableSetOf(origin)
        return ContainerFinder.findClosestMatching(world, origin, range, exclude) { _, pos ->
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
            PastureWorkerManager.markDirtyNow(world)
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

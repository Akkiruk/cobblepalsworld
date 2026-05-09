package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.navigation.ContainerFinder
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.filter.FilterMatcher
import net.minecraft.item.ItemStack
import net.minecraft.recipe.RecipeType
import net.minecraft.recipe.SmeltingRecipe
import net.minecraft.recipe.input.SingleStackRecipeInput
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Smelts raw items from containers using the Pokémon's natural fire power.
 * Extracts smeltable items, transforms them via smelting recipes, and
 * returns the cooked/smelted results for deposit.
 *
 * Bound → exclusive source container.
 * Unbound → finds nearest container with smeltable items.
 */
object SmelterBehavior : TagBehavior {
    override val tagType = TagType.SMELTER
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val range = effectiveRange(tag, state)

        val boundPos = tag.boundPos
        if (boundPos != null) {
            return if (ContainerFinder.isContainer(world, boundPos)
                && hasSmeltableItems(world, boundPos, tag)
            ) boundPos else null
        }

        return BlockPos.iterateOutwards(origin, range, range / 2, range)
            .firstOrNull { pos ->
                ContainerFinder.isContainer(world, pos) && hasSmeltableItems(world, pos, tag)
            }?.toImmutable()
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        if (world !is ServerWorld) return WorkResult.Done()
        val container = ContainerFinder.getInventoryAt(world, target) ?: return WorkResult.Done()
        val maxItems = effectiveMaxItems(tag, state)
        val smelted = mutableListOf<ItemStack>()
        var processed = 0

        for (slot in 0 until container.size()) {
            if (processed >= maxItems) break
            val stack = container.getStack(slot)
            if (stack.isEmpty) continue
            if (!FilterMatcher.matches(stack, tag.filter)) continue

            val result = getSmeltingResult(world, stack) ?: continue
            val toProcess = minOf(stack.count, maxItems - processed)
            smelted.add(result.copyWithCount(toProcess))
            stack.decrement(toProcess)
            if (stack.isEmpty) container.setStack(slot, ItemStack.EMPTY)
            processed += toProcess
        }

        container.markDirty()
        return WorkResult.Done(smelted)
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return ContainerFinder.isContainer(world, target)
    }

    private fun hasSmeltableItems(world: World, pos: BlockPos, tag: TagInstance): Boolean {
        if (world !is ServerWorld) return false
        val container = ContainerFinder.getInventoryAt(world, pos) ?: return false
        for (slot in 0 until container.size()) {
            val stack = container.getStack(slot)
            if (!stack.isEmpty && FilterMatcher.matches(stack, tag.filter)
                && getSmeltingResult(world, stack) != null
            ) return true
        }
        return false
    }

    private fun getSmeltingResult(world: ServerWorld, input: ItemStack): ItemStack? {
        val recipeManager = world.recipeManager
        val recipe = recipeManager.getFirstMatch(
            RecipeType.SMELTING,
            SingleStackRecipeInput(input),
            world
        )
        return recipe.map { it.value.getResult(world.registryManager) }.orElse(null)
    }
}

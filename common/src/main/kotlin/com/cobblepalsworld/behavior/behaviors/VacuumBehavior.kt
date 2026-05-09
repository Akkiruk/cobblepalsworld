package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.filter.FilterMatcher
import net.minecraft.entity.ExperienceOrbEntity
import net.minecraft.entity.ItemEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.World

object VacuumBehavior : TagBehavior {
    override val tagType = TagType.VACUUM
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val range = effectiveRange(tag, state)
        val searchBox = Box(
            origin.x.toDouble() - range, origin.y.toDouble() - range / 2.0, origin.z.toDouble() - range,
            origin.x.toDouble() + range + 1, origin.y.toDouble() + range / 2.0 + 1, origin.z.toDouble() + range + 1
        )

        // Check for items
        val closestItem = world.getEntitiesByClass(ItemEntity::class.java, searchBox) { itemEntity ->
            itemEntity.isAlive && FilterMatcher.matches(itemEntity.stack, tag.filter)
        }.minByOrNull { it.squaredDistanceTo(entity) }

        if (closestItem != null) return closestItem.blockPos

        // XP Vacuum augment: also target XP orbs
        if (tag.augments.vacuumsXp()) {
            val closestXp = world.getEntitiesByClass(ExperienceOrbEntity::class.java, searchBox) { it.isAlive }
                .minByOrNull { it.squaredDistanceTo(entity) }
            if (closestXp != null) return closestXp.blockPos
        }

        return null
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        val range = effectiveRange(tag, state).toDouble()
        val searchBox = Box(
            entity.x - range, entity.y - range, entity.z - range,
            entity.x + range, entity.y + range, entity.z + range
        )

        // Collect items (limited by free slots and maxItemsPerTrip)
        val items = world.getEntitiesByClass(ItemEntity::class.java, searchBox) { itemEntity ->
            itemEntity.isAlive && FilterMatcher.matches(itemEntity.stack, tag.filter)
        }
        val inventory = com.cobblepalsworld.inventory.InventoryManager.getOrCreate(entity.pokemon)
        val maxItems = effectiveMaxItems(tag, state)
        var freeSlots = (0 until inventory.size()).count { inventory.getStack(it).isEmpty }
        var totalCollected = 0
        val collected = mutableListOf<net.minecraft.item.ItemStack>()
        for (itemEntity in items) {
            if (freeSlots <= 0 || totalCollected >= maxItems) break
            val stack = itemEntity.stack
            val canTake = minOf(stack.count, maxItems - totalCollected)
            if (canTake < stack.count) {
                // Partial pickup — only take what we can carry
                collected.add(stack.copyWithCount(canTake))
                stack.decrement(canTake)
            } else {
                collected.add(stack.copy())
                itemEntity.discard()
            }
            totalCollected += canTake
            freeSlots--
        }

        // XP Vacuum augment: collect XP orbs and award to nearest player
        if (tag.augments.vacuumsXp()) {
            val xpOrbs = world.getEntitiesByClass(ExperienceOrbEntity::class.java, searchBox) { it.isAlive }
            if (xpOrbs.isNotEmpty()) {
                val nearestPlayer = world.getClosestPlayer(entity.x, entity.y, entity.z, range * 2, false)
                if (nearestPlayer is ServerPlayerEntity) {
                    var totalXp = 0
                    for (orb in xpOrbs) {
                        totalXp += orb.experienceAmount
                        orb.discard()
                    }
                    nearestPlayer.addExperience(totalXp)
                }
            }
        }

        return WorkResult.Done(collected)
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        return true // Item/XP entities are transient
    }
}

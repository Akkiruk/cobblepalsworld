package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import com.cobblepalsworld.tag.filter.FilterMatcher
import com.cobblepalsworld.tag.filter.TagFilter
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object BreakerBehavior : TagBehavior {
    override val tagType = TagType.BREAKER
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range

    // Blocks that must NEVER be broken — exploit prevention + world protection
    private val BANNED_BLOCKS: Set<Block> = setOf(
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

    // Blocks identified by registry path patterns (catches all variants)
    private val BANNED_PATTERNS = listOf(
        "shulker_box",  // all colored shulker boxes
        "pasture",      // Cobblemon pasture blocks — NEVER break your own home
    )

    /** Tracks the pasture origin per-pokemon so we never break our own pasture */
    private val pastureOrigins = java.util.concurrent.ConcurrentHashMap<java.util.UUID, BlockPos>()

    fun setPastureOrigin(pokemonId: java.util.UUID, origin: BlockPos) {
        pastureOrigins[pokemonId] = origin
    }

    fun clearPastureOrigin(pokemonId: java.util.UUID) {
        pastureOrigins.remove(pokemonId)
    }

    private fun isBlockBanned(block: Block, pos: BlockPos, pokemonId: java.util.UUID): Boolean {
        if (block in BANNED_BLOCKS) return true

        val blockId = Registries.BLOCK.getId(block).path
        if (BANNED_PATTERNS.any { pattern -> blockId.contains(pattern) }) return true

        // Never break the pokemon's own pasture block
        val origin = pastureOrigins[pokemonId]
        if (origin != null && pos == origin) return true

        return false
    }

    private fun isBreakable(world: World, pos: BlockPos, pokemonId: java.util.UUID): Boolean {
        val blockState = world.getBlockState(pos)
        if (blockState.isAir) return false
        if (blockState.getHardness(world, pos) < 0f) return false // unbreakable
        if (isBlockBanned(blockState.block, pos, pokemonId)) return false
        return true
    }

    override fun findTarget(
        world: World, origin: BlockPos, entity: PokemonEntity,
        tag: TagInstance, state: WorkerState
    ): BlockPos? {
        val pokemonId = entity.pokemon.uuid
        val range = effectiveRange(tag, state)
        setPastureOrigin(pokemonId, origin)

        // Bound breaker: only break the specific bound block (if valid)
        val boundPos = tag.boundPos
        if (boundPos != null) {
            return if (isBreakable(world, boundPos, pokemonId)
                && matchesFilter(world.getBlockState(boundPos).block, tag.filter)
                && !ClaimManager.isClaimedByOther(boundPos, pokemonId, world)
            ) boundPos else null
        }

        return BlockPos.iterateOutwards(origin, range, range / 2, range)
            .firstOrNull { pos ->
                isBreakable(world, pos, pokemonId)
                    && matchesFilter(world.getBlockState(pos).block, tag.filter)
                    && !ClaimManager.isClaimedByOther(pos, pokemonId, world)
            }?.toImmutable()
    }

    override fun doWork(
        world: World, entity: PokemonEntity, target: BlockPos,
        tag: TagInstance, state: WorkerState
    ): WorkResult {
        val blockState = world.getBlockState(target)
        if (blockState.isAir) return WorkResult.Done()

        // Final safety check before breaking
        if (isBlockBanned(blockState.block, target, entity.pokemon.uuid)) return WorkResult.Done()

        val serverWorld = world as? ServerWorld ?: return WorkResult.Done()
        val toolStack = ItemStack(Items.NETHERITE_PICKAXE)
        val drops = Block.getDroppedStacks(
            blockState, serverWorld, target,
            world.getBlockEntity(target),
            entity, toolStack
        )
        world.syncWorldEvent(net.minecraft.world.WorldEvents.BLOCK_BROKEN, target, Block.getRawIdFromState(blockState))
        world.setBlockState(target, Blocks.AIR.defaultState)
        return WorkResult.Done(drops)
    }

    override fun isTargetValid(world: World, target: BlockPos, tag: TagInstance): Boolean {
        val blockState = world.getBlockState(target)
        return !blockState.isAir && matchesFilter(blockState.block, tag.filter)
    }

    private fun matchesFilter(block: Block, filter: TagFilter): Boolean {
        val blockItem = block.asItem() ?: return false
        return FilterMatcher.matches(ItemStack(blockItem), filter)
    }
}

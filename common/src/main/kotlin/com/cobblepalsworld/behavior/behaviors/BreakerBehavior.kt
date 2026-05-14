package com.cobblepalsworld.behavior.behaviors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.WorkResult
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.tag.TagInstance
import com.cobblepalsworld.tag.TagType
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.BlockTags
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object BreakerBehavior : TagBehavior {
    override val tagType = TagType.BREAKER
    override val defaultRange get() = ConfigManager.config.getTagConfig(tagType).range
    override fun arrivalDelayTicks(tag: TagInstance, state: WorkerState): Long = 0L
    override fun cooldownTicks(tag: TagInstance, state: WorkerState): Long = 1L

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
        "pasture",      // Cobblemon pasture blocks stay protected for migrated worlds
    )

    private val worksiteOrigins = java.util.concurrent.ConcurrentHashMap<java.util.UUID, BlockPos>()

    fun setWorksiteOrigin(pokemonId: java.util.UUID, origin: BlockPos) {
        worksiteOrigins[pokemonId] = origin
    }

    fun clearWorksiteOrigin(pokemonId: java.util.UUID) {
        worksiteOrigins.remove(pokemonId)
    }

    fun clearAllWorksiteOrigins() {
        worksiteOrigins.clear()
    }

    private fun isBlockBanned(block: Block, pos: BlockPos, pokemonId: java.util.UUID): Boolean {
        if (block in BANNED_BLOCKS) return true

        val blockId = Registries.BLOCK.getId(block).path
        if (BANNED_PATTERNS.any { pattern -> blockId.contains(pattern) }) return true

        val origin = worksiteOrigins[pokemonId]
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
        setWorksiteOrigin(pokemonId, origin)

        val boundPos = tag.boundPos ?: return null
        return if (isBreakable(world, boundPos, pokemonId)
            && !ClaimManager.isClaimedByOther(boundPos, pokemonId, world)
        ) boundPos.toImmutable() else null
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
        val toolStack = miningToolFor(blockState)
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
        return !world.getBlockState(target).isAir
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
}

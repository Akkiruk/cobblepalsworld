package com.cobblepalsworld.router

import com.mojang.serialization.MapCodec
import net.minecraft.block.Block
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityTicker
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.state.StateManager
import net.minecraft.state.property.Properties
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Formatting
import net.minecraft.util.ItemScatterer
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView
import net.minecraft.world.World
import net.minecraft.inventory.Inventory

class RouterBlock(settings: Settings) : BlockWithEntity(settings) {
    companion object {
        val CODEC: MapCodec<RouterBlock> = createCodec(::RouterBlock)
    }

    init {
        defaultState = stateManager.defaultState
            .with(Properties.HORIZONTAL_FACING, net.minecraft.util.math.Direction.NORTH)
            .with(Properties.POWERED, false)
    }

    override fun getCodec(): MapCodec<out BlockWithEntity> = CODEC

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(Properties.HORIZONTAL_FACING, Properties.POWERED)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState {
        return defaultState.with(Properties.HORIZONTAL_FACING, ctx.horizontalPlayerFacing.opposite)
    }

    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = RouterBlockEntity(pos, state)

    override fun getRenderType(state: BlockState): BlockRenderType = BlockRenderType.MODEL

    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hit: BlockHitResult): ActionResult {
        val blockEntity = world.getBlockEntity(pos) as? RouterBlockEntity ?: return ActionResult.PASS
        if (!blockEntity.canAccess(player)) {
            if (!world.isClient) {
                player.sendMessage(Text.literal("This router is secured to another player.").formatted(Formatting.RED), true)
            }
            return ActionResult.SUCCESS
        }

        if (!world.isClient) {
            player.openHandledScreen(blockEntity)
        }
        return ActionResult.SUCCESS
    }

    override fun onPlaced(world: World, pos: BlockPos, state: BlockState, placer: LivingEntity?, itemStack: ItemStack) {
        super.onPlaced(world, pos, state, placer, itemStack)
        val player = placer as? PlayerEntity ?: return
        (world.getBlockEntity(pos) as? RouterBlockEntity)?.setOwner(player)
    }

    override fun onStateReplaced(state: BlockState, world: World, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.isOf(newState.block)) {
            (world.getBlockEntity(pos) as? RouterBlockEntity)?.let { router ->
                ItemScatterer.spawn(world, pos, router)
                world.updateComparators(pos, this)
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved)
    }

    override fun hasComparatorOutput(state: BlockState): Boolean = true

    override fun getComparatorOutput(state: BlockState, world: World, pos: BlockPos): Int {
        val inventory = world.getBlockEntity(pos) as? RouterBlockEntity ?: return 0
        return ScreenHandler.calculateComparatorOutput(inventory as Inventory)
    }

    override fun emitsRedstonePower(state: BlockState): Boolean = true

    override fun getWeakRedstonePower(state: BlockState, world: BlockView, pos: BlockPos, direction: net.minecraft.util.math.Direction): Int {
        return if (state.get(Properties.POWERED)) 15 else 0
    }

    override fun getStrongRedstonePower(state: BlockState, world: BlockView, pos: BlockPos, direction: net.minecraft.util.math.Direction): Int {
        return if (state.get(Properties.POWERED)) 15 else 0
    }

    override fun <T : BlockEntity> getTicker(
        world: World,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        return validateTicker(type, RouterRegistry.ROUTER_BLOCK_ENTITY.get(), RouterBlockEntity::tick)
    }
}
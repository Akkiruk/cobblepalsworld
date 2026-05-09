package com.cobblepalsworld.fabric

import com.cobblepalsworld.platform.ActivatorPlatformBridge
import net.fabricmc.fabric.api.entity.FakePlayer
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult

object FabricActivatorPlatformBridge : ActivatorPlatformBridge.Hooks {
    override fun fakePlayer(world: ServerWorld): ServerPlayerEntity = FakePlayer.get(world)

    override fun useItemOnBlock(
        player: ServerPlayerEntity,
        world: ServerWorld,
        stack: ItemStack,
        hitResult: BlockHitResult
    ): Boolean {
        return player.interactionManager.interactBlock(player, world, stack, Hand.MAIN_HAND, hitResult).isAccepted
    }

    override fun useItem(player: ServerPlayerEntity, world: ServerWorld, stack: ItemStack): Boolean {
        return player.interactionManager.interactItem(player, world, stack, Hand.MAIN_HAND).isAccepted
    }

    override fun interactEntity(player: ServerPlayerEntity, target: Entity): Boolean {
        return player.interact(target, Hand.MAIN_HAND).isAccepted
    }

    override fun attackEntity(player: ServerPlayerEntity, target: Entity): Boolean {
        if (!target.isAttackable) return false
        player.attack(target)
        return true
    }
}
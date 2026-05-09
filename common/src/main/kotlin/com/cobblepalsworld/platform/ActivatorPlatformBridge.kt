package com.cobblepalsworld.platform

import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.hit.BlockHitResult

object ActivatorPlatformBridge {
    interface Hooks {
        fun fakePlayer(world: ServerWorld): ServerPlayerEntity
        fun useItemOnBlock(player: ServerPlayerEntity, world: ServerWorld, stack: ItemStack, hitResult: BlockHitResult): Boolean
        fun useItem(player: ServerPlayerEntity, world: ServerWorld, stack: ItemStack): Boolean
        fun interactEntity(player: ServerPlayerEntity, target: Entity): Boolean
    }

    lateinit var hooks: Hooks

    fun fakePlayer(world: ServerWorld): ServerPlayerEntity = hooks.fakePlayer(world)

    fun useItemOnBlock(player: ServerPlayerEntity, world: ServerWorld, stack: ItemStack, hitResult: BlockHitResult): Boolean {
        return hooks.useItemOnBlock(player, world, stack, hitResult)
    }

    fun useItem(player: ServerPlayerEntity, world: ServerWorld, stack: ItemStack): Boolean {
        return hooks.useItem(player, world, stack)
    }

    fun interactEntity(player: ServerPlayerEntity, target: Entity): Boolean {
        return hooks.interactEntity(player, target)
    }
}
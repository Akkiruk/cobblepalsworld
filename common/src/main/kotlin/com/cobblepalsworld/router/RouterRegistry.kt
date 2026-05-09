package com.cobblepalsworld.router

import com.cobblepalsworld.CobblePalsWorld
import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.RegistrySupplier
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKeys
import net.minecraft.sound.BlockSoundGroup

object RouterRegistry {
    private val BLOCKS = DeferredRegister.create(CobblePalsWorld.MODID, RegistryKeys.BLOCK)
    private val ITEMS = DeferredRegister.create(CobblePalsWorld.MODID, RegistryKeys.ITEM)
    private val BLOCK_ENTITIES = DeferredRegister.create(CobblePalsWorld.MODID, RegistryKeys.BLOCK_ENTITY_TYPE)

    // Keep the router id so existing worlds upgrade in place to the Command Post block.
    val ROUTER: RegistrySupplier<RouterBlock> = BLOCKS.register("router") {
        RouterBlock(
            AbstractBlock.Settings.create()
                .strength(4.0f)
                .sounds(BlockSoundGroup.METAL)
                .requiresTool()
        )
    }

    val ROUTER_ITEM: RegistrySupplier<BlockItem> = ITEMS.register("router") {
        BlockItem(ROUTER.get(), Item.Settings())
    }

    val ROUTER_BLOCK_ENTITY: RegistrySupplier<BlockEntityType<RouterBlockEntity>> =
        BLOCK_ENTITIES.register("router") {
            BlockEntityType.Builder.create(::RouterBlockEntity, ROUTER.get()).build(null)
        }

    fun init() {
        BLOCKS.register()
        ITEMS.register()
        BLOCK_ENTITIES.register()
    }

    fun allStacks(): List<ItemStack> = listOf(ItemStack(ROUTER_ITEM.get()))
}
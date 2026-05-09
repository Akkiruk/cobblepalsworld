package com.cobblepalsworld.augment

import com.cobblepalsworld.CobblePalsWorld
import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.RegistrySupplier
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKeys

object AugmentRegistry {
    private val ITEMS = DeferredRegister.create(CobblePalsWorld.MODID, RegistryKeys.ITEM)
    private val registered = mutableMapOf<AugmentType, RegistrySupplier<AugmentItem>>()

    fun init() {
        for (type in AugmentType.entries) {
            val supplier = ITEMS.register(type.id + "_augment") {
                AugmentItem(type, Item.Settings().maxCount(16))
            }
            registered[type] = supplier
        }
        ITEMS.register()
    }

    fun getItem(type: AugmentType): AugmentItem? = registered[type]?.get()

    fun allItems(): List<RegistrySupplier<AugmentItem>> = registered.values.toList()

    fun allStacks(): List<ItemStack> = registered.values.map { ItemStack(it.get()) }
}

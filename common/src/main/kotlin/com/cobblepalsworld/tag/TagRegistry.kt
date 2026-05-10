package com.cobblepalsworld.tag

import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.augment.AugmentRegistry
import com.cobblepalsworld.router.RouterRegistry
import dev.architectury.registry.CreativeTabRegistry
import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.RegistrySupplier
import net.minecraft.inventory.Inventory
import net.minecraft.item.Item
import net.minecraft.item.ItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKeys
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * Registers all tag items and the creative tab via Architectury's DeferredRegister.
 */
object TagRegistry {

    private val ITEMS = DeferredRegister.create(CobblePalsWorld.MODID, RegistryKeys.ITEM)
    private val TABS = DeferredRegister.create(CobblePalsWorld.MODID, RegistryKeys.ITEM_GROUP)

    private val registered = mutableMapOf<TagType, RegistrySupplier<TagItem>>()
    private val legacyItemIds = mapOf(
        TagType.COURIER to listOf("courier"),
        TagType.STASHER to listOf("stasher")
    )

    val TAB: RegistrySupplier<ItemGroup> = TABS.register("cobblepalsworld") {
        CreativeTabRegistry.create {
            it.icon { ItemStack(registered[TagType.BREAKER]?.get() ?: return@icon ItemStack.EMPTY) }
            it.displayName(Text.translatable("itemGroup.cobblepalsworld"))
            it.entries { _, entries ->
                RouterRegistry.allStacks().forEach { stack -> entries.add(stack) }
                registered.values.forEach { supplier -> entries.add(ItemStack(supplier.get())) }
                AugmentRegistry.allStacks().forEach { stack -> entries.add(stack) }
            }
        }
    }

    fun init() {
        for (type in TagType.entries) {
            val supplier = ITEMS.register(type.id + "_tag") {
                TagItem(type, Item.Settings().maxCount(1))
            }
            registered[type] = supplier

            legacyItemIds[type].orEmpty().forEach { legacyId ->
                ITEMS.register(legacyId + "_tag") {
                    TagItem(type, Item.Settings().maxCount(1), isCanonicalItem = false)
                }
            }
        }
        ITEMS.register()
        TABS.register()
    }

    fun getItem(type: TagType): TagItem? = registered[type]?.get()

    fun allItems(): List<RegistrySupplier<TagItem>> = registered.values.toList()

    fun normalizeStack(stack: ItemStack): ItemStack {
        val tagItem = stack.item as? TagItem ?: return stack
        if (tagItem.isCanonicalItem) return stack

        val canonicalItem = getItem(tagItem.tagType) ?: return stack
        return stack.copyComponentsToNewStack(canonicalItem, stack.count)
    }

    fun normalizeInventorySlot(inventory: Inventory, slot: Int): ItemStack {
        val stack = inventory.getStack(slot)
        val normalized = normalizeStack(stack)
        if (normalized.item !== stack.item) {
            inventory.setStack(slot, normalized)
        }
        return normalized
    }
}

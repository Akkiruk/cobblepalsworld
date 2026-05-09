package com.cobblepalsworld

import net.minecraft.entity.EntityType
import net.minecraft.item.Item
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier

object CobblePalsWorldTags {
    object Items {
        val MODULES: TagKey<Item> = modTag("modules")
        val AUGMENTS: TagKey<Item> = modTag("augments")
        val ACTIVATOR_BLACKLIST: TagKey<Item> = modTag("activator_blacklist")

        private fun modTag(name: String): TagKey<Item> {
            return TagKey.of(RegistryKeys.ITEM, Identifier.of(CobblePalsWorld.MODID, name))
        }
    }

    object EntityTypes {
        val ACTIVATOR_INTERACT_BLACKLIST: TagKey<EntityType<*>> = modTag("activator_interact_blacklist")

        private fun modTag(name: String): TagKey<EntityType<*>> {
            return TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(CobblePalsWorld.MODID, name))
        }
    }
}
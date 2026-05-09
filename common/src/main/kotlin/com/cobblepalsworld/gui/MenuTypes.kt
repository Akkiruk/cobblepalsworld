package com.cobblepalsworld.gui

import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.gui.assignment.PokemonTagScreenHandler
import com.cobblepalsworld.gui.filter.TagFilterScreenHandler
import com.cobblepalsworld.gui.router.RouterScreenHandler
import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.RegistrySupplier
import net.minecraft.registry.RegistryKeys
import net.minecraft.resource.featuretoggle.FeatureFlags
import net.minecraft.screen.ScreenHandlerType

object MenuTypes {
    private val MENU_TYPES = DeferredRegister.create(CobblePalsWorld.MODID, RegistryKeys.SCREEN_HANDLER)

    val TAG_FILTER: RegistrySupplier<ScreenHandlerType<TagFilterScreenHandler>> =
        MENU_TYPES.register("tag_filter") {
            ScreenHandlerType(::TagFilterScreenHandler, FeatureFlags.VANILLA_FEATURES)
        }

    val POKEMON_TAG: RegistrySupplier<ScreenHandlerType<PokemonTagScreenHandler>> =
        MENU_TYPES.register("pokemon_tag") {
            ScreenHandlerType(::PokemonTagScreenHandler, FeatureFlags.VANILLA_FEATURES)
        }

    val ROUTER: RegistrySupplier<ScreenHandlerType<RouterScreenHandler>> =
        MENU_TYPES.register("router") {
            ScreenHandlerType(::RouterScreenHandler, FeatureFlags.VANILLA_FEATURES)
        }

    fun init() {
        MENU_TYPES.register()
    }
}

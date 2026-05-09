package com.cobblepalsworld

import com.cobblepalsworld.gui.MenuTypes
import com.cobblepalsworld.gui.assignment.PokemonTagScreen
import com.cobblepalsworld.gui.assignment.PokemonTagScreenHandler
import com.cobblepalsworld.gui.filter.TagFilterScreen
import com.cobblepalsworld.gui.filter.TagFilterScreenHandler
import com.cobblepalsworld.gui.pasture.PastureManagerScreen
import com.cobblepalsworld.networking.CobblePalsNetworking
import dev.architectury.registry.menu.MenuRegistry
import net.minecraft.client.MinecraftClient

object CobblePalsWorldClient {
    fun init() {
        MenuRegistry.registerScreenFactory(MenuTypes.TAG_FILTER.get()) { handler: TagFilterScreenHandler, inv, title ->
            TagFilterScreen(handler, inv, title)
        }
        MenuRegistry.registerScreenFactory(MenuTypes.POKEMON_TAG.get()) { handler: PokemonTagScreenHandler, inv, title ->
            PokemonTagScreen(handler, inv, title)
        }

        CobblePalsNetworking.registerClient { snapshot ->
            MinecraftClient.getInstance().setScreen(PastureManagerScreen(snapshot))
        }
    }
}

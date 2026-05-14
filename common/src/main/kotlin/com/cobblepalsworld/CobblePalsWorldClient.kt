package com.cobblepalsworld

import com.cobblepalsworld.gui.MenuTypes
import com.cobblepalsworld.gui.assignment.PokemonTagScreen
import com.cobblepalsworld.gui.assignment.PokemonTagScreenHandler
import com.cobblepalsworld.gui.filter.TagFilterScreen
import com.cobblepalsworld.gui.filter.TagFilterScreenHandler
import com.cobblepalsworld.gui.pasture.PastureManagerScreen
import com.cobblepalsworld.gui.router.RouterScreen
import com.cobblepalsworld.gui.router.RouterScreenHandler
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
        MenuRegistry.registerScreenFactory(MenuTypes.ROUTER.get()) { handler: RouterScreenHandler, inv, title ->
            RouterScreen(handler, inv, title)
        }

        CobblePalsNetworking.registerClient { snapshot ->
            val client = MinecraftClient.getInstance()
            val currentScreen = client.currentScreen
            if (currentScreen is PastureManagerScreen && currentScreen.appliesTo(snapshot.pasturePos)) {
                currentScreen.updateSnapshot(snapshot)
            } else {
                client.setScreen(PastureManagerScreen(snapshot))
            }
        }
    }
}

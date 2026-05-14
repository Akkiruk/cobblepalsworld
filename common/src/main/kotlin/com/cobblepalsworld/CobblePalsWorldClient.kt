package com.cobblepalsworld

import com.cobblepalsworld.gui.MenuTypes
import com.cobblepalsworld.gui.assignment.PokemonTagScreen
import com.cobblepalsworld.gui.assignment.PokemonTagScreenHandler
import com.cobblepalsworld.gui.crew.CrewSourceSnapshotCache
import com.cobblepalsworld.gui.crew.CommandPostCrewSnapshotCache
import com.cobblepalsworld.gui.filter.TagFilterScreen
import com.cobblepalsworld.gui.filter.TagFilterScreenHandler
import com.cobblepalsworld.gui.router.CommandPostPcScreen
import com.cobblepalsworld.gui.router.RouterScreenHandler
import com.cobblepalsworld.networking.CobblePalsNetworking
import com.cobblepalsworld.visual.WorkerOverlayRenderer
import dev.architectury.registry.menu.MenuRegistry

object CobblePalsWorldClient {
    fun init() {
        MenuRegistry.registerScreenFactory(MenuTypes.TAG_FILTER.get()) { handler: TagFilterScreenHandler, inv, title ->
            TagFilterScreen(handler, inv, title)
        }
        MenuRegistry.registerScreenFactory(MenuTypes.POKEMON_TAG.get()) { handler: PokemonTagScreenHandler, inv, title ->
            PokemonTagScreen(handler, inv, title)
        }
        MenuRegistry.registerScreenFactory(MenuTypes.ROUTER.get()) { handler: RouterScreenHandler, inv, title ->
            CommandPostPcScreen(handler, inv, title)
        }

        CobblePalsNetworking.registerClient(
            onCrewSources = { routerPos, sources ->
                CrewSourceSnapshotCache.store(routerPos, sources)
            },
            onCommandPostCrew = { snapshot ->
                CommandPostCrewSnapshotCache.store(snapshot)
            },
            onWorkerVisuals = { worksitePos, visuals ->
                WorkerOverlayRenderer.replaceWorksiteVisuals(worksitePos, visuals)
            }
        )
    }
}

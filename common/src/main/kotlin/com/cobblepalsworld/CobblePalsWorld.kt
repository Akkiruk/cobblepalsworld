package com.cobblepalsworld

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.augment.AugmentRegistry
import com.cobblepalsworld.behavior.TagBehaviorRegistry
import com.cobblepalsworld.behavior.behaviors.*
import com.cobblepalsworld.crew.CommandPostCrewLifecycle
import com.cobblepalsworld.gui.MenuTypes
import com.cobblepalsworld.gui.assignment.PokemonTagScreenHandler
import com.cobblepalsworld.networking.CobblePalsNetworking
import com.cobblepalsworld.pasture.PastureWorkerManager
import com.cobblepalsworld.router.RouterRegistry
import com.cobblepalsworld.tag.TagRegistry
import dev.architectury.event.EventResult
import dev.architectury.event.events.common.InteractionEvent
import dev.architectury.event.events.common.LifecycleEvent
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Hand
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object CobblePalsWorld {
    const val MODID = "cobblepalsworld"

    @JvmField
    val LOGGER: Logger = LogManager.getLogger(MODID)

    fun init() {
        LOGGER.info("Launching {}...", MODID)
        RouterRegistry.init()
        TagRegistry.init()
        AugmentRegistry.init()
        MenuTypes.init()
        CobblePalsNetworking.registerServer()
        registerBehaviors()
        registerInteractions()
        registerLifecycle()
    }

    private fun registerBehaviors() {
        TagBehaviorRegistry.register(BreakerBehavior)
        TagBehaviorRegistry.register(GuardianBehavior)
        TagBehaviorRegistry.register(HarvesterBehavior)
        TagBehaviorRegistry.register(VacuumBehavior)
        TagBehaviorRegistry.register(SenderBehavior)      // Courier
        TagBehaviorRegistry.register(PullerBehavior)
        TagBehaviorRegistry.register(DistributorBehavior)  // Stasher
        TagBehaviorRegistry.register(DropperBehavior)
        TagBehaviorRegistry.register(VoidBehavior)
        TagBehaviorRegistry.register(ActivatorBehavior)
        TagBehaviorRegistry.register(ShepherdBehavior)
    }

    private fun registerInteractions() {
        // Shift + right-click an owned Pokemon -> open tag assignment GUI
        InteractionEvent.INTERACT_ENTITY.register { player, entity, hand ->
            if (!player.isSneaking || hand != Hand.MAIN_HAND || entity !is PokemonEntity) {
                return@register EventResult.pass()
            }
            val pokemon = entity.pokemon
            val owner = pokemon.getOwnerPlayer()
            if (owner?.uuid != player.uuid) return@register EventResult.pass()

            if (player is ServerPlayerEntity) {
                player.openHandledScreen(SimpleNamedScreenHandlerFactory(
                    { syncId, inv, _ -> PokemonTagScreenHandler(syncId, inv, pokemon.uuid) },
                    Text.translatable("screen.cobblepalsworld.pokemon_tag")
                ))
            }
            EventResult.interruptTrue()
        }
    }

    private fun registerLifecycle() {
        LifecycleEvent.SERVER_STOPPING.register { server ->
            CommandPostCrewLifecycle.recallAll(server)
            PastureWorkerManager.onServerStopping(server)
        }
    }
}

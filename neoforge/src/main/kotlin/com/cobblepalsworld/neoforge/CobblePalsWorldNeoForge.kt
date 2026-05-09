package com.cobblepalsworld.neoforge

import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.CobblePalsWorldClient
import com.cobblepalsworld.command.CobblePalsCommand
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.networking.CobblePalsNetworking
import com.cobblepalsworld.platform.ActivatorPlatformBridge
import com.cobblepalsworld.visual.TagHighlightRenderer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@Mod(CobblePalsWorld.MODID)
object CobblePalsWorldNeoForge {
    init {
        ConfigManager.init(FMLPaths.CONFIGDIR.get())
        ActivatorPlatformBridge.hooks = NeoForgeActivatorPlatformBridge
        CobblePalsWorld.init()
        NeoForge.EVENT_BUS.addListener { event: RegisterCommandsEvent ->
            CobblePalsCommand.register(event.dispatcher)
        }

        if (FMLEnvironment.dist.isClient) {
            MOD_BUS.addListener { _: FMLClientSetupEvent ->
                CobblePalsWorldClient.init()
            }
            NeoForge.EVENT_BUS.addListener { event: RenderLevelStageEvent ->
                if (event.stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                    TagHighlightRenderer.render(event.poseStack)
                }
            }
        } else {
            // On dedicated server, register S2C type since client init won't run
            CobblePalsNetworking.registerS2CType()
        }
    }
}

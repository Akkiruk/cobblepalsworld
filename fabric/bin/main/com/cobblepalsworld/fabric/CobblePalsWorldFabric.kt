package com.cobblepalsworld.fabric

import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.networking.CobblePalsNetworking
import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader

object CobblePalsWorldFabric : ModInitializer {
    override fun onInitialize() {
        ConfigManager.init(FabricLoader.getInstance().configDir)
        CobblePalsWorld.init()
        // On dedicated server, register S2C type here since client init won't run
        if (FabricLoader.getInstance().environmentType == EnvType.SERVER) {
            CobblePalsNetworking.registerS2CType()
        }
    }
}

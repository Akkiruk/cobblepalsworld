package com.cobblepalsworld.config

import com.cobblepalsworld.CobblePalsWorld
import com.google.gson.GsonBuilder
import net.minecraft.util.PathUtil
import java.nio.file.Files
import java.nio.file.Path

object ConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var configPath: Path

    var config: CobblePalsConfig = CobblePalsConfig()
        private set

    fun init(configDir: Path) {
        configPath = configDir.resolve("cobblepalsworld.json")
        load()
    }

    fun load() {
        try {
            if (Files.exists(configPath)) {
                val json = Files.readString(configPath)
                config = gson.fromJson(json, CobblePalsConfig::class.java) ?: CobblePalsConfig.withDefaults()
                // Validate numeric constraints to prevent division-by-zero / modulo-by-zero
                config = config.copy(general = config.general.validated())
                if (!config.tags.containsKey("sender") && config.tags.containsKey("courier")) {
                    config.tags["sender"] = config.tags["courier"]!!
                }
                if (!config.tags.containsKey("distributor") && config.tags.containsKey("stasher")) {
                    config.tags["distributor"] = config.tags["stasher"]!!
                }
                // Fill in defaults for any new tag types not yet in the config file
                for ((id, defaults) in CobblePalsConfig.TAG_DEFAULTS) {
                    config.tags.putIfAbsent(id, defaults)
                }
            } else {
                config = CobblePalsConfig.withDefaults()
                save()
            }
        } catch (e: Exception) {
            CobblePalsWorld.LOGGER.error("Failed to load config, using defaults", e)
            config = CobblePalsConfig.withDefaults()
        }
    }

    fun save() {
        try {
            Files.createDirectories(configPath.parent)
            Files.writeString(configPath, gson.toJson(config))
        } catch (e: Exception) {
            CobblePalsWorld.LOGGER.error("Failed to save config", e)
        }
    }
}

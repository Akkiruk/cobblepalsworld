package com.cobblepalsworld.persistence

import com.cobblepalsworld.CobblePalsWorld
import net.minecraft.nbt.NbtCompound

/**
 * Versioned migration pipeline for the mod's world save data.
 *
 * Every save written by [CobblePalsSaveData] carries a `SaveVersion` integer.
 * Saves written before versioning existed are treated as version 1. When the
 * structure of any persisted section changes, bump [CURRENT_VERSION] and add a
 * [SaveMigration] step that rewrites the previous structure in place; steps
 * are applied sequentially so a save from any older version can always be
 * brought up to date in one load.
 */
object SaveMigrations {

    /** Bump whenever the persisted NBT structure changes in a breaking way. */
    const val CURRENT_VERSION = 2

    /** Version assigned to saves written before the version field existed. */
    private const val LEGACY_VERSION = 1

    const val VERSION_KEY = "SaveVersion"

    fun interface SaveMigration {
        /** Rewrites [nbt] in place from `fromVersion` to `fromVersion + 1`. */
        fun migrate(nbt: NbtCompound)
    }

    /** migrations[v] upgrades a save from version v to v + 1. */
    private val migrations = mutableMapOf<Int, SaveMigration>(
        // 1 -> 2: formalized versioning. The structure itself is unchanged, but
        // legacy assignment profiles default AllowFallback=true when absent;
        // make that explicit so later migrations can rely on the key existing.
        LEGACY_VERSION to SaveMigration { nbt ->
            if (nbt.contains("WorkerProfiles")) {
                val profiles = nbt.getCompound("WorkerProfiles")
                for (key in profiles.keys.toList()) {
                    val profile = profiles.getCompound(key)
                    if (!profile.contains("AllowFallback")) {
                        profile.putBoolean("AllowFallback", true)
                        profiles.put(key, profile)
                    }
                }
            }
        }
    )

    fun versionOf(nbt: NbtCompound): Int =
        if (nbt.contains(VERSION_KEY)) nbt.getInt(VERSION_KEY) else LEGACY_VERSION

    fun stamp(nbt: NbtCompound) {
        nbt.putInt(VERSION_KEY, CURRENT_VERSION)
    }

    /**
     * Upgrades [nbt] in place to [CURRENT_VERSION]. Returns the version the
     * save was loaded at. A save newer than this build is left untouched and
     * logged loudly (the per-section parsers skip what they don't understand).
     */
    fun upgrade(nbt: NbtCompound): Int {
        val loadedVersion = versionOf(nbt)
        if (loadedVersion > CURRENT_VERSION) {
            CobblePalsWorld.LOGGER.warn(
                "CobblePals World save data is version {} but this build understands up to {}. " +
                    "Loading anyway; unknown data will be preserved where possible.",
                loadedVersion, CURRENT_VERSION
            )
            return loadedVersion
        }
        var version = loadedVersion
        while (version < CURRENT_VERSION) {
            val step = migrations[version]
            if (step == null) {
                CobblePalsWorld.LOGGER.error("Missing save migration step {} -> {}; data may load incompletely", version, version + 1)
                break
            }
            step.migrate(nbt)
            version++
        }
        if (loadedVersion < CURRENT_VERSION) {
            CobblePalsWorld.LOGGER.info("Migrated CobblePals World save data from version {} to {}", loadedVersion, CURRENT_VERSION)
        }
        return loadedVersion
    }
}

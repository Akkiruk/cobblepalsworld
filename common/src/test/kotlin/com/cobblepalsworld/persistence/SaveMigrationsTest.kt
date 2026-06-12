package com.cobblepalsworld.persistence

import net.minecraft.nbt.NbtCompound
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaveMigrationsTest {

    @Test
    fun `unversioned saves are treated as legacy version 1`() {
        assertEquals(1, SaveMigrations.versionOf(NbtCompound()))
    }

    @Test
    fun `stamp writes the current version`() {
        val nbt = NbtCompound()
        SaveMigrations.stamp(nbt)
        assertEquals(SaveMigrations.CURRENT_VERSION, SaveMigrations.versionOf(nbt))
    }

    @Test
    fun `upgrade migrates a legacy save and reports the loaded version`() {
        val nbt = NbtCompound()
        val profiles = NbtCompound()
        val profile = NbtCompound()
        profile.putInt("Mode", 1)
        profiles.put("11111111-1111-1111-1111-111111111111", profile)
        nbt.put("WorkerProfiles", profiles)

        val loadedVersion = SaveMigrations.upgrade(nbt)

        assertEquals(1, loadedVersion)
        val migrated = nbt.getCompound("WorkerProfiles").getCompound("11111111-1111-1111-1111-111111111111")
        assertTrue(migrated.contains("AllowFallback"), "1->2 migration must make AllowFallback explicit")
        assertTrue(migrated.getBoolean("AllowFallback"))
    }

    @Test
    fun `upgrade leaves current saves untouched`() {
        val nbt = NbtCompound()
        SaveMigrations.stamp(nbt)
        assertEquals(SaveMigrations.CURRENT_VERSION, SaveMigrations.upgrade(nbt))
    }

    @Test
    fun `upgrade does not downgrade newer saves`() {
        val nbt = NbtCompound()
        nbt.putInt(SaveMigrations.VERSION_KEY, SaveMigrations.CURRENT_VERSION + 1)
        assertEquals(SaveMigrations.CURRENT_VERSION + 1, SaveMigrations.upgrade(nbt))
        assertEquals(SaveMigrations.CURRENT_VERSION + 1, SaveMigrations.versionOf(nbt))
    }
}

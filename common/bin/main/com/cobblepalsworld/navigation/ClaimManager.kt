package com.cobblepalsworld.navigation

import net.minecraft.registry.RegistryKey
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class DimPos(val dimension: RegistryKey<World>, val pos: BlockPos)

object ClaimManager {
    private val claims = ConcurrentHashMap<DimPos, UUID>()

    fun claim(pos: BlockPos, pokemonId: UUID, world: World) {
        claims[DimPos(world.registryKey, pos)] = pokemonId
    }

    fun release(pos: BlockPos, world: World) {
        claims.remove(DimPos(world.registryKey, pos))
    }

    fun releaseAll(pokemonId: UUID) {
        claims.entries.removeIf { it.value == pokemonId }
    }

    fun isClaimedByOther(pos: BlockPos, pokemonId: UUID, world: World): Boolean {
        val owner = claims[DimPos(world.registryKey, pos)] ?: return false
        return owner != pokemonId
    }

    fun clear() = claims.clear()
}

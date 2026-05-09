package com.cobblepalsworld.navigation

import net.minecraft.registry.RegistryKey
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class DimPos(val dimension: RegistryKey<World>, val pos: BlockPos)
private data class ClaimRecord(val pokemonId: UUID, var lastTouchedTick: Long)

object ClaimManager {
    private val claims = ConcurrentHashMap<DimPos, ClaimRecord>()

    fun claim(pos: BlockPos, pokemonId: UUID, world: World) {
        claims[DimPos(world.registryKey, pos)] = ClaimRecord(pokemonId, world.time)
    }

    fun touch(pos: BlockPos, pokemonId: UUID, world: World) {
        val claim = claims[DimPos(world.registryKey, pos)] ?: return
        if (claim.pokemonId == pokemonId) {
            claim.lastTouchedTick = world.time
        }
    }

    fun release(pos: BlockPos, world: World) {
        claims.remove(DimPos(world.registryKey, pos))
    }

    fun releaseAll(pokemonId: UUID) {
        claims.entries.removeIf { it.value.pokemonId == pokemonId }
    }

    fun isClaimedByOther(pos: BlockPos, pokemonId: UUID, world: World): Boolean {
        val owner = claims[DimPos(world.registryKey, pos)] ?: return false
        return owner.pokemonId != pokemonId
    }

    fun pruneStale(currentTime: Long, staleAfterTicks: Long) {
        claims.entries.removeIf { (_, claim) -> currentTime - claim.lastTouchedTick > staleAfterTicks }
    }

    fun clear() = claims.clear()
}

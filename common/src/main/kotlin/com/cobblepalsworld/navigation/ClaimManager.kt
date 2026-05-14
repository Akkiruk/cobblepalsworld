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
    private val claimsByPokemon = ConcurrentHashMap<UUID, MutableSet<DimPos>>()

    fun claim(pos: BlockPos, pokemonId: UUID, world: World) {
        val key = DimPos(world.registryKey, pos.toImmutable())
        val previous = claims.put(key, ClaimRecord(pokemonId, world.time))
        if (previous != null && previous.pokemonId != pokemonId) {
            removeIndexedClaim(previous.pokemonId, key)
        }
        claimsByPokemon.getOrPut(pokemonId) { ConcurrentHashMap.newKeySet() }.add(key)
    }

    fun touch(pos: BlockPos, pokemonId: UUID, world: World) {
        val claim = claims[DimPos(world.registryKey, pos.toImmutable())] ?: return
        if (claim.pokemonId == pokemonId) {
            claim.lastTouchedTick = world.time
        }
    }

    fun release(pos: BlockPos, world: World) {
        val removed = claims.remove(DimPos(world.registryKey, pos.toImmutable())) ?: return
        removeIndexedClaim(removed.pokemonId, DimPos(world.registryKey, pos.toImmutable()))
    }

    fun releaseAll(pokemonId: UUID) {
        val keys = claimsByPokemon.remove(pokemonId) ?: return
        keys.forEach { key ->
            val claim = claims[key]
            if (claim?.pokemonId == pokemonId) {
                claims.remove(key, claim)
            }
        }
    }

    fun isClaimedByOther(pos: BlockPos, pokemonId: UUID, world: World): Boolean {
        val owner = claims[DimPos(world.registryKey, pos.toImmutable())] ?: return false
        return owner.pokemonId != pokemonId
    }

    fun pruneStale(currentTime: Long, staleAfterTicks: Long) {
        claims.entries.removeIf { (key, claim) ->
            val stale = currentTime - claim.lastTouchedTick > staleAfterTicks
            if (stale) {
                removeIndexedClaim(claim.pokemonId, key)
            }
            stale
        }
    }

    fun count(): Int = claims.size

    fun clear() {
        claims.clear()
        claimsByPokemon.clear()
    }

    private fun removeIndexedClaim(pokemonId: UUID, key: DimPos) {
        val keys = claimsByPokemon[pokemonId] ?: return
        keys.remove(key)
        if (keys.isEmpty()) {
            claimsByPokemon.remove(pokemonId, keys)
        }
    }
}

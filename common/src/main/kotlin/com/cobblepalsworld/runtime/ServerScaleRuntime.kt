package com.cobblepalsworld.runtime

import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.navigation.NavigationBudget
import com.cobblepalsworld.navigation.WorkerNavigationManager
import com.cobblepalsworld.networking.CobblePalsNetworking
import com.cobblepalsworld.assignment.TagAssignmentManager
import com.cobblepalsworld.persistence.CobblePalsSaveData
import net.minecraft.registry.RegistryKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.concurrent.ConcurrentHashMap

private data class WorldPosKey(val dimension: RegistryKey<World>, val pos: BlockPos)
private data class PathBudgetState(var tick: Long = Long.MIN_VALUE, var remaining: Int = 0)
private data class VisualState(var tick: Long = Long.MIN_VALUE, var signature: Int = 0)
private data class NearbyPlayersState(val tick: Long, val players: List<ServerPlayerEntity>)

object ServerScaleRuntime {
    private const val MAINTENANCE_INTERVAL = 200L
    private const val STALE_ENTRY_TTL = 20L * 30L

    private val lastMaintenanceTick = ConcurrentHashMap<MinecraftServer, Long>()
    private val pathBudgets = ConcurrentHashMap<MinecraftServer, PathBudgetState>()
    private val visualStates = ConcurrentHashMap<WorldPosKey, VisualState>()
    private val nearbyPlayerCache = ConcurrentHashMap<WorldPosKey, NearbyPlayersState>()

    fun beforeWorksiteTick(world: ServerWorld) {
        CobblePalsSaveData.ensureLoaded(world)
        val server = world.server
        if (lastMaintenanceTick.put(server, world.time) == world.time) return
        if (world.time % MAINTENANCE_INTERVAL != 0L) return

        CobblePalsSaveData.markDirty(server)
        TagExecutionEngine.pruneStaleRuntime(world.time, STALE_ENTRY_TTL)
        ClaimManager.pruneStale(world.time, STALE_ENTRY_TTL)
        WorkerNavigationManager.pruneFailureCache(world.time)
        InventoryManager.pruneStale { pokemonId, _ -> TagAssignmentManager.has(pokemonId) }
        pruneTransientCaches(world.time)
    }

    fun navigationBudget(world: ServerWorld, localStarts: Int): NavigationBudget {
        return NavigationBudget(localStarts) { consumeGlobalPathStart(world) }
    }

    fun shouldSendWorksiteVisuals(
        world: ServerWorld,
        worksitePos: BlockPos,
        visuals: List<CobblePalsNetworking.WorkerVisualSnapshot>
    ): Boolean {
        val key = WorldPosKey(world.registryKey, worksitePos.toImmutable())
        val signature = visuals.fold(visuals.size) { acc, visual -> 31 * acc + visual.hashCode() }
        val interval = ConfigManager.config.general.visualUpdateIntervalTicks.toLong()
        val current = visualStates[key]
        if (current != null && current.signature == signature && world.time - current.tick < interval) {
            return false
        }
        visualStates[key] = VisualState(world.time, signature)
        return true
    }

    fun nearbyWorksitePlayers(world: ServerWorld, worksitePos: BlockPos): List<ServerPlayerEntity> {
        val key = WorldPosKey(world.registryKey, worksitePos.toImmutable())
        val cached = nearbyPlayerCache[key]
        if (cached != null && world.time - cached.tick < 10L) {
            return cached.players.filterNot { it.isRemoved }
        }

        val range = ConfigManager.config.general.nearbyPlayerRange.toDouble()
        val rangeSq = range * range
        val centerX = worksitePos.x + 0.5
        val centerY = worksitePos.y + 0.5
        val centerZ = worksitePos.z + 0.5
        val players = world.players.filter { player ->
            !player.isSpectator && player.squaredDistanceTo(centerX, centerY, centerZ) <= rangeSq
        }
        nearbyPlayerCache[key] = NearbyPlayersState(world.time, players)
        return players
    }

    fun clear(server: MinecraftServer) {
        lastMaintenanceTick.remove(server)
        pathBudgets.remove(server)
        visualStates.clear()
        nearbyPlayerCache.clear()
    }

    fun clearTransient() {
        pathBudgets.clear()
        visualStates.clear()
        nearbyPlayerCache.clear()
    }

    private fun pruneTransientCaches(currentTime: Long) {
        visualStates.entries.removeIf { (_, state) -> currentTime - state.tick > STALE_ENTRY_TTL }
        nearbyPlayerCache.entries.removeIf { (_, state) -> currentTime - state.tick > STALE_ENTRY_TTL }
    }

    private fun consumeGlobalPathStart(world: ServerWorld): Boolean {
        val limit = ConfigManager.config.general.maxGlobalPathStartsPerTick
        if (limit < 0) return true
        val budget = pathBudgets.compute(world.server) { _, current ->
            val state = current ?: PathBudgetState()
            if (state.tick != world.time) {
                state.tick = world.time
                state.remaining = limit
            }
            state
        } ?: return false
        if (budget.remaining <= 0) return false
        budget.remaining -= 1
        return true
    }
}
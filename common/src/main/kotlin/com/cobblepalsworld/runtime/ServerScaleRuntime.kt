package com.cobblepalsworld.runtime

import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.gui.pasture.PastureSnapshot
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.navigation.NavigationBudget
import com.cobblepalsworld.networking.CobblePalsNetworking
import com.cobblepalsworld.pasture.TagAssignmentManager
import com.cobblepalsworld.persistence.CobblePalsSaveData
import net.minecraft.registry.RegistryKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private data class WorldPosKey(val dimension: RegistryKey<World>, val pos: BlockPos)
private data class PathBudgetState(var tick: Long = Long.MIN_VALUE, var remaining: Int = 0)
private data class VisualState(var tick: Long = Long.MIN_VALUE, var signature: Int = 0)
private data class SnapshotState(val tick: Long, val snapshot: PastureSnapshot)
private data class NearbyPlayersState(val tick: Long, val players: List<ServerPlayerEntity>)

object ServerScaleRuntime {
    private const val MAINTENANCE_INTERVAL = 200L
    private const val STALE_ENTRY_TTL = 20L * 30L

    private val lastMaintenanceTick = ConcurrentHashMap<MinecraftServer, Long>()
    private val pathBudgets = ConcurrentHashMap<MinecraftServer, PathBudgetState>()
    private val visualStates = ConcurrentHashMap<WorldPosKey, VisualState>()
    private val snapshotCache = ConcurrentHashMap<WorldPosKey, SnapshotState>()
    private val nearbyPlayerCache = ConcurrentHashMap<WorldPosKey, NearbyPlayersState>()
    private val pokemonPastureIndex = ConcurrentHashMap<UUID, WorldPosKey>()

    fun beforePastureTick(world: ServerWorld) {
        CobblePalsSaveData.ensureLoaded(world)
        val server = world.server
        if (lastMaintenanceTick.put(server, world.time) == world.time) return
        if (world.time % MAINTENANCE_INTERVAL != 0L) return

        CobblePalsSaveData.markDirty(server)
        TagExecutionEngine.pruneStaleRuntime(world.time, STALE_ENTRY_TTL)
        ClaimManager.pruneStale(world.time, STALE_ENTRY_TTL)
        InventoryManager.pruneStale { pokemonId, _ -> TagAssignmentManager.has(pokemonId) }
    }

    fun navigationBudget(world: ServerWorld, localStarts: Int): NavigationBudget {
        return NavigationBudget(localStarts) { consumeGlobalPathStart(world) }
    }

    fun shouldSendWorkerVisuals(
        world: ServerWorld,
        pasturePos: BlockPos,
        visuals: List<CobblePalsNetworking.WorkerVisualSnapshot>
    ): Boolean {
        val key = WorldPosKey(world.registryKey, pasturePos.toImmutable())
        val signature = visuals.fold(visuals.size) { acc, visual -> 31 * acc + visual.hashCode() }
        val interval = ConfigManager.config.general.visualUpdateIntervalTicks.toLong()
        val current = visualStates[key]
        if (current != null && current.signature == signature && world.time - current.tick < interval) {
            return false
        }
        visualStates[key] = VisualState(world.time, signature)
        return true
    }

    fun nearbyPlayers(world: ServerWorld, pasturePos: BlockPos): List<ServerPlayerEntity> {
        val key = WorldPosKey(world.registryKey, pasturePos.toImmutable())
        val cached = nearbyPlayerCache[key]
        if (cached != null && world.time - cached.tick < 10L) {
            return cached.players.filterNot { it.isRemoved }
        }

        val range = ConfigManager.config.general.nearbyPlayerRange.toDouble()
        val rangeSq = range * range
        val centerX = pasturePos.x + 0.5
        val centerY = pasturePos.y + 0.5
        val centerZ = pasturePos.z + 0.5
        val players = world.players.filter { player ->
            !player.isSpectator && player.squaredDistanceTo(centerX, centerY, centerZ) <= rangeSq
        }
        nearbyPlayerCache[key] = NearbyPlayersState(world.time, players)
        return players
    }

    fun cachedSnapshot(world: ServerWorld, pasturePos: BlockPos, builder: () -> PastureSnapshot): PastureSnapshot {
        val key = WorldPosKey(world.registryKey, pasturePos.toImmutable())
        val ttl = ConfigManager.config.general.managerSnapshotCacheTicks.toLong()
        val cached = snapshotCache[key]
        if (cached != null && world.time - cached.tick < ttl) {
            return cached.snapshot
        }
        val snapshot = builder()
        snapshotCache[key] = SnapshotState(world.time, snapshot)
        return snapshot
    }

    fun invalidateSnapshot(world: World, pasturePos: BlockPos) {
        snapshotCache.remove(WorldPosKey(world.registryKey, pasturePos.toImmutable()))
    }

    fun rememberPastureMembership(world: ServerWorld, pasturePos: BlockPos, pokemonIds: Collection<UUID>) {
        val key = WorldPosKey(world.registryKey, pasturePos.toImmutable())
        pokemonIds.forEach { pokemonPastureIndex[it] = key }
    }

    fun forgetPasture(world: World, pasturePos: BlockPos, pokemonIds: Collection<UUID> = emptyList()) {
        val key = WorldPosKey(world.registryKey, pasturePos.toImmutable())
        visualStates.remove(key)
        snapshotCache.remove(key)
        nearbyPlayerCache.remove(key)
        if (pokemonIds.isEmpty()) {
            pokemonPastureIndex.entries.removeIf { it.value == key }
        } else {
            pokemonIds.forEach { pokemonPastureIndex.remove(it, key) }
        }
    }

    fun findPasturePos(world: ServerWorld, pokemonId: UUID): BlockPos? {
        val key = pokemonPastureIndex[pokemonId] ?: return null
        return if (key.dimension == world.registryKey) key.pos else null
    }

    fun clear(server: MinecraftServer) {
        lastMaintenanceTick.remove(server)
        pathBudgets.remove(server)
        visualStates.clear()
        snapshotCache.clear()
        nearbyPlayerCache.clear()
        pokemonPastureIndex.clear()
    }

    fun clearTransient() {
        pathBudgets.clear()
        visualStates.clear()
        snapshotCache.clear()
        nearbyPlayerCache.clear()
        pokemonPastureIndex.clear()
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
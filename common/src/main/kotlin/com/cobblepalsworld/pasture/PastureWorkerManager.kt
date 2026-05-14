package com.cobblepalsworld.pasture

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.behavior.state.StateManager
import com.cobblepalsworld.config.ConfigManager
import com.cobblepalsworld.inventory.InventoryManager
import com.cobblepalsworld.navigation.ClaimManager
import com.cobblepalsworld.navigation.NavigationHelper
import com.cobblepalsworld.persistence.CobblePalsSaveData
import net.minecraft.registry.RegistryKey
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private data class PastureKey(val dimension: RegistryKey<World>, val pos: BlockPos)

object PastureWorkerManager {
    private val tickInterval get() = ConfigManager.config.general.tickInterval

    // Track previously-tethered UUIDs per pasture for recall cleanup
    private val previousTethered = ConcurrentHashMap<PastureKey, MutableSet<UUID>>()
    // Track previously-tagged UUIDs per pasture for tag removal cleanup
    private val previouslyTagged = ConcurrentHashMap<PastureKey, MutableSet<UUID>>()
    private const val SAVE_INTERVAL = 200L // auto-save every 10 seconds
    private const val STALE_ENTRY_TTL = 20L * 30L

    fun tickPasture(world: World, pos: BlockPos, pasture: PokemonPastureBlockEntity) {
        if (world.isClient) return
        val serverWorld = world as? ServerWorld ?: return
        val pastureKey = PastureKey(serverWorld.registryKey, pos.toImmutable())

        CobblePalsSaveData.ensureLoaded(serverWorld)

        // Periodic auto-save
        if (world.time % SAVE_INTERVAL == 0L) {
            CobblePalsSaveData.markDirty(serverWorld)
            TagExecutionEngine.pruneStaleRuntime(world.time, STALE_ENTRY_TTL)
            InventoryManager.pruneStale { pokemonId, _ -> TagAssignmentManager.has(pokemonId) }
        }

        // Stagger pasture ticks — offset by position hash
        if ((world.time + (pos.hashCode().toLong() and 0x7FFFFFFF)) % tickInterval != 0L) return

        val tethered = pasture.tetheredPokemon

        // Detect recalled Pokémon and clean up their state
        val currentIds = mutableSetOf<UUID>()
        for (t in tethered) {
            try { currentIds.add(t.pokemonId) } catch (_: Exception) {}
        }
        val prevIds = previousTethered.getOrPut(pastureKey) { mutableSetOf() }
        val orphanedAssignments = TagAssignmentManager.findOrphansAt(serverWorld.registryKey.value.toString(), pos, currentIds)
        val missingIds = buildSet {
            prevIds.forEach { prevId ->
                if (prevId !in currentIds) add(prevId)
            }
            addAll(orphanedAssignments)
        }
        for (missingId in missingIds) {
            TagExecutionEngine.cleanup(missingId, world, pos)
            TagAssignmentManager.remove(missingId)
        }
        prevIds.clear()
        prevIds.addAll(currentIds)

        // Detect tag removal: clean up state for pokemon that lost their tag
        val currentlyTagged = mutableSetOf<UUID>()
        for (id in currentIds) {
            if (TagAssignmentManager.has(id)) {
                TagAssignmentManager.associateWithPasture(id, serverWorld.registryKey.value.toString(), pos)
                currentlyTagged.add(id)
            }
        }
        val prevTagged = previouslyTagged.getOrPut(pastureKey) { mutableSetOf() }
        for (prevId in prevTagged) {
            if (prevId in currentIds && prevId !in currentlyTagged) {
                // Pokemon is still tethered but lost its tag — clean up work state
                TagExecutionEngine.cleanup(prevId, world, pos)
            }
        }
        prevTagged.clear()
        prevTagged.addAll(currentlyTagged)

        // Tick each tethered Pokémon that has a tag assigned (capped by maxWorkersPerPasture)
        var workerCount = 0
        val maxWorkers = ConfigManager.config.general.maxWorkersPerPasture
        for (tethering in tethered) {
            val pokemon = try { tethering.getPokemon() } catch (_: Exception) { continue }
                ?: continue
            if (pokemon.isFainted()) continue

            val entity = pokemon.entity ?: continue
            val poseType = entity.dataTracker.get(PokemonEntity.POSE_TYPE)
            if (poseType == PoseType.SLEEP) continue

            val tag = TagAssignmentManager.get(pokemon.uuid)
            if (tag != null) {
                if (workerCount >= maxWorkers) continue
                try {
                    TagExecutionEngine.tick(world, entity, pokemon, tag, pos)
                    workerCount++
                } catch (e: Exception) {
                    com.cobblepalsworld.CobblePalsWorld.LOGGER.error("Error ticking Pokémon worker", e)
                }
            } else {
                // Idle pokemon (no tag): wander within 4 blocks of pasture
                tickIdleWander(world, entity, pos)
            }
        }
    }

    private const val WANDER_RADIUS = 4
    private const val WANDER_INTERVAL = 80L // attempt wander every ~4 seconds

    private fun tickIdleWander(world: World, entity: PokemonEntity, pasturePos: BlockPos) {
        if (world.time % WANDER_INTERVAL != 0L) return

        val dx = entity.x - (pasturePos.x + 0.5)
        val dz = entity.z - (pasturePos.z + 0.5)
        val distSq = dx * dx + dz * dz

        if (distSq > (WANDER_RADIUS + 1) * (WANDER_RADIUS + 1)) {
            // Too far — walk back toward pasture
            entity.navigation.startMovingTo(
                pasturePos.x + 0.5, pasturePos.y.toDouble(), pasturePos.z + 0.5, 0.6
            )
            return
        }

        // Random wander within radius
        val random = world.random
        val targetX = pasturePos.x + random.nextInt(WANDER_RADIUS * 2 + 1) - WANDER_RADIUS
        val targetZ = pasturePos.z + random.nextInt(WANDER_RADIUS * 2 + 1) - WANDER_RADIUS
        val targetY = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, targetX, targetZ)

        entity.navigation.startMovingTo(
            targetX + 0.5, targetY.toDouble(), targetZ + 0.5, 0.5
        )
    }

    fun onPastureBroken(pasture: PokemonPastureBlockEntity) {
        val world = pasture.world ?: return
        if (world.isClient) return

        for (tethering in pasture.tetheredPokemon) {
            try {
                // Clean up worker state and drop carried items at pasture location
                TagExecutionEngine.cleanup(tethering.pokemonId, world, pasture.pos)
                // Remove tag assignment data
                TagAssignmentManager.remove(tethering.pokemonId)
            } catch (_: Exception) {}
        }
        val pastureKey = PastureKey(world.registryKey, pasture.pos.toImmutable())
        previousTethered.remove(pastureKey)
        previouslyTagged.remove(pastureKey)

        // Mark save data dirty so cleanup is persisted
        (world as? ServerWorld)?.let { CobblePalsSaveData.markDirty(it) }
    }

    fun onServerStopping(server: net.minecraft.server.MinecraftServer) {
        CobblePalsSaveData.markDirty(server)
        TagExecutionEngine.resetRuntimeState()
        CobblePalsSaveData.clearLoaded(server)
        resetTransientState(clearInitialization = true)
    }

    fun markDirtyNow(world: World) {
        (world as? ServerWorld)?.let { CobblePalsSaveData.markDirty(it) }
    }

    fun resetTransientState(clearInitialization: Boolean = false) {
        previousTethered.clear()
        previouslyTagged.clear()
    }
}

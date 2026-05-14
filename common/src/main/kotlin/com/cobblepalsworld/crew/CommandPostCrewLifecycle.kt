package com.cobblepalsworld.crew

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.behavior.TagExecutionEngine
import com.cobblepalsworld.navigation.SafePositionResolver
import com.cobblepalsworld.router.RouterBlockEntity
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CommandPostCrewLifecycle {
    private const val HOME_DISTANCE_BLOCKS = 96.0
    private const val HOME_DISTANCE_SQUARED = HOME_DISTANCE_BLOCKS * HOME_DISTANCE_BLOCKS
    private const val RECALL_SETTLE_TICKS = 40L
    private const val KILLED_RESPAWN_COOLDOWN_TICKS = 20L * 30L

    private val recallPendingUntil = ConcurrentHashMap<UUID, Long>()
    private val respawnSuppressedUntil = ConcurrentHashMap<UUID, Long>()

    fun resolvePokemon(world: ServerWorld, member: CommandPostCrewMember, fallbackOwnerUuid: UUID? = null): Pokemon? {
        val ownerUuid = if (member.ownerUuid.leastSignificantBits == 0L && member.ownerUuid.mostSignificantBits == 0L) {
            fallbackOwnerUuid ?: return null
        } else {
            member.ownerUuid
        }
        val storage = Cobblemon.storage
        storage.getParty(ownerUuid, world.registryManager).firstOrNull { it.uuid == member.pokemonId }?.let { return it }
        storage.getPC(ownerUuid, world.registryManager).firstOrNull { it.uuid == member.pokemonId }?.let { return it }
        return null
    }

    fun ensureEntity(world: ServerWorld, anchor: BlockPos, pokemon: Pokemon): PokemonEntity? {
        val now = world.time
        if (isRespawnSuppressed(pokemon.uuid, now)) return null

        val current = pokemon.entity
        if (current != null && !isUsableEntity(current)) {
            pokemon.recall()
            recallPendingUntil[pokemon.uuid] = now + RECALL_SETTLE_TICKS
            return null
        }

        if (isRecallPending(pokemon.uuid, now)) return null

        if (current != null && current.world === world && current.blockPos.getSquaredDistance(anchor) <= HOME_DISTANCE_SQUARED) {
            return current
        }

        if (current != null && current.world === world) {
            return teleportToAnchor(world, anchor, current)
        }

        if (current != null) {
            pokemon.tryRecallWithAnimation()
            recallPendingUntil[pokemon.uuid] = now + RECALL_SETTLE_TICKS
            return null
        }

        val spawnPos = SafePositionResolver.standNear(world, anchor.up(), anchor) ?: anchor.up()
        val spawnVec = Vec3d(spawnPos.x + 0.5, spawnPos.y.toDouble(), spawnPos.z + 0.5)
        return try {
            pokemon.sendOut(world, spawnVec, null) { }
        } catch (e: Exception) {
            CobblePalsWorld.LOGGER.warn("Failed to send out native Command Post crew member ${pokemon.uuid}", e)
            null
        }
    }

    fun handleWorkerDeath(entity: PokemonEntity) {
        val pokemonId = entity.pokemon.uuid
        if (CommandPostCrewManager.bindingFor(pokemonId) == null) return
        val world = entity.world as? ServerWorld ?: return

        respawnSuppressedUntil[pokemonId] = world.time + KILLED_RESPAWN_COOLDOWN_TICKS
        recallPendingUntil.remove(pokemonId)
        TagExecutionEngine.cleanupRuntimeOnly(pokemonId)
    }

    fun recall(world: ServerWorld, member: CommandPostCrewMember, fallbackOwnerUuid: UUID? = null) {
        val pokemon = resolvePokemon(world, member, fallbackOwnerUuid) ?: return
        if (pokemon.entity != null) {
            pokemon.tryRecallWithAnimation()
        }
    }

    fun recallAll(server: MinecraftServer) {
        CommandPostCrewManager.forEachPost { binding, members ->
            val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(binding.dimensionId))) ?: return@forEachPost
            val fallbackOwnerUuid = (world.getBlockEntity(binding.pos) as? RouterBlockEntity)?.ownerUuid()
            members.forEach { member ->
                recall(world, member, fallbackOwnerUuid)
            }
        }
    }

    fun clearRuntimeState() {
        recallPendingUntil.clear()
        respawnSuppressedUntil.clear()
    }

    fun returnHome(world: ServerWorld, anchor: BlockPos, member: CommandPostCrewMember, fallbackOwnerUuid: UUID? = null): PokemonEntity? {
        val pokemon = resolvePokemon(world, member, fallbackOwnerUuid) ?: return null
        val spawnPos = SafePositionResolver.standNear(world, anchor.up(), anchor) ?: anchor.up()
        val spawnVec = Vec3d(spawnPos.x + 0.5, spawnPos.y.toDouble(), spawnPos.z + 0.5)
        val current = pokemon.entity
        if (current != null && current.world === world) {
            current.teleport(spawnVec.x, spawnVec.y, spawnVec.z, false)
            current.navigation.stop()
            return current
        }
        return ensureEntity(world, anchor, pokemon)
    }

    private fun teleportToAnchor(world: ServerWorld, anchor: BlockPos, entity: PokemonEntity): PokemonEntity {
        val spawnPos = SafePositionResolver.standNear(world, anchor.up(), anchor) ?: anchor.up()
        entity.teleport(spawnPos.x + 0.5, spawnPos.y.toDouble(), spawnPos.z + 0.5, false)
        entity.navigation.stop()
        entity.setVelocity(0.0, 0.0, 0.0)
        return entity
    }

    private fun isRespawnSuppressed(pokemonId: UUID, now: Long): Boolean {
        val until = respawnSuppressedUntil[pokemonId] ?: return false
        if (now < until) return true
        respawnSuppressedUntil.remove(pokemonId, until)
        return false
    }

    private fun isRecallPending(pokemonId: UUID, now: Long): Boolean {
        val until = recallPendingUntil[pokemonId] ?: return false
        if (now < until) return true
        recallPendingUntil.remove(pokemonId, until)
        return false
    }

    private fun isUsableEntity(entity: PokemonEntity): Boolean {
        return entity.isAlive && !entity.isRemoved
    }
}
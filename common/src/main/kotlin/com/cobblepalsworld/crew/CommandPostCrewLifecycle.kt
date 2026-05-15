package com.cobblepalsworld.crew

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblepalsworld.CobblePalsWorld
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

object CommandPostCrewLifecycle {
    private const val HOME_DISTANCE_BLOCKS = 96.0
    private const val HOME_DISTANCE_SQUARED = HOME_DISTANCE_BLOCKS * HOME_DISTANCE_BLOCKS

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
        val current = pokemon.entity
        if (current != null && current.world === world && current.blockPos.getSquaredDistance(anchor) <= HOME_DISTANCE_SQUARED) {
            return current
        }

        if (current != null && current.world === world) {
            return teleportToAnchor(world, anchor, current)
        }

        if (current != null) {
            pokemon.tryRecallWithAnimation()
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

    fun handleWorkerDeath(entity: PokemonEntity) {
    }

    fun clearRuntimeState() {
    }

    fun releaseFromCommandPost(world: ServerWorld, anchor: BlockPos, member: CommandPostCrewMember, fallbackOwnerUuid: UUID? = null) {
        recall(world, member, fallbackOwnerUuid)
    }

    fun releaseMembersFromCommandPost(
        world: ServerWorld,
        anchor: BlockPos,
        members: Iterable<CommandPostCrewMember>,
        fallbackOwnerUuid: UUID? = null
    ) {
        members.forEach { member ->
            releaseFromCommandPost(world, anchor, member, fallbackOwnerUuid)
        }
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
}
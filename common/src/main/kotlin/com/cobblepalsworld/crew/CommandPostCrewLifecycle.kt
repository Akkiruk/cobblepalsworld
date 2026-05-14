package com.cobblepalsworld.crew

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblepalsworld.CobblePalsWorld
import com.cobblepalsworld.navigation.SafePositionResolver
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.UUID

object CommandPostCrewLifecycle {
    private const val HOME_DISTANCE_SQUARED = 96.0

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
}
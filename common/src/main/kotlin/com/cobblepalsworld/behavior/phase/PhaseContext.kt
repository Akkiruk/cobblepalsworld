package com.cobblepalsworld.behavior.phase

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblepalsworld.behavior.TagBehavior
import com.cobblepalsworld.behavior.state.WorkerState
import com.cobblepalsworld.navigation.NavigationBudget
import com.cobblepalsworld.tag.TagInstance
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

data class PhaseContext(
    val world: World,
    val entity: PokemonEntity,
    val pokemon: Pokemon,
    val tag: TagInstance,
    val behavior: TagBehavior,
    val state: WorkerState,
    val origin: BlockPos,
    val navigationBudget: NavigationBudget
)

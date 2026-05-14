/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblepalsworld.gui.router

import com.cobblepalsworld.gui.crew.CommandPostCrewMemberSnapshot
import com.cobblepalsworld.gui.crew.CrewSourcePokemonSnapshot
import java.util.UUID

internal interface CommandPostPokemonRenderView {
    val pokemonId: UUID
    val displayName: String
    val species: String
    val speciesIdentifier: String
    val aspects: Set<String>
    val heldItemId: String
    val level: Int
    val isFainted: Boolean
}

internal data class SourcePokemonRenderView(val source: CrewSourcePokemonSnapshot) : CommandPostPokemonRenderView {
    override val pokemonId: UUID get() = source.pokemonId
    override val displayName: String get() = source.displayName
    override val species: String get() = source.species
    override val speciesIdentifier: String get() = source.speciesIdentifier
    override val aspects: Set<String> get() = source.aspects
    override val heldItemId: String get() = source.heldItemId
    override val level: Int get() = source.level
    override val isFainted: Boolean get() = source.isFainted
}

internal data class CrewPokemonRenderView(val member: CommandPostCrewMemberSnapshot) : CommandPostPokemonRenderView {
    override val pokemonId: UUID get() = member.pokemonId
    override val displayName: String get() = member.displayName
    override val species: String get() = member.species
    override val speciesIdentifier: String get() = member.speciesIdentifier
    override val aspects: Set<String> get() = member.aspects
    override val heldItemId: String get() = member.heldItemId
    override val level: Int get() = member.level
    override val isFainted: Boolean get() = member.isFainted
}
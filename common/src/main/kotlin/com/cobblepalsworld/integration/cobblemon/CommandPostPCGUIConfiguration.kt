/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Modifications Copyright (C) 2026 CobblePals World Contributors
 */

package com.cobblepalsworld.integration.cobblemon

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.client.gui.pc.PCGUIConfiguration
import com.cobblepalsworld.networking.CobblePalsNetworking
import net.minecraft.util.math.BlockPos

class CommandPostPCGUIConfiguration(routerPos: BlockPos) : PCGUIConfiguration(
    exitFunction = { it.closeNormally(unlink = false) },
    selectOverride = { pcGui, _, pokemon ->
        if (pokemon != null && !pokemon.isFainted()) {
            CobblePalsNetworking.sendAssignCrewPokemon(routerPos, pokemon.uuid)
            pcGui.playSound(CobblemonSounds.PC_CLICK)
        }
    },
    showParty = true,
    canSelect = { pokemon -> !pokemon.isFainted() }
)
package com.cobblepalsworld.inventory

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblepalsworld.config.ConfigManager

object InventorySizeCalculator {
    fun calculate(pokemon: Pokemon): Int {
        val cfg = ConfigManager.config.general
        val bst = pokemon.species.baseStats.values.sum()
        return (bst / cfg.inventoryBstDivisor).coerceIn(cfg.inventoryMinSlots, cfg.inventoryMaxSlots)
    }
}

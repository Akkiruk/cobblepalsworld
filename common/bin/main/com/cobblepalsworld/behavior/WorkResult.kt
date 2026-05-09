package com.cobblepalsworld.behavior

import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

sealed interface WorkResult {
    /** Work complete. Non-empty items triggers DEPOSITING phase. */
    data class Done(val items: List<ItemStack> = emptyList()) : WorkResult

    /** Navigate to a different position for the next work step. */
    data class MoveTo(val target: BlockPos) : WorkResult

    /** Stay at current target, re-enter ARRIVING after cooldown. */
    data object Repeat : WorkResult

    /** Multi-tick work in progress — stay in WORKING. */
    data object Continue : WorkResult
}

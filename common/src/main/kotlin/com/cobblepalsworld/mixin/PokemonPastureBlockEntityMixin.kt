package com.cobblepalsworld.mixin

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity
import com.cobblepalsworld.pasture.PastureWorkerManager
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(PokemonPastureBlockEntity::class)
@Suppress("unused")
class PokemonPastureBlockEntityMixin {

    @Inject(method = ["togglePastureOn"], at = [At("TAIL")], remap = false)
    private fun onPastureTick(unusedOn: Boolean, unusedCi: CallbackInfo) {
        val pasture = this as PokemonPastureBlockEntity
        val world = pasture.world ?: return
        PastureWorkerManager.tickPasture(world, pasture.pos, pasture)
    }

    @Inject(method = ["onBroken"], at = [At("TAIL")], remap = false)
    private fun onPastureBroken(unusedCi: CallbackInfo) {
        PastureWorkerManager.onPastureBroken(this as PokemonPastureBlockEntity)
    }
}
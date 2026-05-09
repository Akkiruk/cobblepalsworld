package com.cobblepalsworld.mixin;

import com.cobblepalsworld.pasture.PastureWorkerManager;
import net.minecraft.world.World;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PokemonPastureBlockEntity.class)
public class PokemonPastureBlockEntityMixin {

    @Inject(method = "togglePastureOn", at = @At("TAIL"), remap = false)
    private void onPastureTick(boolean on, CallbackInfo ci) {
        PokemonPastureBlockEntity pasture = (PokemonPastureBlockEntity) (Object) this;
        World world = pasture.getWorld();
        if (world != null) {
            PastureWorkerManager.INSTANCE.tickPasture(world, pasture.getPos(), pasture);
        }
    }

    @Inject(method = "onBroken", at = @At("TAIL"), remap = false)
    private void onPastureBroken(CallbackInfo ci) {
        PastureWorkerManager.INSTANCE.onPastureBroken((PokemonPastureBlockEntity) (Object) this);
    }
}

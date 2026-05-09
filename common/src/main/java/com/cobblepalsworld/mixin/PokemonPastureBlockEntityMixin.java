package com.cobblepalsworld.mixin;

import com.cobblepalsworld.pasture.PastureWorkerManager;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PokemonPastureBlockEntity.class)
public class PokemonPastureBlockEntityMixin {

    @Inject(at = @At("TAIL"), method = "TICKER$lambda$0")
    private static void onPastureTick(World world, BlockPos pos, BlockState state, PokemonPastureBlockEntity pasture, CallbackInfo ci) {
        PastureWorkerManager.INSTANCE.tickPasture(world, pos, pasture);
    }

    @Inject(method = "onBroken", at = @At("TAIL"), remap = false)
    private void onPastureBroken(CallbackInfo ci) {
        PastureWorkerManager.INSTANCE.onPastureBroken((PokemonPastureBlockEntity) (Object) this);
    }
}

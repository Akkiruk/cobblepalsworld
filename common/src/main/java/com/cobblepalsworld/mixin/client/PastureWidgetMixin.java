package com.cobblepalsworld.mixin.client;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.client.CobblemonResources;
import com.cobblemon.mod.common.client.gui.pasture.PastureWidget;
import com.cobblemon.mod.common.client.render.RenderHelperKt;
import com.cobblemon.mod.common.util.MiscUtilsKt;
import com.cobblepalsworld.networking.CobblePalsNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PastureWidget.class)
public abstract class PastureWidgetMixin {

    @Unique private static final int BTN_WIDTH = 70;
    @Unique private static final int BTN_HEIGHT = 14;
    @Unique private static final Identifier BTN_TEXTURE = MiscUtilsKt.cobblemonResource("textures/gui/pasture/pasture_button.png");

    @Unique
    private int cobblepals$getBtnX() {
        return ((PastureWidget)(Object)this).getX() + 6;
    }

    @Unique
    private int cobblepals$getBtnY() {
        // Place between title and scroll list (title ends ~y+14, scroll starts at y+31)
        return ((PastureWidget)(Object)this).getY() + 16;
    }

    @Unique
    private boolean cobblepals$isHovered(double mouseX, double mouseY) {
        int x = cobblepals$getBtnX();
        int y = cobblepals$getBtnY();
        return mouseX >= x && mouseX < x + BTN_WIDTH && mouseY >= y && mouseY < y + BTN_HEIGHT;
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void cobblepals$renderManageButton(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int x = cobblepals$getBtnX();
        int y = cobblepals$getBtnY();
        boolean hovered = cobblepals$isHovered(mouseX, mouseY);

        // Use Cobblemon's blitk for the button texture (double-height: normal + hover states)
        GuiUtilsKt.blitk(
                context.getMatrices(),
                BTN_TEXTURE,
                x, y,
                BTN_HEIGHT, BTN_WIDTH,
                0, hovered ? BTN_HEIGHT : 0,
                BTN_WIDTH, BTN_HEIGHT * 2,
                0,
                1, 1, 1, 1,
                true,
                1F
        );

        // Render text using Cobblemon's drawScaledText for the matching font
        MutableText label = Text.literal("CobblePals").styled(s -> s.withBold(true));
        RenderHelperKt.drawScaledText(
                context,
                CobblemonResources.INSTANCE.getDEFAULT_LARGE(),
                label,
                x + BTN_WIDTH / 2.0F,
                y + 2.5F,
                0.8F,                // scale
                1F,                  // opacity
                Integer.MAX_VALUE,   // maxCharWidth
                hovered ? 0xFFFFFFFF : 0xFFDDEEDD,
                true,                // centered
                true,                // shadow
                null, null           // no tooltip
        );
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cobblepals$onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == 0 && cobblepals$isHovered(mouseX, mouseY)) {
            CobblePalsNetworking.INSTANCE.sendOpenRequest();
            cir.setReturnValue(true);
        }
    }
}

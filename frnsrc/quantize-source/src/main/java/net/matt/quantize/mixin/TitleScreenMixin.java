package net.matt.quantize.mixin;

import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "realmsButtonClicked", at = @At("HEAD"), cancellable = true)
    private void disableRealmsButtonClicked(CallbackInfo ci) {
        ci.cancel(); // Prevents the method from executing
    }
}
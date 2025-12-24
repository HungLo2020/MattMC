package net.caffeinemc.mods.sodium.mixin.features.gui.hooks.settings;

import net.sodium.client.gui.SodiumOptionsGUI;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Supplier;

@Mixin(OptionsScreen.class)
public class OptionsScreenMixin extends Screen {
    protected OptionsScreenMixin(Component title) {
        super(title);
    }
    
    /**
     * Redirects the Video Settings button to open Sodium's options GUI instead of vanilla video settings.
     * Uses ModifyArg to intercept the supplier argument when creating the button with the VIDEO component.
     */
    @ModifyArg(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/options/OptionsScreen;openScreenButton(Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;)Lnet/minecraft/client/gui/components/Button;",
            ordinal = 2  // Third call to openScreenButton is for VIDEO (after SKIN_CUSTOMIZATION and SOUNDS)
        ),
        index = 1  // Modify the second parameter (the Supplier)
    )
    private Supplier<Screen> useSodiumOptionsScreen(Supplier<Screen> original) {
        return () -> SodiumOptionsGUI.createScreen(this);
    }
}

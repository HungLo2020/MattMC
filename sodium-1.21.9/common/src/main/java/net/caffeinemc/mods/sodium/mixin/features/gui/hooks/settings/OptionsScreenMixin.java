package net.caffeinemc.mods.sodium.mixin.features.gui.hooks.settings;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

// MattMC: This mixin is disabled because it targets a lambda method with intermediary/Yarn names
// that don't exist in MattMC's official mapping. The Video Settings button will open vanilla
// VideoSettingsScreen instead of Sodium's options GUI.
// To fix: Either access Sodium settings from the game menu or implement a proper hook.
@Mixin(OptionsScreen.class)
public class OptionsScreenMixin extends Screen {
    protected OptionsScreenMixin(Component title) {
        super(title);
    }
    
    // Original mixin targeted lambda$init$2 which doesn't exist in MattMC
    // The Sodium options can still be accessed via the game menu or mod menu integration
}

package net.caffeinemc.mods.sodium.fabric;

import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.hooks.ScreenFactoryHooks;

import java.util.function.Supplier;

/**
 * Implements screen factory hooks to redirect video settings to Sodium's options GUI.
 */
public class SodiumScreenFactoryHook implements ScreenFactoryHooks {
    @Override
    public Supplier<Screen> getVideoSettingsScreenFactory(Supplier<Screen> original, Screen parent) {
        // Replace vanilla video settings with Sodium's options GUI
        return () -> SodiumOptionsGUI.createScreen(parent);
    }
}

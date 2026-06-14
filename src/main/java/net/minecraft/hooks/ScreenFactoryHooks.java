package net.minecraft.hooks;

import net.minecraft.client.gui.screens.Screen;

import java.util.function.Supplier;

/**
 * Hook interface for screen factory modifications.
 * Allows integrated mods to replace or wrap screen creation logic.
 */
public interface ScreenFactoryHooks {
    /**
     * Called when creating the bundled video settings screen from the options menu.
     * Allows integrations to replace the default screen factory with a custom one.
     * 
     * @param current The current screen factory
     * @param parent The parent screen
     * @return A replacement screen factory, or null to use the current factory
     */
    default Supplier<Screen> getVideoSettingsScreenFactory(Supplier<Screen> current, Screen parent) {
        return null;
    }
}

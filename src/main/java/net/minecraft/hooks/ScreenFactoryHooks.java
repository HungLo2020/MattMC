package net.minecraft.hooks;

import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Hook interface for screen factory modifications.
 * Allows mods to replace or wrap screen creation logic.
 */
public interface ScreenFactoryHooks {
    /**
     * Called when creating the video settings screen from the options menu.
     * Allows mods to replace the default video settings screen with a custom one.
     * 
     * @param original The original screen factory
     * @param parent The parent screen
     * @return A replacement screen factory, or null to use the original
     */
    @Nullable
    default Supplier<Screen> getVideoSettingsScreenFactory(Supplier<Screen> original, Screen parent) {
        return null;
    }
}

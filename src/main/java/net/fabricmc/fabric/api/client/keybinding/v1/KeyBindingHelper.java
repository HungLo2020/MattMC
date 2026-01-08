package net.fabricmc.fabric.api.client.keybinding.v1;

import net.minecraft.client.KeyMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for registering key bindings.
 */
public final class KeyBindingHelper {
    private static final List<KeyMapping> REGISTERED_BINDINGS = new ArrayList<>();
    
    private KeyBindingHelper() { }
    
    /**
     * Registers a key binding and returns it.
     * The binding will be added to Minecraft's key mapping list on game initialization.
     */
    public static KeyMapping registerKeyBinding(KeyMapping keyMapping) {
        REGISTERED_BINDINGS.add(keyMapping);
        return keyMapping;
    }
    
    /**
     * Gets all registered key bindings.
     */
    public static List<KeyMapping> getRegisteredBindings() {
        return REGISTERED_BINDINGS;
    }
}

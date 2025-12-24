package net.minecraft.hooks;

import java.util.ArrayList;
import java.util.List;

/**
 * Central registry for all hook implementations.
 * Mods register their hook implementations here at runtime.
 */
public class HookRegistry {
    private static final List<GameHooks> gameHooks = new ArrayList<>();

    /**
     * Register a GameHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerGameHook(GameHooks hook) {
        if (hook != null) {
            gameHooks.add(hook);
        }
    }

    /**
     * Get all registered GameHooks implementations.
     *
     * @return List of registered GameHooks
     */
    public static List<GameHooks> getGameHooks() {
        return new ArrayList<>(gameHooks);
    }

    /**
     * Clear all registered hooks. Useful for testing.
     */
    public static void clearAll() {
        gameHooks.clear();
    }
}

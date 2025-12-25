package net.minecraft.hooks;

import java.util.ArrayList;
import java.util.List;

/**
 * Central registry for all hook implementations.
 * Mods register their hook implementations here at runtime.
 */
public class HookRegistry {
    private static final List<GameHooks> gameHooks = new ArrayList<>();
    private static final List<RenderHooks> renderHooks = new ArrayList<>();
    private static final List<GraphicsConfigHooks> graphicsConfigHooks = new ArrayList<>();
    private static final List<GuiRenderHooks> guiRenderHooks = new ArrayList<>();
    private static final List<DebugScreenHooks> debugScreenHooks = new ArrayList<>();
    private static final List<ScreenFactoryHooks> screenFactoryHooks = new ArrayList<>();

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
     * Register a RenderHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerRenderHook(RenderHooks hook) {
        if (hook != null) {
            renderHooks.add(hook);
        }
    }

    /**
     * Get all registered RenderHooks implementations.
     *
     * @return List of registered RenderHooks
     */
    public static List<RenderHooks> getRenderHooks() {
        return new ArrayList<>(renderHooks);
    }

    /**
     * Register a GraphicsConfigHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerGraphicsConfigHook(GraphicsConfigHooks hook) {
        if (hook != null) {
            graphicsConfigHooks.add(hook);
        }
    }

    /**
     * Get all registered GraphicsConfigHooks implementations.
     *
     * @return List of registered GraphicsConfigHooks
     */
    public static List<GraphicsConfigHooks> getGraphicsConfigHooks() {
        return new ArrayList<>(graphicsConfigHooks);
    }

    /**
     * Register a GuiRenderHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerGuiRenderHook(GuiRenderHooks hook) {
        if (hook != null) {
            guiRenderHooks.add(hook);
        }
    }

    /**
     * Get all registered GuiRenderHooks implementations.
     *
     * @return List of registered GuiRenderHooks
     */
    public static List<GuiRenderHooks> getGuiRenderHooks() {
        return new ArrayList<>(guiRenderHooks);
    }

    /**
     * Register a DebugScreenHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerDebugScreenHook(DebugScreenHooks hook) {
        if (hook != null) {
            debugScreenHooks.add(hook);
        }
    }

    /**
     * Get all registered DebugScreenHooks implementations.
     *
     * @return List of registered DebugScreenHooks
     */
    public static List<DebugScreenHooks> getDebugScreenHooks() {
        return new ArrayList<>(debugScreenHooks);
    }

    /**
     * Register a ScreenFactoryHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerScreenFactoryHook(ScreenFactoryHooks hook) {
        if (hook != null) {
            screenFactoryHooks.add(hook);
        }
    }

    /**
     * Get all registered ScreenFactoryHooks implementations.
     *
     * @return List of registered ScreenFactoryHooks
     */
    public static List<ScreenFactoryHooks> getScreenFactoryHooks() {
        return new ArrayList<>(screenFactoryHooks);
    }

    /**
     * Clear all registered hooks. Useful for testing.
     */
    public static void clearAll() {
        gameHooks.clear();
        renderHooks.clear();
        graphicsConfigHooks.clear();
        guiRenderHooks.clear();
        debugScreenHooks.clear();
        screenFactoryHooks.clear();
    }
}

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
    private static final List<BlockRenderHooks> blockRenderHooks = new ArrayList<>();
    private static final List<RenderTypeHooks> renderTypeHooks = new ArrayList<>();
    private static final List<PlayerPositionHooks> playerPositionHooks = new ArrayList<>();
    private static final List<FogRenderHooks> fogRenderHooks = new ArrayList<>();
    private static final List<EntityRenderHooks> entityRenderHooks = new ArrayList<>();
    private static final List<ChunkStatusHooks> chunkStatusHooks = new ArrayList<>();
    private static final List<WindowCreationHooks> windowCreationHooks = new ArrayList<>();
    private static final List<RenderContextHooks> renderContextHooks = new ArrayList<>();

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
     * Register a BlockRenderHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerBlockRenderHook(BlockRenderHooks hook) {
        if (hook != null) {
            blockRenderHooks.add(hook);
        }
    }

    /**
     * Get all registered BlockRenderHooks implementations.
     *
     * @return List of registered BlockRenderHooks
     */
    public static List<BlockRenderHooks> getBlockRenderHooks() {
        return new ArrayList<>(blockRenderHooks);
    }

    /**
     * Register a RenderTypeHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerRenderTypeHook(RenderTypeHooks hook) {
        if (hook != null) {
            renderTypeHooks.add(hook);
        }
    }

    /**
     * Get all registered RenderTypeHooks implementations.
     *
     * @return List of registered RenderTypeHooks
     */
    public static List<RenderTypeHooks> getRenderTypeHooks() {
        return new ArrayList<>(renderTypeHooks);
    }

    /**
     * Register a PlayerPositionHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerPlayerPositionHook(PlayerPositionHooks hook) {
        if (hook != null) {
            playerPositionHooks.add(hook);
        }
    }

    /**
     * Get all registered PlayerPositionHooks implementations.
     *
     * @return List of registered PlayerPositionHooks
     */
    public static List<PlayerPositionHooks> getPlayerPositionHooks() {
        return new ArrayList<>(playerPositionHooks);
    }

    /**
     * Register a FogRenderHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerFogRenderHook(FogRenderHooks hook) {
        if (hook != null) {
            fogRenderHooks.add(hook);
        }
    }

    /**
     * Get all registered FogRenderHooks implementations.
     *
     * @return List of registered FogRenderHooks
     */
    public static List<FogRenderHooks> getFogRenderHooks() {
        return new ArrayList<>(fogRenderHooks);
    }

    /**
     * Register an EntityRenderHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerEntityRenderHook(EntityRenderHooks hook) {
        if (hook != null) {
            entityRenderHooks.add(hook);
        }
    }

    /**
     * Get all registered EntityRenderHooks implementations.
     *
     * @return List of registered EntityRenderHooks
     */
    public static List<EntityRenderHooks> getEntityRenderHooks() {
        return new ArrayList<>(entityRenderHooks);
    }

    /**
     * Register a ChunkStatusHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerChunkStatusHook(ChunkStatusHooks hook) {
        if (hook != null) {
            chunkStatusHooks.add(hook);
        }
    }

    /**
     * Get all registered ChunkStatusHooks implementations.
     *
     * @return List of registered ChunkStatusHooks
     */
    public static List<ChunkStatusHooks> getChunkStatusHooks() {
        return new ArrayList<>(chunkStatusHooks);
    }

    /**
     * Register a WindowCreationHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerWindowCreationHook(WindowCreationHooks hook) {
        if (hook != null) {
            windowCreationHooks.add(hook);
        }
    }

    /**
     * Get all registered WindowCreationHooks implementations.
     *
     * @return List of registered WindowCreationHooks
     */
    public static List<WindowCreationHooks> getWindowCreationHooks() {
        return new ArrayList<>(windowCreationHooks);
    }

    /**
     * Register a RenderContextHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerRenderContextHook(RenderContextHooks hook) {
        if (hook != null) {
            renderContextHooks.add(hook);
        }
    }

    /**
     * Get all registered RenderContextHooks implementations.
     *
     * @return List of registered RenderContextHooks
     */
    public static List<RenderContextHooks> getRenderContextHooks() {
        return new ArrayList<>(renderContextHooks);
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
        blockRenderHooks.clear();
        renderTypeHooks.clear();
        playerPositionHooks.clear();
        fogRenderHooks.clear();
        entityRenderHooks.clear();
        chunkStatusHooks.clear();
        windowCreationHooks.clear();
        renderContextHooks.clear();
    }
}

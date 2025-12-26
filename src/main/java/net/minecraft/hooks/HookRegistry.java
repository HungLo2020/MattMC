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
    private static final List<SkyColorHooks> skyColorHooks = new ArrayList<>();
    private static final List<RenderBuffersHooks> renderBuffersHooks = new ArrayList<>();
    private static final List<TextureAtlasSpriteHooks> textureAtlasSpriteHooks = new ArrayList<>();
    private static final List<FogColorHooks> fogColorHooks = new ArrayList<>();
    private static final List<AtlasManagerHooks> atlasManagerHooks = new ArrayList<>();
    private static final List<TextureAtlasHooks> textureAtlasHooks = new ArrayList<>();
    private static final List<GuiGraphicsHooks> guiGraphicsHooks = new ArrayList<>();
    private static final List<EntityRendererHooks> entityRendererHooks = new ArrayList<>();
    private static final List<ModelBlockRendererHooks> modelBlockRendererHooks = new ArrayList<>();
    private static final List<ParticleRenderHooks> particleRenderHooks = new ArrayList<>();
    private static final List<ClientPacketListenerHooks> clientPacketListenerHooks = new ArrayList<>();
    private static final List<BlockColorHooks> blockColorHooks = new ArrayList<>();
    private static final List<ClientLevelHooks> clientLevelHooks = new ArrayList<>();
    private static final List<VertexFormatHooks> vertexFormatHooks = new ArrayList<>();
    private static final List<BlockEntityTypeHooks> blockEntityTypeHooks = new ArrayList<>();
    private static final List<SpriteContentsHooks> spriteContentsHooks = new ArrayList<>();

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
     * Register a SkyColorHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerSkyColorHook(SkyColorHooks hook) {
        if (hook != null) {
            skyColorHooks.add(hook);
        }
    }

    /**
     * Get all registered SkyColorHooks implementations.
     *
     * @return List of registered SkyColorHooks
     */
    public static List<SkyColorHooks> getSkyColorHooks() {
        return new ArrayList<>(skyColorHooks);
    }

    /**
     * Register a RenderBuffersHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerRenderBuffersHook(RenderBuffersHooks hook) {
        if (hook != null) {
            renderBuffersHooks.add(hook);
        }
    }

    /**
     * Get all registered RenderBuffersHooks implementations.
     *
     * @return List of registered RenderBuffersHooks
     */
    public static List<RenderBuffersHooks> getRenderBuffersHooks() {
        return new ArrayList<>(renderBuffersHooks);
    }

    /**
     * Register a TextureAtlasSpriteHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerTextureAtlasSpriteHook(TextureAtlasSpriteHooks hook) {
        if (hook != null) {
            textureAtlasSpriteHooks.add(hook);
        }
    }

    /**
     * Get all registered TextureAtlasSpriteHooks implementations.
     *
     * @return List of registered TextureAtlasSpriteHooks
     */
    public static List<TextureAtlasSpriteHooks> getTextureAtlasSpriteHooks() {
        return new ArrayList<>(textureAtlasSpriteHooks);
    }

    /**
     * Register a FogColorHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerFogColorHook(FogColorHooks hook) {
        if (hook != null) {
            fogColorHooks.add(hook);
        }
    }

    /**
     * Get all registered FogColorHooks implementations.
     *
     * @return List of registered FogColorHooks
     */
    public static List<FogColorHooks> getFogColorHooks() {
        return new ArrayList<>(fogColorHooks);
    }

    /**
     * Register an AtlasManagerHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerAtlasManagerHook(AtlasManagerHooks hook) {
        if (hook != null) {
            atlasManagerHooks.add(hook);
        }
    }

    /**
     * Get all registered AtlasManagerHooks implementations.
     *
     * @return List of registered AtlasManagerHooks
     */
    public static List<AtlasManagerHooks> getAtlasManagerHooks() {
        return new ArrayList<>(atlasManagerHooks);
    }

    /**
     * Register a TextureAtlasHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerTextureAtlasHook(TextureAtlasHooks hook) {
        if (hook != null) {
            textureAtlasHooks.add(hook);
        }
    }

    /**
     * Get all registered TextureAtlasHooks implementations.
     *
     * @return List of registered TextureAtlasHooks
     */
    public static List<TextureAtlasHooks> getTextureAtlasHooks() {
        return new ArrayList<>(textureAtlasHooks);
    }

    /**
     * Register a GuiGraphicsHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerGuiGraphicsHook(GuiGraphicsHooks hook) {
        if (hook != null) {
            guiGraphicsHooks.add(hook);
        }
    }

    /**
     * Get all registered GuiGraphicsHooks implementations.
     *
     * @return List of registered GuiGraphicsHooks
     */
    public static List<GuiGraphicsHooks> getGuiGraphicsHooks() {
        return new ArrayList<>(guiGraphicsHooks);
    }

    /**
     * Register an EntityRendererHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerEntityRendererHook(EntityRendererHooks hook) {
        if (hook != null) {
            entityRendererHooks.add(hook);
        }
    }

    /**
     * Get all registered EntityRendererHooks implementations.
     *
     * @return List of registered EntityRendererHooks
     */
    public static List<EntityRendererHooks> getEntityRendererHooks() {
        return new ArrayList<>(entityRendererHooks);
    }

    /**
     * Register a ModelBlockRendererHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerModelBlockRendererHook(ModelBlockRendererHooks hook) {
        if (hook != null) {
            modelBlockRendererHooks.add(hook);
        }
    }

    /**
     * Get all registered ModelBlockRendererHooks implementations.
     *
     * @return List of registered ModelBlockRendererHooks
     */
    public static List<ModelBlockRendererHooks> getModelBlockRendererHooks() {
        return new ArrayList<>(modelBlockRendererHooks);
    }

    /**
     * Register a ParticleRenderHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerParticleRenderHook(ParticleRenderHooks hook) {
        if (hook != null) {
            particleRenderHooks.add(hook);
        }
    }

    /**
     * Get all registered ParticleRenderHooks implementations.
     *
     * @return List of registered ParticleRenderHooks
     */
    public static List<ParticleRenderHooks> getParticleRenderHooks() {
        return new ArrayList<>(particleRenderHooks);
    }

    /**
     * Register a ClientPacketListenerHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerClientPacketListenerHook(ClientPacketListenerHooks hook) {
        if (hook != null) {
            clientPacketListenerHooks.add(hook);
        }
    }

    /**
     * Get all registered ClientPacketListenerHooks implementations.
     *
     * @return List of registered ClientPacketListenerHooks
     */
    public static List<ClientPacketListenerHooks> getClientPacketListenerHooks() {
        return new ArrayList<>(clientPacketListenerHooks);
    }

    /**
     * Register a BlockColorHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerBlockColorHook(BlockColorHooks hook) {
        if (hook != null) {
            blockColorHooks.add(hook);
        }
    }

    /**
     * Get all registered BlockColorHooks implementations.
     *
     * @return List of registered BlockColorHooks
     */
    public static List<BlockColorHooks> getBlockColorHooks() {
        return new ArrayList<>(blockColorHooks);
    }

    /**
     * Register a ClientLevelHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerClientLevelHook(ClientLevelHooks hook) {
        if (hook != null) {
            clientLevelHooks.add(hook);
        }
    }

    /**
     * Get all registered ClientLevelHooks implementations.
     *
     * @return List of registered ClientLevelHooks
     */
    public static List<ClientLevelHooks> getClientLevelHooks() {
        return new ArrayList<>(clientLevelHooks);
    }

    /**
     * Register a VertexFormatHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerVertexFormatHook(VertexFormatHooks hook) {
        if (hook != null) {
            vertexFormatHooks.add(hook);
        }
    }

    /**
     * Get all registered VertexFormatHooks implementations.
     *
     * @return List of registered VertexFormatHooks
     */
    public static List<VertexFormatHooks> getVertexFormatHooks() {
        return new ArrayList<>(vertexFormatHooks);
    }

    /**
     * Register a BlockEntityTypeHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerBlockEntityTypeHook(BlockEntityTypeHooks hook) {
        if (hook != null) {
            blockEntityTypeHooks.add(hook);
        }
    }

    /**
     * Get all registered BlockEntityTypeHooks implementations.
     *
     * @return List of registered BlockEntityTypeHooks
     */
    public static List<BlockEntityTypeHooks> getBlockEntityTypeHooks() {
        return new ArrayList<>(blockEntityTypeHooks);
    }

    /**
     * Register a SpriteContentsHooks implementation.
     * Should be called during mod initialization.
     *
     * @param hook The hook implementation to register
     */
    public static void registerSpriteContentsHook(SpriteContentsHooks hook) {
        if (hook != null) {
            spriteContentsHooks.add(hook);
        }
    }

    /**
     * Get all registered SpriteContentsHooks implementations.
     *
     * @return List of registered SpriteContentsHooks
     */
    public static List<SpriteContentsHooks> getSpriteContentsHooks() {
        return new ArrayList<>(spriteContentsHooks);
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
        skyColorHooks.clear();
        renderBuffersHooks.clear();
        textureAtlasSpriteHooks.clear();
        fogColorHooks.clear();
        atlasManagerHooks.clear();
        textureAtlasHooks.clear();
        guiGraphicsHooks.clear();
        entityRendererHooks.clear();
        modelBlockRendererHooks.clear();
        particleRenderHooks.clear();
        clientPacketListenerHooks.clear();
        blockColorHooks.clear();
        clientLevelHooks.clear();
        vertexFormatHooks.clear();
        blockEntityTypeHooks.clear();
        spriteContentsHooks.clear();
    }
}

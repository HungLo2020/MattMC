package net.sodium.fabric;

import net.sodium.client.SodiumClientMod;
import net.sodium.client.hooks.SodiumBlockColorHook;
import net.sodium.client.hooks.SodiumBlockEntityTypeHook;
import net.sodium.client.hooks.SodiumClientLevelHook;
import net.sodium.client.hooks.SodiumSpriteContentsHook;
import net.sodium.client.hooks.SodiumVertexFormatHook;
import net.sodium.client.hooks.SodiumWindowHook;
import net.sodium.client.perf.real.RealChunkMeshingReplayRunner;
import net.sodium.client.render.frapi.SodiumRenderer;
import net.sodium.client.util.FlawlessFrames;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.hooks.HookRegistry;
import java.util.function.Consumer;

public class SodiumFabricMod implements ClientModInitializer {
    @Override
    @SuppressWarnings("unchecked")
    public void onInitializeClient() {
        ModContainer mod = FabricLoader.getInstance()
                .getModContainer("sodium")
                .orElseThrow(NullPointerException::new);

        SodiumClientMod.onInitialization(mod.getMetadata().getVersion().getFriendlyString());

        FabricLoader.getInstance()
                .getEntrypoints("frex_flawless_frames", Consumer.class)
                .forEach(api -> api.accept(FlawlessFrames.getProvider()));

        Renderer.register(SodiumRenderer.INSTANCE);

        // Register hook implementations (replaces mixin-based approach)
        HookRegistry.registerGameHook(new SodiumGameHook());
        HookRegistry.registerRenderHook(new SodiumRenderHook());
        HookRegistry.registerGraphicsConfigHook(new SodiumGraphicsConfigHook());
        HookRegistry.registerGuiRenderHook(new SodiumGuiRenderHook());
        HookRegistry.registerDebugScreenHook(new SodiumDebugScreenHook());
        HookRegistry.registerBlockRenderHook(new SodiumBlockRenderHook());
        HookRegistry.registerRenderTypeHook(new SodiumRenderTypeHook());
        HookRegistry.registerPlayerPositionHook(new SodiumPlayerPositionHook());
        HookRegistry.registerFogRenderHook(new SodiumFogRenderHook());
        HookRegistry.registerEntityRenderHook(new SodiumEntityRenderHook());
        HookRegistry.registerSkyColorHook(new SodiumSkyColorHook());
        HookRegistry.registerRenderBuffersHook(new SodiumRenderBuffersHook());
        HookRegistry.registerTextureAtlasSpriteHook(new SodiumTextureAtlasSpriteHook());
        HookRegistry.registerFogColorHook(new SodiumFogColorHook());
        HookRegistry.registerAtlasManagerHook(new SodiumAtlasManagerHook());
        HookRegistry.registerTextureAtlasHook(new SodiumTextureAtlasHook());
        HookRegistry.registerGuiGraphicsHook(new SodiumGuiGraphicsHook());
        HookRegistry.registerEntityRendererHook(new SodiumEntityRendererHook());
        HookRegistry.registerModelBlockRendererHook(new SodiumModelBlockRendererHook());
        HookRegistry.registerParticleRenderHook(new SodiumParticleRenderHook());
        HookRegistry.registerClientPacketListenerHook(new SodiumClientPacketListenerHook());
        HookRegistry.registerBlockColorHook(SodiumBlockColorHook.getInstance());
        HookRegistry.registerClientLevelHook(SodiumClientLevelHook.getInstance());
        HookRegistry.registerVertexFormatHook(SodiumVertexFormatHook.getInstance());
        HookRegistry.registerBlockEntityTypeHook(SodiumBlockEntityTypeHook.getInstance());
        HookRegistry.registerSpriteContentsHook(SodiumSpriteContentsHook.getInstance());
        HookRegistry.registerWindowHook(SodiumWindowHook.getInstance());
        RealChunkMeshingReplayRunner.installIfRequested();
    }
}

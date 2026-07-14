package net.irisshaders.iris.pipeline;

import net.blaze3d.systems.RenderPass;
import net.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.shaderpack.properties.CloudSetting;
import net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;

import java.util.OptionalInt;
import java.util.function.Supplier;

public interface WorldRenderingPipeline {
	void beginLevelRendering();

    void renderShadows(LevelRenderer worldRenderer, Camera playerCamera, CameraRenderState renderState); // Changed from LevelRendererAccessor to LevelRenderer

    void addDebugText(DebugScreenDisplayer messages);

	OptionalInt getForcedShadowRenderDistanceChunksForDisplay();

	Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> getTextureMap();

	WorldRenderingPhase getPhase();

	void setPhase(WorldRenderingPhase phase);

	void setOverridePhase(WorldRenderingPhase phase);

	int getCurrentNormalTexture();

	int getCurrentSpecularTexture();

	void onSetShaderTexture(GpuTextureView id);

	void beginHand();

	void beginTranslucents();

	void finalizeLevelRendering();

	void finalizeGameRendering();

	void destroy();

	SodiumPrograms getSodiumPrograms();

	FrameUpdateNotifier getFrameUpdateNotifier();

	boolean shouldDisableVanillaEntityShadows();

	boolean shouldDisableDirectionalShading();

	boolean shouldDisableFrustumCulling();

	boolean shouldDisableOcclusionCulling();

	CloudSetting getCloudSetting();

	boolean shouldRenderUnderwaterOverlay();

	boolean shouldRenderVignette();

	boolean shouldRenderSun();

	boolean shouldRenderWeather();

	boolean shouldRenderWeatherParticles();

	boolean shouldRenderMoon();

	boolean shouldRenderStars();

	boolean shouldRenderSkyDisc();

	boolean shouldWriteRainAndSnowToDepthBuffer();

	ParticleRenderingSettings getParticleRenderingSettings();

	boolean allowConcurrentCompute();

	boolean hasFeature(FeatureFlags flags);

	float getSunPathRotation();

	DHCompat getDHCompat();

	void setIsMainBound(boolean mainBound);

    void onBeginClear();

    boolean supportsEndFlash();

	default RenderPass createSkyRenderPass(Supplier<String> label, boolean includeDepth) {
		return null;
	}

	default void traceColortex0PhaseForDiagnostics(String phase) {
	}
}

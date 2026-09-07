package net.minecraft.client.renderer;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.Std140Builder;
import net.blaze3d.buffers.Std140SizeCalculator;
import net.blaze3d.platform.TextureUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.textures.FilterMode;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import java.util.OptionalInt;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.pbr.TextureTracker;
import net.logging.LogUtils;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.vulkanic.VulkanicAPI;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector3f;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class LightTexture implements AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final boolean PROBE_VULKAN_SHADER_LIGHTMAP = Boolean.getBoolean("mattmc.vulkan.probeShaderLightmap");
	private static final boolean DETERMINISTIC_LIGHTMAP_PARITY = Boolean.getBoolean("mattmc.vulkan.deterministicLightmapParity");
	private static final boolean TRACE_LIGHTMAP_INFO_PARITY = Boolean.getBoolean("mattmc.vulkan.traceLightmapInfoParity");
	private static final int MAX_LIGHTMAP_INFO_PARITY_LOGS = Integer.getInteger("mattmc.vulkan.traceLightmapInfoParity.maxLogs", 512);
	private static final java.util.concurrent.atomic.AtomicInteger LIGHTMAP_INFO_PARITY_LOG_COUNT = new java.util.concurrent.atomic.AtomicInteger();
	/**
	 * Optional capture-only export of Frozen's already-rendered Java OpenGL
	 * lightmap. It never participates in lighting, resource selection, or
	 * renderer policy; the cross-repository harness enables it only while
	 * investigating a paired terrain appearance discrepancy.
	 */
	private static final String STATIC_TERRAIN_APPEARANCE_TRACE_DIR =
		System.getProperty(
			"mattmc.dev.staticTerrainParityDiagnostics.lightmapPath",
			System.getenv("MATTMC_STATIC_TERRAIN_APPEARANCE_TRACE_DIR")
		);
	public static final int FULL_BRIGHT = 15728880;
	public static final int FULL_SKY = 15728640;
	public static final int FULL_BLOCK = 240;
	private static final int TEXTURE_SIZE = 16;
	private static final int LIGHTMAP_UBO_SIZE = new Std140SizeCalculator()
		.putFloat()
		.putFloat()
		.putFloat()
		.putFloat()
		.putFloat()
		.putFloat()
		.putFloat()
		.putVec3()
		.putVec3()
		.get();
	private static final Vector3f END_FLASH_SKY_LIGHT_COLOR = new Vector3f(0.9F, 0.5F, 1.0F);
	public final GpuTexture texture; // Made public for mod integration (was accessed via mixin)
	private final GpuTextureView textureView;
	private boolean dumpedGpuLightmapDebug;
	private GpuTexture shaderLightmapProbeTexture;
	private GpuTextureView shaderLightmapProbeView;
	private boolean dumpedShaderLightmapProbeDebug;
	private boolean dumpedStaticTerrainParityLightmap;
	private boolean updateLightTexture;
	private float blockLightRedFlicker;
	private final GameRenderer renderer;
	private final Minecraft minecraft;
	private final MappableRingBuffer ubo;

	public LightTexture(GameRenderer gameRenderer, Minecraft minecraft) {
		this.renderer = gameRenderer;
		this.minecraft = minecraft;
		int textureUsage = VulkanicAPI.isVulkanBackendSelected()
			? GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT
			: GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
		// The ordinary Frozen texture remains unchanged. Only an explicitly
		// requested capture diagnostic adds readback capability so it can export
		// the result of the normal lightmap shader once for comparison.
		if (STATIC_TERRAIN_APPEARANCE_TRACE_DIR != null && !STATIC_TERRAIN_APPEARANCE_TRACE_DIR.isBlank()) {
			textureUsage |= GpuTexture.USAGE_COPY_SRC;
		}
		this.texture = VulkanicAPI.createTexture(
			"Light Texture",
			textureUsage,
			TextureFormat.RGBA8,
			16,
			16,
			1,
			1
		);
		this.texture.setTextureFilter(FilterMode.LINEAR, false);
		this.textureView = VulkanicAPI.createTextureView(this.texture);
		if (VulkanicAPI.isVulkanBackendSelected() && PROBE_VULKAN_SHADER_LIGHTMAP) {
			this.shaderLightmapProbeTexture = VulkanicAPI.createTexture(
				"Light Texture Shader Probe",
				GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
				TextureFormat.RGBA8,
				TEXTURE_SIZE,
				TEXTURE_SIZE,
				1,
				1
			);
			this.shaderLightmapProbeTexture.setTextureFilter(FilterMode.LINEAR, false);
			this.shaderLightmapProbeView = VulkanicAPI.createTextureView(this.shaderLightmapProbeTexture);
		}
		VulkanicAPI.createCommandEncoder().clearColorTexture(this.texture, -1);
		this.ubo = new MappableRingBuffer(() -> "Lightmap UBO", 130, LIGHTMAP_UBO_SIZE);
	}

	public GpuTextureView getTextureView() {
		return this.textureView;
	}

	public String debugDescribePackedLight(int packedLight) {
		int blockLight = packedLight & 0xF;
		int skyLight = packedLight >> 20 & 0xF;
		return "packed=0x%08X block=%d sky=%d".formatted(packedLight, blockLight, skyLight);
	}

	public void close() {
		if (this.shaderLightmapProbeView != null) {
			this.shaderLightmapProbeView.close();
		}
		if (this.shaderLightmapProbeTexture != null) {
			this.shaderLightmapProbeTexture.close();
		}
		this.texture.close();
		this.textureView.close();
		this.ubo.close();
	}

	public void tick() {
		if (DETERMINISTIC_LIGHTMAP_PARITY) {
			this.blockLightRedFlicker = 0.0F;
		} else {
			this.blockLightRedFlicker = this.blockLightRedFlicker + (float)((Math.random() - Math.random()) * Math.random() * Math.random() * 0.1);
			this.blockLightRedFlicker *= 0.9F;
		}
		this.updateLightTexture = true;
	}

	public void turnOffLightLayer() {
		IrisRenderSystem.bindTextureToUnit(2, 0);
		TextureTracker.INSTANCE.onSetShaderTexture(2, null);
	}

	public void turnOnLightLayer() {
		var ctx = VulkanicAPI.getCommandContext();
		VulkanicAPI.bindTextureUnit(ctx, 2, this.textureView);
		TextureTracker.INSTANCE.onSetShaderTexture(2, this.textureView);
	}

	private float calculateDarknessScale(LivingEntity livingEntity, float f, float g) {
		float h = 0.45F * f;
		float result = Math.max(0.0F, Mth.cos((livingEntity.tickCount - g) * (float) Math.PI * 0.025F) * h);
		
		// Iris: Store darkness value after calculation
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setDarknessLightFactor((float) (result * this.minecraft.options.darknessEffectScale().get()));
		
		return result;
	}

	public void updateLightTexture(float f) {
		if (this.updateLightTexture) {
			this.updateLightTexture = false;
			ProfilerFiller profilerFiller = Profiler.get();
			profilerFiller.push("lightTex");
			ClientLevel clientLevel = this.minecraft.level;
			if (clientLevel != null) {
				// Iris: Reset darkness value before calculating
				net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setDarknessLightFactor(0.0F);
				
				float g = clientLevel.getSkyDarken(1.0F);
				float i;
				Vector3f vector3f;
				if (clientLevel.effects().hasEndFlashes()) {
					vector3f = new Vector3f(0.99F, 1.12F, 1.0F);
					EndFlashState endFlashState = clientLevel.endFlashState();
					if (endFlashState != null && !this.minecraft.options.hideLightningFlash().get()) {
						float h = endFlashState.getIntensity(f);
						if (this.minecraft.gui.getBossOverlay().shouldCreateWorldFog()) {
							i = h / 3.0F;
						} else {
							i = h;
						}
					} else {
						i = 0.0F;
					}
				} else {
					vector3f = new Vector3f(1.0F, 1.0F, 1.0F);
					if (clientLevel.getSkyFlashTime() > 0) {
						i = 1.0F;
					} else {
						i = g * 0.95F + 0.05F;
					}
				}

				float j = this.minecraft.options.darknessEffectScale().get().floatValue();
				float h = this.minecraft.player.getEffectBlendFactor(MobEffects.DARKNESS, f) * j;
				float k = this.calculateDarknessScale(this.minecraft.player, h, f) * j;
				float l = this.minecraft.player.getWaterVision();
				float m;
				if (this.minecraft.player.hasEffect(MobEffects.NIGHT_VISION)) {
					m = GameRenderer.getNightVisionScale(this.minecraft.player, f);
				} else if (l > 0.0F && this.minecraft.player.hasEffect(MobEffects.CONDUIT_POWER)) {
					m = l;
				} else {
					m = 0.0F;
				}

				Vector3f vector3f2;
				if (clientLevel.effects().hasEndFlashes()) {
					vector3f2 = END_FLASH_SKY_LIGHT_COLOR;
				} else {
					vector3f2 = new Vector3f(g, g, 1.0F).lerp(new Vector3f(1.0F, 1.0F, 1.0F), 0.35F);
				}

				if (DETERMINISTIC_LIGHTMAP_PARITY) {
					this.blockLightRedFlicker = 0.0F;
				}
				float n = this.blockLightRedFlicker + 1.5F;
				float o = clientLevel.dimensionType().ambientLight();
				float p = this.minecraft.options.gamma().get().floatValue();
				float q = this.renderer.getDarkenWorldAmount(f);
				float r = Math.max(0.0F, p - h);
				net.minecraft.client.dev.DeterministicCameraCapture.recordLightmapSemanticFingerprint(
					o + "," + i + "," + n + "," + m + "," + k + "," + q + "," + r + ","
						+ vector3f2.x + "," + vector3f2.y + "," + vector3f2.z + ","
						+ vector3f.x + "," + vector3f.y + "," + vector3f.z
				);
				CommandEncoder commandEncoder = VulkanicAPI.createCommandEncoder();

				try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(this.ubo.currentBuffer(), false, true)) {
					Std140Builder.intoBuffer(mappedView.data())
						.putFloat(o)
						.putFloat(i)
						.putFloat(n)
						.putFloat(m)
						.putFloat(k)
						.putFloat(q)
						.putFloat(r)
						.putVec3(vector3f2)
						.putVec3(vector3f);
				}
				this.traceLightmapInfoParity(f, o, i, n, m, k, q, r, vector3f2, vector3f);

					this.renderLightTextureShader(commandEncoder, this.textureView, "Update light");
					this.dumpStaticTerrainParityLightmapOnce();

				if (VulkanicAPI.isVulkanBackendSelected()) {
					this.updateVulkanShaderLightmapProbe(commandEncoder);
					this.dumpGpuLightmapDebugOnce();
				}

				this.ubo.rotate();
				
				// Call registered light texture hooks
				for (net.minecraft.hooks.LightTextureHooks hook : net.minecraft.hooks.HookRegistry.getLightTextureHooks()) {
					hook.onLightTextureUpdated(this, f);
				}
				
				profilerFiller.pop();
			}
		}
	}

	private void renderLightTextureShader(CommandEncoder commandEncoder, GpuTextureView targetView, String label) {
		try (RenderPass renderPass = commandEncoder.createRenderPass(() -> label, targetView, OptionalInt.empty())) {
			renderPass.setPipeline(RenderPipelines.LIGHTMAP);
			VulkanicAPI.bindDefaultUniforms(renderPass);
			renderPass.setUniform("LightmapInfo", this.ubo.currentBuffer());
			renderPass.draw(0, 3);
		}
	}

	private void traceLightmapInfoParity(
		float partialTicks,
		float ambientLight,
		float skyFactor,
		float blockLightFactor,
		float nightVisionScale,
		float darknessScale,
		float darkenWorldAmount,
		float gammaMinusDarkness,
		Vector3f skyLightColor,
		Vector3f lightColor
	) {
		if (!TRACE_LIGHTMAP_INFO_PARITY) {
			return;
		}
		int logIndex = LIGHTMAP_INFO_PARITY_LOG_COUNT.incrementAndGet();
		if (MAX_LIGHTMAP_INFO_PARITY_LOGS >= 0 && logIndex > MAX_LIGHTMAP_INFO_PARITY_LOGS) {
			return;
		}

		LOGGER.info(
			"LightmapInfoParity backend={} deterministic={} partialTicks={} ambientLight={} skyFactor={} blockLightRedFlicker={} blockLightFactor={} nightVisionScale={} darknessScale={} darkenWorldAmount={} gammaMinusDarkness={} skyLightColor=({},{},{}) lightColor=({},{},{})",
			VulkanicAPI.getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT),
			DETERMINISTIC_LIGHTMAP_PARITY,
			partialTicks,
			ambientLight,
			skyFactor,
			this.blockLightRedFlicker,
			blockLightFactor,
			nightVisionScale,
			darknessScale,
			darkenWorldAmount,
			gammaMinusDarkness,
			skyLightColor.x(),
			skyLightColor.y(),
			skyLightColor.z(),
			lightColor.x(),
			lightColor.y(),
			lightColor.z()
		);
	}

	private void updateVulkanShaderLightmapProbe(CommandEncoder commandEncoder) {
		if (this.shaderLightmapProbeView == null) {
			return;
		}

		this.renderLightTextureShader(commandEncoder, this.shaderLightmapProbeView, "Probe Vulkan shader light");
		this.dumpShaderLightmapProbeDebugOnce();
	}

	private void dumpGpuLightmapDebugOnce() {
		if (this.dumpedGpuLightmapDebug) {
			return;
		}

		this.dumpedGpuLightmapDebug = true;
		try {
			Path autoCaptureDir = this.getAutoCaptureDir();
			Files.createDirectories(autoCaptureDir);
			TextureUtil.writeAsPNG(autoCaptureDir, "light_texture_debug_gpu", this.texture, 0, i -> i);
		} catch (IOException ignored) {
		}
	}

	/**
	 * Diagnostic-only readback of the ordinary Frozen OpenGL lightmap after
	 * its normal shader pass has finished. This method is inert unless the
	 * shared capture harness provided a dedicated trace directory.
	 */
	private void dumpStaticTerrainParityLightmapOnce() {
		if (this.dumpedStaticTerrainParityLightmap || STATIC_TERRAIN_APPEARANCE_TRACE_DIR == null
			|| STATIC_TERRAIN_APPEARANCE_TRACE_DIR.isBlank()) {
			return;
		}
		this.dumpedStaticTerrainParityLightmap = true;
		try {
			Path traceDirectory = Path.of(STATIC_TERRAIN_APPEARANCE_TRACE_DIR);
			Files.createDirectories(traceDirectory);
			TextureUtil.writeAsPNG(traceDirectory, "frozen_java_opengl_lightmap", this.texture, 0, i -> i);
		} catch (IOException | RuntimeException ignored) {
			// Baseline diagnostics must never affect Frozen's authoritative frame.
		}
	}

	/** True only once the optional diagnostic readback has completed. */
	public static boolean hasStaticTerrainParityLightmapCapture() {
		if (STATIC_TERRAIN_APPEARANCE_TRACE_DIR == null || STATIC_TERRAIN_APPEARANCE_TRACE_DIR.isBlank()) {
			return true;
		}
		return Files.isRegularFile(
			Path.of(STATIC_TERRAIN_APPEARANCE_TRACE_DIR).resolve("frozen_java_opengl_lightmap_0.png")
		);
	}

	private void dumpShaderLightmapProbeDebugOnce() {
		if (this.dumpedShaderLightmapProbeDebug || this.shaderLightmapProbeTexture == null) {
			return;
		}

		this.dumpedShaderLightmapProbeDebug = true;
		try {
			Path autoCaptureDir = this.getAutoCaptureDir();
			Files.createDirectories(autoCaptureDir);
			TextureUtil.writeAsPNG(autoCaptureDir, "light_texture_debug_shader_probe", this.shaderLightmapProbeTexture, 0, i -> i);
		} catch (IOException exception) {
			LOGGER.warn("Failed to dump Vulkan shader lightmap probe", exception);
		}
	}

	private Path getAutoCaptureDir() {
		Path gameDir = this.minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
		Path parent = gameDir.getParent();
		return (parent != null ? parent : gameDir).resolve("logs/auto-capture");
	}

	public static float getBrightness(DimensionType dimensionType, int i) {
		return getBrightness(dimensionType.ambientLight(), i);
	}

	public static float getBrightness(float f, int i) {
		float g = i / 15.0F;
		float h = g / (4.0F - 3.0F * g);
		return Mth.lerp(f, h, 1.0F);
	}

	public static int pack(int i, int j) {
		return i << 4 | j << 20;
	}

	public static int block(int i) {
		return i >>> 4 & 15;
	}

	public static int sky(int i) {
		return i >>> 20 & 15;
	}

	public static int lightCoordsWithEmission(int i, int j) {
		if (j == 0) {
			return i;
		} else {
			int k = Math.max(sky(i), j);
			int l = Math.max(block(i), j);
			return pack(l, k);
		}
	}
}

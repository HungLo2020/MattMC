package net.minecraft.client.renderer;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.Std140Builder;
import net.blaze3d.buffers.Std140SizeCalculator;
import net.blaze3d.platform.NativeImage;
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
import net.irisshaders.iris.gl.texture.TextureUploadHelper;
import net.irisshaders.iris.pbr.TextureTracker;
import net.logging.LogUtils;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicTextureUploadFormat;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class LightTexture implements AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
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
	private final NativeImage cpuLightmapPixels;
	private boolean dumpedCpuLightmapDebug;
	private boolean dumpedGpuLightmapDebug;
	private boolean dumpedProbeLightmapDebug;
	private boolean dumpedControlAtlasDebug;
	private GpuTexture probeLightmapDebugTexture;
	private boolean loggedCpuLightmapDebug;
	private boolean updateLightTexture;
	private float blockLightRedFlicker;
	private final GameRenderer renderer;
	private final Minecraft minecraft;
	private final MappableRingBuffer ubo;

	public LightTexture(GameRenderer gameRenderer, Minecraft minecraft) {
		this.renderer = gameRenderer;
		this.minecraft = minecraft;
		int textureUsage = VulkanicAPI.isVulkanBackendSelected()
			? GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING
			: GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
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
		this.cpuLightmapPixels = new NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, true);
		if (VulkanicAPI.isVulkanBackendSelected()) {
			VulkanicAPI.createCommandEncoder().writeToTexture(this.texture, this.cpuLightmapPixels);
		} else {
			VulkanicAPI.createCommandEncoder().clearColorTexture(this.texture, -1);
		}
		this.ubo = new MappableRingBuffer(() -> "Lightmap UBO", 130, LIGHTMAP_UBO_SIZE);
	}

	public GpuTextureView getTextureView() {
		return this.textureView;
	}

	public String debugDescribePackedLight(int packedLight) {
		int blockLight = packedLight & 0xF;
		int skyLight = packedLight >> 20 & 0xF;
		int sample = this.cpuLightmapPixels.getPixel(blockLight, skyLight);
		return "packed=0x%08X block=%d sky=%d sample=0x%08X".formatted(packedLight, blockLight, skyLight, sample);
	}

	public void close() {
		if (this.probeLightmapDebugTexture != null) {
			this.probeLightmapDebugTexture.close();
		}
		this.texture.close();
		this.textureView.close();
		this.cpuLightmapPixels.close();
		this.ubo.close();
	}

	public void tick() {
		this.blockLightRedFlicker = this.blockLightRedFlicker + (float)((Math.random() - Math.random()) * Math.random() * Math.random() * 0.1);
		this.blockLightRedFlicker *= 0.9F;
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

				float n = this.blockLightRedFlicker + 1.5F;
				float o = clientLevel.dimensionType().ambientLight();
				float p = this.minecraft.options.gamma().get().floatValue();
				CommandEncoder commandEncoder = VulkanicAPI.createCommandEncoder();

				try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(this.ubo.currentBuffer(), false, true)) {
					Std140Builder.intoBuffer(mappedView.data())
						.putFloat(o)
						.putFloat(i)
						.putFloat(n)
						.putFloat(m)
						.putFloat(k)
						.putFloat(this.renderer.getDarkenWorldAmount(f))
						.putFloat(Math.max(0.0F, p - h))
						.putVec3(vector3f2)
						.putVec3(vector3f);
				}

				if (VulkanicAPI.isVulkanBackendSelected()) {
					this.updateLightTextureCpu(commandEncoder, o, i, n, m, k, this.renderer.getDarkenWorldAmount(f), Math.max(0.0F, p - h), vector3f2, vector3f);
				} else {
					try (RenderPass renderPass = commandEncoder.createRenderPass(() -> "Update light", this.textureView, OptionalInt.empty())) {
						renderPass.setPipeline(RenderPipelines.LIGHTMAP);
						net.vulkanic.VulkanicAPI.bindDefaultUniforms(renderPass);
						renderPass.setUniform("LightmapInfo", this.ubo.currentBuffer());
						renderPass.draw(0, 3);
					}
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

	private void updateLightTextureCpu(
		CommandEncoder commandEncoder,
		float ambientLightFactor,
		float skyFactor,
		float blockFactor,
		float nightVisionFactor,
		float darknessScale,
		float darkenWorldFactor,
		float brightnessFactor,
		Vector3f skyLightColor,
		Vector3f ambientColor
	) {
		for (int skyLight = 0; skyLight < TEXTURE_SIZE; skyLight++) {
			for (int blockLight = 0; blockLight < TEXTURE_SIZE; blockLight++) {
				float blockBrightness = this.lightmapBrightness(blockLight / 15.0F) * blockFactor;
				float skyBrightness = this.lightmapBrightness(skyLight / 15.0F) * skyFactor;
				float red = blockBrightness;
				float green = blockBrightness * ((blockBrightness * 0.6F + 0.4F) * 0.6F + 0.4F);
				float blue = blockBrightness * (blockBrightness * blockBrightness * 0.6F + 0.4F);

				red = Mth.lerp(ambientLightFactor, red, ambientColor.x);
				green = Mth.lerp(ambientLightFactor, green, ambientColor.y);
				blue = Mth.lerp(ambientLightFactor, blue, ambientColor.z);
				red += skyLightColor.x * skyBrightness;
				green += skyLightColor.y * skyBrightness;
				blue += skyLightColor.z * skyBrightness;
				red = Mth.lerp(0.04F, red, 0.75F);
				green = Mth.lerp(0.04F, green, 0.75F);
				blue = Mth.lerp(0.04F, blue, 0.75F);

				if (ambientLightFactor == 0.0F) {
					red = Mth.lerp(darkenWorldFactor, red, red * 0.7F);
					green = Mth.lerp(darkenWorldFactor, green, green * 0.6F);
					blue = Mth.lerp(darkenWorldFactor, blue, blue * 0.6F);
				}

				if (nightVisionFactor > 0.0F) {
					float maxComponent = Math.max(red, Math.max(green, blue));
					if (maxComponent > 0.0F && maxComponent < 1.0F) {
						float brightRed = red / maxComponent;
						float brightGreen = green / maxComponent;
						float brightBlue = blue / maxComponent;
						red = Mth.lerp(nightVisionFactor, red, brightRed);
						green = Mth.lerp(nightVisionFactor, green, brightGreen);
						blue = Mth.lerp(nightVisionFactor, blue, brightBlue);
					}
				}

				if (ambientLightFactor == 0.0F) {
					red -= darknessScale;
					green -= darknessScale;
					blue -= darknessScale;
				}

				red = Mth.clamp(red, 0.0F, 1.0F);
				green = Mth.clamp(green, 0.0F, 1.0F);
				blue = Mth.clamp(blue, 0.0F, 1.0F);

				float[] notGamma = this.notGamma(red, green, blue);
				red = Mth.lerp(brightnessFactor, red, notGamma[0]);
				green = Mth.lerp(brightnessFactor, green, notGamma[1]);
				blue = Mth.lerp(brightnessFactor, blue, notGamma[2]);
				red = Mth.lerp(0.04F, red, 0.75F);
				green = Mth.lerp(0.04F, green, 0.75F);
				blue = Mth.lerp(0.04F, blue, 0.75F);

				this.cpuLightmapPixels.setPixel(blockLight, skyLight, ARGB.colorFromFloat(1.0F, red, green, blue));
			}
		}

		this.dumpCpuLightmapDebugOnce();
		this.logCpuLightmapDebugOnce(
			ambientLightFactor,
			skyFactor,
			blockFactor,
			nightVisionFactor,
			darknessScale,
			darkenWorldFactor,
			brightnessFactor
		);
		this.uploadCpuLightmapWithTexImage2D();
		this.dumpGpuLightmapDebugOnce();
		this.dumpProbeLightmapDebugOnce(commandEncoder);
		this.dumpControlAtlasDebugOnce();
	}

	private void uploadCpuLightmapWithTexImage2D() {
		TextureUploadHelper.resetTextureUploadState();
		IrisRenderSystem.texImage2D(
			VulkanicAPI.getTextureHandle(this.texture),
			0,
			VulkanicTextureUploadFormat.RGBA8_UNORM,
			TEXTURE_SIZE,
			TEXTURE_SIZE,
			0,
			MemoryUtil.memByteBuffer(this.cpuLightmapPixels.pixels, TEXTURE_SIZE * TEXTURE_SIZE * 4)
		);
	}

	private void dumpCpuLightmapDebugOnce() {
		if (this.dumpedCpuLightmapDebug) {
			return;
		}

		this.dumpedCpuLightmapDebug = true;

		try {
			Path autoCaptureDir = this.getAutoCaptureDir();
			Files.createDirectories(autoCaptureDir);
			this.cpuLightmapPixels.writeToFile(autoCaptureDir.resolve("light_texture_debug_cpu_0.png"));
		} catch (IOException ignored) {
		}
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

	private void dumpProbeLightmapDebugOnce(CommandEncoder commandEncoder) {
		if (this.dumpedProbeLightmapDebug) {
			return;
		}

		this.dumpedProbeLightmapDebug = true;
		this.probeLightmapDebugTexture = VulkanicAPI.createTexture(
			"Light Texture Upload Probe",
			GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING,
			TextureFormat.RGBA8,
			TEXTURE_SIZE,
			TEXTURE_SIZE,
			1,
			1
		);

		try {
			commandEncoder.writeToTexture(this.probeLightmapDebugTexture, this.cpuLightmapPixels);
			Path autoCaptureDir = this.getAutoCaptureDir();
			Files.createDirectories(autoCaptureDir);
			TextureUtil.writeAsPNG(autoCaptureDir, "light_texture_debug_probe_gpu", this.probeLightmapDebugTexture, 0, i -> i);
		} catch (IOException ignored) {
		}
	}

	private void dumpControlAtlasDebugOnce() {
		if (this.dumpedControlAtlasDebug) {
			return;
		}

		try {
			Path autoCaptureDir = this.getAutoCaptureDir();
			Files.createDirectories(autoCaptureDir);
			GpuTexture atlasTexture = this.minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getTexture();
			TextureUtil.writeAsPNG(
				autoCaptureDir,
				"light_texture_debug_control_blocks_atlas",
				atlasTexture,
				0,
				i -> i
			);
			this.dumpedControlAtlasDebug = true;
		} catch (IllegalStateException ignored) {
		} catch (IOException exception) {
			LOGGER.warn("Failed to dump Vulkan control atlas texture", exception);
		}
	}

	private void logCpuLightmapDebugOnce(
		float ambientLightFactor,
		float skyFactor,
		float blockFactor,
		float nightVisionFactor,
		float darknessScale,
		float darkenWorldFactor,
		float brightnessFactor
	) {
		if (this.loggedCpuLightmapDebug) {
			return;
		}

		this.loggedCpuLightmapDebug = true;
		int sample00 = this.cpuLightmapPixels.getPixel(0, 0);
		int sample150 = this.cpuLightmapPixels.getPixel(15, 0);
		int sample015 = this.cpuLightmapPixels.getPixel(0, 15);
		int sample1515 = this.cpuLightmapPixels.getPixel(15, 15);
		LOGGER.info(
			"Vulkan CPU lightmap factors ambient={} sky={} block={} nightVision={} darkness={} darkenWorld={} brightness={} samples=[0,0]={} [15,0]={} [0,15]={} [15,15]={}",
			ambientLightFactor,
			skyFactor,
			blockFactor,
			nightVisionFactor,
			darknessScale,
			darkenWorldFactor,
			brightnessFactor,
			String.format("0x%08X", sample00),
			String.format("0x%08X", sample150),
			String.format("0x%08X", sample015),
			String.format("0x%08X", sample1515)
		);
	}

	private Path getAutoCaptureDir() {
		Path gameDir = this.minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
		Path parent = gameDir.getParent();
		return (parent != null ? parent : gameDir).resolve("logs/auto-capture");
	}

	private float lightmapBrightness(float level) {
		return level / (4.0F - 3.0F * level);
	}

	private float[] notGamma(float red, float green, float blue) {
		float maxComponent = Math.max(red, Math.max(green, blue));
		if (maxComponent <= 0.0F) {
			return new float[] {0.0F, 0.0F, 0.0F};
		}

		float maxInverted = 1.0F - maxComponent;
		float maxScaled = 1.0F - maxInverted * maxInverted * maxInverted * maxInverted;
		float scale = maxScaled / maxComponent;
		return new float[] {red * scale, green * scale, blue * scale};
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

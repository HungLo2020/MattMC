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
import net.logging.LogUtils;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.vulkanic.VulkanicAPI;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class LightTexture implements AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final boolean PROBE_VULKAN_SHADER_LIGHTMAP = Boolean.getBoolean("mattmc.vulkan.probeShaderLightmap");
	private static final boolean DETERMINISTIC_LIGHTMAP_PARITY = Boolean.getBoolean("mattmc.vulkan.deterministicLightmapParity");
	private static final boolean TRACE_LIGHTMAP_INFO_PARITY = Boolean.getBoolean("mattmc.vulkan.traceLightmapInfoParity");
	private static final int MAX_LIGHTMAP_INFO_PARITY_LOGS = Integer.getInteger("mattmc.vulkan.traceLightmapInfoParity.maxLogs", 512);
	private static final java.util.concurrent.atomic.AtomicInteger LIGHTMAP_INFO_PARITY_LOG_COUNT = new java.util.concurrent.atomic.AtomicInteger();
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
	@Nullable
	public GpuTexture texture; // Made public for mod integration (was accessed via mixin)
	@Nullable
	private GpuTextureView textureView;
	private boolean dumpedGpuLightmapDebug;
	private GpuTexture shaderLightmapProbeTexture;
	private GpuTextureView shaderLightmapProbeView;
	private boolean dumpedShaderLightmapProbeDebug;
	private boolean updateLightTexture;
	private float blockLightRedFlicker;
	/**
	 * Immutable gameplay inputs used to build the current vanilla lightmap.
	 * Rust consumes these scalars through the normal semantic frame transport;
	 * it never receives this texture, a view, or a command encoder.
	 */
	private volatile RustSemanticLightmapInputs rustSemanticLightmapInputs;
	private long rustSemanticLightmapGeneration;
	private final GameRenderer renderer;
	private final Minecraft minecraft;
	@Nullable
	private MappableRingBuffer ubo;

	public LightTexture(GameRenderer gameRenderer, Minecraft minecraft) {
		this.texture = null;
		this.textureView = null;
		this.ubo = null;
		this.renderer = gameRenderer;
		this.minecraft = minecraft;
		return;
	}

	@Nullable
	public RustSemanticLightmapInputs rustSemanticLightmapInputs() {
		return this.rustSemanticLightmapInputs;
	}

	/**
	 * Supplies the first copied lightmap snapshot to a Rust-owned whole-frame
	 * route before vanilla's later GPU lightmap update has run. This is pure
	 * gameplay-semantic extraction: it neither creates a command encoder nor
	 * exposes this class's texture, UBO, or Iris state.
	 */
	@Nullable
	public RustSemanticLightmapInputs ensureRustSemanticLightmapInputs(float partialTicks) {
		if (this.rustSemanticLightmapInputs != null
			&& !net.minecraft.client.dev.DeterministicCameraCapture.refreshLightmapInputsForCapture()) {
			return this.rustSemanticLightmapInputs;
		}
		ClientLevel clientLevel = this.minecraft.level;
		if (clientLevel == null || this.minecraft.player == null) {
			return null;
		}
		this.rustSemanticLightmapInputs = this.computeRustSemanticLightmapInputs(
			clientLevel, this.minecraft.player, partialTicks, false
		);
		return this.rustSemanticLightmapInputs;
	}

	public record RustSemanticLightmapInputs(
		long generation,
		float ambientLightFactor,
		float skyFactor,
		float blockFactor,
		float nightVisionFactor,
		float darknessScale,
		float darkenWorldFactor,
		float brightnessFactor,
		float skyLightRed,
		float skyLightGreen,
		float skyLightBlue,
		float ambientRed,
		float ambientGreen,
		float ambientBlue
	) { }

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
		if (this.texture != null) {
			this.texture.close();
		}
		if (this.textureView != null) {
			this.textureView.close();
		}
		if (this.ubo != null) {
			this.ubo.close();
		}
		this.shaderLightmapProbeView = null;
		this.shaderLightmapProbeTexture = null;
		this.texture = null;
		this.textureView = null;
		this.ubo = null;
	}

	/** Releases compatibility lightmap resources if Vulkan ownership starts after construction. */
	public void ensureRustSemanticRoute() {
		if (this.texture != null || this.textureView != null || this.ubo != null
			|| this.shaderLightmapProbeTexture != null || this.shaderLightmapProbeView != null) {
			this.close();
		}
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
		return;
	}

	public void turnOnLightLayer() {
		return;
	}

	private float calculateDarknessScale(LivingEntity livingEntity, float f, float g) {
		float h = 0.45F * f;
		float result = Math.max(0.0F, Mth.cos((livingEntity.tickCount - g) * (float) Math.PI * 0.025F) * h);
		
		
		return result;
	}

	private RustSemanticLightmapInputs computeRustSemanticLightmapInputs(
		ClientLevel clientLevel, LocalPlayer player, float partialTicks, boolean updateIrisDarkness
	) {
		float skyDarken = clientLevel.getSkyDarken(1.0F);
		float skyFactor;
		Vector3f ambientColor;
		if (clientLevel.effects().hasEndFlashes()) {
			ambientColor = new Vector3f(0.99F, 1.12F, 1.0F);
			EndFlashState endFlashState = clientLevel.endFlashState();
			if (endFlashState != null && !this.minecraft.options.hideLightningFlash().get()) {
				float intensity = endFlashState.getIntensity(partialTicks);
				skyFactor = this.minecraft.gui.getBossOverlay().shouldCreateWorldFog() ? intensity / 3.0F : intensity;
			} else {
				skyFactor = 0.0F;
			}
		} else {
			ambientColor = new Vector3f(1.0F, 1.0F, 1.0F);
			skyFactor = clientLevel.getSkyFlashTime() > 0 ? 1.0F : skyDarken * 0.95F + 0.05F;
		}

		float darknessEffectScale = this.minecraft.options.darknessEffectScale().get().floatValue();
		float darknessBlend = player.getEffectBlendFactor(MobEffects.DARKNESS, partialTicks) * darknessEffectScale;
		float darknessScale = (updateIrisDarkness
			? this.calculateDarknessScale(player, darknessBlend, partialTicks)
			: Math.max(0.0F, Mth.cos((player.tickCount - partialTicks) * (float)Math.PI * 0.025F) * 0.45F * darknessBlend)
		) * darknessEffectScale;
		float waterVision = player.getWaterVision();
		float nightVisionFactor;
		if (player.hasEffect(MobEffects.NIGHT_VISION)) {
			nightVisionFactor = GameRenderer.getNightVisionScale(player, partialTicks);
		} else if (waterVision > 0.0F && player.hasEffect(MobEffects.CONDUIT_POWER)) {
			nightVisionFactor = waterVision;
		} else {
			nightVisionFactor = 0.0F;
		}

		Vector3f skyLightColor = clientLevel.effects().hasEndFlashes()
			? END_FLASH_SKY_LIGHT_COLOR
			: new Vector3f(skyDarken, skyDarken, 1.0F).lerp(new Vector3f(1.0F, 1.0F, 1.0F), 0.35F);
		if (DETERMINISTIC_LIGHTMAP_PARITY) {
			this.blockLightRedFlicker = 0.0F;
		}
		RustSemanticLightmapInputs inputs = new RustSemanticLightmapInputs(
			++this.rustSemanticLightmapGeneration,
			clientLevel.dimensionType().ambientLight(), skyFactor, this.blockLightRedFlicker + 1.5F,
			nightVisionFactor, darknessScale, this.renderer.getDarkenWorldAmount(partialTicks),
			Math.max(0.0F, this.minecraft.options.gamma().get().floatValue() - darknessBlend),
			skyLightColor.x, skyLightColor.y, skyLightColor.z,
			ambientColor.x, ambientColor.y, ambientColor.z
		);
		inputs = net.minecraft.client.dev.DeterministicCameraCapture.lightmapInputsForCapture(inputs);
		net.minecraft.client.dev.DeterministicCameraCapture.recordLightmapSemanticFingerprint(
			inputs.ambientLightFactor() + "," + inputs.skyFactor() + "," + inputs.blockFactor() + ","
				+ inputs.nightVisionFactor() + "," + inputs.darknessScale() + "," + inputs.darkenWorldFactor() + ","
				+ inputs.brightnessFactor() + "," + inputs.skyLightRed() + "," + inputs.skyLightGreen() + ","
				+ inputs.skyLightBlue() + "," + inputs.ambientRed() + "," + inputs.ambientGreen() + "," + inputs.ambientBlue()
		);
		return inputs;
	}

	public void updateLightTexture(float f) {
		if (this.updateLightTexture) {
			this.updateLightTexture = false;
			ProfilerFiller profilerFiller = Profiler.get();
			profilerFiller.push("lightTex");
			ClientLevel clientLevel = this.minecraft.level;
			if (clientLevel != null) {
				this.ensureRustSemanticRoute();
				// The renderer can tick once while a client level exists but its
				// player has not been attached yet.  Keep the semantic resource
				// unavailable for that frame instead of dereferencing a null player
				// or resurrecting the Java lightmap as a fallback.
				if (this.minecraft.player == null) {
					this.rustSemanticLightmapInputs = null;
					profilerFiller.pop();
					return;
				}
				this.rustSemanticLightmapInputs = this.computeRustSemanticLightmapInputs(
					clientLevel, this.minecraft.player, f, false
				);
				profilerFiller.pop();
				return;
			}
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

	public static float getBrightness(DimensionType dimensionType, int i) {
		return getBrightness(dimensionType.ambientLight(), i);
	}

	public static float getBrightness(float f, int i) {
		float g = i / 15.0F;
		float h = g / (4.0F - 3.0F * g);
		return Mth.lerp(f, h, 1.0F);
	}

	/**
	 * Applies the copied vanilla lightmap equation to a semantic text color.
	 * World text is lowered by Rust, so it cannot sample the Java lightmap
	 * texture; using the same immutable inputs keeps the lighting decision
	 * explicit without borrowing a GPU texture or Iris state.
	 */
	public int rustSemanticPackedLightColor(int packedLight, int argb) {
		RustSemanticLightmapInputs inputs = this.rustSemanticLightmapInputs;
		if (inputs == null) {
			return argb;
		}
		float block = getBrightness(0.0F, block(packedLight));
		block *= inputs.blockFactor();
		float sky = getBrightness(0.0F, sky(packedLight));
		sky *= inputs.skyFactor();
		float red = block;
		float green = block * ((block * 0.6F + 0.4F) * 0.6F + 0.4F);
		float blue = block * (block * block * 0.6F + 0.4F);
		red = Mth.lerp(inputs.ambientLightFactor(), red, inputs.ambientRed());
		green = Mth.lerp(inputs.ambientLightFactor(), green, inputs.ambientGreen());
		blue = Mth.lerp(inputs.ambientLightFactor(), blue, inputs.ambientBlue());
		red += inputs.skyLightRed() * sky;
		green += inputs.skyLightGreen() * sky;
		blue += inputs.skyLightBlue() * sky;
		red = Mth.lerp(0.04F, red, 0.75F);
		green = Mth.lerp(0.04F, green, 0.75F);
		blue = Mth.lerp(0.04F, blue, 0.75F);
		if (inputs.ambientLightFactor() == 0.0F) {
			red = Mth.lerp(inputs.darkenWorldFactor(), red, red * 0.7F);
			green = Mth.lerp(inputs.darkenWorldFactor(), green, green * 0.6F);
			blue = Mth.lerp(inputs.darkenWorldFactor(), blue, blue * 0.6F);
		}
		if (inputs.nightVisionFactor() > 0.0F) {
			float maximum = Math.max(red, Math.max(green, blue));
			if (maximum > 0.0F && maximum < 1.0F) {
				red = Mth.lerp(inputs.nightVisionFactor(), red, red / maximum);
				green = Mth.lerp(inputs.nightVisionFactor(), green, green / maximum);
				blue = Mth.lerp(inputs.nightVisionFactor(), blue, blue / maximum);
			}
		}
		if (inputs.ambientLightFactor() == 0.0F) {
			red -= inputs.darknessScale();
			green -= inputs.darknessScale();
			blue -= inputs.darknessScale();
		}
		red = Mth.clamp(red, 0.0F, 1.0F);
		green = Mth.clamp(green, 0.0F, 1.0F);
		blue = Mth.clamp(blue, 0.0F, 1.0F);
		float maxComponent = Math.max(red, Math.max(green, blue));
		if (maxComponent > 0.0F) {
			float maxInverted = 1.0F - maxComponent;
			float maxScaled = 1.0F - maxInverted * maxInverted * maxInverted * maxInverted;
			float scale = maxScaled / maxComponent;
			red = Mth.lerp(inputs.brightnessFactor(), red, red * scale);
			green = Mth.lerp(inputs.brightnessFactor(), green, green * scale);
			blue = Mth.lerp(inputs.brightnessFactor(), blue, blue * scale);
		}
		red = Mth.lerp(0.04F, red, 0.75F);
		green = Mth.lerp(0.04F, green, 0.75F);
		blue = Mth.lerp(0.04F, blue, 0.75F);
		return ARGB.colorFromFloat(ARGB.alphaFloat(argb),
			ARGB.redFloat(argb) * red, ARGB.greenFloat(argb) * green, ARGB.blueFloat(argb) * blue);
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

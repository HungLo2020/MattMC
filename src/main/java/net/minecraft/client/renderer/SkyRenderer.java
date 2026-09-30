package net.minecraft.client.renderer;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.vertex.BufferBuilder;
import net.blaze3d.vertex.ByteBufferBuilder;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.MeshData;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.blaze3d.vertex.VertexFormat;
import net.math.Axis;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.irisshaders.iris.Iris;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public class SkyRenderer implements AutoCloseable {
	private static final ResourceLocation SUN_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
	private static final ResourceLocation END_LIGHT_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/end_flash.png");
	private static final ResourceLocation MOON_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
	private static final ResourceLocation END_SKY_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");
	private static final float SKY_DISC_RADIUS = 512.0F;
	private static final int SKY_VERTICES = 10;
	private static final int STAR_COUNT = 1500;
	private static final float SUN_SIZE = 30.0F;
	private static final float SUN_HEIGHT = 100.0F;
	private static final float MOON_SIZE = 20.0F;
	private static final float MOON_HEIGHT = 100.0F;
	private static final int SUNRISE_STEPS = 16;
	private static final int END_SKY_QUAD_COUNT = 6;
	private static final float END_FLASH_HEIGHT = 100.0F;
	private static final float END_FLASH_SCALE = 60.0F;
	@Nullable
	private GpuBuffer starBuffer;
	@Nullable
	private final VulkanicAPI.AutoStorageIndexBuffer starIndices;
	@Nullable
	private GpuBuffer topSkyBuffer;
	@Nullable
	private GpuBuffer bottomSkyBuffer;
	@Nullable
	private GpuBuffer endSkyBuffer;
	@Nullable
	private GpuBuffer sunBuffer;
	@Nullable
	private GpuBuffer moonBuffer;
	@Nullable
	private GpuBuffer sunriseBuffer;
	@Nullable
	private GpuBuffer endFlashBuffer;
	@Nullable
	private final VulkanicAPI.AutoStorageIndexBuffer quadIndices;
	@Nullable
	private AbstractTexture sunTexture;
	@Nullable
	private AbstractTexture moonTexture;
	@Nullable
	private AbstractTexture endSkyTexture;
	@Nullable
	private AbstractTexture endFlashTexture;
	private int starIndexCount;

	public SkyRenderer() {
		this.starIndices = null;
		this.quadIndices = null;
		// Rust owns celestial mesh lowering and source-asset copies for the
		// whole-frame route; Java sky vertex buffers are never consumed.
		this.starBuffer = null;
		this.topSkyBuffer = null;
		this.bottomSkyBuffer = null;
		this.endSkyBuffer = null;
		this.endFlashBuffer = null;
		this.sunBuffer = null;
		this.moonBuffer = null;
		this.sunriseBuffer = null;
		return;
	}

	protected void initTextures() {
		// The admitted whole-frame sky route copies its celestial source assets
		// through RustGalWorldPrimitiveRenderer during reload.  Retaining these
		// Java texture objects would only recreate the legacy GPU upload path.
		return;

	}

	public void extractRenderState(ClientLevel clientLevel, float f, Vec3 vec3, SkyRenderState skyRenderState) {
		DimensionSpecialEffects dimensionSpecialEffects = clientLevel.effects();
		skyRenderState.skyType = dimensionSpecialEffects.skyType();
		if (skyRenderState.skyType != DimensionSpecialEffects.SkyType.NONE) {
			if (skyRenderState.skyType == DimensionSpecialEffects.SkyType.END) {
				EndFlashState endFlashState = clientLevel.endFlashState();
				if (endFlashState != null) {
					skyRenderState.endFlashIntensity = endFlashState.getIntensity(f);
					skyRenderState.endFlashXAngle = endFlashState.getXAngle();
					skyRenderState.endFlashYAngle = endFlashState.getYAngle();
				}
			} else {
				skyRenderState.sunAngle = clientLevel.getSunAngle(f);
				skyRenderState.timeOfDay = clientLevel.getTimeOfDay(f);
				skyRenderState.rainBrightness = 1.0F - clientLevel.getRainLevel(f);
				skyRenderState.starBrightness = clientLevel.getStarBrightness(f) * skyRenderState.rainBrightness;
				skyRenderState.sunriseAndSunsetColor = dimensionSpecialEffects.getSunriseOrSunsetColor(skyRenderState.timeOfDay);
				skyRenderState.moonPhase = clientLevel.getMoonPhase();
				skyRenderState.skyColor = clientLevel.getSkyColor(vec3, f);
				skyRenderState.shouldRenderDarkDisc = this.shouldRenderDarkDisc(f, clientLevel);
				skyRenderState.isSunriseOrSunset = dimensionSpecialEffects.isSunriseOrSunset(skyRenderState.timeOfDay);
			}
		}
	}

	private boolean shouldRenderDarkDisc(float f, ClientLevel clientLevel) {
		// During client-level/player attachment there may be a level before a
		// camera entity exists.  Sky extraction is semantic and must remain
		// unavailable for that transient frame rather than dereferencing the
		// missing player or entering a Java GPU fallback.
		if (Minecraft.getInstance().player == null) {
			return false;
		}
		return Minecraft.getInstance().player.getEyePosition(f).y - clientLevel.getLevelData().getHorizonHeight(clientLevel) < 0.0;
	}

	public void close() {
		if (this.sunBuffer != null) this.sunBuffer.close();
		if (this.moonBuffer != null) this.moonBuffer.close();
		if (this.starBuffer != null) this.starBuffer.close();
		if (this.topSkyBuffer != null) this.topSkyBuffer.close();
		if (this.bottomSkyBuffer != null) this.bottomSkyBuffer.close();
		if (this.endSkyBuffer != null) this.endSkyBuffer.close();
		if (this.sunriseBuffer != null) this.sunriseBuffer.close();
		if (this.endFlashBuffer != null) this.endFlashBuffer.close();
		this.sunBuffer = null;
		this.moonBuffer = null;
		this.starBuffer = null;
		this.topSkyBuffer = null;
		this.bottomSkyBuffer = null;
		this.endSkyBuffer = null;
		this.sunriseBuffer = null;
		this.endFlashBuffer = null;
	}

	/** Releases Java celestial buffers if Rust Vulkan ownership begins after sky construction. */
	public void ensureRustSemanticRoute() {
		if ((this.starBuffer != null || this.topSkyBuffer != null || this.bottomSkyBuffer != null
				|| this.endSkyBuffer != null || this.sunBuffer != null || this.moonBuffer != null
				|| this.sunriseBuffer != null || this.endFlashBuffer != null)) {
			this.close();
		}
	}
	
}

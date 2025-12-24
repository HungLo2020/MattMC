package net.minecraft.client.renderer;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public abstract class RenderType extends RenderStateShard {
	private static final int MEGABYTE = 1048576;
	public static final int BIG_BUFFER_SIZE = 4194304;
	public static final int SMALL_BUFFER_SIZE = 786432;
	public static final int TRANSIENT_BUFFER_SIZE = 1536;
	private static final RenderType SOLID = create(
		"solid",
		1536,
		true,
		false,
		RenderPipelines.SOLID,
		RenderType.CompositeState.builder().setLightmapState(LIGHTMAP).setTextureState(BLOCK_SHEET_MIPPED).createCompositeState(true)
	);
	private static final RenderType CUTOUT_MIPPED = create(
		"cutout_mipped",
		1536,
		true,
		false,
		RenderPipelines.CUTOUT_MIPPED,
		RenderType.CompositeState.builder().setLightmapState(LIGHTMAP).setTextureState(BLOCK_SHEET_MIPPED).createCompositeState(true)
	);
	private static final RenderType CUTOUT = create(
		"cutout",
		1536,
		true,
		false,
		RenderPipelines.CUTOUT,
		RenderType.CompositeState.builder().setLightmapState(LIGHTMAP).setTextureState(BLOCK_SHEET).createCompositeState(true)
	);
	private static final RenderType TRANSLUCENT_MOVING_BLOCK = create(
		"translucent_moving_block",
		786432,
		false,
		true,
		RenderPipelines.TRANSLUCENT_MOVING_BLOCK,
		RenderType.CompositeState.builder()
			.setLightmapState(LIGHTMAP)
			.setTextureState(BLOCK_SHEET_MIPPED)
			.setOutputState(ITEM_ENTITY_TARGET)
			.createCompositeState(true)
	);
	private static final Function<ResourceLocation, RenderType> ARMOR_CUTOUT_NO_CULL = Util.memoize(
		resourceLocation -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.setLayeringState(VIEW_OFFSET_Z_LAYERING)
				.createCompositeState(true);
			return create("armor_cutout_no_cull", 1536, true, false, RenderPipelines.ARMOR_CUTOUT_NO_CULL, compositeState);
		}
	);
	private static final Function<ResourceLocation, RenderType> ARMOR_TRANSLUCENT = Util.memoize(
		resourceLocation -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.setLayeringState(VIEW_OFFSET_Z_LAYERING)
				.createCompositeState(true);
			return create("armor_translucent", 1536, true, true, RenderPipelines.ARMOR_TRANSLUCENT, compositeState);
		}
	);
	private static final Function<ResourceLocation, RenderType> ENTITY_SOLID = Util.memoize(
		resourceLocation -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.createCompositeState(true);
			return create("entity_solid", 1536, true, false, RenderPipelines.ENTITY_SOLID, compositeState);
		}
	);
	private static final Function<ResourceLocation, RenderType> ENTITY_SOLID_Z_OFFSET_FORWARD = Util.memoize(
		resourceLocation -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.setLayeringState(VIEW_OFFSET_Z_LAYERING_FORWARD)
				.createCompositeState(true);
			return create("entity_solid_z_offset_forward", 1536, true, false, RenderPipelines.ENTITY_SOLID_Z_OFFSET_FORWARD, compositeState);
		}
	);
	private static final Function<ResourceLocation, RenderType> ENTITY_CUTOUT = Util.memoize(
		resourceLocation -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.createCompositeState(true);
			return create("entity_cutout", 1536, true, false, RenderPipelines.ENTITY_CUTOUT, compositeState);
		}
	);
	private static final BiFunction<ResourceLocation, Boolean, RenderType> ENTITY_CUTOUT_NO_CULL = Util.memoize(
		(resourceLocation, boolean_) -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.createCompositeState(boolean_);
			return create("entity_cutout_no_cull", 1536, true, false, RenderPipelines.ENTITY_CUTOUT_NO_CULL, compositeState);
		}
	);
	private static final BiFunction<ResourceLocation, Boolean, RenderType> ENTITY_CUTOUT_NO_CULL_Z_OFFSET = Util.memoize(
		(resourceLocation, boolean_) -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.setLayeringState(VIEW_OFFSET_Z_LAYERING)
				.createCompositeState(boolean_);
			return create("entity_cutout_no_cull_z_offset", 1536, true, false, RenderPipelines.ENTITY_CUTOUT_NO_CULL_Z_OFFSET, compositeState);
		}
	);
	private static final Function<ResourceLocation, RenderType> ITEM_ENTITY_TRANSLUCENT_CULL = Util.memoize(
		resourceLocation -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setOutputState(ITEM_ENTITY_TARGET)
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.createCompositeState(true);
			return create("item_entity_translucent_cull", 1536, true, true, RenderPipelines.ITEM_ENTITY_TRANSLUCENT_CULL, compositeState);
		}
	);
	private static final BiFunction<ResourceLocation, Boolean, RenderType> ENTITY_TRANSLUCENT = Util.memoize(
		(resourceLocation, boolean_) -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.createCompositeState(boolean_);
			return create("entity_translucent", 1536, true, true, RenderPipelines.ENTITY_TRANSLUCENT, compositeState);
		}
	);
	private static final BiFunction<ResourceLocation, Boolean, RenderType> ENTITY_TRANSLUCENT_EMISSIVE = Util.memoize(
		(resourceLocation, boolean_) -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setOverlayState(OVERLAY)
				.createCompositeState(boolean_);
			return create("entity_translucent_emissive", 1536, true, true, RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE, compositeState);
		}
	);
	private static final Function<ResourceLocation, RenderType> ENTITY_SMOOTH_CUTOUT = Util.memoize(
		resourceLocation -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.createCompositeState(true);
			return create("entity_smooth_cutout", 1536, RenderPipelines.ENTITY_SMOOTH_CUTOUT, compositeState);
		}
	);
	private static final BiFunction<ResourceLocation, Boolean, RenderType> BEACON_BEAM = Util.memoize(
		(resourceLocation, boolean_) -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.createCompositeState(false);
			return create("beacon_beam", 1536, false, true, boolean_ ? RenderPipelines.BEACON_BEAM_TRANSLUCENT : RenderPipelines.BEACON_BEAM_OPAQUE, compositeState);
		}
	);
	private static final Function<ResourceLocation, RenderType> ENTITY_DECAL = Util.memoize(
		resourceLocation -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.createCompositeState(false);
			return create("entity_decal", 1536, RenderPipelines.ENTITY_DECAL, compositeState);
		}
	);
	private static final Function<ResourceLocation, RenderType> ENTITY_NO_OUTLINE = Util.memoize(
		resourceLocation -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.createCompositeState(false);
			return create("entity_no_outline", 1536, false, true, RenderPipelines.ENTITY_NO_OUTLINE, compositeState);
		}
	);
	private static final Function<ResourceLocation, RenderType> ENTITY_SHADOW = Util.memoize(
		resourceLocation -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.setLayeringState(VIEW_OFFSET_Z_LAYERING)
				.createCompositeState(false);
			return create("entity_shadow", 1536, false, false, RenderPipelines.ENTITY_SHADOW, compositeState);
		}
	);
	private static final Function<ResourceLocation, RenderType> DRAGON_EXPLOSION_ALPHA = Util.memoize(
		resourceLocation -> {
			RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.createCompositeState(true);
			return create("entity_alpha", 1536, RenderPipelines.DRAGON_EXPLOSION_ALPHA, compositeState);
		}
	);
	private static final Function<ResourceLocation, RenderType> EYES = Util.memoize(
		resourceLocation -> {
			RenderStateShard.TextureStateShard textureStateShard = new RenderStateShard.TextureStateShard(resourceLocation, false);
			return create(
				"eyes", 1536, false, true, RenderPipelines.EYES, RenderType.CompositeState.builder().setTextureState(textureStateShard).createCompositeState(false)
			);
		}
	);
	private static final RenderType LEASH = create(
		"leash", 1536, RenderPipelines.LEASH, RenderType.CompositeState.builder().setTextureState(NO_TEXTURE).setLightmapState(LIGHTMAP).createCompositeState(false)
	);
	private static final RenderType WATER_MASK = create(
		"water_mask", 1536, RenderPipelines.WATER_MASK, RenderType.CompositeState.builder().setTextureState(NO_TEXTURE).createCompositeState(false)
	);
	private static final RenderType ARMOR_ENTITY_GLINT = create(
		"armor_entity_glint",
		1536,
		RenderPipelines.GLINT,
		RenderType.CompositeState.builder()
			.setTextureState(new RenderStateShard.TextureStateShard(ItemRenderer.ENCHANTED_GLINT_ARMOR, false))
			.setTexturingState(ARMOR_ENTITY_GLINT_TEXTURING)
			.setLayeringState(VIEW_OFFSET_Z_LAYERING)
			.createCompositeState(false)
	);
	private static final RenderType GLINT_TRANSLUCENT = create(
		"glint_translucent",
		1536,
		RenderPipelines.GLINT,
		RenderType.CompositeState.builder()
			.setTextureState(new RenderStateShard.TextureStateShard(ItemRenderer.ENCHANTED_GLINT_ITEM, false))
			.setTexturingState(GLINT_TEXTURING)
			.setOutputState(ITEM_ENTITY_TARGET)
			.createCompositeState(false)
	);
	private static final RenderType GLINT = create(
		"glint",
		1536,
		RenderPipelines.GLINT,
		RenderType.CompositeState.builder()
			.setTextureState(new RenderStateShard.TextureStateShard(ItemRenderer.ENCHANTED_GLINT_ITEM, false))
			.setTexturingState(GLINT_TEXTURING)
			.createCompositeState(false)
	);
	private static final RenderType ENTITY_GLINT = create(
		"entity_glint",
		1536,
		RenderPipelines.GLINT,
		RenderType.CompositeState.builder()
			.setTextureState(new RenderStateShard.TextureStateShard(ItemRenderer.ENCHANTED_GLINT_ITEM, false))
			.setTexturingState(ENTITY_GLINT_TEXTURING)
			.createCompositeState(false)
	);
	private static final Function<ResourceLocation, RenderType> CRUMBLING = Util.memoize(
		resourceLocation -> {
			RenderStateShard.TextureStateShard textureStateShard = new RenderStateShard.TextureStateShard(resourceLocation, false);
			return create(
				"crumbling",
				1536,
				false,
				true,
				RenderPipelines.CRUMBLING,
				RenderType.CompositeState.builder().setTextureState(textureStateShard).createCompositeState(false)
			);
		}
	);
	private static final Function<ResourceLocation, RenderType> TEXT = Util.memoize(
		resourceLocation -> create(
			"text",
			786432,
			false,
			false,
			RenderPipelines.TEXT,
			RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.createCompositeState(false)
		)
	);
	private static final RenderType TEXT_BACKGROUND = create(
		"text_background",
		1536,
		false,
		true,
		RenderPipelines.TEXT_BACKGROUND,
		RenderType.CompositeState.builder().setTextureState(NO_TEXTURE).setLightmapState(LIGHTMAP).createCompositeState(false)
	);
	private static final Function<ResourceLocation, RenderType> TEXT_INTENSITY = Util.memoize(
		resourceLocation -> create(
			"text_intensity",
			786432,
			false,
			false,
			RenderPipelines.TEXT_INTENSITY,
			RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.createCompositeState(false)
		)
	);
	private static final Function<ResourceLocation, RenderType> TEXT_POLYGON_OFFSET = Util.memoize(
		resourceLocation -> create(
			"text_polygon_offset",
			1536,
			false,
			true,
			RenderPipelines.TEXT_POLYGON_OFFSET,
			RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.createCompositeState(false)
		)
	);
	private static final Function<ResourceLocation, RenderType> TEXT_INTENSITY_POLYGON_OFFSET = Util.memoize(
		resourceLocation -> create(
			"text_intensity_polygon_offset",
			1536,
			false,
			true,
			RenderPipelines.TEXT_INTENSITY,
			RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.createCompositeState(false)
		)
	);
	private static final Function<ResourceLocation, RenderType> TEXT_SEE_THROUGH = Util.memoize(
		resourceLocation -> create(
			"text_see_through",
			1536,
			false,
			false,
			RenderPipelines.TEXT_SEE_THROUGH,
			RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.createCompositeState(false)
		)
	);
	private static final RenderType TEXT_BACKGROUND_SEE_THROUGH = create(
		"text_background_see_through",
		1536,
		false,
		true,
		RenderPipelines.TEXT_BACKGROUND_SEE_THROUGH,
		RenderType.CompositeState.builder().setTextureState(NO_TEXTURE).setLightmapState(LIGHTMAP).createCompositeState(false)
	);
	private static final Function<ResourceLocation, RenderType> TEXT_INTENSITY_SEE_THROUGH = Util.memoize(
		resourceLocation -> create(
			"text_intensity_see_through",
			1536,
			false,
			true,
			RenderPipelines.TEXT_INTENSITY_SEE_THROUGH,
			RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setLightmapState(LIGHTMAP)
				.createCompositeState(false)
		)
	);
	private static final RenderType LIGHTNING = create(
		"lightning", 1536, false, true, RenderPipelines.LIGHTNING, RenderType.CompositeState.builder().setOutputState(WEATHER_TARGET).createCompositeState(false)
	);
	private static final RenderType DRAGON_RAYS = create(
		"dragon_rays", 1536, false, false, RenderPipelines.DRAGON_RAYS, RenderType.CompositeState.builder().createCompositeState(false)
	);
	private static final RenderType DRAGON_RAYS_DEPTH = create(
		"dragon_rays_depth", 1536, false, false, RenderPipelines.DRAGON_RAYS_DEPTH, RenderType.CompositeState.builder().createCompositeState(false)
	);
	private static final RenderType TRIPWIRE = create(
		"tripwire",
		1536,
		true,
		true,
		RenderPipelines.TRIPWIRE,
		RenderType.CompositeState.builder().setLightmapState(LIGHTMAP).setTextureState(BLOCK_SHEET_MIPPED).setOutputState(WEATHER_TARGET).createCompositeState(true)
	);
	private static final RenderType END_PORTAL = create(
		"end_portal",
		1536,
		false,
		false,
		RenderPipelines.END_PORTAL,
		RenderType.CompositeState.builder()
			.setTextureState(
				RenderStateShard.MultiTextureStateShard.builder()
					.add(AbstractEndPortalRenderer.END_SKY_LOCATION, false)
					.add(AbstractEndPortalRenderer.END_PORTAL_LOCATION, false)
					.build()
			)
			.createCompositeState(false)
	);
	private static final RenderType END_GATEWAY = create(
		"end_gateway",
		1536,
		false,
		false,
		RenderPipelines.END_GATEWAY,
		RenderType.CompositeState.builder()
			.setTextureState(
				RenderStateShard.MultiTextureStateShard.builder()
					.add(AbstractEndPortalRenderer.END_SKY_LOCATION, false)
					.add(AbstractEndPortalRenderer.END_PORTAL_LOCATION, false)
					.build()
			)
			.createCompositeState(false)
	);
	public static final RenderType.CompositeRenderType LINES = create(
		"lines",
		1536,
		RenderPipelines.LINES,
		RenderType.CompositeState.builder()
			.setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
			.setLayeringState(VIEW_OFFSET_Z_LAYERING)
			.setOutputState(ITEM_ENTITY_TARGET)
			.createCompositeState(false)
	);
	public static final RenderType.CompositeRenderType SECONDARY_BLOCK_OUTLINE = create(
		"secondary_block_outline",
		1536,
		RenderPipelines.SECONDARY_BLOCK_OUTLINE,
		RenderType.CompositeState.builder()
			.setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(7.0)))
			.setLayeringState(VIEW_OFFSET_Z_LAYERING)
			.setOutputState(ITEM_ENTITY_TARGET)
			.createCompositeState(false)
	);
	public static final RenderType.CompositeRenderType LINE_STRIP = create(
		"line_strip",
		1536,
		RenderPipelines.LINE_STRIP,
		RenderType.CompositeState.builder()
			.setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
			.setLayeringState(VIEW_OFFSET_Z_LAYERING)
			.setOutputState(ITEM_ENTITY_TARGET)
			.createCompositeState(false)
	);
	private static final Function<Double, RenderType.CompositeRenderType> DEBUG_LINE_STRIP = Util.memoize(
		double_ -> create(
			"debug_line_strip",
			1536,
			RenderPipelines.DEBUG_LINE_STRIP,
			RenderType.CompositeState.builder().setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(double_))).createCompositeState(false)
		)
	);
	private static final RenderType.CompositeRenderType DEBUG_FILLED_BOX = create(
		"debug_filled_box",
		1536,
		false,
		true,
		RenderPipelines.DEBUG_FILLED_BOX,
		RenderType.CompositeState.builder().setLayeringState(VIEW_OFFSET_Z_LAYERING).createCompositeState(false)
	);
	private static final RenderType.CompositeRenderType DEBUG_QUADS = create(
		"debug_quads", 1536, false, true, RenderPipelines.DEBUG_QUADS, RenderType.CompositeState.builder().createCompositeState(false)
	);
	private static final RenderType.CompositeRenderType DEBUG_TRIANGLE_FAN = create(
		"debug_triangle_fan", 1536, false, true, RenderPipelines.DEBUG_TRIANGLE_FAN, RenderType.CompositeState.builder().createCompositeState(false)
	);
	private static final RenderType.CompositeRenderType DEBUG_STRUCTURE_QUADS = create(
		"debug_structure_quads", 1536, false, true, RenderPipelines.DEBUG_STRUCTURE_QUADS, RenderType.CompositeState.builder().createCompositeState(false)
	);
	private static final RenderType.CompositeRenderType DEBUG_SECTION_QUADS = create(
		"debug_section_quads",
		1536,
		false,
		true,
		RenderPipelines.DEBUG_SECTION_QUADS,
		RenderType.CompositeState.builder().setLayeringState(VIEW_OFFSET_Z_LAYERING).createCompositeState(false)
	);
	private static final Function<ResourceLocation, RenderType> WEATHER_DEPTH_WRITE = createWeather(RenderPipelines.WEATHER_DEPTH_WRITE);
	private static final Function<ResourceLocation, RenderType> WEATHER_NO_DEPTH_WRITE = createWeather(RenderPipelines.WEATHER_NO_DEPTH_WRITE);
	private static final Function<ResourceLocation, RenderType> BLOCK_SCREEN_EFFECT = Util.memoize(
		resourceLocation -> create(
			"block_screen_effect",
			1536,
			false,
			false,
			RenderPipelines.BLOCK_SCREEN_EFFECT,
			RenderType.CompositeState.builder().setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false)).createCompositeState(false)
		)
	);
	private static final Function<ResourceLocation, RenderType> FIRE_SCREEN_EFFECT = Util.memoize(
		resourceLocation -> create(
			"fire_screen_effect",
			1536,
			false,
			false,
			RenderPipelines.FIRE_SCREEN_EFFECT,
			RenderType.CompositeState.builder().setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false)).createCompositeState(false)
		)
	);
	private final int bufferSize;
	private final boolean affectsCrumbling;
	private final boolean sortOnUpload;

	public static RenderType solid() {
		return SOLID;
	}

	public static RenderType cutoutMipped() {
		return CUTOUT_MIPPED;
	}

	public static RenderType cutout() {
		return CUTOUT;
	}

	public static RenderType translucentMovingBlock() {
		return TRANSLUCENT_MOVING_BLOCK;
	}

	public static RenderType armorCutoutNoCull(ResourceLocation resourceLocation) {
		return (RenderType)ARMOR_CUTOUT_NO_CULL.apply(resourceLocation);
	}

	public static RenderType createArmorDecalCutoutNoCull(ResourceLocation resourceLocation) {
		RenderType.CompositeState compositeState = RenderType.CompositeState.builder()
			.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
			.setLightmapState(LIGHTMAP)
			.setOverlayState(OVERLAY)
			.setLayeringState(VIEW_OFFSET_Z_LAYERING)
			.createCompositeState(true);
		return create("armor_decal_cutout_no_cull", 1536, true, false, RenderPipelines.ARMOR_DECAL_CUTOUT_NO_CULL, compositeState);
	}

	public static RenderType armorTranslucent(ResourceLocation resourceLocation) {
		return (RenderType)ARMOR_TRANSLUCENT.apply(resourceLocation);
	}

	public static RenderType entitySolid(ResourceLocation resourceLocation) {
		return (RenderType)ENTITY_SOLID.apply(resourceLocation);
	}

	public static RenderType entitySolidZOffsetForward(ResourceLocation resourceLocation) {
		return (RenderType)ENTITY_SOLID_Z_OFFSET_FORWARD.apply(resourceLocation);
	}

	public static RenderType entityCutout(ResourceLocation resourceLocation) {
		return (RenderType)ENTITY_CUTOUT.apply(resourceLocation);
	}

	public static RenderType entityCutoutNoCull(ResourceLocation resourceLocation, boolean bl) {
		return (RenderType)ENTITY_CUTOUT_NO_CULL.apply(resourceLocation, bl);
	}

	public static RenderType entityCutoutNoCull(ResourceLocation resourceLocation) {
		return entityCutoutNoCull(resourceLocation, true);
	}

	public static RenderType entityCutoutNoCullZOffset(ResourceLocation resourceLocation, boolean bl) {
		return (RenderType)ENTITY_CUTOUT_NO_CULL_Z_OFFSET.apply(resourceLocation, bl);
	}

	public static RenderType entityCutoutNoCullZOffset(ResourceLocation resourceLocation) {
		return entityCutoutNoCullZOffset(resourceLocation, true);
	}

	public static RenderType itemEntityTranslucentCull(ResourceLocation resourceLocation) {
		return (RenderType)ITEM_ENTITY_TRANSLUCENT_CULL.apply(resourceLocation);
	}

	public static RenderType entityTranslucent(ResourceLocation resourceLocation, boolean bl) {
		return (RenderType)ENTITY_TRANSLUCENT.apply(resourceLocation, bl);
	}

	public static RenderType entityTranslucent(ResourceLocation resourceLocation) {
		return entityTranslucent(resourceLocation, true);
	}

	public static RenderType entityTranslucentEmissive(ResourceLocation resourceLocation, boolean bl) {
		return (RenderType)ENTITY_TRANSLUCENT_EMISSIVE.apply(resourceLocation, bl);
	}

	public static RenderType entityTranslucentEmissive(ResourceLocation resourceLocation) {
		return entityTranslucentEmissive(resourceLocation, true);
	}

	public static RenderType entitySmoothCutout(ResourceLocation resourceLocation) {
		return (RenderType)ENTITY_SMOOTH_CUTOUT.apply(resourceLocation);
	}

	public static RenderType beaconBeam(ResourceLocation resourceLocation, boolean bl) {
		return (RenderType)BEACON_BEAM.apply(resourceLocation, bl);
	}

	public static RenderType entityDecal(ResourceLocation resourceLocation) {
		return (RenderType)ENTITY_DECAL.apply(resourceLocation);
	}

	public static RenderType entityNoOutline(ResourceLocation resourceLocation) {
		return (RenderType)ENTITY_NO_OUTLINE.apply(resourceLocation);
	}

	public static RenderType entityShadow(ResourceLocation resourceLocation) {
		return (RenderType)ENTITY_SHADOW.apply(resourceLocation);
	}

	public static RenderType dragonExplosionAlpha(ResourceLocation resourceLocation) {
		return (RenderType)DRAGON_EXPLOSION_ALPHA.apply(resourceLocation);
	}

	public static RenderType eyes(ResourceLocation resourceLocation) {
		return (RenderType)EYES.apply(resourceLocation);
	}

	public static RenderType breezeEyes(ResourceLocation resourceLocation) {
		return (RenderType)ENTITY_TRANSLUCENT_EMISSIVE.apply(resourceLocation, false);
	}

	public static RenderType breezeWind(ResourceLocation resourceLocation, float f, float g) {
		return create(
			"breeze_wind",
			1536,
			false,
			true,
			RenderPipelines.BREEZE_WIND,
			RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setTexturingState(new RenderStateShard.OffsetTexturingStateShard(f, g))
				.setLightmapState(LIGHTMAP)
				.setOverlayState(NO_OVERLAY)
				.createCompositeState(false)
		);
	}

	public static RenderType energySwirl(ResourceLocation resourceLocation, float f, float g) {
		return create(
			"energy_swirl",
			1536,
			false,
			true,
			RenderPipelines.ENERGY_SWIRL,
			RenderType.CompositeState.builder()
				.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
				.setTexturingState(new RenderStateShard.OffsetTexturingStateShard(f, g))
				.setLightmapState(LIGHTMAP)
				.setOverlayState(OVERLAY)
				.createCompositeState(false)
		);
	}

	public static RenderType leash() {
		return LEASH;
	}

	public static RenderType waterMask() {
		return WATER_MASK;
	}

	public static RenderType outline(ResourceLocation resourceLocation) {
		return (RenderType)RenderType.CompositeRenderType.OUTLINE.apply(resourceLocation, false);
	}

	public static RenderType armorEntityGlint() {
		return ARMOR_ENTITY_GLINT;
	}

	public static RenderType glintTranslucent() {
		return GLINT_TRANSLUCENT;
	}

	public static RenderType glint() {
		return GLINT;
	}

	public static RenderType entityGlint() {
		return ENTITY_GLINT;
	}

	public static RenderType crumbling(ResourceLocation resourceLocation) {
		return (RenderType)CRUMBLING.apply(resourceLocation);
	}

	public static RenderType text(ResourceLocation resourceLocation) {
		return (RenderType)TEXT.apply(resourceLocation);
	}

	public static RenderType textBackground() {
		return TEXT_BACKGROUND;
	}

	public static RenderType textIntensity(ResourceLocation resourceLocation) {
		return (RenderType)TEXT_INTENSITY.apply(resourceLocation);
	}

	public static RenderType textPolygonOffset(ResourceLocation resourceLocation) {
		return (RenderType)TEXT_POLYGON_OFFSET.apply(resourceLocation);
	}

	public static RenderType textIntensityPolygonOffset(ResourceLocation resourceLocation) {
		return (RenderType)TEXT_INTENSITY_POLYGON_OFFSET.apply(resourceLocation);
	}

	public static RenderType textSeeThrough(ResourceLocation resourceLocation) {
		return (RenderType)TEXT_SEE_THROUGH.apply(resourceLocation);
	}

	public static RenderType textBackgroundSeeThrough() {
		return TEXT_BACKGROUND_SEE_THROUGH;
	}

	public static RenderType textIntensitySeeThrough(ResourceLocation resourceLocation) {
		return (RenderType)TEXT_INTENSITY_SEE_THROUGH.apply(resourceLocation);
	}

	public static RenderType lightning() {
		return LIGHTNING;
	}

	public static RenderType dragonRays() {
		return DRAGON_RAYS;
	}

	public static RenderType dragonRaysDepth() {
		return DRAGON_RAYS_DEPTH;
	}

	public static RenderType tripwire() {
		return TRIPWIRE;
	}

	public static RenderType endPortal() {
		return END_PORTAL;
	}

	public static RenderType endGateway() {
		return END_GATEWAY;
	}

	public static RenderType lines() {
		return LINES;
	}

	public static RenderType secondaryBlockOutline() {
		return SECONDARY_BLOCK_OUTLINE;
	}

	public static RenderType lineStrip() {
		return LINE_STRIP;
	}

	public static RenderType debugLineStrip(double d) {
		return (RenderType)DEBUG_LINE_STRIP.apply(d);
	}

	public static RenderType debugFilledBox() {
		return DEBUG_FILLED_BOX;
	}

	public static RenderType debugQuads() {
		return DEBUG_QUADS;
	}

	public static RenderType debugTriangleFan() {
		return DEBUG_TRIANGLE_FAN;
	}

	public static RenderType debugStructureQuads() {
		return DEBUG_STRUCTURE_QUADS;
	}

	public static RenderType debugSectionQuads() {
		return DEBUG_SECTION_QUADS;
	}

	private static Function<ResourceLocation, RenderType> createWeather(RenderPipeline renderPipeline) {
		return Util.memoize(
			resourceLocation -> create(
				"weather",
				1536,
				false,
				false,
				renderPipeline,
				RenderType.CompositeState.builder()
					.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
					.setOutputState(WEATHER_TARGET)
					.setLightmapState(LIGHTMAP)
					.createCompositeState(false)
			)
		);
	}

	public static RenderType weather(ResourceLocation resourceLocation, boolean bl) {
		return (RenderType)(bl ? WEATHER_DEPTH_WRITE : WEATHER_NO_DEPTH_WRITE).apply(resourceLocation);
	}

	public static RenderType blockScreenEffect(ResourceLocation resourceLocation) {
		return (RenderType)BLOCK_SCREEN_EFFECT.apply(resourceLocation);
	}

	public static RenderType fireScreenEffect(ResourceLocation resourceLocation) {
		return (RenderType)FIRE_SCREEN_EFFECT.apply(resourceLocation);
	}

	public RenderType(String string, int i, boolean bl, boolean bl2, Runnable runnable, Runnable runnable2) {
		super(string, runnable, runnable2);
		this.bufferSize = i;
		this.affectsCrumbling = bl;
		this.sortOnUpload = bl2;
	}

	public static RenderType.CompositeRenderType create(String string, int i, RenderPipeline renderPipeline, RenderType.CompositeState compositeState) {
		return create(string, i, false, false, renderPipeline, compositeState);
	}

	public static RenderType.CompositeRenderType create(
		String string, int i, boolean bl, boolean bl2, RenderPipeline renderPipeline, RenderType.CompositeState compositeState
	) {
		return new RenderType.CompositeRenderType(string, i, bl, bl2, renderPipeline, compositeState);
	}

	public abstract void draw(MeshData meshData);

	public int bufferSize() {
		return this.bufferSize;
	}

	public abstract VertexFormat format();

	public abstract VertexFormat.Mode mode();

	public Optional<RenderType> outline() {
		return Optional.empty();
	}

	public boolean isOutline() {
		return false;
	}

	public abstract RenderPipeline pipeline();

	public boolean affectsCrumbling() {
		return this.affectsCrumbling;
	}

	public boolean canConsolidateConsecutiveGeometry() {
		return !this.mode().connectedPrimitives;
	}

	public boolean sortOnUpload() {
		return this.sortOnUpload;
	}

	@Environment(EnvType.CLIENT)
	public static final class CompositeRenderType extends RenderType {
		static final BiFunction<ResourceLocation, Boolean, RenderType> OUTLINE = Util.memoize(
			(resourceLocation, boolean_) -> RenderType.create(
				"outline",
				1536,
				boolean_ ? RenderPipelines.OUTLINE_CULL : RenderPipelines.OUTLINE_NO_CULL,
				RenderType.CompositeState.builder()
					.setTextureState(new RenderStateShard.TextureStateShard(resourceLocation, false))
					.setOutputState(OUTLINE_TARGET)
					.createCompositeState(RenderType.OutlineProperty.IS_OUTLINE)
			)
		);
		private final RenderType.CompositeState state;
		private final RenderPipeline renderPipeline;
		private final Optional<RenderType> outline;
		private final boolean isOutline;

		CompositeRenderType(String string, int i, boolean bl, boolean bl2, RenderPipeline renderPipeline, RenderType.CompositeState compositeState) {
			super(
				string,
				i,
				bl,
				bl2,
				() -> compositeState.states.forEach(RenderStateShard::setupRenderState),
				() -> compositeState.states.forEach(RenderStateShard::clearRenderState)
			);
			this.state = compositeState;
			this.renderPipeline = renderPipeline;
			this.outline = compositeState.outlineProperty == RenderType.OutlineProperty.AFFECTS_OUTLINE
				? compositeState.textureState.cutoutTexture().map(resourceLocation -> (RenderType)OUTLINE.apply(resourceLocation, renderPipeline.isCull()))
				: Optional.empty();
			this.isOutline = compositeState.outlineProperty == RenderType.OutlineProperty.IS_OUTLINE;
		}

		@Override
		public Optional<RenderType> outline() {
			return this.outline;
		}

		@Override
		public boolean isOutline() {
			return this.isOutline;
		}

		@Override
		public VertexFormat format() {
			return this.renderPipeline.getVertexFormat();
		}

		@Override
		public VertexFormat.Mode mode() {
			return this.renderPipeline.getVertexFormatMode();
		}

		@Override
		public RenderPipeline pipeline() {
			return this.renderPipeline;
		}

		@Override
		public void draw(MeshData meshData) {
			this.setupRenderState();
			GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
				.writeTransform(
					RenderSystem.getModelViewMatrix(),
					new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
					new Vector3f(),
					RenderSystem.getTextureMatrix(),
					RenderSystem.getShaderLineWidth()
				);
			MeshData var3 = meshData;

			try {
				GpuBuffer gpuBuffer = this.renderPipeline.getVertexFormat().uploadImmediateVertexBuffer(meshData.vertexBuffer());
				GpuBuffer gpuBuffer2;
				VertexFormat.IndexType indexType;
				if (meshData.indexBuffer() == null) {
					RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(meshData.drawState().mode());
					gpuBuffer2 = autoStorageIndexBuffer.getBuffer(meshData.drawState().indexCount());
					indexType = autoStorageIndexBuffer.type();
				} else {
					gpuBuffer2 = this.renderPipeline.getVertexFormat().uploadImmediateIndexBuffer(meshData.indexBuffer());
					indexType = meshData.drawState().indexType();
				}

				RenderTarget renderTarget = this.state.outputState.getRenderTarget();
				GpuTextureView gpuTextureView = RenderSystem.outputColorTextureOverride != null
					? RenderSystem.outputColorTextureOverride
					: renderTarget.getColorTextureView();
				GpuTextureView gpuTextureView2 = renderTarget.useDepth
					? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : renderTarget.getDepthTextureView())
					: null;

				try (RenderPass renderPass = RenderSystem.getDevice()
						.createCommandEncoder()
						.createRenderPass(() -> "Immediate draw for " + this.getName(), gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
					renderPass.setPipeline(this.renderPipeline);
					ScissorState scissorState = RenderSystem.getScissorStateForRenderTypeDraws();
					if (scissorState.enabled()) {
						renderPass.enableScissor(scissorState.x(), scissorState.y(), scissorState.width(), scissorState.height());
					}

					RenderSystem.bindDefaultUniforms(renderPass);
					renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
					renderPass.setVertexBuffer(0, gpuBuffer);

					for (int i = 0; i < 12; i++) {
						GpuTextureView gpuTextureView3 = RenderSystem.getShaderTexture(i);
						if (gpuTextureView3 != null) {
							renderPass.bindSampler("Sampler" + i, gpuTextureView3);
						}
					}

					renderPass.setIndexBuffer(gpuBuffer2, indexType);
					renderPass.drawIndexed(0, 0, meshData.drawState().indexCount(), 1);
				}
			} catch (Throwable var17) {
				if (meshData != null) {
					try {
						var3.close();
					} catch (Throwable var14) {
						var17.addSuppressed(var14);
					}
				}

				throw var17;
			}

			if (meshData != null) {
				meshData.close();
			}

			this.clearRenderState();
		}

		@Override
		public String toString() {
			return "RenderType[" + this.name + ":" + this.state + "]";
		}
	}

	@Environment(EnvType.CLIENT)
	public static final class CompositeState {
		final RenderStateShard.EmptyTextureStateShard textureState;
		public final RenderStateShard.OutputStateShard outputState;
		final RenderType.OutlineProperty outlineProperty;
		final ImmutableList<RenderStateShard> states;

		CompositeState(
			RenderStateShard.EmptyTextureStateShard emptyTextureStateShard,
			RenderStateShard.LightmapStateShard lightmapStateShard,
			RenderStateShard.OverlayStateShard overlayStateShard,
			RenderStateShard.LayeringStateShard layeringStateShard,
			RenderStateShard.OutputStateShard outputStateShard,
			RenderStateShard.TexturingStateShard texturingStateShard,
			RenderStateShard.LineStateShard lineStateShard,
			RenderType.OutlineProperty outlineProperty
		) {
			this.textureState = emptyTextureStateShard;
			this.outputState = outputStateShard;
			this.outlineProperty = outlineProperty;
			this.states = ImmutableList.of(
				emptyTextureStateShard, lightmapStateShard, overlayStateShard, layeringStateShard, outputStateShard, texturingStateShard, lineStateShard
			);
		}

		public String toString() {
			return "CompositeState[" + this.states + ", outlineProperty=" + this.outlineProperty + "]";
		}

		public static RenderType.CompositeState.CompositeStateBuilder builder() {
			return new RenderType.CompositeState.CompositeStateBuilder();
		}

		@Environment(EnvType.CLIENT)
		public static class CompositeStateBuilder {
			private RenderStateShard.EmptyTextureStateShard textureState = RenderStateShard.NO_TEXTURE;
			private RenderStateShard.LightmapStateShard lightmapState = RenderStateShard.NO_LIGHTMAP;
			private RenderStateShard.OverlayStateShard overlayState = RenderStateShard.NO_OVERLAY;
			private RenderStateShard.LayeringStateShard layeringState = RenderStateShard.NO_LAYERING;
			private RenderStateShard.OutputStateShard outputState = RenderStateShard.MAIN_TARGET;
			private RenderStateShard.TexturingStateShard texturingState = RenderStateShard.DEFAULT_TEXTURING;
			private RenderStateShard.LineStateShard lineState = RenderStateShard.DEFAULT_LINE;

			CompositeStateBuilder() {
			}

			public RenderType.CompositeState.CompositeStateBuilder setTextureState(RenderStateShard.EmptyTextureStateShard emptyTextureStateShard) {
				this.textureState = emptyTextureStateShard;
				return this;
			}

			public RenderType.CompositeState.CompositeStateBuilder setLightmapState(RenderStateShard.LightmapStateShard lightmapStateShard) {
				this.lightmapState = lightmapStateShard;
				return this;
			}

			public RenderType.CompositeState.CompositeStateBuilder setOverlayState(RenderStateShard.OverlayStateShard overlayStateShard) {
				this.overlayState = overlayStateShard;
				return this;
			}

			public RenderType.CompositeState.CompositeStateBuilder setLayeringState(RenderStateShard.LayeringStateShard layeringStateShard) {
				this.layeringState = layeringStateShard;
				return this;
			}

			public RenderType.CompositeState.CompositeStateBuilder setOutputState(RenderStateShard.OutputStateShard outputStateShard) {
				this.outputState = outputStateShard;
				return this;
			}

			public RenderType.CompositeState.CompositeStateBuilder setTexturingState(RenderStateShard.TexturingStateShard texturingStateShard) {
				this.texturingState = texturingStateShard;
				return this;
			}

			public RenderType.CompositeState.CompositeStateBuilder setLineState(RenderStateShard.LineStateShard lineStateShard) {
				this.lineState = lineStateShard;
				return this;
			}

			public RenderType.CompositeState createCompositeState(boolean bl) {
				return this.createCompositeState(bl ? RenderType.OutlineProperty.AFFECTS_OUTLINE : RenderType.OutlineProperty.NONE);
			}

			public RenderType.CompositeState createCompositeState(RenderType.OutlineProperty outlineProperty) {
				return new RenderType.CompositeState(
					this.textureState, this.lightmapState, this.overlayState, this.layeringState, this.outputState, this.texturingState, this.lineState, outlineProperty
				);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public static enum OutlineProperty {
		NONE("none"),
		IS_OUTLINE("is_outline"),
		AFFECTS_OUTLINE("affects_outline");

		private final String name;

		private OutlineProperty(final String string2) {
			this.name = string2;
		}

		public String toString() {
			return this.name;
		}
	}
	
	// Iris compatibility methods
	public RenderPipeline iris$getPipeline() {
		return null;
	}
	
	public RenderTarget iris$getRenderTarget() {
		return null;
	}
}

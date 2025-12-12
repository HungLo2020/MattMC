// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.pipeline;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.shaders.programs.ShaderKey;
import net.minecraft.client.renderer.shaders.shadows.ShadowRenderingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Function;

/**
 * Maps vanilla RenderPipelines to ShaderKeys.
 * 
 * Based on IRIS's IrisPipelines class
 * Reference: frnsrc/Iris-1.21.9/.../pipeline/IrisPipelines.java
 */
public class IrisPipelines {
	private static final Logger LOGGER = LoggerFactory.getLogger(IrisPipelines.class);
	
	private static final Map<RenderPipeline, Function<ShaderPackPipeline, ShaderKey>> coreShaderMap = new Object2ObjectOpenHashMap<>();
	private static final Map<RenderPipeline, Function<ShaderPackPipeline, ShaderKey>> coreShaderMapShadow = new Object2ObjectOpenHashMap<>();
	
	static {
		// Main rendering pipelines
		assignToMain(RenderPipelines.SOLID, p -> ShaderKey.TERRAIN_SOLID);
		assignToMain(RenderPipelines.CUTOUT, p -> ShaderKey.TERRAIN_CUTOUT);
		assignToMain(RenderPipelines.CUTOUT_MIPPED, p -> ShaderKey.TERRAIN_CUTOUT);
		assignToMain(RenderPipelines.TRANSLUCENT, p -> ShaderKey.TERRAIN_TRANSLUCENT);
		assignToMain(RenderPipelines.TRANSLUCENT_MOVING_BLOCK, p -> ShaderKey.MOVING_BLOCK);
		assignToMain(RenderPipelines.TRIPWIRE, p -> ShaderKey.TERRAIN_TRANSLUCENT);
		assignToMain(RenderPipelines.ENTITY_CUTOUT, p -> getCutout(p));
		assignToMain(RenderPipelines.ENTITY_CUTOUT_NO_CULL, p -> getCutout(p));
		assignToMain(RenderPipelines.ENTITY_CUTOUT_NO_CULL_Z_OFFSET, p -> getCutout(p));
		assignToMain(RenderPipelines.ENTITY_SMOOTH_CUTOUT, p -> getCutout(p));
		assignToMain(RenderPipelines.ITEM_ENTITY_TRANSLUCENT_CULL, p -> getTranslucent(p));
		assignToMain(RenderPipelines.ENTITY_TRANSLUCENT, p -> getTranslucent(p));
		assignToMain(RenderPipelines.ENTITY_SHADOW, p -> getTranslucent(p));
		assignToMain(RenderPipelines.ENTITY_NO_OUTLINE, p -> getTranslucent(p));
		assignToMain(RenderPipelines.ENTITY_DECAL, p -> getCutout(p));
		assignToMain(RenderPipelines.LINES, p -> ShaderKey.LINES);
		assignToMain(RenderPipelines.LINE_STRIP, p -> ShaderKey.LINES);
		assignToMain(RenderPipelines.SECONDARY_BLOCK_OUTLINE, p -> ShaderKey.LINES);
		assignToMain(RenderPipelines.STARS, p -> ShaderKey.SKY_BASIC);
		assignToMain(RenderPipelines.SUNRISE_SUNSET, p -> ShaderKey.SKY_BASIC_COLOR);
		assignToMain(RenderPipelines.SKY, p -> ShaderKey.SKY_BASIC);
		assignToMain(RenderPipelines.CELESTIAL, p -> ShaderKey.SKY_TEXTURED);
		assignToMain(RenderPipelines.OPAQUE_PARTICLE, p -> ShaderKey.PARTICLES);
		assignToMain(RenderPipelines.TRANSLUCENT_PARTICLE, p -> ShaderKey.PARTICLES_TRANS);
		assignToMain(RenderPipelines.WATER_MASK, p -> ShaderKey.BASIC);
		assignToMain(RenderPipelines.GLINT, p -> ShaderKey.GLINT);
		assignToMain(RenderPipelines.ARMOR_CUTOUT_NO_CULL, p -> getCutout(p));
		assignToMain(RenderPipelines.EYES, p -> ShaderKey.ENTITIES_EYES);
		assignToMain(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE, p -> ShaderKey.ENTITIES_EYES_TRANS);
		assignToMain(RenderPipelines.ARMOR_DECAL_CUTOUT_NO_CULL, p -> getCutout(p));
		assignToMain(RenderPipelines.ARMOR_TRANSLUCENT, p -> getTranslucent(p));
		assignToMain(RenderPipelines.BREEZE_WIND, p -> getTranslucent(p));
		assignToMain(RenderPipelines.ENTITY_SOLID, p -> getSolid(p));
		assignToMain(RenderPipelines.ENTITY_SOLID_Z_OFFSET_FORWARD, p -> getSolid(p));
		assignToMain(RenderPipelines.END_GATEWAY, p -> ShaderKey.BLOCK_ENTITY);
		assignToMain(RenderPipelines.ENERGY_SWIRL, p -> ShaderKey.ENTITIES_CUTOUT);
		assignToMain(RenderPipelines.LIGHTNING, p -> ShaderKey.LIGHTNING);
		assignToMain(RenderPipelines.DRAGON_RAYS, p -> ShaderKey.LIGHTNING);
		assignToMain(RenderPipelines.DRAGON_RAYS_DEPTH, p -> ShaderKey.LIGHTNING);
		assignToMain(RenderPipelines.BEACON_BEAM_OPAQUE, p -> ShaderKey.BEACON);
		assignToMain(RenderPipelines.BEACON_BEAM_TRANSLUCENT, p -> ShaderKey.BEACON);
		assignToMain(RenderPipelines.END_PORTAL, p -> ShaderKey.BLOCK_ENTITY);
		assignToMain(RenderPipelines.END_SKY, p -> ShaderKey.SKY_TEXTURED);
		assignToMain(RenderPipelines.WEATHER_DEPTH_WRITE, p -> ShaderKey.WEATHER);
		assignToMain(RenderPipelines.WEATHER_NO_DEPTH_WRITE, p -> ShaderKey.WEATHER);
		assignToMain(RenderPipelines.TEXT, p -> getText(p));
		assignToMain(RenderPipelines.TEXT_POLYGON_OFFSET, p -> getText(p));
		assignToMain(RenderPipelines.TEXT_SEE_THROUGH, p -> getText(p));
		assignToMain(RenderPipelines.TEXT_INTENSITY_SEE_THROUGH, p -> getTextIntensity(p));
		assignToMain(RenderPipelines.TEXT_BACKGROUND, p -> ShaderKey.TEXT_BG);
		assignToMain(RenderPipelines.TEXT_BACKGROUND_SEE_THROUGH, p -> ShaderKey.TEXT_BG);
		assignToMain(RenderPipelines.TEXT_INTENSITY, p -> getTextIntensity(p));
		assignToMain(RenderPipelines.DRAGON_EXPLOSION_ALPHA, p -> ShaderKey.ENTITIES_ALPHA);
		assignToMain(RenderPipelines.CRUMBLING, p -> ShaderKey.CRUMBLING);
		assignToMain(RenderPipelines.LEASH, p -> ShaderKey.LEASH);
		assignToMain(RenderPipelines.CLOUDS, p -> ShaderKey.CLOUDS);
		assignToMain(RenderPipelines.FLAT_CLOUDS, p -> ShaderKey.CLOUDS);
		assignToMain(RenderPipelines.DEBUG_LINE_STRIP, p -> ShaderKey.BASIC_COLOR);

		// Shadow rendering pipelines
		assignToShadow(RenderPipelines.SOLID, p -> ShaderKey.SHADOW_TERRAIN_CUTOUT);
		assignToShadow(RenderPipelines.CUTOUT, p -> ShaderKey.SHADOW_TERRAIN_CUTOUT);
		assignToShadow(RenderPipelines.CUTOUT_MIPPED, p -> ShaderKey.SHADOW_TERRAIN_CUTOUT);
		assignToShadow(RenderPipelines.TRANSLUCENT, p -> ShaderKey.SHADOW_TRANSLUCENT);
		assignToShadow(RenderPipelines.TRANSLUCENT_MOVING_BLOCK, p -> ShaderKey.SHADOW_TRANSLUCENT);
		assignToShadow(RenderPipelines.TRIPWIRE, p -> ShaderKey.SHADOW_TRANSLUCENT);
		assignToShadow(RenderPipelines.ENTITY_CUTOUT, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.ARMOR_CUTOUT_NO_CULL, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.ITEM_ENTITY_TRANSLUCENT_CULL, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.ARMOR_DECAL_CUTOUT_NO_CULL, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.ENTITY_SOLID, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.CRUMBLING, p -> ShaderKey.SHADOW_TEX);
		assignToShadow(RenderPipelines.ENTITY_SOLID_Z_OFFSET_FORWARD, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.ENTITY_CUTOUT_NO_CULL, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.ENTITY_CUTOUT_NO_CULL_Z_OFFSET, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.ENTITY_SMOOTH_CUTOUT, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.ENTITY_TRANSLUCENT, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.BREEZE_WIND, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.EYES, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.DRAGON_EXPLOSION_ALPHA, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.ENTITY_NO_OUTLINE, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.ENERGY_SWIRL, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.ENTITY_DECAL, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.GLINT, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.WEATHER_DEPTH_WRITE, p -> ShaderKey.SHADOW_PARTICLES);
		assignToShadow(RenderPipelines.WEATHER_NO_DEPTH_WRITE, p -> ShaderKey.SHADOW_PARTICLES);
		assignToShadow(RenderPipelines.OPAQUE_PARTICLE, p -> ShaderKey.SHADOW_PARTICLES);
		assignToShadow(RenderPipelines.TRANSLUCENT_PARTICLE, p -> ShaderKey.SHADOW_PARTICLES);
		assignToShadow(RenderPipelines.LINES, p -> ShaderKey.SHADOW_LINES);
		assignToShadow(RenderPipelines.LINE_STRIP, p -> ShaderKey.SHADOW_LINES);
		assignToShadow(RenderPipelines.LEASH, p -> ShaderKey.SHADOW_LEASH);
		assignToShadow(RenderPipelines.SECONDARY_BLOCK_OUTLINE, p -> ShaderKey.SHADOW_LINES);
		assignToShadow(RenderPipelines.TEXT, p -> ShaderKey.SHADOW_TEXT);
		assignToShadow(RenderPipelines.TEXT_POLYGON_OFFSET, p -> ShaderKey.SHADOW_TEXT);
		assignToShadow(RenderPipelines.TEXT_SEE_THROUGH, p -> ShaderKey.SHADOW_TEXT);
		assignToShadow(RenderPipelines.TEXT_INTENSITY_SEE_THROUGH, p -> ShaderKey.SHADOW_TEXT_INTENSITY);
		assignToShadow(RenderPipelines.TEXT_BACKGROUND, p -> ShaderKey.SHADOW_TEXT_BG);
		assignToShadow(RenderPipelines.TEXT_BACKGROUND_SEE_THROUGH, p -> ShaderKey.SHADOW_TEXT_BG);
		assignToShadow(RenderPipelines.TEXT_INTENSITY, p -> ShaderKey.SHADOW_TEXT_INTENSITY);
		assignToShadow(RenderPipelines.WATER_MASK, p -> ShaderKey.SHADOW_BASIC);
		assignToShadow(RenderPipelines.BEACON_BEAM_OPAQUE, p -> ShaderKey.SHADOW_BEACON_BEAM);
		assignToShadow(RenderPipelines.BEACON_BEAM_TRANSLUCENT, p -> ShaderKey.SHADOW_BEACON_BEAM);
		assignToShadow(RenderPipelines.END_PORTAL, p -> ShaderKey.SHADOW_BLOCK);
		assignToShadow(RenderPipelines.END_GATEWAY, p -> ShaderKey.SHADOW_BLOCK);
		assignToShadow(RenderPipelines.ARMOR_TRANSLUCENT, p -> ShaderKey.SHADOW_ENTITIES_CUTOUT);
		assignToShadow(RenderPipelines.LIGHTNING, p -> ShaderKey.SHADOW_LIGHTNING);
	}
	
	private static void assignToMain(RenderPipeline pipeline, Function<ShaderPackPipeline, ShaderKey> mapping) {
		if (coreShaderMap.containsKey(pipeline)) {
			LOGGER.warn("Duplicate main pipeline mapping for: {}", pipeline.getLocation());
		}
		coreShaderMap.put(pipeline, mapping);
	}
	
	private static void assignToShadow(RenderPipeline pipeline, Function<ShaderPackPipeline, ShaderKey> mapping) {
		if (coreShaderMapShadow.containsKey(pipeline)) {
			LOGGER.warn("Duplicate shadow pipeline mapping for: {}", pipeline.getLocation());
		}
		coreShaderMapShadow.put(pipeline, mapping);
	}
	
	private static ShaderKey getText(ShaderPackPipeline pipeline) {
		if (isBlockEntities(pipeline)) {
			return ShaderKey.TEXT_BE;
		} else {
			return ShaderKey.TEXT;
		}
	}
	
	private static ShaderKey getTextIntensity(ShaderPackPipeline pipeline) {
		if (isBlockEntities(pipeline)) {
			return ShaderKey.TEXT_INTENSITY_BE;
		} else {
			return ShaderKey.TEXT_INTENSITY;
		}
	}
	
	private static ShaderKey getCutout(ShaderPackPipeline pipeline) {
		// TODO: Implement hand rendering check
		if (isBlockEntities(pipeline)) {
			return ShaderKey.BLOCK_ENTITY_DIFFUSE;
		} else {
			return ShaderKey.ENTITIES_CUTOUT_DIFFUSE;
		}
	}
	
	private static ShaderKey getSolid(ShaderPackPipeline pipeline) {
		// TODO: Implement hand rendering check
		if (isBlockEntities(pipeline)) {
			return ShaderKey.BLOCK_ENTITY;
		} else {
			return ShaderKey.ENTITIES_CUTOUT;
		}
	}
	
	private static ShaderKey getTranslucent(ShaderPackPipeline pipeline) {
		// TODO: Implement hand rendering check
		if (isBlockEntities(pipeline)) {
			return ShaderKey.BE_TRANSLUCENT;
		} else {
			return ShaderKey.ENTITIES_TRANSLUCENT;
		}
	}
	
	private static boolean isBlockEntities(ShaderPackPipeline pipeline) {
		// TODO: Check WorldRenderingPhase for block entity rendering
		return false;
	}
	
	/**
	 * Gets the ShaderKey for a given RenderPipeline.
	 * 
	 * @param pipeline The shader pack pipeline (for context like hand rendering)
	 * @param renderPipeline The vanilla render pipeline to map
	 * @return The ShaderKey to use, or null if not mapped
	 */
	public static ShaderKey getPipeline(ShaderPackPipeline pipeline, RenderPipeline renderPipeline) {
		// Check if we're in shadow rendering mode
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			Function<ShaderPackPipeline, ShaderKey> mapping = coreShaderMapShadow.get(renderPipeline);
			if (mapping != null) {
				return mapping.apply(pipeline);
			}
		}
		
		// Use main rendering map
		Function<ShaderPackPipeline, ShaderKey> mapping = coreShaderMap.get(renderPipeline);
		if (mapping != null) {
			return mapping.apply(pipeline);
		}
		
		return null;
	}
	
	/**
	 * Checks if the given pipeline has a mapping.
	 */
	public static boolean hasPipeline(RenderPipeline renderPipeline) {
		return coreShaderMap.containsKey(renderPipeline) || coreShaderMapShadow.containsKey(renderPipeline);
	}
}

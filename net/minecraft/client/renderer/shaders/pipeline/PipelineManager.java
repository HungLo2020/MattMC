package net.minecraft.client.renderer.shaders.pipeline;

import net.minecraft.client.renderer.shaders.core.ShaderSystem;
import net.minecraft.client.renderer.shaders.pack.ShaderPackSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages world rendering pipelines per dimension.
 * Creates, caches, and destroys pipelines as needed.
 * 
 * Based on IRIS's PipelineManager (verbatim pattern match).
 * Reference: frnsrc/Iris-1.21.9/.../pipeline/PipelineManager.java
 * 
 * This is the central manager for shader pipeline lifecycle.
 */
public class PipelineManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(PipelineManager.class);
	
	// Per-dimension pipeline caching - matches IRIS pattern
	private final Map<String, WorldRenderingPipeline> pipelinesPerDimension = new HashMap<>();
	private WorldRenderingPipeline pipeline = new VanillaRenderingPipeline();
	
	/**
	 * Prepares a pipeline for the given dimension.
	 * Creates a new pipeline if one doesn't exist, or returns the cached one.
	 * 
	 * Matches IRIS's preparePipeline(NamespacedId) pattern exactly.
	 * 
	 * @param currentDimension The dimension ID (e.g., "minecraft:overworld")
	 * @return The prepared pipeline
	 */
	public WorldRenderingPipeline preparePipeline(String currentDimension) {
		// Check if we already have a pipeline for this dimension - matches IRIS pattern
		if (!pipelinesPerDimension.containsKey(currentDimension)) {
			LOGGER.info("Creating pipeline for dimension {}", currentDimension);
			
			// Create new pipeline based on configuration
			pipeline = createPipeline(currentDimension);
			pipelinesPerDimension.put(currentDimension, pipeline);
		} else {
			// Use cached pipeline - matches IRIS pattern
			pipeline = pipelinesPerDimension.get(currentDimension);
		}
		
		return pipeline;
	}
	
	/**
	 * Creates a new pipeline for the given dimension.
	 * Determines whether to create a vanilla or shader pack pipeline.
	 * 
	 * @param dimension The dimension ID
	 * @return A new WorldRenderingPipeline
	 */
	private WorldRenderingPipeline createPipeline(String dimension) {
		// Check if shaders are enabled - matches IRIS pattern
		if (ShaderSystem.getInstance().getConfig().areShadersEnabled()) {
			String packName = ShaderSystem.getInstance().getConfig().getSelectedPack();
			
			if (packName != null && ShaderSystem.getInstance().getRepository() != null) {
				ShaderPackSource packSource = ShaderSystem.getInstance().getRepository().getPackSource(packName);
				
				if (packSource != null) {
					LOGGER.info("Creating shader pack pipeline for pack: {} in dimension: {}", packName, dimension);
					return new ShaderPackPipeline(packName, dimension, packSource);
				} else {
					LOGGER.warn("Shader pack '{}' not found, falling back to vanilla", packName);
				}
			}
		}
		
		// Fall back to vanilla pipeline - matches IRIS pattern
		LOGGER.info("Creating vanilla pipeline for dimension: {}", dimension);
		return new VanillaRenderingPipeline();
	}
	
	/**
	 * Gets the current pipeline, or null if none is prepared.
	 * Matches IRIS's getPipelineNullable() method.
	 * 
	 * @return The current pipeline, or null
	 */
	public WorldRenderingPipeline getPipelineNullable() {
		return pipeline;
	}
	
	/**
	 * Destroys all pipelines.
	 * 
	 * WARNING: This is EXTREMELY DANGEROUS per IRIS documentation!
	 * Must immediately re-prepare pipelines after destroying to avoid inconsistent state.
	 * 
	 * Matches IRIS's destroyPipeline() method exactly.
	 */
	public void destroyPipeline() {
		// Destroy all cached pipelines - matches IRIS pattern
		pipelinesPerDimension.forEach((dimensionId, pipeline) -> {
			LOGGER.info("Destroying pipeline {}", dimensionId);
			pipeline.destroy();
		});
		
		pipelinesPerDimension.clear();
		pipeline = null;
	}
	
	/**
	 * Reloads all pipelines.
	 * Destroys existing pipelines and prepares a new vanilla pipeline.
	 * 
	 * This is safe because it immediately creates a new pipeline.
	 */
	public void reloadPipelines() {
		LOGGER.info("Reloading all pipelines");
		destroyPipeline();
		
		// Create a new vanilla pipeline to avoid null state
		pipeline = new VanillaRenderingPipeline();
	}
}

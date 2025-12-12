package net.minecraft.client.renderer.shaders.pipeline;

import net.minecraft.client.renderer.shaders.Iris;
import net.minecraft.client.renderer.shaders.core.ShaderSystem;
import net.minecraft.client.renderer.shaders.pack.FileSystemShaderPackSource;
import net.minecraft.client.renderer.shaders.pack.ShaderPackSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
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
	private static PipelineManager INSTANCE;
	
	// Per-dimension pipeline caching - matches IRIS pattern
	private final Map<String, WorldRenderingPipeline> pipelinesPerDimension = new HashMap<>();
	private WorldRenderingPipeline pipeline = new VanillaRenderingPipeline();
	// Track the current file system source for cleanup
	private FileSystemShaderPackSource currentPackSource = null;

	/**
	 * Gets the singleton instance of PipelineManager.
	 * @return The PipelineManager instance
	 */
	public static PipelineManager getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new PipelineManager();
		}
		return INSTANCE;
	}

	/**
	 * Gets the currently active pipeline.
	 * @return The active pipeline
	 */
	public WorldRenderingPipeline getActivePipeline() {
		return pipeline;
	}

	/**
	 * Checks if there's an active shader pack pipeline (not vanilla).
	 * @return true if a shader pack pipeline is active
	 */
	public boolean hasActiveShaderPipeline() {
		return pipeline instanceof ShaderPackPipeline;
	}
	
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
	 * Loads shader packs from the shaderpacks directory (following Iris pattern).
	 * 
	 * @param dimension The dimension ID
	 * @return A new WorldRenderingPipeline
	 */
	private WorldRenderingPipeline createPipeline(String dimension) {
		// Check if shaders are enabled - matches IRIS pattern
		// First check the Iris config (which is what the GUI sets)
		boolean shadersEnabled = Iris.getIrisConfig().areShadersEnabled();
		String packName = Iris.getIrisConfig().getShaderPackName().orElse(null);
		
		// Also check ShaderSystem config as a fallback
		if (!shadersEnabled && ShaderSystem.getInstance().isInitialized()) {
			var systemConfig = ShaderSystem.getInstance().getConfig();
			if (systemConfig != null) {
				shadersEnabled = systemConfig.areShadersEnabled();
				if (packName == null) {
					packName = systemConfig.getSelectedPack();
				}
			}
		}
		
		if (shadersEnabled && packName != null) {
			// Try to load from the shaderpacks directory (external packs)
			Path shaderpacksDir = Iris.getShaderpacksDirectory();
			Path packPath = shaderpacksDir.resolve(packName);
			
			if (Iris.isValidShaderpack(packPath)) {
				try {
					// Clean up any previous pack source
					if (currentPackSource != null) {
						currentPackSource.close();
					}
					
					currentPackSource = new FileSystemShaderPackSource(packName, packPath);
					LOGGER.info("Creating shader pack pipeline for external pack: {} in dimension: {}", packName, dimension);
					return new ShaderPackPipeline(packName, dimension, currentPackSource);
				} catch (IOException e) {
					LOGGER.error("Failed to load shader pack '{}' from file system", packName, e);
				}
			} else {
				LOGGER.warn("Shader pack '{}' is not valid at path: {}", packName, packPath);
			}
			
			// Try ShaderSystem repository as fallback (for resource-based packs)
			if (ShaderSystem.getInstance().getRepository() != null) {
				ShaderPackSource packSource = ShaderSystem.getInstance().getRepository().getPackSource(packName);
				
				if (packSource != null) {
					LOGGER.info("Creating shader pack pipeline for resource pack: {} in dimension: {}", packName, dimension);
					return new ShaderPackPipeline(packName, dimension, packSource);
				}
			}
			
			LOGGER.warn("Shader pack '{}' not found, falling back to vanilla", packName);
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
	 * Gets the current pipeline.
	 * Alias for getPipelineNullable() for compatibility.
	 * 
	 * @return The current pipeline, or null
	 */
	public WorldRenderingPipeline getCurrentPipeline() {
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
		
		// Clean up file system source
		if (currentPackSource != null) {
			currentPackSource.close();
			currentPackSource = null;
		}
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

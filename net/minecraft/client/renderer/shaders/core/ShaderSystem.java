package net.minecraft.client.renderer.shaders.core;

import net.minecraft.client.renderer.shaders.pack.ShaderPackRepository;
import net.minecraft.client.renderer.shaders.pipeline.PipelineManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Core shader system singleton that manages the lifecycle of the shader system.
 * This is the main entry point for all shader-related functionality in MattMC.
 * 
 * Architecture inspired by Iris's initialization pattern, adapted for MattMC's baked-in design.
 */
public class ShaderSystem {
	private static final Logger LOGGER = LoggerFactory.getLogger(ShaderSystem.class);
	private static ShaderSystem instance;
	
	private boolean initialized = false;
	private ShaderConfig config;
	private Path gameDirectory;
	private ShaderPackRepository repository;
	private PipelineManager pipelineManager;
	
	private ShaderSystem() {
		// Private constructor for singleton pattern
	}
	
	/**
	 * Gets the singleton instance of the shader system.
	 * @return The shader system instance
	 */
	public static ShaderSystem getInstance() {
		if (instance == null) {
			instance = new ShaderSystem();
		}
		return instance;
	}
	
	/**
	 * Early initialization of the shader system, called during Minecraft startup
	 * before OpenGL context is available. This matches Iris's onEarlyInitialize pattern.
	 * 
	 * @param gameDirectory The game directory path for storing configuration
	 */
	public void earlyInitialize(Path gameDirectory) {
		if (initialized) {
			LOGGER.warn("ShaderSystem already initialized");
			return;
		}
		
		LOGGER.info("Initializing MattMC Shader System");
		this.gameDirectory = gameDirectory;
		this.config = new ShaderConfig();
		this.config.initialize(gameDirectory);
		this.initialized = true;
		
		LOGGER.info("Shader System initialized successfully - Shaders: {}, Pack: {}", 
			config.areShadersEnabled(), 
			config.getSelectedPack() != null ? config.getSelectedPack() : "None");
	}
	
	/**
	 * Checks if the shader system has been initialized.
	 * @return true if initialized, false otherwise
	 */
	public boolean isInitialized() {
		return initialized;
	}
	
	/**
	 * Gets the shader configuration.
	 * @return The shader configuration
	 */
	public ShaderConfig getConfig() {
		return config;
	}
	
	/**
	 * Gets the game directory path.
	 * @return The game directory path
	 */
	public Path getGameDirectory() {
		return gameDirectory;
	}
	
	/**
	 * Called when the ResourceManager is ready to scan for shader packs.
	 * This matches Iris's pattern of loading shader packs after resources are available.
	 * 
	 * Reference: frnsrc/Iris-1.21.9/.../Iris.java - loadShaderpack() method
	 * 
	 * @param resourceManager The resource manager to use for scanning
	 */
	public void onResourceManagerReady(ResourceManager resourceManager) {
		LOGGER.info("Initializing shader pack repository");
		this.repository = new ShaderPackRepository(resourceManager);
		this.repository.scanForPacks();
		
		if (repository.hasShaderPacks()) {
			LOGGER.info("Shader packs available: {}", String.join(", ", repository.getAvailablePacks()));
		} else {
			LOGGER.info("No shader packs found in resources");
		}
		
		// Initialize pipeline manager - matches IRIS pattern
		this.pipelineManager = new PipelineManager();
		LOGGER.info("Pipeline manager initialized");
	}
	
	/**
	 * Gets the shader pack repository.
	 * @return The shader pack repository, or null if not yet initialized
	 */
	public ShaderPackRepository getRepository() {
		return repository;
	}
	
	/**
	 * Gets the pipeline manager.
	 * @return The pipeline manager, or null if not yet initialized
	 */
	public PipelineManager getPipelineManager() {
		return pipelineManager;
	}
	
	/**
	 * Checks if shaders are currently enabled.
	 * @return true if shaders are enabled in configuration, false otherwise
	 */
	public boolean areShadersEnabled() {
		return config != null && config.areShadersEnabled();
	}
	
	/**
	 * Gets the currently active rendering pipeline.
	 * Returns the pipeline for the current dimension if a shader pack is active.
	 * 
	 * @return The active pipeline, or null if none is active
	 */
	public Object getActivePipeline() {
		if (pipelineManager != null) {
			return pipelineManager.getCurrentPipeline();
		}
		return null;
	}
}

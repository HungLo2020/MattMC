package net.minecraft.client.renderer.shaders.core;

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
}

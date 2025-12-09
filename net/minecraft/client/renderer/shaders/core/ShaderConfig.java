package net.minecraft.client.renderer.shaders.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Shader configuration that persists shader settings to disk.
 * Manages enabled state, selected pack, and pack-specific options.
 * 
 * Based on Iris's IrisConfig pattern, adapted for MattMC's simpler structure.
 */
public class ShaderConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger(ShaderConfig.class);
	private static final String CONFIG_FILE = "shader-config.json";
	
	private boolean shadersEnabled = true;
	private String selectedPack = null;
	private Map<String, String> packOptions = new HashMap<>();
	private transient Path configPath;
	private transient Gson gson;
	
	public ShaderConfig() {
		this.gson = new GsonBuilder().setPrettyPrinting().create();
	}
	
	/**
	 * Initializes the configuration and loads from disk if available.
	 * 
	 * @param gameDirectory The game directory to store configuration in
	 */
	public void initialize(Path gameDirectory) {
		this.configPath = gameDirectory.resolve(CONFIG_FILE);
		load();
	}
	
	/**
	 * Loads configuration from disk.
	 */
	public void load() {
		if (configPath == null || !Files.exists(configPath)) {
			LOGGER.info("No shader config found, using defaults");
			return;
		}
		
		try {
			String json = Files.readString(configPath);
			ShaderConfig loaded = gson.fromJson(json, ShaderConfig.class);
			
			this.shadersEnabled = loaded.shadersEnabled;
			this.selectedPack = loaded.selectedPack;
			this.packOptions = loaded.packOptions != null ? loaded.packOptions : new HashMap<>();
			
			LOGGER.info("Loaded shader configuration");
		} catch (IOException e) {
			LOGGER.error("Failed to load shader config", e);
		}
	}
	
	/**
	 * Saves configuration to disk.
	 */
	public void save() {
		if (configPath == null) {
			LOGGER.warn("Cannot save config - path not initialized");
			return;
		}
		
		try {
			String json = gson.toJson(this);
			Files.writeString(configPath, json, 
				StandardOpenOption.CREATE, 
				StandardOpenOption.TRUNCATE_EXISTING);
			
			LOGGER.info("Saved shader configuration");
		} catch (IOException e) {
			LOGGER.error("Failed to save shader config", e);
		}
	}
	
	/**
	 * Checks if shaders are enabled.
	 * @return true if shaders are enabled
	 */
	public boolean areShadersEnabled() {
		return shadersEnabled;
	}
	
	/**
	 * Sets whether shaders are enabled.
	 * @param enabled true to enable shaders
	 */
	public void setShadersEnabled(boolean enabled) {
		this.shadersEnabled = enabled;
		save();
	}
	
	/**
	 * Gets the currently selected shader pack name.
	 * @return The shader pack name, or null if none selected
	 */
	public String getSelectedPack() {
		return selectedPack;
	}
	
	/**
	 * Sets the selected shader pack.
	 * @param packName The shader pack name to select
	 */
	public void setSelectedPack(String packName) {
		this.selectedPack = packName;
		save();
	}
	
	/**
	 * Sets a shader pack option value.
	 * @param key The option key
	 * @param value The option value
	 */
	public void setPackOption(String key, String value) {
		packOptions.put(key, value);
		save();
	}
	
	/**
	 * Gets a shader pack option value.
	 * @param key The option key
	 * @param defaultValue The default value if key not found
	 * @return The option value
	 */
	public String getPackOption(String key, String defaultValue) {
		return packOptions.getOrDefault(key, defaultValue);
	}
}

package net.irisshaders.iris.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.option.IrisVideoSettings;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * A class dedicated to storing the config values of shaderpacks. Right now it only stores the path to the current shaderpack
 */
public class IrisConfig {
	private static final String COMMENT =
		"This file stores configuration options for Iris, such as the currently active shaderpack";
	private final Path propertiesPath;
	private final Path excludedPath;
	/**
	 * The path to the current shaderpack. Null if the internal shaderpack is being used.
	 */
	private String shaderPackName;
	/**
	 * Whether or not shaders are used for rendering. False to disable all shader-based rendering, true to enable it.
	 */
	private boolean enableShaders;
	/**
	 * Whether or not to allow core shaders to draw to the main color texture.
	 */
	private boolean allowUnknownShaders;
	/**
	 * If debug features should be enabled. Gives much more detailed OpenGL error outputs at the cost of performance.
	 */
	private boolean enableDebugOptions;
	/**
	 * What shaders should be nuked.
	 */
	private List<ResourceLocation> shadersToSkip = new ArrayList<>();
	/**
	 * If the update notification should be disabled or not.
	 */
	private boolean disableUpdateMessage;

	public IrisConfig(Path propertiesPath, Path excluded) {
		shaderPackName = null;
		enableShaders = true;
		allowUnknownShaders = false;
		enableDebugOptions = false;
		disableUpdateMessage = false;
		this.propertiesPath = propertiesPath;
		this.excludedPath = excluded;
	}

	/**
	 * Initializes the configuration, loading it if it is present and creating a default config otherwise.
	 *
	 * @throws IOException file exceptions
	 */
	public void initialize() throws IOException {
		// Step 5: Configuration now unified in Minecraft Options - do not create separate files
		// Values are already loaded from options.txt via Options.load()
		// Just sync local fields with Options
		syncFromMinecraftOptions();
		// Note: No longer saving to iris.properties - all config in options.txt
	}
	
	/**
	 * Syncs local fields from Minecraft's Options system (Step 5: Configuration Unification).
	 * Options.load() has already loaded values from options.txt, we just sync them.
	 */
	private void syncFromMinecraftOptions() {
		try {
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			if (mc != null && mc.options != null) {
				// DEBUG: Log what we're loading
				Iris.logger.info("[Config Load] mc.options values: shaderPackName='{}', enableShaders={}", 
					mc.options.shaderPackName, mc.options.enableShaders().get());
				
				// Sync from unified Options (already loaded from options.txt)
				this.shaderPackName = mc.options.shaderPackName;
				this.enableShaders = mc.options.enableShaders().get();
				
				// DEBUG: Log what we loaded
				Iris.logger.info("[Config Load] IrisConfig fields after sync: shaderPackName='{}', enableShaders={}", 
					this.shaderPackName, this.enableShaders);
			}
		} catch (Exception e) {
			Iris.logger.error("[Config Load] Exception while syncing from Minecraft options", e);
			// Fallback to defaults if Options not available
			this.shaderPackName = null;
			this.enableShaders = true;
		}
	}

	/**
	 * returns whether or not the current shaderpack is internal
	 *
	 * @return if the shaderpack is internal
	 */
	public boolean isInternal() {
		return false;
	}

	/**
	 * Returns the name of the current shaderpack
	 *
	 * @return Returns the current shaderpack name - if internal shaders are being used it returns "(internal)"
	 */
	public Optional<String> getShaderPackName() {
		return Optional.ofNullable(shaderPackName);
	}

	/**
	 * Sets the name of the current shaderpack
	 */
	public void setShaderPackName(String name) {
		if (name == null || name.equals("(internal)") || name.isEmpty()) {
			this.shaderPackName = null;
		} else {
			this.shaderPackName = name;
		}
	}

	/**
	 * Determines whether or not shaders are used for rendering.
	 *
	 * @return False to disable all shader-based rendering, true to enable shader-based rendering.
	 */
	public boolean areShadersEnabled() {
		return enableShaders;
	}

	public boolean areDebugOptionsEnabled() {
		return enableDebugOptions;
	}

	public boolean shouldDisableUpdateMessage() {
		return disableUpdateMessage;
	}

	public void setDebugEnabled(boolean enabled) {
		enableDebugOptions = enabled;
	}

	/**
	 * Sets whether shaders should be used for rendering.
	 */
	public void setShadersEnabled(boolean enabled) {
		this.enableShaders = enabled;
	}

	private static Gson GSON = new Gson();

	/**
	 * loads the config file and then populates the string, int, and boolean entries with the parsed entries
	 *
	 * @throws IOException if the file cannot be loaded
	 */

	public void load() throws IOException {
		if (Files.exists(excludedPath)) {
			JsonArray json = JsonParser.parseString(Files.readString(excludedPath)).getAsJsonObject().getAsJsonArray("excluded");
			for (int i = 0; i < json.size(); i++) {
				ResourceLocation resource = ResourceLocation.tryParse(json.get(i).getAsString());
				if (resource == null) {
					Iris.logger.warn("Unknown shader " + json.get(i).getAsString());
				}

				shadersToSkip.add(resource);
			}
		} else {
			JsonObject defaultV = new JsonObject();
			JsonArray array = new JsonArray();
			array.add("put:valuesHere");
			defaultV.add("excluded", array);
			Files.writeString(excludedPath, GSON.toJson(defaultV));
		}

		if (!Files.exists(propertiesPath)) {
			return;
		}

		Properties properties = new Properties();
		// NB: This uses ISO-8859-1 with unicode escapes as the encoding
		try (InputStream is = Files.newInputStream(propertiesPath)) {
			properties.load(is);
		}

		shaderPackName = properties.getProperty("shaderPack");
		enableShaders = !"false".equals(properties.getProperty("enableShaders"));
		allowUnknownShaders = "true".equals(properties.getProperty("allowUnknownShaders"));
		enableDebugOptions = "true".equals(properties.getProperty("enableDebugOptions"));
		disableUpdateMessage = "true".equals(properties.getProperty("disableUpdateMessage"));
		try {
			IrisVideoSettings.shadowDistance = Integer.parseInt(properties.getProperty("maxShadowRenderDistance", "32"));
			IrisVideoSettings.colorSpace = ColorSpace.valueOf(properties.getProperty("colorSpace", "SRGB"));
		} catch (IllegalArgumentException e) {
			Iris.logger.error("Shadow distance setting reset; value is invalid.");
			IrisVideoSettings.shadowDistance = 32;
			IrisVideoSettings.colorSpace = ColorSpace.SRGB;
			save();
		}

		if (shaderPackName != null) {
			if (shaderPackName.equals("(internal)") || shaderPackName.isEmpty()) {
				shaderPackName = null;
			}
		}
	}

	/**
	 * Serializes the config into a file. Should be called whenever any config values are modified.
	 *
	 * @throws IOException file exceptions
	 */
	public void save() throws IOException {
		// Step 5: Configuration now unified in Minecraft Options - do not create separate files
		// Save to Minecraft Options instead
		saveToMinecraftOptions();
		// Note: No longer saving to iris.properties - all config in options.txt
	}
	
	/**
	 * Saves configuration to Minecraft's Options system (Step 5: Configuration Unification).
	 * Replaces saving to iris.properties file.
	 */
	private void saveToMinecraftOptions() {
		try {
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			if (mc != null && mc.options != null) {
				// Write to unified Options
				mc.options.shaderPackName = this.shaderPackName;
				mc.options.enableShaders().set(this.enableShaders);
				
				// DEBUG: Log what we're saving
				Iris.logger.info("[Config Save] Writing shaderPackName='{}', enableShaders={}", 
					this.shaderPackName, this.enableShaders);
				Iris.logger.info("[Config Save] mc.options values BEFORE save: shaderPackName='{}', enableShaders={}", 
					mc.options.shaderPackName, mc.options.enableShaders().get());
				
				// Trigger save to options.txt
				mc.options.save();
				
				// DEBUG: Verify values after save
				Iris.logger.info("[Config Save] mc.options values AFTER save: shaderPackName='{}', enableShaders={}", 
					mc.options.shaderPackName, mc.options.enableShaders().get());
			}
		} catch (Exception e) {
			Iris.logger.error("[Config Save] Exception while saving to Minecraft options", e);
		}
	}

	public boolean shouldAllowUnknownShaders() {
		return allowUnknownShaders;
	}

	public boolean shouldSkip(ResourceLocation value) {
		return shadersToSkip.contains(value); // TODO
	}

	public void setUnknown(boolean b) throws IOException {
		this.allowUnknownShaders = b;
		save();
	}
}

package net.minecraft.client.renderer.shaders.pack;

import net.minecraft.client.renderer.shaders.helpers.OptionalBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * The parsed representation of the shaders.properties file.
 * This class is not meant to be stored permanently, rather it merely exists as an
 * intermediate step until we build up PackDirectives and ProgramDirectives objects.
 * 
 * Matches IRIS's ShaderProperties implementation verbatim for Step 4.
 * Reference: frnsrc/Iris-1.21.9/.../shaderpack/properties/ShaderProperties.java
 * 
 * This is a foundational class - following IRIS exactly is critical for compatibility.
 */
public class ShaderProperties {
	private static final Logger LOGGER = LoggerFactory.getLogger(ShaderProperties.class);
	
	// Boolean rendering toggles - using OptionalBoolean like IRIS
	private OptionalBoolean weather = OptionalBoolean.DEFAULT;
	private OptionalBoolean oldLighting = OptionalBoolean.DEFAULT;
	private OptionalBoolean underwaterOverlay = OptionalBoolean.DEFAULT;
	private OptionalBoolean sun = OptionalBoolean.DEFAULT;
	private OptionalBoolean moon = OptionalBoolean.DEFAULT;
	private OptionalBoolean stars = OptionalBoolean.DEFAULT;
	private OptionalBoolean sky = OptionalBoolean.DEFAULT;
	private OptionalBoolean vignette = OptionalBoolean.DEFAULT;
	
	// Additional flags matching IRIS
	private OptionalBoolean shadowEnabled = OptionalBoolean.DEFAULT;
	private OptionalBoolean shadowTerrain = OptionalBoolean.DEFAULT;
	private OptionalBoolean shadowTranslucent = OptionalBoolean.DEFAULT;
	private OptionalBoolean shadowEntities = OptionalBoolean.DEFAULT;
	private OptionalBoolean shadowPlayer = OptionalBoolean.DEFAULT;
	private OptionalBoolean shadowBlockEntities = OptionalBoolean.DEFAULT;
	
	// Texture path
	private String noiseTexturePath = null;
	
	private ShaderProperties() {
		// empty - matches IRIS pattern
	}
	
	/**
	 * Constructs shader properties from properties content.
	 * Matches IRIS's constructor pattern exactly.
	 * 
	 * @param contents The raw properties file content
	 */
	public ShaderProperties(String contents) {
		Properties properties = new Properties();
		
		try {
			properties.load(new StringReader(contents));
		} catch (IOException e) {
			LOGGER.error("Error loading shaders.properties!", e);
		}
		
		// Parse properties - matching IRIS's forEach pattern
		properties.forEach((keyObject, valueObject) -> {
			String key = (String) keyObject;
			String value = (String) valueObject;
			
			// Texture path - matches IRIS exactly
			if ("texture.noise".equals(key)) {
				noiseTexturePath = value;
				return;
			}
			
			// Boolean directives - matches IRIS's handleBooleanDirective calls
			handleBooleanDirective(key, value, "oldLighting", bool -> oldLighting = bool);
			handleBooleanDirective(key, value, "shadowTerrain", bool -> shadowTerrain = bool);
			handleBooleanDirective(key, value, "shadowTranslucent", bool -> shadowTranslucent = bool);
			handleBooleanDirective(key, value, "shadowEntities", bool -> shadowEntities = bool);
			handleBooleanDirective(key, value, "shadowPlayer", bool -> shadowPlayer = bool);
			handleBooleanDirective(key, value, "shadowBlockEntities", bool -> shadowBlockEntities = bool);
			handleBooleanDirective(key, value, "underwaterOverlay", bool -> underwaterOverlay = bool);
			handleBooleanDirective(key, value, "sun", bool -> sun = bool);
			handleBooleanDirective(key, value, "moon", bool -> moon = bool);
			handleBooleanDirective(key, value, "stars", bool -> stars = bool);
			handleBooleanDirective(key, value, "sky", bool -> sky = bool);
			handleBooleanDirective(key, value, "vignette", bool -> vignette = bool);
			handleBooleanDirective(key, value, "shadow.enabled", bool -> shadowEnabled = bool);
			
			// Weather has special handling in IRIS - it can be "true" or "false" and affects weather particles
			if ("weather".equals(key)) {
				weather = "true".equals(value) ? OptionalBoolean.TRUE : OptionalBoolean.FALSE;
			}
		});
	}
	
	/**
	 * Loads shader properties from a shader pack source.
	 * Reads shaders.properties file and parses it.
	 * 
	 * @param source The shader pack source to read from
	 * @return Parsed shader properties
	 * @throws IOException if reading fails
	 */
	public static ShaderProperties load(ShaderPackSource source) throws IOException {
		java.util.Optional<String> content = source.readFile("shaders.properties");
		
		if (content.isEmpty()) {
			LOGGER.info("No shaders.properties found for pack: {}, using defaults", source.getName());
			return empty();
		}
		
		return new ShaderProperties(content.get());
	}
	
	/**
	 * Creates an empty ShaderProperties with all default values.
	 * Matches IRIS's empty() method exactly.
	 * 
	 * @return Empty shader properties
	 */
	public static ShaderProperties empty() {
		return new ShaderProperties();
	}
	
	/**
	 * Handles a boolean directive from properties.
	 * Matches IRIS's handleBooleanDirective implementation verbatim.
	 * 
	 * @param key The property key
	 * @param value The property value
	 * @param expectedKey The expected key to match
	 * @param handler Consumer to accept the OptionalBoolean value
	 */
	private static void handleBooleanDirective(String key, String value, String expectedKey, Consumer<OptionalBoolean> handler) {
		if (!expectedKey.equals(key)) {
			return;
		}
		
		if ("true".equals(value) || "1".equals(value)) {
			handler.accept(OptionalBoolean.TRUE);
		} else if ("false".equals(value) || "0".equals(value)) {
			handler.accept(OptionalBoolean.FALSE);
		} else {
			LOGGER.warn("Unexpected value for boolean key " + key + " in shaders.properties: got " + value + ", but expected either true or false");
		}
	}
	
	// Getters - matching IRIS's getter pattern exactly
	
	public OptionalBoolean getOldLighting() {
		return oldLighting;
	}
	
	public OptionalBoolean getShadowTerrain() {
		return shadowTerrain;
	}
	
	public OptionalBoolean getShadowTranslucent() {
		return shadowTranslucent;
	}
	
	public OptionalBoolean getShadowEntities() {
		return shadowEntities;
	}
	
	public OptionalBoolean getShadowPlayer() {
		return shadowPlayer;
	}
	
	public OptionalBoolean getShadowBlockEntities() {
		return shadowBlockEntities;
	}
	
	public OptionalBoolean getUnderwaterOverlay() {
		return underwaterOverlay;
	}
	
	public OptionalBoolean getSun() {
		return sun;
	}
	
	public OptionalBoolean getMoon() {
		return moon;
	}
	
	public OptionalBoolean getStars() {
		return stars;
	}
	
	public OptionalBoolean getSky() {
		return sky;
	}
	
	public OptionalBoolean getVignette() {
		return vignette;
	}
	
	public OptionalBoolean getShadowEnabled() {
		return shadowEnabled;
	}
	
	public OptionalBoolean getWeather() {
		return weather;
	}
	
	public String getNoiseTexturePath() {
		return noiseTexturePath;
	}
}

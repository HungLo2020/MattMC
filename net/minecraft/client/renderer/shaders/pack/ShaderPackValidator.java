package net.minecraft.client.renderer.shaders.pack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates shader pack structure and required files before attempting to load.
 * 
 * Following IRIS validation patterns from:
 * - frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/Iris.java (isValidShaderpack method)
 * - frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/shaderpack/ShaderPack.java (constructor validation)
 * 
 * IRIS validates that:
 * 1. A "shaders" directory exists (line 494 in Iris.java)
 * 2. Feature flags are valid (lines 224-237 in ShaderPack.java)
 * 3. Properties files are parseable
 * 
 * Step 10 of NEW-SHADER-PLAN.md
 */
public class ShaderPackValidator {
	private static final Logger LOGGER = LoggerFactory.getLogger(ShaderPackValidator.class);
	
	/**
	 * Validation result containing validity status, errors, and warnings.
	 * Matches the pattern from NEW-SHADER-PLAN.md Step 10.
	 */
	public static class ValidationResult {
		private final boolean valid;
		private final List<String> errors;
		private final List<String> warnings;
		
		public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
			this.valid = valid;
			this.errors = new ArrayList<>(errors);
			this.warnings = new ArrayList<>(warnings);
		}
		
		public boolean isValid() { 
			return valid; 
		}
		
		public List<String> getErrors() { 
			return new ArrayList<>(errors); 
		}
		
		public List<String> getWarnings() { 
			return new ArrayList<>(warnings); 
		}
		
		/**
		 * Logs validation results to the logger.
		 * Matches IRIS's logging pattern for validation feedback.
		 */
		public void logResults() {
			if (!errors.isEmpty()) {
				LOGGER.error("Validation errors:");
				errors.forEach(e -> LOGGER.error("  - {}", e));
			}
			if (!warnings.isEmpty()) {
				LOGGER.warn("Validation warnings:");
				warnings.forEach(w -> LOGGER.warn("  - {}", w));
			}
			if (valid && errors.isEmpty() && warnings.isEmpty()) {
				LOGGER.info("Validation passed with no issues");
			}
		}
	}
	
	/**
	 * Validates a shader pack source.
	 * 
	 * Following IRIS validation approach:
	 * 1. Check for essential shader files (like IRIS checks for "shaders" directory)
	 * 2. Validate properties files if present
	 * 3. Check for common configuration issues
	 * 
	 * @param source The shader pack source to validate
	 * @return Validation result with errors and warnings
	 */
	public static ValidationResult validate(ShaderPackSource source) {
		List<String> errors = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		
		LOGGER.info("Validating shader pack: {}", source.getName());
		
		// Check for shaders directory - IRIS primary check (Iris.java:494)
		checkShadersDirectory(source, errors);
		
		// Check for required files
		checkRequiredFiles(source, errors, warnings);
		
		// Check for shader programs
		checkShaderPrograms(source, errors, warnings);
		
		// Check for dimension support
		checkDimensionSupport(source, warnings);
		
		// Validate properties files
		validatePropertiesFiles(source, errors, warnings);
		
		boolean valid = errors.isEmpty();
		return new ValidationResult(valid, errors, warnings);
	}
	
	/**
	 * Check for shaders directory existence.
	 * IRIS validation: Iris.java line 494 - checks for "shaders" directory.
	 * This is the primary validation IRIS performs.
	 */
	private static void checkShadersDirectory(ShaderPackSource source, List<String> errors) {
		// In MattMC's baked-in design, we check for at least one file in shaders/
		boolean hasShadersDir = false;
		try {
			List<String> shadersFiles = source.listFiles("shaders");
			if (!shadersFiles.isEmpty()) {
				hasShadersDir = true;
			}
		} catch (IOException e) {
			LOGGER.debug("Could not list shaders/ directory for {}", source.getName());
		}
		
		// Also check if any shaders/*.{vsh,fsh,gsh} files exist
		if (!hasShadersDir) {
			String[] shaderExtensions = {"vsh", "fsh", "gsh"};
			for (String ext : shaderExtensions) {
				if (source.fileExists("shaders/composite." + ext) ||
					source.fileExists("shaders/final." + ext) ||
					source.fileExists("shaders/gbuffers_terrain." + ext)) {
					hasShadersDir = true;
					break;
				}
			}
		}
		
		if (!hasShadersDir) {
			errors.add("No shaders directory found or shaders directory is empty - not a valid shader pack");
		}
	}
	
	/**
	 * Check for essential shader files.
	 * Following IRIS pattern: shader packs need at least some shader files to be useful.
	 */
	private static void checkRequiredFiles(ShaderPackSource source, 
	                                      List<String> errors, 
	                                      List<String> warnings) {
		// Check for at least one of the essential shader files
		// These are the most common shaders in IRIS packs
		String[] essentialShaders = {
			"shaders/gbuffers_terrain.fsh",
			"shaders/gbuffers_terrain.vsh",
			"shaders/composite.fsh",
			"shaders/final.fsh"
		};
		
		boolean hasAnyShader = false;
		for (String shader : essentialShaders) {
			if (source.fileExists(shader)) {
				hasAnyShader = true;
				break;
			}
		}
		
		if (!hasAnyShader) {
			errors.add("No essential shader files found (gbuffers_terrain, composite, or final)");
		}
		
		// Warn if properties file missing (IRIS identifies packs by shaders.properties)
		if (!source.fileExists("shaders.properties")) {
			warnings.add("No shaders.properties file found - using defaults");
		}
	}
	
	/**
	 * Check for common shader program pairs.
	 * IRIS shader programs typically come in vertex+fragment pairs.
	 */
	private static void checkShaderPrograms(ShaderPackSource source, 
	                                       List<String> errors, 
	                                       List<String> warnings) {
		// Check for common shader program pairs
		// Based on IRIS's ProgramSet.java - these are standard program names
		String[] programNames = {
			"gbuffers_terrain",
			"gbuffers_water",
			"gbuffers_entities",
			"gbuffers_hand",
			"gbuffers_textured",
			"gbuffers_textured_lit",
			"gbuffers_skybasic",
			"gbuffers_skytextured",
			"gbuffers_clouds",
			"gbuffers_weather"
		};
		
		for (String program : programNames) {
			boolean hasVsh = source.fileExists("shaders/" + program + ".vsh");
			boolean hasFsh = source.fileExists("shaders/" + program + ".fsh");
			boolean hasGsh = source.fileExists("shaders/" + program + ".gsh");
			
			if (hasVsh && !hasFsh && !hasGsh) {
				warnings.add("Found " + program + ".vsh but missing .fsh (fragment shader required)");
			} else if (!hasVsh && hasFsh) {
				warnings.add("Found " + program + ".fsh but missing .vsh (vertex shader may be needed)");
			}
		}
		
		// Check for final pass (common in IRIS packs)
		if (!source.fileExists("shaders/final.fsh")) {
			warnings.add("No final.fsh found - shader pack may not render post-processing effects");
		}
	}
	
	/**
	 * Check for dimension support consistency.
	 * Based on Step 9's DimensionConfig implementation.
	 */
	private static void checkDimensionSupport(ShaderPackSource source, List<String> warnings) {
		// Check for dimension-specific folders (world0, world-1, world1)
		// Following IRIS's dimension detection pattern
		boolean hasWorld0 = source.fileExists("world0/composite.fsh") || 
		                   source.fileExists("world0/gbuffers_terrain.fsh");
		boolean hasWorldNeg1 = source.fileExists("world-1/composite.fsh") || 
		                       source.fileExists("world-1/gbuffers_terrain.fsh");
		boolean hasWorld1 = source.fileExists("world1/composite.fsh") || 
		                   source.fileExists("world1/gbuffers_terrain.fsh");
		
		// Warn about incomplete dimension support
		if (hasWorld0 && !hasWorldNeg1) {
			warnings.add("Has Overworld shaders (world0/) but missing Nether shaders (world-1/)");
		}
		if (hasWorld0 && !hasWorld1) {
			warnings.add("Has Overworld shaders (world0/) but missing End shaders (world1/)");
		}
		
		// Check for dimension.properties if dimension folders exist
		if ((hasWorld0 || hasWorldNeg1 || hasWorld1) && !source.fileExists("dimension.properties")) {
			warnings.add("Has dimension folders but no dimension.properties file");
		}
	}
	
	/**
	 * Validate properties files.
	 * Following IRIS's ShaderProperties parsing validation.
	 */
	private static void validatePropertiesFiles(ShaderPackSource source, 
	                                           List<String> errors, 
	                                           List<String> warnings) {
		// Validate shaders.properties if it exists
		if (source.fileExists("shaders.properties")) {
			try {
				ShaderProperties props = ShaderProperties.load(source);
				
				// Basic validation - ensure it parsed successfully
				// More detailed property validation will be added in later steps
				// when additional properties are implemented
				
			} catch (Exception e) {
				errors.add("Failed to parse shaders.properties: " + e.getMessage());
			}
		}
		
		// Validate dimension.properties if it exists
		if (source.fileExists("dimension.properties")) {
			try {
				// Try to load dimension config
				DimensionConfig config = DimensionConfig.load(source);
				
				// Verify it loaded successfully (non-null result means success)
				if (config == null) {
					warnings.add("dimension.properties exists but could not be loaded");
				}
			} catch (Exception e) {
				warnings.add("Failed to parse dimension.properties: " + e.getMessage());
			}
		}
	}
}

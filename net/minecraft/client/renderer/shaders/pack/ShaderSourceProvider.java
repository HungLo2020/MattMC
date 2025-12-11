package net.minecraft.client.renderer.shaders.pack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Provides processed shader source code with all #include directives resolved.
 * 
 * Based on IRIS's source provider pattern in ShaderPack.java (lines 286-320).
 * Reference: frnsrc/Iris-1.21.9/.../shaderpack/ShaderPack.java
 * 
 * This class wraps IncludeProcessor and provides a Function<AbsolutePackPath, String>
 * that returns fully processed shader source code.
 */
public class ShaderSourceProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(ShaderSourceProvider.class);
	
	private final ShaderPackSource packSource;
	private final IncludeGraph includeGraph;
	private final IncludeProcessor includeProcessor;
	private final Function<AbsolutePackPath, String> sourceProvider;
	private final Map<String, String> sourceCache;
	
	/**
	 * Creates a ShaderSourceProvider for the given shader pack.
	 * 
	 * @param packSource The shader pack source to read files from
	 * @param startingPaths The starting shader file paths to discover
	 */
	public ShaderSourceProvider(ShaderPackSource packSource, List<AbsolutePackPath> startingPaths) {
		this.packSource = packSource;
		this.sourceCache = new HashMap<>();
		
		LOGGER.info("Creating ShaderSourceProvider for pack: {}", packSource.getName());
		
		// Build include graph (matches IRIS pattern)
		this.includeGraph = new IncludeGraph(packSource, startingPaths);
		
		// Check for failures
		if (!includeGraph.getFailures().isEmpty()) {
			LOGGER.warn("Include graph has {} failures:", includeGraph.getFailures().size());
			includeGraph.getFailures().forEach((path, error) -> 
				LOGGER.warn("  {}: {}", path, error));
		}
		
		// Create include processor
		this.includeProcessor = new IncludeProcessor(includeGraph);
		
		// Create source provider function (matches IRIS pattern exactly - ShaderPack.java lines 286-320)
		this.sourceProvider = (path) -> {
			String pathString = path.getPathString();
			
			// Get included file (with all #include directives expanded)
			List<String> lines = includeProcessor.getIncludedFile(path);
			
			if (lines == null) {
				LOGGER.warn("Failed to get included file: {}", pathString);
				return null;
			}
			
			// Join lines into source string (matches IRIS pattern)
			StringBuilder builder = new StringBuilder();
			for (String line : lines) {
				builder.append(line);
				builder.append('\n');
			}
			
			String source = builder.toString();
			
			// Note: IRIS applies JcppProcessor here for GLSL preprocessing
			// We skip this for now as it will be implemented in the compilation steps (11-15)
			// This matches the Step 7 plan which focuses on include resolution only
			
			return source;
		};
		
		LOGGER.info("ShaderSourceProvider created successfully");
	}
	
	/**
	 * Gets the processed source code for a shader file.
	 * Tries dimension-specific paths first, then falls back to base shaders directory.
	 * 
	 * @param shaderPath The path to the shader file (e.g., "gbuffers_terrain.fsh")
	 * @return The processed source code, or null if not found
	 */
	public String getShaderSource(String shaderPath) {
		return getShaderSource(shaderPath, null);
	}
	
	/**
	 * Gets the processed source code for a shader file with dimension awareness.
	 * Tries dimension-specific paths first, then falls back to base shaders directory.
	 * 
	 * IRIS pattern: Shader packs can have dimension-specific folders:
	 * - world0 for overworld
	 * - world-1 for nether  
	 * - world1 for end
	 * 
	 * @param shaderPath The path to the shader file (e.g., "gbuffers_terrain.fsh")
	 * @param dimensionId The dimension ID (e.g., "minecraft:overworld") or null
	 * @return The processed source code, or null if not found
	 */
	public String getShaderSource(String shaderPath, String dimensionId) {
		// Build cache key with dimension
		String cacheKey = dimensionId != null ? dimensionId + ":" + shaderPath : shaderPath;
		
		// Check cache first
		if (sourceCache.containsKey(cacheKey)) {
			LOGGER.debug("Returning cached source for: {}", cacheKey);
			return sourceCache.get(cacheKey);
		}
		
		// Get the dimension folder name
		String dimensionFolder = getDimensionFolder(dimensionId);
		
		// Try dimension-specific path first
		if (dimensionFolder != null) {
			String dimensionPath = "/shaders/" + dimensionFolder + "/" + shaderPath;
			LOGGER.debug("Trying dimension-specific path: {}", dimensionPath);
			
			String source = loadShaderFromPath(dimensionPath);
			if (source != null) {
				sourceCache.put(cacheKey, source);
				LOGGER.debug("Loaded dimension-specific shader: {} ({} chars)", dimensionPath, source.length());
				return source;
			}
		}
		
		// Fall back to base shaders directory
		String basePath = "/shaders/" + shaderPath;
		LOGGER.debug("Trying base path: {}", basePath);
		
		String source = loadShaderFromPath(basePath);
		if (source != null) {
			sourceCache.put(cacheKey, source);
			LOGGER.debug("Loaded base shader: {} ({} chars)", basePath, source.length());
			return source;
		}
		
		LOGGER.debug("Shader not found: {} (tried dimension={}, base)", shaderPath, dimensionFolder);
		return null;
	}
	
	/**
	 * Gets the dimension folder name for a given dimension ID.
	 * IRIS pattern: maps dimension registry names to folder names.
	 */
	private String getDimensionFolder(String dimensionId) {
		if (dimensionId == null) {
			return "world0"; // Default to overworld
		}
		
		return switch (dimensionId) {
			case "minecraft:overworld" -> "world0";
			case "minecraft:the_nether" -> "world-1";
			case "minecraft:the_end" -> "world1";
			default -> {
				// Custom dimensions - extract the last part of the ID
				// e.g., "mymod:custom_dim" -> null (use base shaders)
				yield null;
			}
		};
	}
	
	/**
	 * Loads shader source from a specific path.
	 */
	private String loadShaderFromPath(String fullPath) {
		try {
			AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath(fullPath);
			return sourceProvider.apply(path);
		} catch (Exception e) {
			LOGGER.debug("Failed to load shader from path {}: {}", fullPath, e.getMessage());
			return null;
		}
	}
	
	/**
	 * Checks if a shader file exists in the pack.
	 * 
	 * @param shaderPath The path to check
	 * @return true if the file exists
	 */
	public boolean hasShaderFile(String shaderPath) {
		String fullPath = shaderPath.startsWith("shaders/") ? 
			shaderPath : "shaders/" + shaderPath;
		return packSource.fileExists(fullPath);
	}
	
	/**
	 * Clears the source cache.
	 * Call this when shader pack is reloaded.
	 */
	public void clearCache() {
		sourceCache.clear();
		LOGGER.debug("Cleared shader source cache");
	}
	
	/**
	 * Gets all cached sources (for debugging/testing).
	 * 
	 * @return A copy of the source cache
	 */
	public Map<String, String> getAllCachedSources() {
		return new HashMap<>(sourceCache);
	}
	
	/**
	 * Gets the underlying source provider function.
	 * Matches IRIS's sourceProvider field pattern.
	 * 
	 * @return The source provider function
	 */
	public Function<AbsolutePackPath, String> getSourceProvider() {
		return sourceProvider;
	}
	
	/**
	 * Gets the include graph.
	 * Useful for debugging and testing.
	 * 
	 * @return The include graph
	 */
	public IncludeGraph getIncludeGraph() {
		return includeGraph;
	}
	
	/**
	 * Gets the shader pack source.
	 * 
	 * @return The pack source
	 */
	public ShaderPackSource getPackSource() {
		return packSource;
	}
}

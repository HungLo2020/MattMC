package net.minecraft.client.renderer.shaders.pack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Repository for discovering and managing available shader packs.
 * Scans resources for shader packs and provides access to them.
 * 
 * Based on IRIS's ShaderpackDirectoryManager pattern, adapted for ResourceManager.
 * Reference: frnsrc/Iris-1.21.9/.../shaderpack/discovery/ShaderpackDirectoryManager.java
 */
public class ShaderPackRepository {
	private static final Logger LOGGER = LoggerFactory.getLogger(ShaderPackRepository.class);
	private static final Set<String> EXCLUDED_DIRS = Set.of("core", "post", "include");
	
	private final ResourceManager resourceManager;
	private final List<String> availablePacks = new ArrayList<>();
	
	/**
	 * Creates a new shader pack repository.
	 * 
	 * @param resourceManager The resource manager to scan for shader packs
	 */
	public ShaderPackRepository(ResourceManager resourceManager) {
		this.resourceManager = resourceManager;
	}
	
	/**
	 * Scans for available shader packs in the resources.
	 * Looks for subdirectories under assets/minecraft/shaders/ that contain
	 * a shaders.properties file.
	 * 
	 * Following IRIS pattern: shader packs are identified by the presence of
	 * shaders.properties in their root directory.
	 */
	public void scanForPacks() {
		availablePacks.clear();
		
		LOGGER.info("Scanning for shader packs in resources...");
		
		try {
			// Scan assets/minecraft/shaders/ for subdirectories
			Set<String> packNames = new HashSet<>();
			
			// Try to find shader pack directories by looking for shaders.properties files
			// This matches IRIS's pattern of identifying packs by shaders.properties
			resourceManager.listResources("shaders", loc -> 
				loc.getPath().endsWith("shaders.properties")
			).forEach((location, resource) -> {
				String path = location.getPath();
				// Extract pack name: shaders/PACKNAME/shaders.properties
				String[] parts = path.split("/");
				if (parts.length >= 2) {
					String packName = parts[1];
					if (!EXCLUDED_DIRS.contains(packName)) {
						packNames.add(packName);
						LOGGER.debug("Discovered shader pack: {}", packName);
					} else {
						LOGGER.trace("Skipping excluded directory: {}", packName);
					}
				}
			});
			
			availablePacks.addAll(packNames);
			
			LOGGER.info("Found {} shader pack(s): {}", 
				availablePacks.size(), 
				availablePacks.isEmpty() ? "(none)" : String.join(", ", availablePacks));
				
		} catch (Exception e) {
			LOGGER.error("Failed to scan for shader packs", e);
		}
	}
	
	/**
	 * Gets the list of available shader pack names.
	 * 
	 * @return A copy of the list of available pack names
	 */
	public List<String> getAvailablePacks() {
		return new ArrayList<>(availablePacks);
	}
	
	/**
	 * Gets a shader pack source for the specified pack name.
	 * 
	 * @param packName The name of the shader pack
	 * @return The shader pack source, or null if pack not found
	 */
	public ShaderPackSource getPackSource(String packName) {
		if (!availablePacks.contains(packName)) {
			LOGGER.warn("Requested shader pack not found: {}", packName);
			return null;
		}
		return new ResourceShaderPackSource(resourceManager, packName);
	}
	
	/**
	 * Checks if any shader packs are available.
	 * 
	 * @return true if at least one shader pack is available
	 */
	public boolean hasShaderPacks() {
		return !availablePacks.isEmpty();
	}
}

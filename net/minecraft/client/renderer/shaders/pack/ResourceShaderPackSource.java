package net.minecraft.client.renderer.shaders.pack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shader pack source that reads from Minecraft's ResourceManager.
 * Allows shader packs to be baked into the JAR file as resources.
 * 
 * Based on IRIS's filesystem-based shader pack access, adapted for ResourceManager.
 * Reference: frnsrc/Iris-1.21.9/.../shaderpack/ShaderPack.java
 */
public class ResourceShaderPackSource implements ShaderPackSource {
	private static final Logger LOGGER = LoggerFactory.getLogger(ResourceShaderPackSource.class);
	private final ResourceManager resourceManager;
	private final String packName;
	private final String basePath;
	
	/**
	 * Creates a new resource-based shader pack source.
	 * 
	 * @param resourceManager The resource manager to read from
	 * @param packName The name of the shader pack
	 */
	public ResourceShaderPackSource(ResourceManager resourceManager, String packName) {
		this.resourceManager = resourceManager;
		this.packName = packName;
		// Shader packs are stored in assets/minecraft/shaders/PACKNAME/
		this.basePath = "shaders/" + packName + "/";
	}
	
	@Override
	public String getName() {
		return packName;
	}
	
	@Override
	public Optional<String> readFile(String relativePath) throws IOException {
		try {
			// Construct the full resource location
			ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
				"minecraft",
				basePath + relativePath
			);
			
			// Try to get the resource
			Optional<Resource> resourceOpt = resourceManager.getResource(location);
			if (resourceOpt.isEmpty()) {
				LOGGER.debug("Resource not found: {}", location);
				return Optional.empty();
			}
			
			// Read the resource content
			try (InputStream stream = resourceOpt.get().open()) {
				byte[] bytes = stream.readAllBytes();
				String content = new String(bytes, StandardCharsets.UTF_8);
				LOGGER.debug("Successfully read file: {} ({} bytes)", relativePath, bytes.length);
				return Optional.of(content);
			}
		} catch (IOException e) {
			LOGGER.debug("Failed to read file: {}", relativePath, e);
			return Optional.empty();
		}
	}
	
	@Override
	public boolean fileExists(String relativePath) {
		ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
			"minecraft",
			basePath + relativePath
		);
		boolean exists = resourceManager.getResource(location).isPresent();
		LOGGER.trace("File exists check: {} -> {}", relativePath, exists);
		return exists;
	}
	
	@Override
	public List<String> listFiles(String directory) throws IOException {
		// For now, return empty list - will be implemented when needed for shader file discovery
		// IRIS uses filesystem directory listing, but for baked-in resources we'd need
		// a different approach (possibly scanning via ResourceManager.listResources)
		LOGGER.debug("listFiles not yet implemented for directory: {}", directory);
		return new ArrayList<>();
	}
}

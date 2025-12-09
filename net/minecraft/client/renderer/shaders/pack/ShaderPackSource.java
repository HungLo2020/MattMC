package net.minecraft.client.renderer.shaders.pack;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Interface for accessing shader pack files.
 * Abstracts the source of shader files, allowing for different implementations
 * (e.g., from JAR resources, filesystem, or ZIP archives).
 * 
 * Based on IRIS's shader pack access pattern, adapted for MattMC's baked-in design.
 */
public interface ShaderPackSource {
	/**
	 * Gets the name of this shader pack.
	 * @return The shader pack name
	 */
	String getName();
	
	/**
	 * Reads a file from the shader pack.
	 * @param relativePath The path relative to the shader pack root
	 * @return The file content, or empty if not found
	 * @throws IOException if reading fails
	 */
	Optional<String> readFile(String relativePath) throws IOException;
	
	/**
	 * Checks if a file exists in the shader pack.
	 * @param relativePath The path relative to the shader pack root
	 * @return true if the file exists
	 */
	boolean fileExists(String relativePath);
	
	/**
	 * Lists files in a directory within the shader pack.
	 * @param directory The directory path relative to shader pack root
	 * @return List of file paths
	 * @throws IOException if listing fails
	 */
	List<String> listFiles(String directory) throws IOException;
}

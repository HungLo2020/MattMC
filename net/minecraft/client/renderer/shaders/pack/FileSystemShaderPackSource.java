package net.minecraft.client.renderer.shaders.pack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * ShaderPackSource implementation for file system-based shader packs.
 * Supports both directory-based packs and ZIP archives.
 * 
 * Based on IRIS's shader pack loading pattern.
 * Reference: frnsrc/Iris-1.21.9/.../Iris.java loadExternalShaderpack()
 */
public class FileSystemShaderPackSource implements ShaderPackSource {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemShaderPackSource.class);
	
	private final String name;
	private final Path shaderPackPath;
	private final FileSystem zipFileSystem;
	
	/**
	 * Creates a new file system shader pack source.
	 * 
	 * @param name The name of the shader pack
	 * @param packRoot The root path of the shader pack (directory or zip file)
	 * @throws IOException if the pack cannot be accessed
	 */
	public FileSystemShaderPackSource(String name, Path packRoot) throws IOException {
		this.name = name;
		
		if (!Files.exists(packRoot)) {
			throw new IOException("Shader pack does not exist: " + packRoot);
		}
		
		if (Files.isDirectory(packRoot)) {
			// Directory-based pack - use the pack root as base
			// The caller will request paths like "shaders/gbuffers_terrain.vsh"
			this.shaderPackPath = packRoot;
			this.zipFileSystem = null;
			
			// Verify the pack has a shaders directory
			if (!Files.exists(packRoot.resolve("shaders"))) {
				throw new IOException("Shader pack lacks 'shaders' directory: " + packRoot);
			}
		} else if (packRoot.toString().endsWith(".zip")) {
			// ZIP-based pack - open the zip file system
			this.zipFileSystem = FileSystems.newFileSystem(packRoot);
			
			// Use the zip root as base, the caller will request paths like "shaders/gbuffers_terrain.vsh"
			Path shadersDir = this.zipFileSystem.getPath("/shaders");
			if (Files.exists(shadersDir)) {
				// Use the zip root, not the shaders directory
				this.shaderPackPath = this.zipFileSystem.getPath("/");
			} else {
				// Some packs have the shaders in a subdirectory (e.g., PackName/shaders/)
				Path altPath = findShadersDirectory(this.zipFileSystem.getPath("/"));
				if (altPath != null) {
					// Use the parent of shaders directory as the pack root
					this.shaderPackPath = altPath.getParent();
				} else {
					this.zipFileSystem.close();
					throw new IOException("Shader pack lacks 'shaders' directory: " + packRoot);
				}
			}
		} else {
			throw new IOException("Unknown shader pack format: " + packRoot);
		}
		
		LOGGER.info("Loaded shader pack source: {} from {}", name, shaderPackPath);
	}
	
	/**
	 * Finds the shaders directory by searching subdirectories.
	 * Some packs nest the shaders directory inside another folder.
	 */
	private Path findShadersDirectory(Path root) throws IOException {
		try (Stream<Path> stream = Files.list(root)) {
			return stream
				.filter(Files::isDirectory)
				.map(entry -> entry.resolve("shaders"))
				.filter(shadersDir -> Files.exists(shadersDir) && Files.isDirectory(shadersDir))
				.findFirst()
				.orElse(null);
		}
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public Optional<String> readFile(String relativePath) throws IOException {
		Path filePath = shaderPackPath.resolve(relativePath);
		
		if (!Files.exists(filePath)) {
			return Optional.empty();
		}
		
		try (InputStream is = Files.newInputStream(filePath)) {
			return Optional.of(new String(is.readAllBytes(), StandardCharsets.UTF_8));
		}
	}
	
	@Override
	public boolean fileExists(String relativePath) {
		Path filePath = shaderPackPath.resolve(relativePath);
		boolean exists = Files.exists(filePath);
		if (!exists && relativePath.contains("world0")) {
			LOGGER.debug("File check: {} -> {} = {}", relativePath, filePath, exists);
		}
		return exists;
	}
	
	@Override
	public List<String> listFiles(String directory) throws IOException {
		Path dirPath = shaderPackPath.resolve(directory);
		
		if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
			return new ArrayList<>();
		}
		
		try (Stream<Path> stream = Files.list(dirPath)) {
			return stream
				.map(entry -> entry.getFileName().toString())
				.collect(java.util.stream.Collectors.toList());
		}
	}
	
	/**
	 * Closes any resources held by this source.
	 * Should be called when the shader pack is no longer needed.
	 */
	public void close() {
		if (zipFileSystem != null) {
			try {
				zipFileSystem.close();
			} catch (IOException e) {
				LOGGER.warn("Failed to close zip file system for pack: {}", name, e);
			}
		}
	}
}

package net.minecraft.client.renderer.shaders;

import net.minecraft.client.renderer.shaders.shaderpack.ShaderPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stub for Iris main class - minimal implementation for GUI compilation.
 * Full implementation will be added when shader pack loading is implemented.
 */
public class Iris {
	public static final String MODNAME = "Iris";
	public static final Logger logger = LoggerFactory.getLogger("Iris");

	private static final IrisConfig irisConfig = new IrisConfig();
	private static ShaderpacksDirectoryManager shaderpacksDirectoryManager;
	private static final UpdateChecker updateChecker = new UpdateChecker();
	private static ShaderPack currentPack = null;
	private static String currentPackName = null;
	private static boolean fallback = false;
	private static boolean resetShaderPackOptions = false;
	private static final java.util.Map<String, String> shaderPackOptionQueue = new java.util.HashMap<>();

	public static IrisConfig getIrisConfig() {
		return irisConfig;
	}

	public static Path getShaderpacksDirectory() {
		// Default shaderpacks directory in run/shaderpacks (relative to working directory)
		return Paths.get("shaderpacks");
	}

	public static ShaderpacksDirectoryManager getShaderpacksDirectoryManager() {
		if (shaderpacksDirectoryManager == null) {
			shaderpacksDirectoryManager = new ShaderpacksDirectoryManager(getShaderpacksDirectory());
		}
		return shaderpacksDirectoryManager;
	}

	/**
	 * Gets the current shader pack if one is loaded.
	 * Matches Iris.getCurrentPack() pattern.
	 */
	public static Optional<ShaderPack> getCurrentPack() {
		return Optional.ofNullable(currentPack);
	}

	/**
	 * Gets the name of the currently loaded shader pack.
	 * Matches Iris.getCurrentPackName() pattern.
	 */
	public static String getCurrentPackName() {
		return currentPackName;
	}

	/**
	 * Returns whether the current pack is running in fallback mode.
	 * Matches Iris.isFallback() pattern.
	 */
	public static boolean isFallback() {
		return fallback;
	}

	/**
	 * Requests that shader pack options be reset on the next reload.
	 * Matches Iris.resetShaderPackOptionsOnNextReload() pattern.
	 */
	public static void resetShaderPackOptionsOnNextReload() {
		resetShaderPackOptions = true;
	}

	/**
	 * Gets the Iris version string.
	 */
	public static String getVersion() {
		return "1.8.0"; // Stub version
	}

	/**
	 * Gets the update checker instance.
	 */
	public static UpdateChecker getUpdateChecker() {
		return updateChecker;
	}

	/**
	 * Checks if a path is valid to show in the pack list.
	 * Shows directories and zip files.
	 */
	public static boolean isValidToShowPack(Path pack) {
		return Files.isDirectory(pack) || pack.toString().endsWith(".zip");
	}

	/**
	 * Checks if a path is a valid shader pack.
	 * A valid shader pack is either:
	 * - A directory containing a "shaders" subdirectory
	 * - A zip file (assumed to contain shader pack structure)
	 */
	public static boolean isValidShaderpack(Path pack) {
		if (Files.isDirectory(pack)) {
			// Don't allow the shaderpacks directory itself to be identified as a shader pack
			if (pack.equals(getShaderpacksDirectory())) {
				return false;
			}
			// Check if the directory contains a "shaders" folder
			return Files.exists(pack.resolve("shaders"));
		}
		// Accept zip files as shader packs
		return pack.toString().endsWith(".zip");
	}

	/**
	 * Stub method for getting shader pack option queue.
	 * Returns the option queue.
	 */
	public static java.util.Map<String, String> getShaderPackOptionQueue() {
		return shaderPackOptionQueue;
	}

	/**
	 * Clears the shader pack option queue.
	 */
	public static void clearShaderPackOptionQueue() {
		shaderPackOptionQueue.clear();
	}

	/**
	 * Queues shader pack options from properties.
	 */
	public static void queueShaderPackOptionsFromProperties(java.util.Properties properties) {
		// Stub - will be implemented when option system is added
	}

	/**
	 * Stub method for queueing shader pack options from a profile.
	 */
	public static void queueShaderPackOptionsFromProfile(Object profile) {
		// Stub - will be implemented when profile system is added
	}

	/**
	 * Returns whether shader pack options should be reset on next reload.
	 */
	public static boolean shouldResetShaderPackOptionsOnNextReload() {
		return resetShaderPackOptions;
	}

	/**
	 * Gets the pipeline manager from ShaderSystem.
	 * @return The pipeline manager, or null if not initialized
	 */
	public static Object getPipelineManager() {
		net.minecraft.client.renderer.shaders.core.ShaderSystem system = 
			net.minecraft.client.renderer.shaders.core.ShaderSystem.getInstance();
		return system.isInitialized() ? system.getPipelineManager() : null;
	}
	
	/**
	 * Reloads the shader pack configuration and triggers pipeline recreation.
	 * This is the main method called when applying shader changes.
	 * Matches IRIS's reload() pattern.
	 * 
	 * @throws IOException if config save fails
	 */
	public static void reload() throws IOException {
		logger.info("Reloading shader configuration");
		
		// Save the Iris config
		irisConfig.save();
		
		// Sync to ShaderSystem
		net.minecraft.client.renderer.shaders.core.ShaderSystem system = 
			net.minecraft.client.renderer.shaders.core.ShaderSystem.getInstance();
		
		var systemConfig = system.isInitialized() ? system.getConfig() : null;
		
		if (systemConfig != null) {
			// Sync Iris config to ShaderSystem config
			systemConfig.setShadersEnabled(irisConfig.areShadersEnabled());
			String packName = irisConfig.getShaderPackName().orElse(null);
			if (packName != null) {
				systemConfig.setSelectedPack(packName);
			}
			
			// Update currentPackName for consistency
			currentPackName = packName;
			
			// Reload pipelines
			if (system.getPipelineManager() != null) {
				logger.info("Reloading shader pipelines");
				system.getPipelineManager().reloadPipelines();
			}
			
			logger.info("Shader reload complete - enabled={}, pack={}", 
				irisConfig.areShadersEnabled(), packName);
		}
	}

	/**
	 * Sets debug mode.
	 */
	public static void setDebug(boolean debug) {
		// Stub - will be implemented when debug system is added
	}

	/**
	 * Stub for UpdateChecker
	 */
	public static class UpdateChecker {
		public Optional<String> getUpdateMessage() {
			return Optional.empty();
		}

		public Optional<java.net.URI> getUpdateLink() {
			return Optional.empty();
		}
	}

	/**
	 * Stub for IrisConfig - tracks shader pack configuration
	 */
	public static class IrisConfig {
		private boolean shadersEnabled = true;
		private String currentShaderPackName = null;
		private boolean debugOptionsEnabled = false;

		public boolean areShadersEnabled() {
			return shadersEnabled;
		}

		public void setShadersEnabled(boolean enabled) {
			this.shadersEnabled = enabled;
		}

		public java.util.Optional<String> getShaderPackName() {
			return java.util.Optional.ofNullable(currentShaderPackName);
		}

		public void setShaderPackName(String name) {
			this.currentShaderPackName = name;
		}

		public void setUnknown(boolean unknown) throws java.io.IOException {
			// Stub - will be implemented later
		}

		public boolean areDebugOptionsEnabled() {
			return debugOptionsEnabled;
		}

		public void save() throws java.io.IOException {
			// Stub - configuration saving will be implemented later
		}
	}

	/**
	 * Manages the shaderpacks directory - enumerating and copying shader packs.
	 * Based on Iris ShaderpackDirectoryManager.
	 */
	public static class ShaderpacksDirectoryManager {
		private final Path root;

		public ShaderpacksDirectoryManager(Path root) {
			this.root = root;
		}

		/**
		 * Remove section-sign based chat formatting from a String (used for sorting).
		 */
		private static String removeFormatting(String formatted) {
			char[] original = formatted.toCharArray();
			char[] cleaned = new char[original.length];
			int c = 0;

			for (int i = 0; i < original.length; i++) {
				// check if it's a section sign
				if (original[i] == '§') {
					// Skip the next character (format code) if it exists
					if (i + 1 < original.length) {
						i++;
					}
				} else {
					cleaned[c++] = original[i];
				}
			}

			return new String(cleaned, 0, c);
		}

		/**
		 * Enumerate all shader packs in the directory.
		 * Returns a sorted list of shader pack names (file/directory names).
		 */
		public List<String> enumerate() throws IOException {
			// Create the directory if it doesn't exist
			if (!Files.exists(root)) {
				Files.createDirectories(root);
				return new ArrayList<>();
			}

			if (!Files.isDirectory(root)) {
				logger.error("Shaderpacks directory exists but is not a directory: {}", root);
				return new ArrayList<>();
			}

			// Case-insensitive sorting for intuitive user experience
			// Also ignore chat formatting characters when sorting
			boolean debug = irisConfig.areDebugOptionsEnabled();

			Comparator<String> baseComparator = String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
			Comparator<Path> comparator = (a, b) -> {
				// In debug mode, show unzipped packs above zipped ones
				if (debug) {
					if (Files.isDirectory(a)) {
						if (!Files.isDirectory(b)) return -1;
					} else if (Files.isDirectory(b)) {
						if (!Files.isDirectory(a)) return 1;
					}
				}

				return baseComparator.compare(removeFormatting(a.getFileName().toString()), removeFormatting(b.getFileName().toString()));
			};

			try (Stream<Path> list = Files.list(root)) {
				return list.filter(Iris::isValidToShowPack)
					.sorted(comparator)
					.map(path -> path.getFileName().toString())
					.collect(Collectors.toList());
			}
		}

		public java.net.URI getDirectoryUri() {
			return root.toUri();
		}

		public void copyPackIntoDirectory(String fileName, Path pack) throws java.io.IOException {
			Path target = root.resolve(fileName);

			// Handle directories - need to copy entire directory tree
			if (Files.isDirectory(pack)) {
				// Create the target directory first
				Files.createDirectories(target);
				
				// Copy all subdirectories
				try (Stream<Path> stream = Files.walk(pack)) {
					for (Path p : stream.filter(Files::isDirectory).toList()) {
						Path folder = pack.relativize(p);
						Path targetFolder = target.resolve(folder);
						if (!Files.exists(targetFolder)) {
							Files.createDirectories(targetFolder);
						}
					}
				}

				// Copy all files
				try (Stream<Path> stream = Files.walk(pack)) {
					for (Path p : stream.filter(file -> !Files.isDirectory(file)).collect(Collectors.toSet())) {
						Path file = pack.relativize(p);
						Files.copy(p, target.resolve(file));
					}
				}
			} else {
				// Copy the pack file (zip) into the shaderpacks folder
				Files.copy(pack, target);
			}
		}
	}
}

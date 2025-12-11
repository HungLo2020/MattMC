package net.minecraft.client.renderer.shaders;

import net.minecraft.client.renderer.shaders.shaderpack.ShaderPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Stub for Iris main class - minimal implementation for GUI compilation.
 * Full implementation will be added when shader pack loading is implemented.
 */
public class Iris {
	public static final Logger logger = LoggerFactory.getLogger("Iris");

	private static final IrisConfig irisConfig = new IrisConfig();
	private static final ShaderpacksDirectoryManager shaderpacksDirectoryManager = new ShaderpacksDirectoryManager();
	private static ShaderPack currentPack = null;
	private static String currentPackName = null;
	private static boolean fallback = false;
	private static boolean resetShaderPackOptions = false;

	public static IrisConfig getIrisConfig() {
		return irisConfig;
	}

	public static Path getShaderpacksDirectory() {
		// Default shaderpacks directory in .minecraft/shaderpacks
		return Paths.get("shaderpacks");
	}

	public static ShaderpacksDirectoryManager getShaderpacksDirectoryManager() {
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
	 * Stub for IrisConfig - tracks shader pack configuration
	 */
	public static class IrisConfig {
		private boolean shadersEnabled = true;
		private String currentShaderPackName = null;

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
	}

	/**
	 * Stub for ShaderpacksDirectoryManager - manages shader pack enumeration
	 */
	public static class ShaderpacksDirectoryManager {
		public java.util.List<String> enumerate() {
			// Return empty list for now - will be implemented with actual pack loading
			return new java.util.ArrayList<>();
		}
	}
}

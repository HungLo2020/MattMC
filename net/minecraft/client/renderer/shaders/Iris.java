package net.minecraft.client.renderer.shaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Stub for Iris main class - minimal implementation for GUI compilation.
 * Full implementation will be added when shader pack loading is implemented.
 */
public class Iris {
	public static final Logger logger = LoggerFactory.getLogger("Iris");

	private static final IrisConfig irisConfig = new IrisConfig();
	private static final ShaderpacksDirectoryManager shaderpacksDirectoryManager = new ShaderpacksDirectoryManager();

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

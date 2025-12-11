package net.minecraft.client.renderer.shaders.api.v0;

/**
 * Stub class for IRIS API
 * Full implementation requires complete IRIS API system
 */
public class IrisApi {
	public static IrisApi getInstance() {
		return new IrisApi();
	}
	
	public boolean isShaderPackInUse() {
		return false;
	}
	
	public String getCurrentPackName() {
		return null;
	}

	public Config getConfig() {
		return new Config();
	}

	/**
	 * Stub Config class
	 */
	public static class Config {
		public void setShadersEnabledAndApply(boolean enabled) {
			// Stub - will be implemented with API system
		}
	}
}

package net.minecraft.client.renderer.shaders.platform;

/**
 * Stub class for platform helpers
 * Full implementation requires platform detection system
 */
public class IrisPlatformHelpers {
	private static final IrisPlatformHelpers INSTANCE = new IrisPlatformHelpers();

	public static IrisPlatformHelpers getInstance() {
		return INSTANCE;
	}

	public boolean isDevelopmentEnvironment() {
		return false;
	}
	
	public String getPlatformName() {
		return "Unknown";
	}
}

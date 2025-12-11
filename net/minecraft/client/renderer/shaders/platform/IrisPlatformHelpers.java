package net.minecraft.client.renderer.shaders.platform;

/**
 * Stub class for platform helpers
 * Full implementation requires platform detection system
 */
public class IrisPlatformHelpers {
	public static boolean isDevelopmentEnvironment() {
		return false;
	}
	
	public static String getPlatformName() {
		return "Unknown";
	}
}

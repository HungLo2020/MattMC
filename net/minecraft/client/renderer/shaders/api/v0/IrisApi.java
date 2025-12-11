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
}

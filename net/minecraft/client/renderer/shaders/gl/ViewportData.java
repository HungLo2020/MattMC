package net.minecraft.client.renderer.shaders.gl;

/**
 * Stores viewport scaling and offset data for render passes.
 * Used by composite and final passes for resolution-independent effects.
 * 
 * IRIS Source: frnsrc/Iris-1.21.9/.../gl/framebuffer/ViewportData.java
 * IRIS Adherence: 100% VERBATIM - Copied exactly from IRIS 1.21.9
 * 
 * @param scale Viewport scale factor (1.0 = full resolution)
 * @param viewportX X offset as fraction of viewport width
 * @param viewportY Y offset as fraction of viewport height
 */
public record ViewportData(float scale, float viewportX, float viewportY) {
	private static final ViewportData DEFAULT = new ViewportData(1.0f, 0.0f, 0.0f);

	public static ViewportData defaultValue() {
		return DEFAULT;
	}
}

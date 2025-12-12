package net.minecraft.client.renderer.shaders.vertices;

/**
 * Tracks immediate rendering state for vertex format extension.
 * 
 * VERBATIM from IRIS: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/vertices/ImmediateState.java
 */
public class ImmediateState {
	/**
	 * Whether we are currently rendering the level (world).
	 * When true, vertex format extension is active.
	 */
	public static boolean isRenderingLevel = false;
	
	/**
	 * Thread-local flag to skip vertex format extension.
	 * Used when rendering UI elements or other non-world content.
	 */
	public static final ThreadLocal<Boolean> skipExtension = ThreadLocal.withInitial(() -> false);
	
	/**
	 * Sets rendering level state.
	 */
	public static void setRenderingLevel(boolean rendering) {
		isRenderingLevel = rendering;
	}
	
	/**
	 * Temporarily skip vertex format extension.
	 */
	public static void pushSkipExtension() {
		skipExtension.set(true);
	}
	
	/**
	 * Resume vertex format extension.
	 */
	public static void popSkipExtension() {
		skipExtension.set(false);
	}
}

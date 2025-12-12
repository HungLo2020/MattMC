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
	 * Thread-local flag indicating chunk compilation is in progress.
	 * Used to enable vertex format extension on worker threads during chunk compilation.
	 * This allows terrain chunks to use extended vertex formats when shaders are active.
	 * 
	 * NOTE: IRIS doesn't have this - it uses Sodium for terrain. We need this for vanilla
	 * chunk compilation.
	 */
	public static final ThreadLocal<Boolean> isCompilingChunks = ThreadLocal.withInitial(() -> false);
	
	/**
	 * Whether to render with extended vertex format.
	 * VERBATIM from IRIS: Controls whether RenderPipeline.getVertexFormat() returns extended formats.
	 * 
	 * Default is TRUE (always use extended format when conditions are met).
	 * Only set to false temporarily during non-level batch rendering.
	 */
	public static boolean renderWithExtendedVertexFormat = true;
	
	/**
	 * Sets rendering level state.
	 */
	public static void setRenderingLevel(boolean rendering) {
		isRenderingLevel = rendering;
	}
	
	/**
	 * Sets chunk compilation state for current thread.
	 */
	public static void setCompilingChunks(boolean compiling) {
		isCompilingChunks.set(compiling);
	}
	
	/**
	 * Checks if vertex format extension should be applied.
	 * Returns true if either:
	 * 1. Rendering level on render thread (isRenderingLevel)
	 * 2. Compiling chunks on worker thread (isCompilingChunks)
	 */
	public static boolean shouldExtendFormat() {
		return isRenderingLevel || isCompilingChunks.get();
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

package net.minecraft.client.renderer.shaders;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.renderer.shaders.pipeline.PipelineManager;
import net.minecraft.client.renderer.shaders.pipeline.ShaderPackPipeline;
import net.minecraft.client.renderer.shaders.pipeline.WorldRenderingPipeline;

/**
 * Main entry point for the Iris shader system.
 * Manages shader pack loading, activation, and provides access to the current pipeline.
 */
public class IrisShaders {
	private static final ShaderPackOptionQueue optionQueue = new ShaderPackOptionQueue();
	private static boolean shadersEnabled = true;

	public static ShaderPackOptionQueue getShaderPackOptionQueue() {
		return optionQueue;
	}

	/**
	 * Checks if shaders are currently enabled.
	 * @return true if shaders are enabled
	 */
	public static boolean isEnabled() {
		return shadersEnabled && PipelineManager.getInstance().hasActiveShaderPipeline();
	}

	/**
	 * Sets whether shaders are enabled.
	 * @param enabled true to enable shaders
	 */
	public static void setEnabled(boolean enabled) {
		shadersEnabled = enabled;
	}

	/**
	 * Gets the active shader pack pipeline.
	 * @return The active ShaderPackPipeline, or null if none is active
	 */
	public static ShaderPackPipeline getActivePipeline() {
		WorldRenderingPipeline pipeline = PipelineManager.getInstance().getActivePipeline();
		if (pipeline instanceof ShaderPackPipeline) {
			return (ShaderPackPipeline) pipeline;
		}
		return null;
	}

	// Stub for shader pack option queue
	public static class ShaderPackOptionQueue {
		private final Map<String, String> pendingChanges = new HashMap<>();

		public void put(String optionName, String value) {
			this.pendingChanges.put(optionName, value);
		}

		public Map<String, String> getPendingChanges() {
			return new HashMap<>(this.pendingChanges);
		}

		public void clear() {
			this.pendingChanges.clear();
		}
	}
}

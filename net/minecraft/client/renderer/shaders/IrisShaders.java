package net.minecraft.client.renderer.shaders;

import java.util.HashMap;
import java.util.Map;

// Stub implementation for IrisShaders main class
// Full implementation will be added when shader pack system is implemented
public class IrisShaders {
	private static final ShaderPackOptionQueue optionQueue = new ShaderPackOptionQueue();

	public static ShaderPackOptionQueue getShaderPackOptionQueue() {
		return optionQueue;
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

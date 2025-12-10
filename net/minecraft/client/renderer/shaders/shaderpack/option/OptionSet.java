package net.minecraft.client.renderer.shaders.shaderpack.option;

import java.util.HashMap;
import java.util.Map;

// Stub implementation for OptionSet
// Full implementation will be added when shader pack option parsing is implemented
public class OptionSet {
	private final Map<String, BooleanOptionInfo> booleanOptions;
	private final Map<String, StringOptionInfo> stringOptions;

	public OptionSet() {
		this.booleanOptions = new HashMap<>();
		this.stringOptions = new HashMap<>();
	}

	public Map<String, BooleanOptionInfo> getBooleanOptions() {
		return this.booleanOptions;
	}

	public Map<String, StringOptionInfo> getStringOptions() {
		return this.stringOptions;
	}

	// Nested info classes
	public static class BooleanOptionInfo {
		private final BooleanOption option;

		public BooleanOptionInfo(BooleanOption option) {
			this.option = option;
		}

		public BooleanOption getOption() {
			return this.option;
		}
	}

	public static class StringOptionInfo {
		private final StringOption option;

		public StringOptionInfo(StringOption option) {
			this.option = option;
		}

		public StringOption getOption() {
			return this.option;
		}
	}
}

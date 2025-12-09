package net.minecraft.client.renderer.shaders.option;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A set of discovered shader options.
 * Based on IRIS 1.21.9 OptionSet pattern.
 */
public class OptionSet {
	private final Map<String, BooleanOption> booleanOptions;
	private final Map<String, StringOption> stringOptions;

	private OptionSet(Builder builder) {
		this.booleanOptions = Collections.unmodifiableMap(new HashMap<>(builder.booleanOptions));
		this.stringOptions = Collections.unmodifiableMap(new HashMap<>(builder.stringOptions));
	}

	public static Builder builder() {
		return new Builder();
	}

	public Map<String, BooleanOption> getBooleanOptions() {
		return this.booleanOptions;
	}

	public Map<String, StringOption> getStringOptions() {
		return this.stringOptions;
	}

	public boolean isBooleanOption(String name) {
		return booleanOptions.containsKey(name);
	}

	public static class Builder {
		private final Map<String, BooleanOption> booleanOptions;
		private final Map<String, StringOption> stringOptions;

		public Builder() {
			this.booleanOptions = new HashMap<>();
			this.stringOptions = new HashMap<>();
		}

		public Builder addBooleanOption(BooleanOption option) {
			booleanOptions.put(option.getName(), option);
			return this;
		}

		public Builder addStringOption(StringOption option) {
			stringOptions.put(option.getName(), option);
			return this;
		}

		public OptionSet build() {
			return new OptionSet(this);
		}
	}
}

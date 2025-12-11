package net.minecraft.client.renderer.shaders.shaderpack.option;

import java.util.HashMap;
import java.util.Map;

// Stub implementation for OptionValues
// Full implementation will be added when shader pack option system is implemented
public class OptionValues {
	private final Map<String, String> values;
	private final OptionSet optionSet;

	public OptionValues(OptionSet optionSet) {
		this.values = new HashMap<>();
		this.optionSet = optionSet;
	}

	public boolean getBooleanValueOrDefault(String name) {
		String value = values.get(name);
		if (value != null) {
			return Boolean.parseBoolean(value);
		}
		// Return default from option set if available
		return false;
	}

	public String getStringValueOrDefault(String name) {
		String value = values.get(name);
		if (value != null) {
			return value;
		}
		// Return default from option set if available
		return "";
	}

	public OptionSet getOptionSet() {
		return this.optionSet;
	}

	public void put(String name, String value) {
		this.values.put(name, value);
	}
}

package net.minecraft.client.renderer.shaders.shaderpack.option;

import java.util.ArrayList;
import java.util.List;

// Stub implementation for StringOption  
// Full implementation will be added when shader pack option parsing is implemented
public class StringOption {
	private final String name;
	private final String defaultValue;
	private final List<String> allowedValues;

	public StringOption(String name, String defaultValue, List<String> allowedValues) {
		this.name = name;
		this.defaultValue = defaultValue;
		this.allowedValues = new ArrayList<>(allowedValues);
	}

	public String getName() {
		return this.name;
	}

	public String getDefaultValue() {
		return this.defaultValue;
	}

	public List<String> getAllowedValues() {
		return this.allowedValues;
	}
}

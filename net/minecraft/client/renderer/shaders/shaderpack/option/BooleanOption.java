package net.minecraft.client.renderer.shaders.shaderpack.option;

// Stub implementation for BooleanOption
// Full implementation will be added when shader pack option parsing is implemented
public class BooleanOption {
	private final String name;
	private final boolean defaultValue;

	public BooleanOption(String name, boolean defaultValue) {
		this.name = name;
		this.defaultValue = defaultValue;
	}

	public String getName() {
		return this.name;
	}

	public boolean getDefaultValue() {
		return this.defaultValue;
	}
}

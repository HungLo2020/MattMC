package net.minecraft.client.renderer.shaders.shaderpack;

import net.minecraft.client.renderer.shaders.shaderpack.option.menu.OptionMenuContainer;

/**
 * Stub class for ShaderPack
 * Full implementation requires shader pack parsing system
 */
public class ShaderPack {
	private final String name;
	
	public ShaderPack(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public OptionMenuContainer getMenuContainer() {
		// Stub - returns empty container
		return new OptionMenuContainer(getName());
	}
}

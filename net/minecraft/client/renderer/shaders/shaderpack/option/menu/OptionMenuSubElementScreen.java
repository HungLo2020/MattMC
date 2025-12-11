package net.minecraft.client.renderer.shaders.shaderpack.option.menu;

/**
 * Stub class for sub option menu screen element
 * Full implementation requires shader pack option parsing system
 * Matches Iris OptionMenuSubElementScreen pattern with screenId field
 */
public class OptionMenuSubElementScreen implements OptionMenuElementScreen {
	public final String screenId;
	
	public OptionMenuSubElementScreen(String screenId) {
		this.screenId = screenId;
	}
}

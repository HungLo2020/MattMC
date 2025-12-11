package net.minecraft.client.renderer.shaders.shaderpack.option.menu;

import java.util.HashMap;
import java.util.Map;

/**
 * Placeholder class for Option Menu Container
 * Will be fully implemented when shader pack option system is complete
 */
public class OptionMenuContainer {
	private final String name;
	public final OptionMenuElementScreen mainScreen;
	public final Map<String, OptionMenuElementScreen> subScreens = new HashMap<>();
	
	public OptionMenuContainer(String name) {
		this.name = name;
		// Create a stub main screen for now
		this.mainScreen = new OptionMenuElementScreen() {
			// Stub implementation
		};
	}
	
	public String getName() {
		return name;
	}
}

package net.minecraft.client.renderer.shaders.shaderpack.option.menu;

import java.util.ArrayList;
import java.util.List;

/**
 * Stub implementation for option menu element screens
 * Full implementation requires shader pack option parsing system
 * Matches Iris OptionMenuElementScreen pattern
 */
public interface OptionMenuElementScreen extends OptionMenuElement {
	/**
	 * Gets the list of elements in this screen.
	 * Default implementation returns empty list.
	 */
	default List<OptionMenuElement> getElements() {
		return new ArrayList<>();
	}
	
	/**
	 * Gets the column count for this screen.
	 * Default implementation returns 2 columns.
	 */
	default int getColumnCount() {
		return 2;
	}
}

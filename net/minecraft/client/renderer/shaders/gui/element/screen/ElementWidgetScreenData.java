package net.minecraft.client.renderer.shaders.gui.element.screen;

import net.minecraft.network.chat.Component;

/**
 * Data for element widget screens
 * <p>
 * IRIS VERBATIM - Copied from Iris 1.21.9
 */
public record ElementWidgetScreenData(Component heading, boolean backButton) {
	public static final ElementWidgetScreenData EMPTY = new ElementWidgetScreenData(Component.empty(), true);
}

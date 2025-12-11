package net.minecraft.client.renderer.shaders.gui.option;

import net.minecraft.client.renderer.shaders.Iris;
import net.minecraft.client.renderer.shaders.pathways.colorspace.ColorSpace;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.io.IOException;

/**
 * Stub for Iris video settings.
 * Full implementation will be added when pipeline system is complete.
 */
public class IrisVideoSettings {
	private static final Tooltip DISABLED_TOOLTIP = Tooltip.create(Component.translatable("options.iris.shadowDistance.disabled"));
	private static final Tooltip ENABLED_TOOLTIP = Tooltip.create(Component.translatable("options.iris.shadowDistance.enabled"));
	public static int shadowDistance = 32;
	public static ColorSpace colorSpace = ColorSpace.SRGB;
	
	// Stub option instance - will be properly implemented when pipeline is ready
	public static final OptionInstance<Integer> RENDER_DISTANCE = new OptionInstance<>(
		"options.iris.shadowDistance",
		OptionInstance.noTooltip(),
		(component, value) -> Component.translatable("options.generic_value",
			Component.translatable("options.iris.shadowDistance"),
			Component.translatable("options.chunks", value)),
		new OptionInstance.IntRange(0, 32),
		shadowDistance,
		integer -> {
			shadowDistance = integer;
			try {
				Iris.getIrisConfig().save();
			} catch (IOException e) {
				Iris.logger.error("Failed to save config!", e);
			}
		}
	);

	public static int getOverriddenShadowDistance(int base) {
		// Stub - will be implemented with pipeline system
		return base;
	}

	public static boolean isShadowDistanceSliderEnabled() {
		// Stub - will be implemented with pipeline system
		return true;
	}
}

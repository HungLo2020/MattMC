package net.irisshaders.iris.gui.option;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pathways.colorspace.ColorSpace;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.io.IOException;

public class IrisVideoSettings {
	private static final Tooltip ENABLED_TOOLTIP = Tooltip.create(Component.translatable("options.iris.shadowDistance.enabled"));
	public static int shadowDistance = 32;
	public static ColorSpace colorSpace = ColorSpace.SRGB;
	public static final OptionInstance<Integer> RENDER_DISTANCE = new ShadowDistanceOption<>("options.iris.shadowDistance",
		mc -> ENABLED_TOOLTIP,
		(arg, d) -> {
			if (d <= 0.0) {
				return Component.translatable("options.generic_value", Component.translatable("options.iris.shadowDistance"), "0 (disabled)");
			} else {
				return Component.translatable("options.generic_value",
					Component.translatable("options.iris.shadowDistance"),
					Component.translatable("options.chunks", d));
			}
		},
		new OptionInstance.IntRange(0, 32),
		getOverriddenShadowDistance(shadowDistance),
		integer -> {
			shadowDistance = integer;
			try {
				Iris.getIrisConfig().save();
			} catch (IOException e) {
				Iris.logger.fatal("Failed to save config!", e);
			}
		});

	public static int getOverriddenShadowDistance(int base) {
		return base;
	}

	public static boolean isShadowDistanceSliderEnabled() {
		return true;
	}
}

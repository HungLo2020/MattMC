package net.minecraft.client.gui.components;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Options;
import net.minecraft.network.chat.CommonComponents;

@Environment(EnvType.CLIENT)
public abstract class AbstractOptionSliderButton extends AbstractSliderButton {
	protected final Options options;

	protected AbstractOptionSliderButton(Options options, int i, int j, int k, int l, double d) {
		super(i, j, k, l, CommonComponents.EMPTY, d);
		this.options = options;
	}
}

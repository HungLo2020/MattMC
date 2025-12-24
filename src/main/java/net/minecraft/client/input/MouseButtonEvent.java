package net.minecraft.client.input;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public record MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo) implements InputWithModifiers {
	@Override
	public int input() {
		return this.button();
	}

	public int button() {
		return this.buttonInfo().button();
	}

	@Override
	public int modifiers() {
		return this.buttonInfo().modifiers();
	}
}

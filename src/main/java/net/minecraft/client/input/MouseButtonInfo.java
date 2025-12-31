package net.minecraft.client.input;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public record MouseButtonInfo(int button, int modifiers) implements InputWithModifiers {
	@Override
	public int input() {
		return this.button;
	}
}

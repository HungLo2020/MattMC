package net.minecraft.client.input;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record MouseButtonInfo(int button, int modifiers) implements InputWithModifiers {
	@Override
	public int input() {
		return this.button;
	}
}

package net.minecraft.client.input;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record KeyEvent(int key, int scancode, int modifiers) implements InputWithModifiers {
	@Override
	public int input() {
		return this.key;
	}
}

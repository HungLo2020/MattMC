package net.minecraft.client;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public enum InputType {
	NONE,
	MOUSE,
	KEYBOARD_ARROW,
	KEYBOARD_TAB;

	public boolean isMouse() {
		return this == MOUSE;
	}

	public boolean isKeyboard() {
		return this == KEYBOARD_ARROW || this == KEYBOARD_TAB;
	}
}

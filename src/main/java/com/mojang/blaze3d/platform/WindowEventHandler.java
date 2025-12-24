package com.mojang.blaze3d.platform;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface WindowEventHandler {
	void setWindowActive(boolean bl);

	void resizeDisplay();

	void cursorEntered();
}

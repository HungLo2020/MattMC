package net.minecraft.client.gui.components;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public enum Whence {
	ABSOLUTE,
	RELATIVE,
	END;
}

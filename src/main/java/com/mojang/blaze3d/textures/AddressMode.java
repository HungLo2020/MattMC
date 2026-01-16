package com.mojang.blaze3d.textures;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public enum AddressMode {
	REPEAT,
	CLAMP_TO_EDGE
}

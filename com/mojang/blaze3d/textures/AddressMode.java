package com.mojang.blaze3d.textures;

import com.mojang.blaze3d.DontObfuscate;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public enum AddressMode {
	REPEAT,
	CLAMP_TO_EDGE;
}

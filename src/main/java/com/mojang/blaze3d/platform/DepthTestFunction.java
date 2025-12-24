package com.mojang.blaze3d.platform;

import com.mojang.blaze3d.DontObfuscate;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public enum DepthTestFunction {
	NO_DEPTH_TEST,
	EQUAL_DEPTH_TEST,
	LEQUAL_DEPTH_TEST,
	LESS_DEPTH_TEST,
	GREATER_DEPTH_TEST;
}

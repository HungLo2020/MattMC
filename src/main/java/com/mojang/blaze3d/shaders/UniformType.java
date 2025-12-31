package com.mojang.blaze3d.shaders;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public enum UniformType {
	UNIFORM_BUFFER("ubo"),
	TEXEL_BUFFER("utb");

	final String name;

	private UniformType(final String string2) {
		this.name = string2;
	}
}

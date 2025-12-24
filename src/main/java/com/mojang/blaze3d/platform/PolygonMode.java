package com.mojang.blaze3d.platform;

import com.mojang.blaze3d.DontObfuscate;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public enum PolygonMode {
	FILL,
	WIREFRAME;
}

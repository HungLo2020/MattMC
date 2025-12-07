package com.mojang.blaze3d;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class GpuOutOfMemoryException extends RuntimeException {
	public GpuOutOfMemoryException(String string) {
		super(string);
	}
}

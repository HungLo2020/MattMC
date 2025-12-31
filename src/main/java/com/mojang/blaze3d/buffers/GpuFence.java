package com.mojang.blaze3d.buffers;

import com.mojang.blaze3d.DontObfuscate;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public interface GpuFence extends AutoCloseable {
	void close();

	boolean awaitCompletion(long l);
}

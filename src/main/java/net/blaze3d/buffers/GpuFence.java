package net.blaze3d.buffers;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface GpuFence extends AutoCloseable {
	void close();

	boolean awaitCompletion(long l);
}

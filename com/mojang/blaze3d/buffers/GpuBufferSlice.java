package com.mojang.blaze3d.buffers;

import com.mojang.blaze3d.DontObfuscate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public record GpuBufferSlice(GpuBuffer buffer, int offset, int length) {
	public GpuBufferSlice slice(int i, int j) {
		if (i >= 0 && j >= 0 && i + j < this.length) {
			return new GpuBufferSlice(this.buffer, this.offset + i, j);
		} else {
			throw new IllegalArgumentException("Offset of " + i + " and length " + j + " would put new slice outside existing slice's range (of " + i + "," + j + ")");
		}
	}
}

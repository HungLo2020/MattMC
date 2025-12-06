package com.mojang.blaze3d.buffers;

import com.mojang.blaze3d.DontObfuscate;
import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public abstract class GpuBuffer implements AutoCloseable {
	public static final int USAGE_MAP_READ = 1;
	public static final int USAGE_MAP_WRITE = 2;
	public static final int USAGE_HINT_CLIENT_STORAGE = 4;
	public static final int USAGE_COPY_DST = 8;
	public static final int USAGE_COPY_SRC = 16;
	public static final int USAGE_VERTEX = 32;
	public static final int USAGE_INDEX = 64;
	public static final int USAGE_UNIFORM = 128;
	public static final int USAGE_UNIFORM_TEXEL_BUFFER = 256;
	private final int usage;
	private final int size;

	public GpuBuffer(int i, int j) {
		this.size = j;
		this.usage = i;
	}

	public int size() {
		return this.size;
	}

	public int usage() {
		return this.usage;
	}

	public abstract boolean isClosed();

	public abstract void close();

	public GpuBufferSlice slice(int i, int j) {
		if (i >= 0 && j >= 0 && i + j <= this.size) {
			return new GpuBufferSlice(this, i, j);
		} else {
			throw new IllegalArgumentException("Offset of " + i + " and length " + j + " would put new slice outside buffer's range (of 0," + j + ")");
		}
	}

	public GpuBufferSlice slice() {
		return new GpuBufferSlice(this, 0, this.size);
	}

	@Environment(EnvType.CLIENT)
	@DontObfuscate
	public interface MappedView extends AutoCloseable {
		ByteBuffer data();

		void close();
	}
}

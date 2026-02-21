package net.blaze3d.buffers;

import java.nio.ByteBuffer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public abstract class GpuBuffer implements AutoCloseable, net.vulkanic.resources.VulkanicBuffer {
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

	// VulkanicBuffer bridge methods — allow GpuBuffer to be used anywhere a
	// VulkanicBuffer is expected without a cast.

	/** Returns the size of the buffer in bytes. */
	@Override
	public int getSize() {
		return size();
	}

	/** Returns the usage flags bitmask. */
	@Override
	public int getUsage() {
		return usage();
	}

	/**
	 * Returns the backend-native handle for this buffer.
	 * <ul>
	 *   <li>OpenGL: GL buffer object name</li>
	 *   <li>Vulkan: VkBuffer handle</li>
	 * </ul>
	 */
	@Override
	public abstract long getNativeHandle();

	@Override
	public abstract boolean isClosed();

	@Override
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
	public interface MappedView extends net.vulkanic.resources.VulkanicMapView {
		ByteBuffer data();

		void close();
	}
}

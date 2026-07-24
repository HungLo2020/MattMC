package net.blaze3d.opengl;

import net.blaze3d.buffers.GpuBuffer;
import net.minecraft.util.profiling.TracyCompat.MemoryPool;
import net.minecraft.util.profiling.TracyCompat;
import java.nio.ByteBuffer;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class GlBuffer extends GpuBuffer {
	protected static final MemoryPool MEMORY_POOl = TracyCompat.createMemoryPool("GPU Buffers");
	protected boolean closed;
	@Nullable
	protected final Supplier<String> label;
	private final DirectStateAccess dsa;
	protected final int handle;
	@Nullable
	protected ByteBuffer persistentBuffer;

	protected GlBuffer(@Nullable Supplier<String> supplier, DirectStateAccess directStateAccess, int i, int j, int k, @Nullable ByteBuffer byteBuffer) {
		super(i, j);
		this.label = supplier;
		this.dsa = directStateAccess;
		this.handle = k;
		this.persistentBuffer = byteBuffer;
		MEMORY_POOl.malloc(k, j);
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	public int getHandle() {
		return this.handle;
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			if (this.persistentBuffer != null) {
				this.dsa.unmapBuffer(this.handle, this.usage());
				this.persistentBuffer = null;
			}

			net.irisshaders.iris.gl.IrisRenderSystem.decrementTrackedBuffers();
			VulkanicAPI.deleteBuffer(VulkanicAPI.getCommandContext(), this.handle);
			MEMORY_POOl.free(this.handle);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class GlMappedView implements GpuBuffer.MappedView {
		private final Runnable unmap;
		private final GlBuffer buffer;
		private final ByteBuffer data;
		private boolean closed;

		protected GlMappedView(Runnable runnable, GlBuffer glBuffer, ByteBuffer byteBuffer) {
			this.unmap = runnable;
			this.buffer = glBuffer;
			this.data = byteBuffer;
		}

		@Override
		public ByteBuffer data() {
			return this.data;
		}

		@Override
		public void close() {
			if (!this.closed) {
				this.closed = true;
				this.unmap.run();
			}
		}
	}
}

package net.minecraft.client.renderer;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.buffers.Std140Builder;
import net.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PerspectiveProjectionMatrixBuffer implements AutoCloseable {
	@Nullable
	private GpuBuffer buffer;
	@Nullable
	private final GpuBufferSlice bufferSlice;
	private final String label;

	public PerspectiveProjectionMatrixBuffer(String string) {
		this.label = "perspective:" + string;
		this.buffer = null;
		this.bufferSlice = null;
		return;
	}

	public void close() {
		if (this.buffer != null) {
			this.buffer.close();
			this.buffer = null;
		}
	}

	public void ensureRustSemanticRoute() {
		this.close();
	}
}

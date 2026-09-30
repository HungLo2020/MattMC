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
public class CachedPerspectiveProjectionMatrixBuffer implements AutoCloseable {
	@Nullable
	private GpuBuffer buffer;
	@Nullable
	private final GpuBufferSlice bufferSlice;
	private final String label;
	private final float zNear;
	private final float zFar;
	private int width;
	private int height;
	private float fov;

	public CachedPerspectiveProjectionMatrixBuffer(String string, float f, float g) {
		this.label = "cached-perspective:" + string;
		this.zNear = f;
		this.zFar = g;
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

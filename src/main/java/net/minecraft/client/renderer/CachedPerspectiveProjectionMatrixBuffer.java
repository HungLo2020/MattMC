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
	private final GpuBuffer buffer;
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
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			this.buffer = null;
			this.bufferSlice = null;
			return;
		}
		this.buffer = net.vulkanic.VulkanicAPI.createBuffer(() -> "Projection matrix UBO " + string, 136, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
		this.bufferSlice = this.buffer.slice(0, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
		net.vulkanic.VulkanicAPI.labelProjectionMatrix(this.bufferSlice, this.label);
	}

	public GpuBufferSlice getBuffer(int i, int j, float f) {
		if (this.buffer == null || this.bufferSlice == null) {
			throw new IllegalStateException("Java cached projection UBO rendering is unavailable while Rust owns whole-frame presentation");
		}
		if (this.width != i || this.height != j || this.fov != f) {
			Matrix4f matrix4f = this.createProjectionMatrix(i, j, f);

			try (MemoryStack memoryStack = MemoryStack.stackPush()) {
				ByteBuffer byteBuffer = Std140Builder.onStack(memoryStack, RenderSystem.PROJECTION_MATRIX_UBO_SIZE).putMat4f(matrix4f).get();
				net.vulkanic.VulkanicAPI.createCommandEncoder().writeToBuffer(this.buffer.slice(), byteBuffer);
			}

			this.width = i;
			this.height = j;
			this.fov = f;
		}

		net.vulkanic.VulkanicAPI.labelProjectionMatrix(this.bufferSlice, this.label);
		return this.bufferSlice;
	}

	private Matrix4f createProjectionMatrix(int i, int j, float f) {
		return new Matrix4f().perspective(f * (float) (Math.PI / 180.0), (float)i / j, this.zNear, this.zFar);
	}

	public void close() {
		if (this.buffer != null) {
			this.buffer.close();
		}
	}
}

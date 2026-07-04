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

@Environment(EnvType.CLIENT)
public class PerspectiveProjectionMatrixBuffer implements AutoCloseable {
	private final GpuBuffer buffer;
	private final GpuBufferSlice bufferSlice;
	private final String label;

	public PerspectiveProjectionMatrixBuffer(String string) {
		this.label = "perspective:" + string;
		this.buffer = net.vulkanic.VulkanicAPI.createBuffer(() -> "Projection matrix UBO " + string, 136, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
		this.bufferSlice = this.buffer.slice(0, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
		net.vulkanic.VulkanicAPI.labelProjectionMatrix(this.bufferSlice, this.label);
	}

	public GpuBufferSlice getBuffer(Matrix4f matrix4f) {
		try (MemoryStack memoryStack = MemoryStack.stackPush()) {
			ByteBuffer byteBuffer = Std140Builder.onStack(memoryStack, RenderSystem.PROJECTION_MATRIX_UBO_SIZE).putMat4f(matrix4f).get();
			net.vulkanic.VulkanicAPI.createCommandEncoder().writeToBuffer(this.buffer.slice(), byteBuffer);
		}

		net.vulkanic.VulkanicAPI.labelProjectionMatrix(this.bufferSlice, this.label);
		return this.bufferSlice;
	}

	public void close() {
		this.buffer.close();
	}
}

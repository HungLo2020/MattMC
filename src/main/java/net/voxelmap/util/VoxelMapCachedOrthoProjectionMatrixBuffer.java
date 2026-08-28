package net.voxelmap.util;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.buffers.Std140Builder;
import net.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

/**
 * See {@link net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer}
 */
public class VoxelMapCachedOrthoProjectionMatrixBuffer implements AutoCloseable {
    private final GpuBuffer buffer;
    private final GpuBufferSlice bufferSlice;
    private final String label;
    private final boolean useZeroToOneDepthWhenVulkan;

    public VoxelMapCachedOrthoProjectionMatrixBuffer(String string, float left, float right, float bottom, float top, float zNear, float zFar) {
        this(string, left, right, bottom, top, zNear, zFar, false);
    }

    public VoxelMapCachedOrthoProjectionMatrixBuffer(String string, float left, float right, float bottom, float top, float zNear, float zFar, boolean useZeroToOneDepthWhenVulkan) {
        if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
            || net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
            throw new IllegalStateException("Java VoxelMap projection UBO rendering is unavailable while Rust owns whole-frame presentation");
        }
        this.label = "voxelmap-ortho:" + string;
        this.buffer = net.vulkanic.VulkanicAPI.createBuffer(() -> "Projection matrix UBO " + string, GpuBuffer.USAGE_UNIFORM + GpuBuffer.USAGE_COPY_DST, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
        this.bufferSlice = this.buffer.slice(0, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
        net.vulkanic.VulkanicAPI.labelProjectionMatrix(this.bufferSlice, this.label);
        this.useZeroToOneDepthWhenVulkan = useZeroToOneDepthWhenVulkan;

        Matrix4f matrix4f = new Matrix4f().setOrtho(
                left,
                right,
                bottom,
                top,
                zNear,
                zFar,
                this.useZeroToOneDepthWhenVulkan && net.vulkanic.VulkanicAPI.isVulkanBackendSelected());

        try (MemoryStack memoryStack = MemoryStack.stackPush()) {
            ByteBuffer byteBuffer = Std140Builder.onStack(memoryStack, RenderSystem.PROJECTION_MATRIX_UBO_SIZE)
                    .putMat4f(matrix4f).get();
            net.vulkanic.VulkanicAPI.createCommandEncoder().writeToBuffer(this.buffer.slice(), byteBuffer);
        }
    }

    public GpuBufferSlice getBuffer() {
        net.vulkanic.VulkanicAPI.labelProjectionMatrix(this.bufferSlice, this.label);
        return this.bufferSlice;
    }

    @Override
    public void close() {
        this.buffer.close();
    }
}

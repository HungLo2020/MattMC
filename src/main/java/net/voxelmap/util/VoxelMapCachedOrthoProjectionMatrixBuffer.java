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
        throw new IllegalStateException("Java VoxelMap projection UBO rendering is unavailable while Rust owns whole-frame presentation");
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

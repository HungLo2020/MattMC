package net.vulkanic.backends.opengl;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderSystem;
import net.vulkanic.VulkanicBuffer;
import java.nio.ByteBuffer;

/**
 * OpenGL implementation of VulkanicBuffer.
 * 
 * Wraps Blaze3D's GpuBuffer to provide the Vulkanic buffer interface.
 */
public class OpenGLBuffer implements VulkanicBuffer {
    private final GpuBuffer gpuBuffer;
    private final int sizeInBytes;
    
    /**
     * Creates a new OpenGL buffer with the specified size.
     * 
     * @param sizeInBytes the size of the buffer in bytes
     */
    public OpenGLBuffer(int sizeInBytes) {
        this.sizeInBytes = sizeInBytes;
        
        // Create GPU buffer using Blaze3D device
        // Usage flags: VERTEX | MAP_WRITE for general-purpose vertex/uniform data
        int usage = GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_DST;
        this.gpuBuffer = RenderSystem.getDevice().createBuffer(
            () -> "VulkanicBuffer", 
            usage, 
            sizeInBytes
        );
    }
    
    @Override
    public void upload(ByteBuffer data) {
        if (data.remaining() > sizeInBytes) {
            throw new IllegalArgumentException("Data size (" + data.remaining() + ") exceeds buffer size (" + sizeInBytes + ")");
        }
        
        // Use command encoder to write data to the buffer
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBufferSlice slice = gpuBuffer.slice();
        encoder.writeToBuffer(slice, data);
    }
    
    @Override
    public void uploadSubData(int offset, ByteBuffer data) {
        if (offset < 0 || offset >= sizeInBytes) {
            throw new IllegalArgumentException("Offset " + offset + " is out of bounds for buffer of size " + sizeInBytes);
        }
        if (offset + data.remaining() > sizeInBytes) {
            throw new IllegalArgumentException("Data at offset " + offset + " exceeds buffer size");
        }
        
        // Use command encoder to write data to a slice of the buffer
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBufferSlice slice = gpuBuffer.slice(offset, data.remaining());
        encoder.writeToBuffer(slice, data);
    }
    
    @Override
    public int getSize() {
        return sizeInBytes;
    }
    
    /**
     * Gets the underlying Blaze3D GPU buffer.
     * Package-private for use by other OpenGL backend classes.
     * 
     * @return the GPU buffer
     */
    GpuBuffer getGpuBuffer() {
        return gpuBuffer;
    }
    
    @Override
    public void close() {
        if (!gpuBuffer.isClosed()) {
            gpuBuffer.close();
        }
    }
}

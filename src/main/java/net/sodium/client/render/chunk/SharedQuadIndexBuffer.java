package net.sodium.client.render.chunk;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.opengl.LegacyHandleGlBuffer;
import net.sodium.client.gl.buffer.GlBuffer;
import net.sodium.client.gl.buffer.GlBufferMapFlags;
import net.sodium.client.gl.buffer.GlBufferUsage;
import net.sodium.client.gl.buffer.GlMutableBuffer;
import net.sodium.client.gl.device.CommandList;
import net.sodium.client.gl.util.EnumBitField;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.sodium.client.util.NativeBuffer;
import net.vulkanic.VulkanicIndexType;

import java.nio.ByteBuffer;

public class SharedQuadIndexBuffer {
    private static final int ELEMENTS_PER_PRIMITIVE = 6;

    private final boolean nativeGpuBuffer;
    private final GlMutableBuffer buffer;
    private final IndexType indexType;
    private GpuBuffer gpuBuffer;

    private int maxPrimitives;

    public SharedQuadIndexBuffer(CommandList commandList, IndexType indexType) {
        this.nativeGpuBuffer = net.vulkanic.VulkanicAPI.isVulkanBackendInitializedAndSelected();
        this.buffer = this.nativeGpuBuffer ? null : commandList.createMutableBuffer();
        this.indexType = indexType;
    }

    public void ensureCapacity(CommandList commandList, int elementCount) {
        if (elementCount > this.indexType.getMaxElementCount()) {
            throw new IllegalArgumentException("Tried to reserve storage for more vertices in this buffer than it can hold");
        }

        int primitiveCount = elementCount / ELEMENTS_PER_PRIMITIVE;

        if (primitiveCount > this.maxPrimitives) {
            this.grow(commandList, this.getNextSize(primitiveCount));
        }
    }

    private int getNextSize(int primitiveCount) {
        return Math.min(Math.max(this.maxPrimitives * 2, primitiveCount + 16384), this.indexType.getMaxPrimitiveCount());
    }

    private void grow(CommandList commandList, int primitiveCount) {
        var bufferSize = primitiveCount * this.indexType.getBytesPerElement() * ELEMENTS_PER_PRIMITIVE;

        if (this.nativeGpuBuffer) {
            NativeBuffer indexBuffer = createIndexBuffer(this.indexType, primitiveCount);
            GpuBuffer previous = this.gpuBuffer;
            this.gpuBuffer = net.vulkanic.VulkanicAPI.createBuffer(
                    () -> "Shared quad index buffer",
                    GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
                    indexBuffer.getDirectBuffer());
            indexBuffer.free();

            if (previous != null) {
                previous.close();
            }
        } else {
            commandList.allocateStorage(this.buffer, bufferSize, GlBufferUsage.STATIC_DRAW);

            var mapped = commandList.mapBuffer(this.buffer, 0, bufferSize, EnumBitField.of(GlBufferMapFlags.INVALIDATE_BUFFER, GlBufferMapFlags.WRITE, GlBufferMapFlags.UNSYNCHRONIZED));
            this.indexType.createIndexBuffer(mapped.getMemoryBuffer(), primitiveCount);

            commandList.unmap(mapped);
        }

        this.maxPrimitives = primitiveCount;
    }

    public static NativeBuffer createIndexBuffer(IndexType indexType, int primitiveCount) {
        var bufferSize = primitiveCount * indexType.getBytesPerElement() * ELEMENTS_PER_PRIMITIVE;
        var buffer = new NativeBuffer(bufferSize);

        indexType.createIndexBuffer(buffer.getDirectBuffer(), primitiveCount);

        return buffer;
    }

    public GlBuffer getBufferObject() {
        if (this.buffer == null) {
            throw new UnsupportedOperationException("Native shared quad index buffer does not expose a legacy GL buffer");
        }
        return this.buffer;
    }

    public GpuBuffer gpuBufferView(int usage) {
        if (this.gpuBuffer != null) {
            return this.gpuBuffer;
        }
        return new LegacyHandleGlBuffer(() -> "Shared quad index buffer", usage, Math.toIntExact(this.buffer.getSize()), this.buffer.handle());
    }

    public void delete(CommandList commandList) {
        if (this.gpuBuffer != null) {
            this.gpuBuffer.close();
            this.gpuBuffer = null;
        }
        if (this.buffer != null) {
            commandList.deleteBuffer(this.buffer);
        }
    }

    public enum IndexType {
        SHORT(VulkanicIndexType.SHORT, 64 * 1024) {
            @Override
            public void createIndexBuffer(ByteBuffer byteBuffer, int primitiveCount) {
                NativeChunkMeshEncoder.writeSharedQuadIndexBuffer(byteBuffer, this.getBytesPerElement(), primitiveCount);
            }
        },
        INTEGER(VulkanicIndexType.INT, Integer.MAX_VALUE) {
            @Override
            public void createIndexBuffer(ByteBuffer byteBuffer, int primitiveCount) {
                NativeChunkMeshEncoder.writeSharedQuadIndexBuffer(byteBuffer, this.getBytesPerElement(), primitiveCount);
            }
        };

        public static final IndexType[] VALUES = IndexType.values();

        private final VulkanicIndexType format;
        private final int maxElementCount;

        IndexType(VulkanicIndexType format, int maxElementCount) {
            this.format = format;
            this.maxElementCount = maxElementCount;
        }

        public abstract void createIndexBuffer(ByteBuffer buffer, int primitiveCount);

        public int getBytesPerElement() {
            return this.format.bytesPerIndex();
        }

        public VulkanicIndexType getFormat() {
            return this.format;
        }

        public int getMaxPrimitiveCount() {
            return this.maxElementCount / 4;
        }

        public int getMaxElementCount() {
            return this.maxElementCount;
        }
    }
}

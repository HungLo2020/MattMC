package net.sodium.client.gl.device;

import net.sodium.client.gl.array.GlVertexArray;
import net.sodium.client.gl.buffer.*;
import net.sodium.client.gl.buffer.*;
import net.sodium.client.gl.sync.GlFence;
import net.sodium.client.gl.tessellation.GlPrimitiveType;
import net.sodium.client.gl.tessellation.GlTessellation;
import net.sodium.client.gl.tessellation.TessellationBinding;
import net.sodium.client.render.device.RenderBufferTarget;
import net.sodium.client.render.device.RenderTessellation;
import net.sodium.client.render.device.RenderTessellationBinding;
import net.vulkanic.VulkanicPrimitiveMode;
import net.sodium.client.gl.util.EnumBitField;

import java.nio.ByteBuffer;

public interface CommandList extends AutoCloseable {
    GlMutableBuffer createMutableBuffer();

    GlImmutableBuffer createImmutableBuffer(long bufferSize, EnumBitField<GlBufferStorageFlags> flags);

    GlTessellation createTessellation(GlPrimitiveType primitiveType, TessellationBinding[] bindings);

    RenderTessellation createTessellation(VulkanicPrimitiveMode primitiveMode, RenderTessellationBinding[] bindings);

    void bindVertexArray(GlVertexArray array);

    void uploadData(GlMutableBuffer glBuffer, ByteBuffer byteBuffer, GlBufferUsage usage);

    void copyBufferSubData(GlBuffer src, GlBuffer dst, long readOffset, long writeOffset, long bytes);

    void bindBuffer(GlBufferTarget target, GlBuffer buffer);

    void bindBuffer(RenderBufferTarget target, GlBuffer buffer);

    void unbindVertexArray();

    void allocateStorage(GlMutableBuffer buffer, long bufferSize, GlBufferUsage usage);

    void deleteBuffer(GlBuffer buffer);

    void deleteVertexArray(GlVertexArray vertexArray);

    void flush();

    DrawCommandList beginTessellating(GlTessellation tessellation);

    DrawCommandList beginTessellating(RenderTessellation tessellation);

    void deleteTessellation(GlTessellation tessellation);

    default void deleteTessellation(RenderTessellation tessellation) {
        tessellation.delete(this);
    }

    @Override
    default void close() {
        this.flush();
    }

    GlBufferMapping mapBuffer(GlBuffer buffer, long offset, long length, EnumBitField<GlBufferMapFlags> flags);

    void unmap(GlBufferMapping map);

    void flushMappedRange(GlBufferMapping map, int offset, int length);

    GlFence createFence();
}

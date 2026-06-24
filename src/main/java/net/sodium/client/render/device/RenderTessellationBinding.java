package net.sodium.client.render.device;

import net.blaze3d.buffers.GpuBuffer;
import net.sodium.client.gl.attribute.GlVertexAttributeBinding;
import net.sodium.client.gl.buffer.GlBuffer;
import net.sodium.client.gl.tessellation.TessellationBinding;
import org.jetbrains.annotations.Nullable;

public record RenderTessellationBinding(RenderBufferTarget target,
                                        @Nullable GlBuffer legacyBuffer,
                                        @Nullable GpuBuffer gpuBuffer,
                                        GlVertexAttributeBinding[] attributeBindings) {
    public static RenderTessellationBinding forVertexBuffer(GlBuffer buffer, GlVertexAttributeBinding[] attributes) {
        return new RenderTessellationBinding(RenderBufferTarget.VERTEX, buffer, null, attributes);
    }

    public static RenderTessellationBinding forVertexBuffer(GpuBuffer buffer, GlVertexAttributeBinding[] attributes) {
        return new RenderTessellationBinding(RenderBufferTarget.VERTEX, null, buffer, attributes);
    }

    public static RenderTessellationBinding forElementBuffer(GlBuffer buffer) {
        return new RenderTessellationBinding(RenderBufferTarget.INDEX, buffer, null, new GlVertexAttributeBinding[0]);
    }

    public static RenderTessellationBinding forElementBuffer(GpuBuffer buffer) {
        return new RenderTessellationBinding(RenderBufferTarget.INDEX, null, buffer, new GlVertexAttributeBinding[0]);
    }

    public GlBuffer requireLegacyBuffer() {
        if (this.legacyBuffer == null) {
            throw new IllegalStateException("OpenGL tessellation requires a legacy Sodium GlBuffer");
        }
        return this.legacyBuffer;
    }

    public GpuBuffer requireGpuBuffer() {
        if (this.gpuBuffer == null) {
            throw new IllegalStateException("Vulkan tessellation requires a backend-owned GpuBuffer");
        }
        return this.gpuBuffer;
    }

    public TessellationBinding toLegacyGlBinding() {
        return new TessellationBinding(this.target.toGlBufferTarget(), this.requireLegacyBuffer(), this.attributeBindings);
    }

    public static TessellationBinding[] toLegacyGlBindings(RenderTessellationBinding[] bindings) {
        TessellationBinding[] legacyBindings = new TessellationBinding[bindings.length];
        for (int i = 0; i < bindings.length; i++) {
            legacyBindings[i] = bindings[i].toLegacyGlBinding();
        }
        return legacyBindings;
    }
}

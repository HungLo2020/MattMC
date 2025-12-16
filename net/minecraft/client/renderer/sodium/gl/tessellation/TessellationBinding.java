package net.minecraft.client.renderer.sodium.gl.tessellation;

import net.minecraft.client.renderer.gl.advanced.attribute.GlVertexAttributeBinding;
import net.minecraft.client.renderer.gl.advanced.buffer.GlBuffer;
import net.minecraft.client.renderer.gl.advanced.buffer.GlBufferTarget;

public record TessellationBinding(GlBufferTarget target,
                                  GlBuffer buffer,
                                  GlVertexAttributeBinding[] attributeBindings) {
    public static TessellationBinding forVertexBuffer(GlBuffer buffer, GlVertexAttributeBinding[] attributes) {
        return new TessellationBinding(GlBufferTarget.ARRAY_BUFFER, buffer, attributes);
    }

    public static TessellationBinding forElementBuffer(GlBuffer buffer) {
        return new TessellationBinding(GlBufferTarget.ELEMENT_BUFFER, buffer, new GlVertexAttributeBinding[0]);
    }
}

package net.sodium.client.render.device;

import net.sodium.client.gl.attribute.GlVertexAttributeBinding;
import net.sodium.client.gl.buffer.GlBuffer;
import net.sodium.client.gl.tessellation.TessellationBinding;

public record RenderTessellationBinding(RenderBufferTarget target,
                                        GlBuffer buffer,
                                        GlVertexAttributeBinding[] attributeBindings) {
    public static RenderTessellationBinding forVertexBuffer(GlBuffer buffer, GlVertexAttributeBinding[] attributes) {
        return new RenderTessellationBinding(RenderBufferTarget.VERTEX, buffer, attributes);
    }

    public static RenderTessellationBinding forElementBuffer(GlBuffer buffer) {
        return new RenderTessellationBinding(RenderBufferTarget.INDEX, buffer, new GlVertexAttributeBinding[0]);
    }

    public TessellationBinding toLegacyGlBinding() {
        return new TessellationBinding(this.target.toGlBufferTarget(), this.buffer, this.attributeBindings);
    }

    public static TessellationBinding[] toLegacyGlBindings(RenderTessellationBinding[] bindings) {
        TessellationBinding[] legacyBindings = new TessellationBinding[bindings.length];
        for (int i = 0; i < bindings.length; i++) {
            legacyBindings[i] = bindings[i].toLegacyGlBinding();
        }
        return legacyBindings;
    }
}

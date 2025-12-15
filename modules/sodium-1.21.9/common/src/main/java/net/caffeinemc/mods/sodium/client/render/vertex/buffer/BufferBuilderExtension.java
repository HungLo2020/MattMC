package net.caffeinemc.mods.sodium.client.render.vertex.buffer;

import net.minecraft.client.renderer.advanced.vertex.buffer.VertexBufferWriter;

public interface BufferBuilderExtension extends VertexBufferWriter {
    void sodium$duplicateVertex();
}

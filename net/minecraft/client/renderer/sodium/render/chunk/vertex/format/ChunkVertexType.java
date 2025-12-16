package net.minecraft.client.renderer.sodium.render.chunk.vertex.format;

import net.minecraft.client.renderer.gl.advanced.attribute.GlVertexFormat;

public interface ChunkVertexType {
    GlVertexFormat getVertexFormat();

    ChunkVertexEncoder getEncoder();
}

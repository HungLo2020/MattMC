package net.minecraft.client.renderer.chunk.advanced.vertex.format;

import net.minecraft.client.renderer.gl.advanced.attribute.GlVertexFormat;

public interface ChunkVertexType {
    GlVertexFormat getVertexFormat();

    ChunkVertexEncoder getEncoder();
}

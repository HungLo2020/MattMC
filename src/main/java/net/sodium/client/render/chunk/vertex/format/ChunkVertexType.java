package net.sodium.client.render.chunk.vertex.format;

import net.sodium.client.gl.attribute.GlVertexFormat;

public interface ChunkVertexType {
    GlVertexFormat getVertexFormat();

    ChunkVertexEncoder getEncoder();
}

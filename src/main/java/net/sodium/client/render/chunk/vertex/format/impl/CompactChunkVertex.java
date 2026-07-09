package net.sodium.client.render.chunk.vertex.format.impl;

import net.sodium.client.gl.attribute.GlVertexFormat;
import net.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.sodium.client.render.chunk.vertex.format.NativeChunkVertexFormat;

public class CompactChunkVertex implements ChunkVertexType {
    public static final int STRIDE = 20;

    public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(STRIDE)
            .addElement(DefaultChunkMeshAttributes.POSITION, ChunkShaderBindingPoints.ATTRIBUTE_POSITION, 0)
            .addElement(DefaultChunkMeshAttributes.COLOR, ChunkShaderBindingPoints.ATTRIBUTE_COLOR, 8)
            .addElement(DefaultChunkMeshAttributes.TEXTURE, ChunkShaderBindingPoints.ATTRIBUTE_TEXTURE, 12)
            .addElement(DefaultChunkMeshAttributes.LIGHT_MATERIAL_INDEX, ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_MATERIAL_INDEX, 16)
            .build();

    public static final int POSITION_MAX_VALUE = 1 << 20;
    public static final int TEXTURE_MAX_VALUE = 1 << 15;

    private static final float MODEL_ORIGIN = 8.0f;
    private static final float MODEL_RANGE = 32.0f;

    @Override
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    @Override
    public NativeChunkVertexFormat getNativeFormat() {
        return NativeChunkVertexFormat.compact();
    }
}

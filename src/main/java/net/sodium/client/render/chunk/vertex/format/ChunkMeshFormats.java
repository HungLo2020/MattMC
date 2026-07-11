package net.sodium.client.render.chunk.vertex.format;

import net.sodium.client.gl.attribute.GlVertexFormat;
import net.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import net.sodium.client.render.chunk.vertex.format.impl.DefaultChunkMeshAttributes;

public class ChunkMeshFormats {
    public static final int COMPACT_TEXTURE_MAX_VALUE =
            NativeChunkMeshEncoder.compactFormatValue(NativeChunkMeshEncoder.COMPACT_VALUE_TEXTURE_MAX_VALUE);
    public static final int COMPACT_POSITION_MAX_VALUE =
            NativeChunkMeshEncoder.compactFormatValue(NativeChunkMeshEncoder.COMPACT_VALUE_POSITION_MAX_VALUE);

    public static final ChunkVertexType COMPACT = new ChunkVertexType() {
        private final NativeChunkVertexFormat nativeFormat = NativeChunkMeshEncoder.compactNativeFormat();
        private final GlVertexFormat vertexFormat = GlVertexFormat.builder(this.nativeFormat.stride())
                .addElement(DefaultChunkMeshAttributes.POSITION, ChunkShaderBindingPoints.ATTRIBUTE_POSITION,
                        NativeChunkMeshEncoder.compactFormatValue(NativeChunkMeshEncoder.COMPACT_VALUE_POSITION_OFFSET))
                .addElement(DefaultChunkMeshAttributes.COLOR, ChunkShaderBindingPoints.ATTRIBUTE_COLOR,
                        NativeChunkMeshEncoder.compactFormatValue(NativeChunkMeshEncoder.COMPACT_VALUE_COLOR_OFFSET))
                .addElement(DefaultChunkMeshAttributes.TEXTURE, ChunkShaderBindingPoints.ATTRIBUTE_TEXTURE,
                        NativeChunkMeshEncoder.compactFormatValue(NativeChunkMeshEncoder.COMPACT_VALUE_TEXTURE_OFFSET))
                .addElement(DefaultChunkMeshAttributes.LIGHT_MATERIAL_INDEX,
                        ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_MATERIAL_INDEX,
                        NativeChunkMeshEncoder.compactFormatValue(
                                NativeChunkMeshEncoder.COMPACT_VALUE_LIGHT_MATERIAL_INDEX_OFFSET))
                .build();

        @Override
        public GlVertexFormat getVertexFormat() {
            return this.vertexFormat;
        }

        @Override
        public NativeChunkVertexFormat getNativeFormat() {
            return this.nativeFormat;
        }
    };
}

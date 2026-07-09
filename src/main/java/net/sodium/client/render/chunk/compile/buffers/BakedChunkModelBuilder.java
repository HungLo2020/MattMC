package net.sodium.client.render.chunk.compile.buffers;

import net.blaze3d.vertex.VertexConsumer;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.sodium.client.render.chunk.terrain.material.Material;
import net.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.NotNull;

public class BakedChunkModelBuilder implements ChunkModelBuilder {
    private final ChunkMeshBufferBuilder[] vertexBuffers;
    private final NativeSectionMeshBuilder sectionBuilder;
    private final ChunkVertexConsumer fallbackVertexConsumer = new ChunkVertexConsumer(this);

    private BuiltSectionInfo.Builder renderData;

    public BakedChunkModelBuilder(ChunkMeshBufferBuilder[] vertexBuffers) {
        this(vertexBuffers, null);
    }

    public BakedChunkModelBuilder(ChunkMeshBufferBuilder[] vertexBuffers, NativeSectionMeshBuilder sectionBuilder) {
        this.vertexBuffers = vertexBuffers;
        this.sectionBuilder = sectionBuilder;
    }

    @Override
    public ChunkMeshBufferBuilder getVertexBuffer(ModelQuadFacing facing) {
        return this.vertexBuffers[facing.ordinal()];
    }

    @Override
    public void addSprite(@NotNull TextureAtlasSprite sprite) {
        this.renderData.addSprite(sprite);
    }

    @Override
    public VertexConsumer asFallbackVertexConsumer(Material material, TranslucentGeometryCollector collector) {
        fallbackVertexConsumer.setData(material, collector);
        return fallbackVertexConsumer;
    }

    public void destroy() {
        if (this.sectionBuilder != null) {
            this.sectionBuilder.close();
        } else {
            for (ChunkMeshBufferBuilder builder : this.vertexBuffers) {
                builder.destroy();
            }
        }
    }

    public void begin(BuiltSectionInfo.Builder renderData, int sectionIndex) {
        this.renderData = renderData;

        if (this.sectionBuilder != null) {
            this.sectionBuilder.start(sectionIndex);
        }
        for (var vertexBuffer : this.vertexBuffers) {
            vertexBuffer.start(sectionIndex);
        }
    }

    public NativeSectionMeshBuilder getSectionBuilder() {
        if (this.sectionBuilder != null) {
            return this.sectionBuilder;
        }

        return this.vertexBuffers[ModelQuadFacing.UNASSIGNED.ordinal()].sectionBuilder();
    }
}

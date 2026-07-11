package net.sodium.client.render.chunk.compile.buffers;

import net.blaze3d.vertex.VertexConsumer;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.sodium.client.render.chunk.terrain.material.Material;
import net.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.NotNull;

public class BakedChunkModelBuilder implements ChunkModelBuilder {
    private final NativeSectionMeshBuilder.FacingBuffer[] vertexBuffers;
    private final NativeSectionMeshBuilder sectionBuilder;
    private final ChunkVertexConsumer fallbackVertexConsumer = new ChunkVertexConsumer(this);

    private BuiltSectionInfo.Builder renderData;

    public BakedChunkModelBuilder(NativeSectionMeshBuilder.FacingBuffer[] vertexBuffers) {
        this(vertexBuffers, null);
    }

    public BakedChunkModelBuilder(NativeSectionMeshBuilder.FacingBuffer[] vertexBuffers,
            NativeSectionMeshBuilder sectionBuilder) {
        this.vertexBuffers = vertexBuffers;
        this.sectionBuilder = sectionBuilder;
    }

    @Override
    public NativeSectionMeshBuilder.FacingBuffer getVertexBuffer(ModelQuadFacing facing) {
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

    public int getFallbackConsumerEmittedQuadCount() {
        return this.fallbackVertexConsumer.getEmittedQuadCount();
    }

    public void resetFallbackConsumerEmittedQuadCount() {
        this.fallbackVertexConsumer.resetEmittedQuadCount();
    }

    public void destroy() {
        for (NativeSectionMeshBuilder.FacingBuffer builder : this.vertexBuffers) {
            builder.destroy();
        }

        if (this.sectionBuilder != null) {
            this.sectionBuilder.close();
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

    public void flushPending() {
        for (var vertexBuffer : this.vertexBuffers) {
            vertexBuffer.flushPending();
        }
    }

    public NativeSectionMeshBuilder getSectionBuilder() {
        this.flushPending();

        if (this.sectionBuilder != null) {
            return this.sectionBuilder;
        }

        return this.vertexBuffers[ModelQuadFacing.UNASSIGNED.ordinal()].sectionBuilder();
    }
}

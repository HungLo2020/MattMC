package net.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.sodium.client.render.chunk.translucent_sorting.quad.FullTQuad;
import net.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import net.sodium.client.render.chunk.vertex.format.NativeChunkVertexFormat;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;

import java.nio.ByteBuffer;

public class UpdatedQuadsList extends ReferenceArrayList<FullTQuad> {
    private int meshQuadCount;
    private int indexQuadCount;

    public int getMeshQuadCount() {
        return this.meshQuadCount;
    }

    public int getIndexQuadCount() {
        return this.indexQuadCount;
    }

    public void setQuadCounts(int meshQuadCount, int indexQuadCount) {
        this.meshQuadCount = meshQuadCount;
        this.indexQuadCount = indexQuadCount;
    }

    public void applyBufferUpdates(ChunkMeshBufferBuilder builder, ByteBuffer buffer) {
        this.applyBufferUpdates(builder.nativeFormat(), builder.sectionIndex(), buffer);
    }

    public void applyBufferUpdates(NativeChunkVertexFormat format, int sectionIndex, ByteBuffer buffer) {
        if (this.isEmpty()) {
            return;
        }

        try (NativeSectionMeshBuilder updateSectionBuilder = NativeSectionMeshBuilder.create(this.size())) {
            updateSectionBuilder.start(sectionIndex);
            ChunkMeshBufferBuilder updateBuffer = new ChunkMeshBufferBuilder(format,
                    updateSectionBuilder, net.sodium.client.model.quad.properties.ModelQuadFacing.UNASSIGNED.ordinal());
            int[] outputVertexOffsets = new int[this.size()];
            int updateCount = 0;

            for (var quad : this) {
                int writeToIndex = quad.getWriteToIndex();
                if (writeToIndex < 0) {
                    continue;
                }

                updateBuffer.push(quad.getVertices(), DefaultMaterials.TRANSLUCENT);
                outputVertexOffsets[updateCount] = TranslucentData.quadCountToVertexCount(writeToIndex);
                updateCount++;
            }

            updateBuffer.flushPending();
            updateSectionBuilder.encodeScatteredUnassigned(outputVertexOffsets, updateCount,
                    buffer, format, usesSeparateAo());
        }
    }

    private static boolean usesSeparateAo() {
        return net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.shouldUseSeparateAo();
    }
}

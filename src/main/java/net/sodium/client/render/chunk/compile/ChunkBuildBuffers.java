package net.sodium.client.render.chunk.compile;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.compile.buffers.BakedChunkModelBuilder;
import net.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.render.chunk.terrain.material.Material;
import net.sodium.client.render.chunk.translucent_sorting.bsp_tree.NativeUpdatedQuads;
import net.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.sodium.client.render.chunk.vertex.format.NativeChunkVertexFormat;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;

/**
 * A collection of temporary buffers for each worker thread which will be used to build chunk meshes for given render
 * passes. This makes a best-effort attempt to pick a suitable size for each scratch buffer, but will never try to
 * shrink a buffer.
 */
public class ChunkBuildBuffers {
    private final Reference2ReferenceOpenHashMap<TerrainRenderPass, BakedChunkModelBuilder> builders = new Reference2ReferenceOpenHashMap<>();

    private final ChunkVertexType vertexType;
    private final NativeChunkVertexFormat nativeFormat;

    public ChunkBuildBuffers(ChunkVertexType vertexType) {
        NativeChunkMeshEncoder.verifyAvailable();
        this.vertexType = vertexType;
        this.nativeFormat = vertexType.getNativeFormat();

        for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
            NativeSectionMeshBuilder sectionBuilder = NativeSectionMeshBuilder.create(128 * 1024 / 4);
            var vertexBuffers = new NativeSectionMeshBuilder.FacingBuffer[ModelQuadFacing.COUNT];

            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                vertexBuffers[facing] = new NativeSectionMeshBuilder.FacingBuffer(this.nativeFormat, sectionBuilder,
                        facing, false);
            }

            this.builders.put(pass, new BakedChunkModelBuilder(vertexBuffers, sectionBuilder));
        }
    }

    public void init(BuiltSectionInfo.Builder renderData, int sectionIndex) {
        for (var builder : this.builders.values()) {
            builder.begin(renderData, sectionIndex);
        }
    }

    public ChunkModelBuilder get(Material material) {
        return this.builders.get(material.pass);
    }

    public ChunkModelBuilder get(TerrainRenderPass pass) {
        return this.builders.get(pass);
    }

    public int getFallbackConsumerEmittedQuadCount() {
        int count = 0;
        for (var builder : this.builders.values()) {
            count += builder.getFallbackConsumerEmittedQuadCount();
        }
        return count;
    }

    public void resetFallbackConsumerEmittedQuadCount() {
        for (var builder : this.builders.values()) {
            builder.resetFallbackConsumerEmittedQuadCount();
        }
    }

    /**
     * Creates immutable baked chunk meshes from all non-empty scratch buffers. This is used after all blocks
     * have been rendered to pass the finished meshes over to the graphics card. This function can be called multiple
     * times to return multiple copies.
     */
    public BuiltSectionMeshParts createMesh(TerrainRenderPass pass, int visibleSlices, boolean forceUnassigned, boolean sliceReordering) {
        var builder = this.builders.get(pass);
        return builder.getSectionBuilder().finishMesh(this.nativeFormat, visibleSlices,
                forceUnassigned, sliceReordering, usesSeparateAo());
    }

    public BuiltSectionMeshParts createModifiedTranslucentMesh(NativeUpdatedQuads updatedQuads) {
        var builder = this.builders.get(DefaultTerrainRenderPasses.TRANSLUCENT);
        return builder.getSectionBuilder().finishModifiedTranslucentMesh(updatedQuads, this.nativeFormat,
                usesSeparateAo());
    }

    public int appendStaticModelSnapshot(TerrainRenderPass pass, long recordAddress, int recordCount,
            int sectionIndex, boolean storeRawQuads) {
        if (recordCount == 0) {
            return 0;
        }

        var builder = this.builders.get(pass);
        return builder.getSectionBuilder().appendStaticModelBatchEncoded(recordAddress, recordCount,
                this.nativeFormat, sectionIndex, usesSeparateAo(), storeRawQuads);
    }

    public int appendNativeSectionSnapshot(TerrainRenderPass pass, long recordAddress, int recordCount,
            int passId, int sectionIndex, boolean storeRawQuads, TranslucentGeometryCollector collector) {
        if (recordCount == 0) {
            return 0;
        }

        var builder = this.builders.get(pass);
        long analyzerHandle = pass.isTranslucent() && collector != null && collector.supportsNativeBatching()
                ? collector.nativeAnalyzerHandle()
                : 0L;
        return builder.getSectionBuilder().appendNativeSectionEncoded(recordAddress, recordCount, passId,
                this.nativeFormat, sectionIndex, usesSeparateAo(), storeRawQuads, analyzerHandle);
    }

    public int nativeFluidSpriteMask(TerrainRenderPass pass) {
        return this.builders.get(pass).getSectionBuilder().fluidSpriteMask();
    }

    public void destroy() {
        for (var builder : this.builders.values()) {
            builder.destroy();
        }
    }

    private static boolean usesSeparateAo() {
        return net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.shouldUseSeparateAo();
    }
}

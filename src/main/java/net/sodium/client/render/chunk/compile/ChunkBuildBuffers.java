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
import net.sodium.client.render.chunk.translucent_sorting.bsp_tree.UpdatedQuadsList;
import net.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.sodium.client.render.chunk.vertex.format.NativeChunkVertexFormat;
import net.sodium.client.util.NativeBuffer;

/**
 * A collection of temporary buffers for each worker thread which will be used to build chunk meshes for given render
 * passes. This makes a best-effort attempt to pick a suitable size for each scratch buffer, but will never try to
 * shrink a buffer.
 */
public class ChunkBuildBuffers {
    private static final int UNASSIGNED_SEGMENT_INDEX = ModelQuadFacing.UNASSIGNED.ordinal() << 1;

    private final Reference2ReferenceOpenHashMap<TerrainRenderPass, BakedChunkModelBuilder> builders = new Reference2ReferenceOpenHashMap<>();

    private final ChunkVertexType vertexType;
    private final NativeChunkVertexFormat nativeFormat;

    public ChunkBuildBuffers(ChunkVertexType vertexType) {
        NativeChunkMeshEncoder.verifyAvailable();
        this.vertexType = vertexType;
        this.nativeFormat = vertexType.getNativeFormat();

        for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
            var vertexBuffers = new ChunkMeshBufferBuilder[ModelQuadFacing.COUNT];

            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                vertexBuffers[facing] = new ChunkMeshBufferBuilder(this.vertexType, 128 * 1024);
            }

            this.builders.put(pass, new BakedChunkModelBuilder(vertexBuffers));
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

    public static int[] makeVertexSegments() {
        return new int[ModelQuadFacing.COUNT << 1];
    }

    /**
     * Creates immutable baked chunk meshes from all non-empty scratch buffers. This is used after all blocks
     * have been rendered to pass the finished meshes over to the graphics card. This function can be called multiple
     * times to return multiple copies.
     */
    public BuiltSectionMeshParts createMesh(TerrainRenderPass pass, int visibleSlices, boolean forceUnassigned, boolean sliceReordering) {
        var builder = this.builders.get(pass);
        int[] vertexSegments = makeVertexSegments();
        long[] logicalAddresses = new long[ModelQuadFacing.COUNT];
        int[] vertexCounts = new int[ModelQuadFacing.COUNT];
        int vertexTotal = collectLogicalInputs(builder, logicalAddresses, vertexCounts);

        if (vertexTotal == 0) {
            return null;
        }

        var mergedBuffer = new NativeBuffer(vertexTotal * this.nativeFormat.stride());
        NativeChunkMeshEncoder.assemble(logicalAddresses, vertexCounts, mergedBuffer.getDirectBuffer(), vertexSegments,
                this.nativeFormat, builder.getVertexBuffer(ModelQuadFacing.UNASSIGNED).sectionIndex(), visibleSlices,
                forceUnassigned, sliceReordering, usesSeparateAo());

        return new BuiltSectionMeshParts(mergedBuffer, vertexSegments);
    }

    public BuiltSectionMeshParts createModifiedTranslucentMesh(UpdatedQuadsList updatedQuads) {
        // mesh modification assumes non-empty mesh with predetermined size

        var builder = this.builders.get(DefaultTerrainRenderPasses.TRANSLUCENT);

        var vertexTotal = TranslucentData.quadCountToVertexCount(updatedQuads.getMeshQuadCount());
        var mergedBuffer = new NativeBuffer(vertexTotal * this.nativeFormat.stride());
        var mergedBufferBuilder = mergedBuffer.getDirectBuffer();
        long[] logicalAddresses = new long[ModelQuadFacing.COUNT];
        int[] vertexCounts = new int[ModelQuadFacing.COUNT];
        collectLogicalInputs(builder, logicalAddresses, vertexCounts);

        int[] ignoredSegments = makeVertexSegments();
        NativeChunkMeshEncoder.assemble(logicalAddresses, vertexCounts, mergedBufferBuilder, ignoredSegments,
                this.nativeFormat, builder.getVertexBuffer(ModelQuadFacing.UNASSIGNED).sectionIndex(), 0,
                true, false, usesSeparateAo());

        updatedQuads.applyBufferUpdates(builder.getVertexBuffer(ModelQuadFacing.UNASSIGNED), mergedBufferBuilder);

        int[] vertexSegments = makeVertexSegments();
        vertexSegments[UNASSIGNED_SEGMENT_INDEX] = vertexTotal;
        vertexSegments[UNASSIGNED_SEGMENT_INDEX + 1] = ModelQuadFacing.UNASSIGNED.ordinal();

        return new BuiltSectionMeshParts(mergedBuffer, vertexSegments);
    }

    public void destroy() {
        for (var builder : this.builders.values()) {
            builder.destroy();
        }
    }

    private static int collectLogicalInputs(BakedChunkModelBuilder builder, long[] logicalAddresses, int[] vertexCounts) {
        int vertexTotal = 0;

        for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            var buffer = builder.getVertexBuffer(facing);
            int facingIndex = facing.ordinal();
            logicalAddresses[facingIndex] = buffer.logicalAddress();
            vertexCounts[facingIndex] = buffer.count();
            vertexTotal += buffer.count();
        }

        return vertexTotal;
    }

    private static boolean usesSeparateAo() {
        return net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.shouldUseSeparateAo();
    }
}

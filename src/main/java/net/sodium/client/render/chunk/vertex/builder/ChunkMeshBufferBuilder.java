package net.sodium.client.render.chunk.vertex.builder;

import net.sodium.client.render.chunk.terrain.material.Material;
import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.sodium.client.render.chunk.vertex.format.NativeSectionMeshBuilder;
import net.sodium.client.render.chunk.vertex.format.NativeChunkVertexFormat;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class ChunkMeshBufferBuilder {
    private final NativeChunkVertexFormat nativeFormat;
    private final int nativeQuadStride = NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE;

    private final NativeSectionMeshBuilder sectionBuilder;
    private final int facing;
    private final boolean ownsSectionBuilder;
    private final NativeSectionMeshBuilder.StagingBuffers stagingBuffers;

    private int sectionIndex;
    private int pendingQuadCount;
    private TranslucentGeometryCollector pendingCollector;
    private ModelQuadFacing pendingCollectorFacing;

    public ChunkMeshBufferBuilder(ChunkVertexType vertexType, int initialCapacity) {
        this(vertexType.getNativeFormat(),
                NativeSectionMeshBuilder.create(Math.max(1, (initialCapacity + 3) >> 2)),
                net.sodium.client.model.quad.properties.ModelQuadFacing.UNASSIGNED.ordinal(), true);
    }

    public ChunkMeshBufferBuilder(NativeChunkVertexFormat nativeFormat, NativeSectionMeshBuilder sectionBuilder,
            int facing) {
        this(nativeFormat, sectionBuilder, facing, false);
    }

    private ChunkMeshBufferBuilder(NativeChunkVertexFormat nativeFormat, NativeSectionMeshBuilder sectionBuilder,
            int facing, boolean ownsSectionBuilder) {
        this.nativeFormat = nativeFormat;
        this.sectionBuilder = sectionBuilder;
        this.facing = facing;
        this.ownsSectionBuilder = ownsSectionBuilder;
        this.stagingBuffers = sectionBuilder.stagingBuffers(facing);
    }

    public void push(ChunkVertexEncoder.Vertex[] vertices, Material material) {
        this.push(vertices, material.bits());
    }

    public void push(ChunkVertexEncoder.Vertex[] vertices, int materialBits) {
        if (vertices.length != 4) {
            throw new IllegalArgumentException("Only quad primitives (with 4 vertices) can be pushed");
        }

        this.queueQuad(vertices, materialBits, null, null, 0);
    }

    public boolean pushTranslucent(ChunkVertexEncoder.Vertex[] vertices, int materialBits,
            TranslucentGeometryCollector collector, ModelQuadFacing facing, int packedNormal) {
        if (vertices.length != 4) {
            throw new IllegalArgumentException("Only quad primitives (with 4 vertices) can be pushed");
        }

        if (TranslucentGeometryCollector.isInvalidQuad(vertices)) {
            return true;
        }

        if (!collector.supportsNativeBatching()) {
            if (collector.appendQuad(vertices, facing, packedNormal)) {
                return true;
            }

            this.queueQuad(vertices, materialBits, null, null, 0);
            return false;
        }

        this.queueQuad(vertices, materialBits, collector, facing, packedNormal);
        return false;
    }

    public long prepareQuadAddress() {
        this.flushPending();
        return this.sectionBuilder.prepareQuadAddress(this.facing);
    }

    public void commitPreparedQuad() {
        this.sectionBuilder.commitQuad(this.facing);
    }

    public void start(int sectionIndex) {
        this.sectionIndex = sectionIndex;
        this.clearPending();

        if (this.ownsSectionBuilder) {
            this.sectionBuilder.start(sectionIndex);
        }
    }

    public void destroy() {
        if (this.ownsSectionBuilder) {
            this.sectionBuilder.close();
        }
    }

    public boolean isEmpty() {
        return this.count() == 0;
    }

    public ByteBuffer slice() {
        this.flushPending();

        if (this.isEmpty()) {
            throw new IllegalStateException("No vertex data in buffer");
        }

        return MemoryUtil.memByteBuffer(this.logicalAddress(), this.nativeQuadStride * (this.count() >> 2));
    }

    public int count() {
        this.flushPending();
        return this.sectionBuilder.facingVertexCount(this.facing);
    }

    public long logicalAddress() {
        this.flushPending();
        return this.sectionBuilder.facingAddress(this.facing);
    }

    public NativeChunkVertexFormat nativeFormat() {
        return this.nativeFormat;
    }

    public int sectionIndex() {
        return this.sectionIndex;
    }

    public NativeSectionMeshBuilder sectionBuilder() {
        this.flushPending();
        return this.sectionBuilder;
    }

    public void flushPending() {
        if (this.pendingQuadCount == 0) {
            return;
        }

        int quadCount = this.pendingQuadCount;
        TranslucentGeometryCollector collector = this.pendingCollector;

        if (collector == null) {
            int committedCount = this.sectionBuilder.appendBatch(this.facing, this.stagingBuffers.quadAddress(),
                    quadCount);
            if (committedCount != quadCount) {
                throw new IllegalStateException("Native batch committed " + committedCount
                        + " quads from an unfiltered batch of " + quadCount + " quads");
            }
        } else {
            NativeSectionMeshBuilder.TranslucentBatchResult result = this.sectionBuilder.appendTranslucentBatch(
                    this.facing, this.stagingBuffers.quadAddress(), quadCount, collector.nativeAnalyzerHandle(),
                    this.pendingCollectorFacing.ordinal(), this.stagingBuffers.packedNormalsAddress());

            if (result.validCount() != result.committedCount()) {
                throw new IllegalStateException("Native translucent batch accepted " + result.validCount()
                        + " quads but committed " + result.committedCount() + " quads");
            }
        }

        this.clearPending();
    }

    private void queueQuad(ChunkVertexEncoder.Vertex[] vertices, int materialBits,
            TranslucentGeometryCollector collector, ModelQuadFacing collectorFacing, int packedNormal) {
        if (!this.matchesPendingMode(collector, collectorFacing)) {
            this.flushPending();
        }

        if (this.pendingQuadCount == this.stagingBuffers.capacity()) {
            this.flushPending();
        }

        this.pendingCollector = collector;
        this.pendingCollectorFacing = collectorFacing;

        int quadIndex = this.pendingQuadCount;
        long quadAddress = this.stagingBuffers.quadAddress() + (long) quadIndex * this.nativeQuadStride;
        NativeChunkMeshEncoder.writeNativeQuad(quadAddress, vertices, materialBits);

        if (collector != null) {
            MemoryUtil.memPutInt(this.stagingBuffers.packedNormalsAddress() + (long) quadIndex * Integer.BYTES,
                    packedNormal);
        }

        this.pendingQuadCount++;
    }

    private boolean matchesPendingMode(TranslucentGeometryCollector collector, ModelQuadFacing collectorFacing) {
        if (this.pendingQuadCount == 0) {
            return true;
        }
        if (this.pendingCollector != collector) {
            return false;
        }

        return collector == null || this.pendingCollectorFacing == collectorFacing;
    }

    private void clearPending() {
        this.pendingQuadCount = 0;
        this.pendingCollector = null;
        this.pendingCollectorFacing = null;
    }
}

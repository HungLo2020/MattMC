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

    private int sectionIndex;

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
    }

    public void push(ChunkVertexEncoder.Vertex[] vertices, Material material) {
        this.push(vertices, material.bits());
    }

    public void push(ChunkVertexEncoder.Vertex[] vertices, int materialBits) {
        if (vertices.length != 4) {
            throw new IllegalArgumentException("Only quad primitives (with 4 vertices) can be pushed");
        }

        long address = this.prepareQuadAddress();
        NativeChunkMeshEncoder.writeNativeQuad(address, vertices, materialBits);
        this.commitPreparedQuad();
    }

    public boolean pushTranslucent(ChunkVertexEncoder.Vertex[] vertices, int materialBits,
            TranslucentGeometryCollector collector, ModelQuadFacing facing, int packedNormal) {
        if (vertices.length != 4) {
            throw new IllegalArgumentException("Only quad primitives (with 4 vertices) can be pushed");
        }

        long address = this.prepareQuadAddress();
        NativeChunkMeshEncoder.writeNativeQuad(address, vertices, materialBits);
        if (collector.appendNativeQuad(address, vertices, facing, packedNormal)) {
            return true;
        }

        this.commitPreparedQuad();
        return false;
    }

    public long prepareQuadAddress() {
        return this.sectionBuilder.prepareQuadAddress(this.facing);
    }

    public void commitPreparedQuad() {
        this.sectionBuilder.commitQuad(this.facing);
    }

    public void start(int sectionIndex) {
        this.sectionIndex = sectionIndex;

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
        if (this.isEmpty()) {
            throw new IllegalStateException("No vertex data in buffer");
        }

        return MemoryUtil.memByteBuffer(this.logicalAddress(), this.nativeQuadStride * (this.count() >> 2));
    }

    public int count() {
        return this.sectionBuilder.facingVertexCount(this.facing);
    }

    public long logicalAddress() {
        return this.sectionBuilder.facingAddress(this.facing);
    }

    public NativeChunkVertexFormat nativeFormat() {
        return this.nativeFormat;
    }

    public int sectionIndex() {
        return this.sectionIndex;
    }

    public NativeSectionMeshBuilder sectionBuilder() {
        return this.sectionBuilder;
    }
}

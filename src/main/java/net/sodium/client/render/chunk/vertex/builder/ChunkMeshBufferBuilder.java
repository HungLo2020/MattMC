package net.sodium.client.render.chunk.vertex.builder;

import net.sodium.client.render.chunk.terrain.material.Material;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.sodium.client.render.chunk.vertex.format.NativeChunkVertexFormat;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class ChunkMeshBufferBuilder {
    private static final int VERTEX_STRIDE = 32;
    private static final int OFFSET_X = 0;
    private static final int OFFSET_Y = 4;
    private static final int OFFSET_Z = 8;
    private static final int OFFSET_COLOR = 12;
    private static final int OFFSET_AO = 16;
    private static final int OFFSET_U = 20;
    private static final int OFFSET_V = 24;
    private static final int OFFSET_LIGHT = 28;

    private static final int OFFSET_BLOCK_EMISSION = VERTEX_STRIDE * 4;
    private static final int OFFSET_RENDER_TYPE = OFFSET_BLOCK_EMISSION + 1;
    private static final int OFFSET_IGNORE_MID_BLOCK = OFFSET_BLOCK_EMISSION + 2;
    private static final int OFFSET_BLOCK_ID = OFFSET_BLOCK_EMISSION + 4;
    private static final int OFFSET_LOCAL_X = OFFSET_BLOCK_ID + 4;
    private static final int OFFSET_LOCAL_Y = OFFSET_LOCAL_X + 4;
    private static final int OFFSET_LOCAL_Z = OFFSET_LOCAL_Y + 4;
    private static final int OFFSET_MATERIAL_BITS = OFFSET_LOCAL_Z + 4;

    private final NativeChunkVertexFormat nativeFormat;
    private final int nativeQuadStride = NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE;

    private final int initialQuadCapacity;

    private ByteBuffer buffer;
    private ByteBuffer scratchQuad;
    private int quadCount;
    private int quadCapacity;

    private int sectionIndex;

    public ChunkMeshBufferBuilder(ChunkVertexType vertexType, int initialCapacity) {
        this.nativeFormat = vertexType.getNativeFormat();

        this.buffer = null;
        this.scratchQuad = null;

        this.quadCapacity = Math.max(1, (initialCapacity + 3) >> 2);
        this.initialQuadCapacity = this.quadCapacity;
    }

    public void push(ChunkVertexEncoder.Vertex[] vertices, Material material) {
        this.push(vertices, material.bits());
    }

    public void push(ChunkVertexEncoder.Vertex[] vertices, int materialBits) {
        if (vertices.length != 4) {
            throw new IllegalArgumentException("Only quad primitives (with 4 vertices) can be pushed");
        }

        this.ensureCapacity(1);

        writeNativeQuad(MemoryUtil.memAddress(this.buffer, this.quadCount * this.nativeQuadStride), vertices, materialBits);

        this.quadCount++;
    }

    public void writeExternal(ByteBuffer buffer, int position, ChunkVertexEncoder.Vertex[] vertices, Material material) {
        if (this.scratchQuad == null) {
            this.scratchQuad = MemoryUtil.memAlloc(this.nativeQuadStride);
        }

        writeNativeQuad(MemoryUtil.memAddress(this.scratchQuad), vertices, material.bits());
        NativeChunkMeshEncoder.encode(this.scratchQuad, 4, buffer, position, this.nativeFormat, this.sectionIndex, usesSeparateAo());
    }

    private void ensureCapacity(int quadCount) {
        if (this.quadCount + quadCount >= this.quadCapacity) {
            this.grow(quadCount);
        }
    }

    private void grow(int quadCount) {
        this.reallocate(
                // The new capacity will at least twice as large
                Math.max(this.quadCapacity * 2, this.quadCapacity + quadCount)
        );
    }

    private void reallocate(int quadCount) {
        this.buffer = MemoryUtil.memRealloc(this.buffer, quadCount * this.nativeQuadStride);
        this.quadCapacity = quadCount;
    }

    public void start(int sectionIndex) {
        this.quadCount = 0;
        this.sectionIndex = sectionIndex;

        this.reallocate(this.initialQuadCapacity);
    }

    public void destroy() {
        if (this.buffer != null) {
            MemoryUtil.memFree(this.buffer);
        }

        this.buffer = null;

        if (this.scratchQuad != null) {
            MemoryUtil.memFree(this.scratchQuad);
        }

        this.scratchQuad = null;
    }

    public boolean isEmpty() {
        return this.quadCount == 0;
    }

    public ByteBuffer slice() {
        if (this.isEmpty()) {
            throw new IllegalStateException("No vertex data in buffer");
        }

        return MemoryUtil.memSlice(this.buffer, 0, this.nativeQuadStride * this.quadCount);
    }

    public int count() {
        return this.quadCount << 2;
    }

    public long logicalAddress() {
        return this.isEmpty() ? 0L : MemoryUtil.memAddress(this.buffer);
    }

    public NativeChunkVertexFormat nativeFormat() {
        return this.nativeFormat;
    }

    public int sectionIndex() {
        return this.sectionIndex;
    }

    private static boolean usesSeparateAo() {
        return net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.shouldUseSeparateAo();
    }

    private static void writeNativeQuad(long ptr, ChunkVertexEncoder.Vertex[] vertices, int materialBits) {
        long vertexPtr = ptr;

        for (ChunkVertexEncoder.Vertex vertex : vertices) {
            writeNativeQuadVertex(vertexPtr, vertex);
            vertexPtr += VERTEX_STRIDE;
        }

        var extension = (net.irisshaders.iris.vertices.sodium.terrain.ChunkVertexExtension) vertices[0];

        MemoryUtil.memPutByte(ptr + OFFSET_BLOCK_EMISSION, extension.getBlockEmission());
        MemoryUtil.memPutByte(ptr + OFFSET_RENDER_TYPE, extension.getRenderType());
        MemoryUtil.memPutByte(ptr + OFFSET_IGNORE_MID_BLOCK, (byte) (extension.ignoreMidBlock() ? 1 : 0));
        MemoryUtil.memPutByte(ptr + OFFSET_BLOCK_EMISSION + 3, (byte) 0);
        MemoryUtil.memPutInt(ptr + OFFSET_BLOCK_ID, extension.getBlockId());
        MemoryUtil.memPutInt(ptr + OFFSET_LOCAL_X, extension.getLocalPosX());
        MemoryUtil.memPutInt(ptr + OFFSET_LOCAL_Y, extension.getLocalPosY());
        MemoryUtil.memPutInt(ptr + OFFSET_LOCAL_Z, extension.getLocalPosZ());
        MemoryUtil.memPutInt(ptr + OFFSET_MATERIAL_BITS, materialBits);
    }

    private static void writeNativeQuadVertex(long ptr, ChunkVertexEncoder.Vertex vertex) {
        MemoryUtil.memPutFloat(ptr + OFFSET_X, vertex.x);
        MemoryUtil.memPutFloat(ptr + OFFSET_Y, vertex.y);
        MemoryUtil.memPutFloat(ptr + OFFSET_Z, vertex.z);
        MemoryUtil.memPutInt(ptr + OFFSET_COLOR, vertex.color);
        MemoryUtil.memPutFloat(ptr + OFFSET_AO, vertex.ao);
        MemoryUtil.memPutFloat(ptr + OFFSET_U, vertex.u);
        MemoryUtil.memPutFloat(ptr + OFFSET_V, vertex.v);
        MemoryUtil.memPutInt(ptr + OFFSET_LIGHT, vertex.light);
    }
}

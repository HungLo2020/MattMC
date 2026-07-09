package net.sodium.client.render.chunk.vertex.builder;

import net.sodium.client.render.chunk.terrain.material.Material;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.sodium.client.render.chunk.vertex.format.NativeChunkMeshEncoder;
import net.sodium.client.render.chunk.vertex.format.NativeChunkQuadBuffer;
import net.sodium.client.render.chunk.vertex.format.NativeChunkVertexFormat;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class ChunkMeshBufferBuilder {
    private final NativeChunkVertexFormat nativeFormat;
    private final int nativeQuadStride = NativeChunkMeshEncoder.NATIVE_QUAD_STRIDE;

    private final int initialQuadCapacity;

    private NativeChunkQuadBuffer buffer;
    private int quadCount;
    private int quadCapacity;

    private int sectionIndex;

    public ChunkMeshBufferBuilder(ChunkVertexType vertexType, int initialCapacity) {
        this.nativeFormat = vertexType.getNativeFormat();

        this.buffer = null;

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

        NativeChunkMeshEncoder.writeNativeQuad(this.buffer.addressAt(this.quadCount), vertices, materialBits);

        this.quadCount++;
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
        if (this.buffer == null) {
            this.buffer = NativeChunkQuadBuffer.create(quadCount);
        } else {
            this.buffer.ensureCapacity(quadCount);
        }
        this.quadCapacity = this.buffer.capacity();
    }

    public void start(int sectionIndex) {
        this.quadCount = 0;
        this.sectionIndex = sectionIndex;

        this.reallocate(this.initialQuadCapacity);
    }

    public void destroy() {
        if (this.buffer != null) {
            this.buffer.close();
        }

        this.buffer = null;
    }

    public boolean isEmpty() {
        return this.quadCount == 0;
    }

    public ByteBuffer slice() {
        if (this.isEmpty()) {
            throw new IllegalStateException("No vertex data in buffer");
        }

        return MemoryUtil.memByteBuffer(this.buffer.address(), this.nativeQuadStride * this.quadCount);
    }

    public int count() {
        return this.quadCount << 2;
    }

    public long logicalAddress() {
        return this.isEmpty() ? 0L : this.buffer.address();
    }

    public NativeChunkVertexFormat nativeFormat() {
        return this.nativeFormat;
    }

    public int sectionIndex() {
        return this.sectionIndex;
    }
}

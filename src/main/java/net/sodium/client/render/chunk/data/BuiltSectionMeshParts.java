package net.sodium.client.render.chunk.data;

import net.sodium.client.model.quad.properties.ModelQuadFacing;
import net.sodium.client.util.NativeBuffer;

/**
 * The array of vertex segments is structured as follows:
 * - It consists of 2 * ModelQuadFacing.COUNT ints.
 * - The first and every second int after that are vertex counts.
 * - The second and every second int after that are the ModelQuadFacing index that the preceding count applies to.
 * - If the vertex count is zero, the segment is not used and reading the facing index is undefined behavior.
 * - The array of vertex segments starts with some number of filled segments, followed by empty segments for the rest of the fixed size.
 */
public class BuiltSectionMeshParts {
    private final int[] vertexSegments;
    private final int[] primitiveMetadata;
    private final NativeBuffer buffer;

    public BuiltSectionMeshParts(NativeBuffer buffer, int[] vertexSegments) {
        this(buffer, vertexSegments, new int[0]);
    }

    public BuiltSectionMeshParts(NativeBuffer buffer, int[] vertexSegments, int[] primitiveMetadata) {
        this.vertexSegments = vertexSegments;
        this.primitiveMetadata = primitiveMetadata;
        this.buffer = buffer;
    }

    public NativeBuffer getVertexData() {
        return this.buffer;
    }

    public int[] getVertexSegments() {
        return this.vertexSegments;
    }

    public int[] getPrimitiveMetadata() {
        return this.primitiveMetadata;
    }

}
